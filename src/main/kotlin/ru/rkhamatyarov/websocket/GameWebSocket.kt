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
import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.proto.GameStateDelta
import ru.rkhamatyarov.service.GameEngine
import ru.rkhamatyarov.service.PowerUpManager
import ru.rkhamatyarov.service.StateHistory
import java.util.concurrent.ConcurrentHashMap

@WebSocket(path = "/game")
@ApplicationScoped
class GameWebSocket {
    private val log = Logger.getLogger(javaClass)

    @Inject lateinit var engine: GameEngine

    @Inject lateinit var powerUpManager: PowerUpManager

    @Inject lateinit var mapper: ObjectMapper

    @Inject lateinit var vertx: Vertx

    @Inject lateinit var history: StateHistory

    private val sessions = ConcurrentHashMap<String, WebSocketConnection>()
    private var lastFullState: GameStateDelta? = null

    private val timeshiftSessions = ConcurrentHashMap.newKeySet<String>()
    private var ticksSinceFullSnapshot = 0

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
                handleFinishLine()
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
        val snapshot = history.getByOffsetSeconds(offset)
        if (snapshot == null) {
            sendError(
                connection,
                "No snapshot available for offset ${offset}s " +
                    "(history has ${history.size()} frames, " +
                    "max retention ${StateHistory.MAX_RETENTION_SECONDS}s)",
            )
            return
        }
        timeshiftSessions.add(connection.id())
        connection.sendBinary(snapshot).subscribe().with(
            {},
            { t -> log.warnf(t, "Failed to send timeshift snapshot to %s", connection.id()) },
        )
    }

    private fun handleResume(connection: WebSocketConnection) {
        timeshiftSessions.remove(connection.id())
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
    }

    private fun handleReset() {
        engine.resetPuck()
        engine.clearLines()
        engine.paused = false
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
            engine.updateCurrentLine(x, y)
        } else {
            sendError(connection, "Invalid UPDATE_LINE: x and y required")
        }
    }

    private fun handleFinishLine() {
        engine.finishCurrentLine()
    }

    private fun handleSetSpeed(data: Map<*, *>) {
        log.info("SET_SPEED received")
    }

    private fun handleSpawnPowerUp() {
        log.info("SPAWN_POWERUP received")
    }

    // ── Tick ─────────────────────────────────────────────────────────────────
    //
    // BUG THAT WAS HERE (now fixed):
    //
    // The OLD tick() called `currentStateBinary()` TWICE per tick:
    //   1. history.add(currentStateBinary(isFull))    ← sets lastFullState = currentState
    //   2. val binary = currentStateBinary(false)      ← computeDelta(currentState, currentState) = EMPTY!
    //
    // `currentStateBinary` has a side effect: it sets `lastFullState = newState`.
    // Call 1 captured the current game state and stored it as lastFullState.
    // Call 2 built the SAME state again (nothing changed between the two calls),
    // diffed it against lastFullState (now identical), and produced an empty delta.
    // Result: every live broadcast frame was an empty Protobuf message.
    // The frontend decoder received {}, applied no updates, puck appeared frozen.
    //
    // FIX: Build the game state ONCE per tick. Compute the delta ONCE.
    // Update lastFullState ONCE. Use the same delta bytes for both history and broadcast.

    private fun tick() {
        engine.tick(deltaSeconds = 0.016)
        powerUpManager.update(0.016)

        val now = System.nanoTime()

        // ── Build current state ONCE ──────────────────────────────────────────
        val currentState = buildGameStateDelta(now)

        // ── Compute live delta ONCE (vs last tick's state) ────────────────────
        val liveState =
            if (lastFullState == null) {
                currentState
            } else {
                computeDelta(lastFullState!!, currentState)
            }
        val liveBytes = liveState.toByteArray()
        val fullBytes = currentState.toByteArray()

        // ── Advance lastFullState to this tick ────────────────────────────────
        // Must happen AFTER computing liveState, BEFORE anything else modifies it.
        lastFullState = currentState

        // ── Record to history ring buffer ─────────────────────────────────────
        // Full snapshots every FULL_SNAPSHOT_INTERVAL ticks (~250 ms) for timeshift;
        // delta bytes for the frames in between.
        if (!engine.paused) {
            val isFull = (ticksSinceFullSnapshot >= FULL_SNAPSHOT_INTERVAL)
            history.add(if (isFull) fullBytes else liveBytes)
            if (isFull) ticksSinceFullSnapshot = 0 else ticksSinceFullSnapshot++
        }

        if (sessions.isEmpty()) return

        // ── Broadcast live delta to connected non-rewinding clients ───────────
        val dead = mutableListOf<String>()
        sessions.forEach { (id, conn) ->
            if (id in timeshiftSessions) return@forEach // skip rewinding clients
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

    fun getActiveSessionsCount(): Int = sessions.size

    // ── Protobuf helpers ──────────────────────────────────────────────────────
    //
    // currentStateBinary() is now ONLY called from onOpen and handleResume —
    // never from tick(). Each call correctly updates lastFullState once.

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

    private fun buildGameStateDelta(nowNs: Long): GameStateDelta {
        val puck = engine.puck ?: return GameStateDelta.getDefaultInstance()
        val score = engine.score ?: return GameStateDelta.getDefaultInstance()

        val builder =
            GameStateDelta
                .newBuilder()
                .setPuckX(puck.x)
                .setPuckY(puck.y)
                .setPuckVx(puck.vx)
                .setPuckVy(puck.vy)
                .setPaddle1Y(engine.paddle1Y)
                .setPaddle2Y(engine.paddle2Y)
                .setScoreA(score.playerA)
                .setScoreB(score.playerB)
                .setPaused(engine.paused)

        (engine.lines ?: emptyList()).forEach { line ->
            val lb =
                ru.rkhamatyarov.proto.Line
                    .newBuilder()
                    .setWidth(line.width)
                    .setAnimationProgress(line.animationProgress)
                    .setIsAnimating(line.isAnimating)
            line.flattenedPoints?.forEach { pt ->
                lb.addPoints(
                    ru.rkhamatyarov.proto.Point
                        .newBuilder()
                        .setX(pt.x)
                        .setY(pt.y),
                )
            }
            builder.addLines(lb)
        }

        (engine.powerUps ?: emptyList()).filter { it.isActive }.forEach { pu ->
            builder.addPowerUps(
                ru.rkhamatyarov.proto.PowerUp
                    .newBuilder()
                    .setX(pu.x)
                    .setY(pu.y)
                    .setRadius(pu.radius)
                    .setType(pu.type.name)
                    .setEmoji(pu.type.emoji)
                    .setColor(PowerUpType.getColorCode(pu.type)),
            )
        }

        (engine.activePowerUpEffects ?: emptyList()).filter { !it.isExpired() }.forEach { eff ->
            val remaining = ((eff.duration - (nowNs - eff.activationTime)) / 1_000_000_000).coerceAtLeast(0)
            builder.addActivePowerUps(
                ru.rkhamatyarov.proto.ActivePowerUp
                    .newBuilder()
                    .setType(eff.type.name)
                    .setEmoji(eff.type.emoji)
                    .setRemainingSeconds(remaining),
            )
        }
        return builder.build()
    }

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

    companion object {
        private const val FULL_SNAPSHOT_INTERVAL = 15 // 15 × 16ms ≈ 250 ms
    }
}
