package ru.rkhamatyarov.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.quarkus.runtime.StartupEvent
import io.quarkus.websockets.next.OnClose
import io.quarkus.websockets.next.OnOpen
import io.quarkus.websockets.next.OnTextMessage
import io.quarkus.websockets.next.WebSocket
import io.quarkus.websockets.next.WebSocketConnection
import io.vertx.core.Vertx
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import org.jboss.logging.Logger
import ru.rkhamatyarov.proto.GameStateDelta
import ru.rkhamatyarov.service.GameEngine
import ru.rkhamatyarov.service.PowerUpManager
import ru.rkhamatyarov.service.StateHistory
import java.util.concurrent.ConcurrentHashMap

@WebSocket(path = "/game")
@ApplicationScoped
class GameWebSocket {
    private val log = Logger.getLogger(javaClass)

    @Inject
    lateinit var engine: GameEngine

    @Inject
    lateinit var powerUpManager: PowerUpManager

    @Inject
    lateinit var mapper: ObjectMapper

    @Inject
    lateinit var vertx: Vertx

    @Inject
    lateinit var history: StateHistory

    private val sessions = ConcurrentHashMap<String, WebSocketConnection>()
    private var lastFullState: GameStateDelta? = null

    private val timeshiftSessions = ConcurrentHashMap.newKeySet<String>()
    private val timeshiftDrafts = ConcurrentHashMap<String, GameStateDelta>()
    private var pauseAnchorNs: Long? = null

    fun onStart(
        @Observes event: StartupEvent,
    ) {
        vertx.setPeriodic(16L) { tick() }
    }

    @OnOpen
    fun onOpen(connection: WebSocketConnection) {
        sessions[connection.id()] = connection
        log.info("Client connected: ${connection.id()} (total: ${sessions.size})")
        lastFullState = null
        connection.sendBinary(currentStateBinary(true))
    }

    @OnClose
    fun onClose(connection: WebSocketConnection) {
        sessions.remove(connection.id())
        timeshiftSessions.remove(connection.id())
        timeshiftDrafts.remove(connection.id())
        log.info("Client disconnected: ${connection.id()} (remaining: ${sessions.size})")
    }

    @OnTextMessage
    fun onMessage(
        message: String,
        connection: WebSocketConnection,
    ) {
        try {
            if (message.isBlank()) {
                sendError(connection, "Empty message")
                return
            }
            handleCommand(mapper.readValue<Map<String, Any>>(message), connection)
        } catch (e: Exception) {
            log.error("Failed to handle message: $message", e)
            sendError(connection, "Invalid command format")
        }
    }

    private fun handleCommand(
        cmd: Map<String, Any>,
        connection: WebSocketConnection,
    ) {
        val type =
            cmd["type"]?.toString()?.uppercase()
                ?: return sendError(connection, "Missing 'type'")
        val data = cmd["data"]?.let { it as? Map<*, *> } ?: cmd

        when (type) {
            "MOVE_PADDLE" -> {
                handleMovePaddle(data, connection)
            }

            "TOGGLE_PAUSE" -> {
                handleTogglePause()
            }

            "RESET" -> {
                handleReset()
            }

            "CLEAR_LINES" -> {
                handleClearLines()
            }

            "START_LINE" -> {
                handleStartLine(data, connection)
            }

            "UPDATE_LINE" -> {
                handleUpdateLine(data, connection)
            }

            "FINISH_LINE" -> {
                handleFinishLine(connection)
            }

            "SET_SPEED" -> {
                handleSetSpeed(data)
            }

            "SPAWN_POWERUP" -> {
                handleSpawnPowerUp()
            }

            "TIMESHIFT" -> {
                handleTimeshift(data, connection)
            }

            "COMMIT_TIMESHIFT" -> {
                handleCommitTimeshift(data, connection)
            }

            "RESUME" -> {
                handleResume(connection)
            }

            else -> {
                log.warn("Unknown command: $type")
                sendError(connection, "Unknown command: $type")
            }
        }
    }

    private fun handleTimeshift(
        data: Map<*, *>,
        connection: WebSocketConnection,
    ) {
        val offset = data["offset"]?.toString()?.toDoubleOrNull()
        if (offset == null || offset < 0.0) {
            sendError(connection, "TIMESHIFT requires a numeric 'offset' >= 0 (seconds)")
            return
        }
        val snapshot = history.getByOffsetSeconds(offset, rewindReferenceNs())
        if (snapshot == null) {
            sendError(
                connection,
                "No snapshot available for offset ${offset}s " +
                    "(history has ${history.size()} frames, " +
                    "max retention ${StateHistory.MAX_RETENTION_SECONDS}s)",
            )
            return
        }
        val pausedAwareSnapshot = snapshot.withCurrentPauseState()
        timeshiftSessions.add(connection.id())
        timeshiftDrafts[connection.id()] = GameStateDelta.parseFrom(pausedAwareSnapshot)
        connection.sendBinary(pausedAwareSnapshot).subscribe().with(
            {},
            { t -> log.warnf(t, "Failed to send timeshift snapshot to %s", connection.id()) },
        )
    }

    private fun handleCommitTimeshift(
        data: Map<*, *>,
        connection: WebSocketConnection,
    ) {
        val offset = data["offset"]?.toString()?.toDoubleOrNull()
        if (offset == null || offset < 0.0) {
            sendError(connection, "COMMIT_TIMESHIFT requires a numeric 'offset' >= 0 (seconds)")
            return
        }
        val snapshot =
            timeshiftDrafts[connection.id()]?.toByteArray()
                ?: history.getByOffsetSeconds(offset, rewindReferenceNs())
        if (snapshot == null) {
            sendError(connection, "No snapshot available for offset ${offset}s")
            return
        }

        try {
            engine.restoreFromDelta(GameStateDelta.parseFrom(snapshot))
            history.clear()
            lastFullState = null
            timeshiftSessions.clear()
            timeshiftDrafts.clear()
            broadcastFullState()
            log.infof("Timeline branched from %.2f seconds ago by %s", offset, connection.id())
        } catch (e: Exception) {
            log.error("Failed to commit timeshift snapshot", e)
            sendError(connection, "Failed to restore snapshot")
        }
    }

    private fun handleResume(connection: WebSocketConnection) {
        timeshiftSessions.remove(connection.id())
        timeshiftDrafts.remove(connection.id())
        log.debugf("Connection %s resumed live stream", connection.id())
        connection.sendBinary(currentStateBinary(true)).subscribe().with(
            {},
            { t -> log.warnf(t, "Failed to send resume snapshot to %s", connection.id()) },
        )
    }

    private fun handleMovePaddle(
        data: Map<*, *>,
        connection: WebSocketConnection,
    ) {
        val y = data["y"]?.toString()?.toDoubleOrNull()
        if (y != null) {
            engine.movePaddle2(y)
        } else {
            sendError(connection, "Invalid MOVE_PADDLE: y required")
        }
    }

    private fun handleTogglePause() {
        engine.paused = !engine.paused
        pauseAnchorNs =
            if (engine.paused) {
                System.nanoTime().also { pausedAt ->
                    history.add(buildGameStateDelta(pausedAt).toByteArray(), pausedAt)
                }
            } else {
                null
            }
    }

    private fun handleReset() {
        engine.resetPuck()
        engine.clearLines()
        engine.paused = false
        pauseAnchorNs = null
    }

    private fun handleClearLines() {
        engine.clearLines()
    }

    private fun handleStartLine(
        data: Map<*, *>,
        connection: WebSocketConnection,
    ) {
        val x = data["x"]?.toString()?.toDoubleOrNull()
        val y = data["y"]?.toString()?.toDoubleOrNull()
        if (x != null && y != null) {
            if (updateTimeshiftDraftLine(connection, LineDraftCommand.START, x, y)) return
            engine.startNewLine(x, y)
        } else {
            sendError(connection, "Invalid START_LINE: x and y required")
        }
    }

    private fun handleUpdateLine(
        data: Map<*, *>,
        connection: WebSocketConnection,
    ) {
        val x = data["x"]?.toString()?.toDoubleOrNull()
        val y = data["y"]?.toString()?.toDoubleOrNull()
        if (x != null && y != null) {
            if (updateTimeshiftDraftLine(connection, LineDraftCommand.UPDATE, x, y)) return
            engine.updateCurrentLine(x, y)
        } else {
            sendError(connection, "Invalid UPDATE_LINE: x and y required")
        }
    }

    private fun handleFinishLine(connection: WebSocketConnection) {
        if (updateTimeshiftDraftLine(connection, LineDraftCommand.FINISH, null, null)) return
        engine.finishCurrentLine()
    }

    private fun handleSetSpeed(data: Map<*, *>) {
        log.info("SET_SPEED received")
    }

    private fun handleSpawnPowerUp() {
        log.info("SPAWN_POWERUP received")
    }

    private enum class LineDraftCommand {
        START,
        UPDATE,
        FINISH,
    }

    private fun updateTimeshiftDraftLine(
        connection: WebSocketConnection,
        command: LineDraftCommand,
        x: Double?,
        y: Double?,
    ): Boolean {
        val id = connection.id()
        if (id !in timeshiftSessions) return false

        val current =
            timeshiftDrafts[id]
                ?: return true.also { sendError(connection, "No active timeshift snapshot") }

        val lines = current.linesList.map { it.toBuilder() }.toMutableList()
        when (command) {
            LineDraftCommand.START -> {
                if (x == null || y == null) return true
                lines.add(
                    ru.rkhamatyarov.proto.Line
                        .newBuilder()
                        .setWidth(5.0)
                        .setIsAnimating(true)
                        .addPoints(protoPoint(x, y)),
                )
            }

            LineDraftCommand.UPDATE -> {
                if (x == null || y == null) return true
                val line = lines.lastOrNull { it.isAnimating } ?: lines.lastOrNull() ?: return true
                line.addPoints(protoPoint(x, y))
            }

            LineDraftCommand.FINISH -> {
                lines.lastOrNull { it.isAnimating }?.setIsAnimating(false)
            }
        }

        val updated =
            current
                .toBuilder()
                .clearLines()
                .addAllLines(lines.map { it.build() })
                .setFullState(true)
                .build()

        timeshiftDrafts[id] = updated
        connection.sendBinary(updated.toByteArray()).subscribe().with(
            {},
            { t -> log.warnf(t, "Failed to send timeshift draft to %s", id) },
        )
        return true
    }

    private fun protoPoint(
        x: Double,
        y: Double,
    ): ru.rkhamatyarov.proto.Point =
        ru.rkhamatyarov.proto.Point
            .newBuilder()
            .setX(x)
            .setY(y)
            .build()

    private fun rewindReferenceNs(): Long = pauseAnchorNs ?: System.nanoTime()

    private fun ByteArray.withCurrentPauseState(): ByteArray {
        if (!engine.paused) return this
        return GameStateDelta
            .parseFrom(this)
            .toBuilder()
            .setPaused(true)
            .setFullState(true)
            .build()
            .toByteArray()
    }

    private fun tick() {
        engine.tick(deltaSeconds = 0.016)
        powerUpManager.update(0.016)

        val now = System.nanoTime()

        val currentState = buildGameStateDelta(now)

        val liveState =
            if (lastFullState == null) {
                currentState
            } else {
                computeDelta(lastFullState!!, currentState)
            }
        val liveBytes = liveState.toByteArray()
        val fullBytes = currentState.toByteArray()

        lastFullState = currentState

        if (!engine.paused) {
            history.add(fullBytes)
        }

        if (sessions.isEmpty()) return

        val dead = mutableListOf<String>()

        sessions.forEach { (id, conn) ->
            if (id in timeshiftSessions) return@forEach
            conn.sendBinary(liveBytes).subscribe().with({}, { t ->
                log.warn("Failed to send to $id: ${t.message}")
                dead.add(id)
            })
        }
        dead.forEach {
            sessions.remove(it)
            timeshiftSessions.remove(it)
        }
    }

    private fun broadcastFullState() {
        val bytes = currentStateBinary(true)
        val dead = mutableListOf<String>()
        sessions.forEach { (id, conn) ->
            conn.sendBinary(bytes).subscribe().with({}, { t ->
                log.warn("Failed to send full state to $id: ${t.message}")
                dead.add(id)
            })
        }
        dead.forEach { sessions.remove(it) }
    }

    private fun currentStateBinary(full: Boolean): ByteArray {
        val now = System.nanoTime()
        val newState = buildGameStateDelta(now)
        val result =
            if (full || lastFullState == null) {
                newState
            } else {
                computeDelta(lastFullState!!, newState)
            }
        lastFullState = newState
        return result.toByteArray()
    }

    private fun buildGameStateDelta(nowNs: Long): GameStateDelta = engine.toGameStateDelta(nowNs)

    private fun computeDelta(
        prev: GameStateDelta,
        curr: GameStateDelta,
    ): GameStateDelta {
        val b = GameStateDelta.newBuilder()
        if (curr.puckX != prev.puckX) b.puckX = curr.puckX
        if (curr.puckY != prev.puckY) b.puckY = curr.puckY
        if (curr.puckVx != prev.puckVx) b.puckVx = curr.puckVx
        if (curr.puckVy != prev.puckVy) b.puckVy = curr.puckVy
        if (curr.paddle1Y != prev.paddle1Y) b.paddle1Y = curr.paddle1Y
        if (curr.paddle2Y != prev.paddle2Y) b.paddle2Y = curr.paddle2Y
        if (curr.scoreA != prev.scoreA) b.scoreA = curr.scoreA
        if (curr.scoreB != prev.scoreB) b.scoreB = curr.scoreB
        if (curr.paused != prev.paused) b.paused = curr.paused
        if (curr.linesList != prev.linesList) b.addAllLines(curr.linesList)
        if (curr.powerUpsList != prev.powerUpsList) b.addAllPowerUps(curr.powerUpsList)
        if (curr.activePowerUpsList != prev.activePowerUpsList) b.addAllActivePowerUps(curr.activePowerUpsList)
        return b.build()
    }

    private fun sendError(
        connection: WebSocketConnection,
        message: String,
    ) {
        try {
            connection.sendTextAndAwait(
                mapper.writeValueAsString(mapOf("type" to "ERROR", "message" to message)),
            )
        } catch (e: Exception) {
            log.error("Failed to send error to ${connection.id()}", e)
        }
    }
}
