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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import ru.rkhamatyarov.proto.GameStateDelta
import ru.rkhamatyarov.service.GameEngine
import ru.rkhamatyarov.service.PowerUpManager
import ru.rkhamatyarov.service.RoomRegistry
import ru.rkhamatyarov.service.StateHistory
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.MviGameEngine
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
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

    @Inject
    lateinit var roomRegistry: RoomRegistry

    @Inject
    lateinit var mviEngine: MviGameEngine

    @ConfigProperty(name = "remimho.coroutines.state-flow-enabled", defaultValue = "false")
    var stateFlowEnabled: Boolean = false

    @ConfigProperty(name = "remimho.rooms.enabled", defaultValue = "false")
    var roomsEnabled: Boolean = false

    @ConfigProperty(name = "remimho.engine.mvi.enabled", defaultValue = "false")
    var mviEnabled: Boolean = false

    private val sessions = ConcurrentHashMap<String, WebSocketConnection>()
    private val connectionRooms = ConcurrentHashMap<String, String>()
    private val collectorJobs = ConcurrentHashMap<String, Job>()
    private val lastFullStateByRoom = ConcurrentHashMap<String, GameStateDelta>()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val timeshiftSessions = ConcurrentHashMap.newKeySet<String>()
    private val timeshiftDrafts = ConcurrentHashMap<String, GameStateDelta>()
    private val pauseAnchorNsByRoom = ConcurrentHashMap<String, Long>()

    private val mutableStateFlow =
        MutableSharedFlow<ByteArray>(
            replay = 0,
            extraBufferCapacity = 3,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val stateFlow: SharedFlow<ByteArray> = mutableStateFlow.asSharedFlow()

    fun onStart(
        @Observes event: StartupEvent,
    ) {
        vertx.setPeriodic(16L) { tick() }
    }

    @OnOpen
    fun onOpen(connection: WebSocketConnection) {
        val roomId = roomId(connection)
        if (roomsEnabled) roomRegistry.get(roomId)
        connectionRooms[connection.id()] = roomId
        sessions[connection.id()] = connection
        log.info("Client connected: ${connection.id()} room=$roomId (total: ${sessions.size})")
        lastFullStateByRoom.remove(roomId)
        if (stateFlowEnabled) launchStateCollector(connection, roomId)
        connection.sendBinary(currentStateBinary(roomId, true))
    }

    @OnClose
    fun onClose(connection: WebSocketConnection) {
        sessions.remove(connection.id())
        connectionRooms.remove(connection.id())
        collectorJobs.remove(connection.id())?.cancel()
        timeshiftSessions.remove(connection.id())
        timeshiftDrafts.remove(connection.id())
        log.info("Client disconnected: ${connection.id()} (remaining: ${sessions.size})")
    }

    private fun launchStateCollector(
        connection: WebSocketConnection,
        roomId: String,
    ) {
        val flow = if (roomsEnabled) roomRegistry.get(roomId).stateFlow else stateFlow
        collectorJobs[connection.id()] =
            applicationScope.launch {
                flow.collectLatest { bytes ->
                    if (connection.id() in timeshiftSessions || connection.isClosed) return@collectLatest
                    connection.sendBinary(bytes).subscribe().with(
                        {},
                        { t -> log.warn("Failed to send flow frame to ${connection.id()}: ${t.message}") },
                    )
                }
            }
    }

    private fun roomId(connection: WebSocketConnection): String {
        if (!roomsEnabled) return RoomRegistry.DEFAULT_ROOM_ID
        val query = connection.handshakeRequest().query() ?: return RoomRegistry.DEFAULT_ROOM_ID
        return query
            .split("&")
            .firstOrNull { it.substringBefore("=") == "roomId" }
            ?.substringAfter("=", "")
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
            ?.takeIf { it.isNotBlank() }
            ?: RoomRegistry.DEFAULT_ROOM_ID
    }

    private fun engineFor(roomId: String): GameEngine =
        if (roomsEnabled) {
            roomRegistry.get(roomId).engine
        } else {
            engine
        }

    private fun historyFor(roomId: String): StateHistory =
        if (roomsEnabled) {
            roomRegistry.get(roomId).history
        } else {
            history
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
                handleTogglePause(connection)
            }

            "RESET" -> {
                handleReset(connection)
            }

            "CLEAR_LINES" -> {
                handleClearLines(connection)
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

            "ERASE_LINE" -> {
                handleEraseLine(data, connection)
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

            "PLAY_GHOST" -> {
                handlePlayGhost(data, connection)
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
        val roomId = roomId(connection)
        val roomHistory = historyFor(roomId)
        val roomEngine = engineFor(roomId)
        val offset = data["offset"]?.toString()?.toDoubleOrNull()
        if (offset == null || offset < 0.0) {
            sendError(connection, "TIMESHIFT requires a numeric 'offset' >= 0 (seconds)")
            return
        }
        val snapshot = roomHistory.getByOffsetSeconds(offset, rewindReferenceNs(roomId))
        if (snapshot == null) {
            sendError(
                connection,
                "No snapshot available for offset ${offset}s " +
                    "(history has ${roomHistory.size()} frames, " +
                    "max retention ${StateHistory.MAX_RETENTION_SECONDS}s)",
            )
            return
        }
        val pausedAwareSnapshot = snapshot.withCurrentPauseState(roomEngine.paused)
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
        val roomId = roomId(connection)
        val roomEngine = engineFor(roomId)
        val roomHistory = historyFor(roomId)
        val offset = data["offset"]?.toString()?.toDoubleOrNull()
        if (offset == null || offset < 0.0) {
            sendError(connection, "COMMIT_TIMESHIFT requires a numeric 'offset' >= 0 (seconds)")
            return
        }
        val snapshot =
            timeshiftDrafts[connection.id()]?.toByteArray()
                ?: roomHistory.getByOffsetSeconds(offset, rewindReferenceNs(roomId))
        if (snapshot == null) {
            sendError(connection, "No snapshot available for offset ${offset}s")
            return
        }

        try {
            roomEngine.restoreFromDelta(GameStateDelta.parseFrom(snapshot))
            roomHistory.clear()
            lastFullStateByRoom.remove(roomId)
            timeshiftSessions.clear()
            timeshiftDrafts.clear()
            broadcastFullState(roomId)
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
        connection.sendBinary(currentStateBinary(roomId(connection), true)).subscribe().with(
            {},
            { t -> log.warnf(t, "Failed to send resume snapshot to %s", connection.id()) },
        )
    }

    private fun handlePlayGhost(
        data: Map<*, *>,
        connection: WebSocketConnection,
    ) {
        val startOffset = data["startOffset"]?.toString()?.toDoubleOrNull() ?: 0.0
        val endOffset =
            data["endOffset"]?.toString()?.toDoubleOrNull()
                ?: StateHistory.MAX_RETENTION_SECONDS.toDouble()
        if (startOffset < 0.0 ||
            endOffset < startOffset ||
            endOffset > StateHistory.MAX_RETENTION_SECONDS
        ) {
            sendError(
                connection,
                "PLAY_GHOST requires 0 <= startOffset <= endOffset <= ${StateHistory.MAX_RETENTION_SECONDS}",
            )
            return
        }

        val roomId = roomId(connection)
        val ghostFrames = historyFor(roomId).exportRange(startOffset, endOffset)
        if (ghostFrames.isEmpty()) {
            sendError(connection, "No ghost data available for range ${startOffset}s..${endOffset}s")
            return
        }

        timeshiftSessions.add(connection.id())
        timeshiftDrafts.remove(connection.id())
        applicationScope.launch {
            try {
                var previousTimestampNs: Long? = null
                for ((timestampNs, bytes) in ghostFrames.asReversed()) {
                    if (connection.isClosed) break
                    previousTimestampNs?.let {
                        delay(((timestampNs - it) / 1_000_000L).coerceAtLeast(0L))
                    }
                    connection.sendBinary(bytes).subscribe().with(
                        {},
                        { t -> log.warn("Failed to send ghost frame to ${connection.id()}: ${t.message}") },
                    )
                    previousTimestampNs = timestampNs
                }
            } finally {
                timeshiftSessions.remove(connection.id())
            }
        }
    }

    private fun handleMovePaddle(
        data: Map<*, *>,
        connection: WebSocketConnection,
    ) {
        val y = data["y"]?.toString()?.toDoubleOrNull()
        if (y != null) {
            if (mviEnabled && !roomsEnabled) {
                mviEngine.tryDispatch(GameAction.MovePaddle(y))
            } else {
                engineFor(roomId(connection)).movePaddle2(y)
            }
        } else {
            sendError(connection, "Invalid MOVE_PADDLE: y required")
        }
    }

    private fun handleTogglePause(connection: WebSocketConnection) {
        val roomId = roomId(connection)
        val roomEngine = engineFor(roomId)
        if (mviEnabled && !roomsEnabled) {
            mviEngine.tryDispatch(GameAction.TogglePause)
            return
        }
        roomEngine.paused = !roomEngine.paused
        if (roomEngine.paused) {
            pauseAnchorNsByRoom[roomId] =
                System.nanoTime().also { pausedAt ->
                    historyFor(roomId).add(buildGameStateDelta(roomId, pausedAt).toByteArray(), pausedAt)
                }
        } else {
            pauseAnchorNsByRoom.remove(roomId)
        }
    }

    private fun handleReset(connection: WebSocketConnection) {
        val roomId = roomId(connection)
        if (mviEnabled && !roomsEnabled) {
            mviEngine.tryDispatch(GameAction.Reset)
            pauseAnchorNsByRoom.remove(roomId)
            return
        }
        val roomEngine = engineFor(roomId)
        roomEngine.resetPuck()
        roomEngine.clearLines()
        roomEngine.paused = false
        roomEngine.elapsedSeconds = 0.0
        pauseAnchorNsByRoom.remove(roomId)
    }

    private fun handleClearLines(connection: WebSocketConnection) {
        engineFor(roomId(connection)).clearLines()
    }

    private fun handleStartLine(
        data: Map<*, *>,
        connection: WebSocketConnection,
    ) {
        val x = data["x"]?.toString()?.toDoubleOrNull()
        val y = data["y"]?.toString()?.toDoubleOrNull()
        if (x != null && y != null) {
            if (updateTimeshiftDraftLine(connection, LineDraftCommand.START, x, y)) return
            engineFor(roomId(connection)).startNewLine(x, y)
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
            engineFor(roomId(connection)).updateCurrentLine(x, y)
        } else {
            sendError(connection, "Invalid UPDATE_LINE: x and y required")
        }
    }

    private fun handleFinishLine(connection: WebSocketConnection) {
        if (updateTimeshiftDraftLine(connection, LineDraftCommand.FINISH, null, null)) return
        engineFor(roomId(connection)).finishCurrentLine()
    }

    private fun handleEraseLine(
        data: Map<*, *>,
        connection: WebSocketConnection,
    ) {
        val lineId = data["lineId"]?.toString()?.takeIf { it.isNotBlank() }
        if (lineId == null) {
            sendError(connection, "Invalid ERASE_LINE: lineId required")
            return
        }
        if (eraseTimeshiftDraftLine(connection, lineId)) return
        engineFor(roomId(connection)).eraseLine(lineId)
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
                        .setId(UUID.randomUUID().toString())
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

    private fun eraseTimeshiftDraftLine(
        connection: WebSocketConnection,
        lineId: String,
    ): Boolean {
        val id = connection.id()
        if (id !in timeshiftSessions) return false

        val current =
            timeshiftDrafts[id]
                ?: return true.also { sendError(connection, "No active timeshift snapshot") }

        val remaining = current.linesList.filter { it.id != lineId }
        if (remaining.size == current.linesList.size) return true

        val updated =
            current
                .toBuilder()
                .clearLines()
                .addAllLines(remaining)
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

    private fun rewindReferenceNs(roomId: String): Long = pauseAnchorNsByRoom[roomId] ?: System.nanoTime()

    private fun ByteArray.withCurrentPauseState(paused: Boolean): ByteArray {
        if (!paused) return this
        return GameStateDelta
            .parseFrom(this)
            .toBuilder()
            .setPaused(true)
            .setFullState(true)
            .build()
            .toByteArray()
    }

    private fun tick() {
        if (roomsEnabled) {
            roomRegistry.activeRooms().forEach { tickRoom(it.id, it.engine, it.history, it::tryEmit) }
            return
        }

        if (mviEnabled) {
            val emitted = mviEngine.tryDispatch(GameAction.Tick(0.016))
            if (!emitted) log.warn("MVI tick action was dropped")
        }

        tickRoom(RoomRegistry.DEFAULT_ROOM_ID, engine, history, mutableStateFlow::tryEmit)
    }

    private fun tickRoom(
        roomId: String,
        roomEngine: GameEngine,
        roomHistory: StateHistory,
        emit: (ByteArray) -> Boolean,
    ) {
        if (!mviEnabled || roomsEnabled) {
            roomEngine.tick(deltaSeconds = 0.016)
        }

        val now = System.nanoTime()

        val currentState = buildGameStateDelta(roomId, now)
        val lastFullState = lastFullStateByRoom[roomId]

        val liveState =
            if (lastFullState == null) {
                currentState
            } else {
                computeDelta(lastFullState, currentState)
            }
        val liveBytes = liveState.toByteArray()
        val fullBytes = currentState.toByteArray()

        lastFullStateByRoom[roomId] = currentState

        if (!roomEngine.paused) {
            roomHistory.add(fullBytes)
        }

        if (sessions.isEmpty()) return

        if (stateFlowEnabled) {
            if (!emit(liveBytes)) log.warnf("State flow buffer full for room %s; oldest frame dropped", roomId)
            return
        }

        broadcastBytes(roomId, liveBytes)
    }

    private fun broadcastBytes(
        roomId: String,
        bytes: ByteArray,
    ) {
        val dead = mutableListOf<String>()

        sessions.forEach { (id, conn) ->
            if (connectionRooms[id] != roomId) return@forEach
            if (id in timeshiftSessions) return@forEach
            conn.sendBinary(bytes).subscribe().with({}, { t ->
                log.warn("Failed to send to $id: ${t.message}")
                dead.add(id)
            })
        }
        dead.forEach {
            sessions.remove(it)
            connectionRooms.remove(it)
            collectorJobs.remove(it)?.cancel()
            timeshiftSessions.remove(it)
        }
    }

    private fun broadcastFullState(roomId: String) {
        broadcastBytes(roomId, currentStateBinary(roomId, true))
    }

    private fun currentStateBinary(
        roomId: String,
        full: Boolean,
    ): ByteArray {
        val now = System.nanoTime()
        val newState = buildGameStateDelta(roomId, now)
        val lastFullState = lastFullStateByRoom[roomId]
        val result =
            if (full || lastFullState == null) {
                newState
            } else {
                computeDelta(lastFullState, newState)
            }
        lastFullStateByRoom[roomId] = newState
        return result.toByteArray()
    }

    private fun buildGameStateDelta(
        roomId: String,
        nowNs: Long,
    ): GameStateDelta =
        if (mviEnabled && !roomsEnabled) {
            mviEngine.toGameStateDelta()
        } else {
            engineFor(roomId).toGameStateDelta(nowNs)
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
}
