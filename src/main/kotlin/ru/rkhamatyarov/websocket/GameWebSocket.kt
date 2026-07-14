package ru.rkhamatyarov.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import io.quarkus.runtime.annotations.RegisterForReflection
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jboss.logging.Logger
import ru.rkhamatyarov.proto.GameStateDelta
import ru.rkhamatyarov.service.GameRoom
import ru.rkhamatyarov.service.RoomRegistry
import ru.rkhamatyarov.service.StateHistory
import ru.rkhamatyarov.service.mvi.EphemeralEvent
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.GameIntent
import ru.rkhamatyarov.service.mvi.MviLine
import ru.rkhamatyarov.service.mvi.MviPoint
import ru.rkhamatyarov.service.mvi.PaddleSide
import ru.rkhamatyarov.service.mvi.mviStateFromDelta
import ru.rkhamatyarov.service.turbo.TurboHudState
import ru.rkhamatyarov.service.turbo.TurboSnapshot
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.nanoseconds

@WebSocket(path = "/game")
@ApplicationScoped
class GameWebSocket {
    private val log = Logger.getLogger(javaClass)

    @Inject
    lateinit var mapper: ObjectMapper

    @Inject
    lateinit var vertx: Vertx

    @Inject
    lateinit var roomRegistry: RoomRegistry

    private val sessions = ConcurrentHashMap<String, WebSocketConnection>()
    private val connectionRooms = ConcurrentHashMap<String, String>()
    private val connectionSides = ConcurrentHashMap<String, PaddleSide>()
    private val collectorJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val turboCollectorJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val lastFullStateByRoom = ConcurrentHashMap<String, GameStateDelta>()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val timeshiftSessions = ConcurrentHashMap.newKeySet<String>()
    private val timeshiftDrafts = ConcurrentHashMap<String, GameStateDelta>()
    private val timeshiftTurboDrafts = ConcurrentHashMap<String, TurboSnapshot>()
    private val pauseAnchorNsByRoom = ConcurrentHashMap<String, Long>()
    private val connectionDrawingLines = ConcurrentHashMap<String, MviLine>()
    private var tickTimerId: Long? = null

    fun onStart(
        @Observes event: StartupEvent,
    ) {
        tickTimerId = vertx.setPeriodic(16L) { tick() }
    }

    fun onStop(
        @Observes event: ShutdownEvent,
    ) {
        tickTimerId?.let { vertx.cancelTimer(it) }
        tickTimerId = null
    }

    @OnOpen
    fun onOpen(connection: WebSocketConnection) {
        val roomId = roomId(connection)
        val side = side(connection)
        val room = roomRegistry.get(roomId)
        room.registerHumanSide(side)
        connectionRooms[connection.id()] = roomId
        connectionSides[connection.id()] = side
        sessions[connection.id()] = connection
        log.info("Client connected: ${connection.id()} room=$roomId side=$side (total: ${sessions.size})")
        lastFullStateByRoom.remove(roomId)
        launchStateCollector(connection, roomId)
        launchTurboCollector(connection, roomId)
        connection.sendBinary(currentStateBinary(roomId))
    }

    @OnClose
    fun onClose(connection: WebSocketConnection) {
        sessions.remove(connection.id())
        connectionRooms.remove(connection.id())?.let { roomId ->
            connectionSides.remove(connection.id())?.let { roomRegistry.get(roomId).unregisterHumanSide(it) }
        }
        collectorJobs.remove(connection.id())?.cancel()
        turboCollectorJobs.remove(connection.id())?.cancel()
        timeshiftSessions.remove(connection.id())
        timeshiftDrafts.remove(connection.id())
        timeshiftTurboDrafts.remove(connection.id())
        connectionDrawingLines.remove(connection.id())
        log.info("Client disconnected: ${connection.id()} (remaining: ${sessions.size})")
    }

    private fun launchTurboCollector(
        connection: WebSocketConnection,
        roomId: String,
    ) {
        turboCollectorJobs[connection.id()] =
            applicationScope.launch {
                roomRegistry.get(roomId).turboState.collectLatest { state ->
                    if (connection.id() in timeshiftSessions || connection.isClosed) return@collectLatest
                    sendTurboState(connection, state)
                }
            }
    }

    private fun launchStateCollector(
        connection: WebSocketConnection,
        roomId: String,
    ) {
        collectorJobs[connection.id()] =
            applicationScope.launch {
                roomRegistry.get(roomId).stateFlow.collectLatest { bytes ->
                    if (connection.id() in timeshiftSessions || connection.isClosed) return@collectLatest
                    connection.sendBinary(bytes).subscribe().with(
                        {},
                        { t -> log.warn("Failed to send flow frame to ${connection.id()}: ${t.message}") },
                    )
                }
            }
    }

    private fun roomId(connection: WebSocketConnection): String =
        queryParam(connection, "roomId")
            ?.takeIf { it.isNotBlank() }
            ?: RoomRegistry.DEFAULT_ROOM_ID

    private fun side(connection: WebSocketConnection): PaddleSide =
        when (queryParam(connection, "side")?.uppercase()) {
            "A" -> PaddleSide.A
            "B", null, "" -> PaddleSide.B
            else -> PaddleSide.B
        }

    private fun queryParam(
        connection: WebSocketConnection,
        name: String,
    ): String? {
        val query = connection.handshakeRequest().query() ?: return null
        return query
            .split("&")
            .firstOrNull { it.substringBefore("=") == name }
            ?.substringAfter("=", "")
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
    }

    private fun historyFor(roomId: String): StateHistory = roomRegistry.get(roomId).history

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

            "ACTIVATE_TURBO" -> {
                handleActivateTurbo(connection)
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

            "P2P_TELEMETRY" -> {
                handleP2pTelemetry(data, connection)
            }

            else -> {
                log.warn("Unknown command: $type")
                sendError(connection, "Unknown command: $type")
            }
        }
    }

    private fun handleMovePaddle(
        data: Map<*, *>,
        connection: WebSocketConnection,
    ) {
        val y = data["y"]?.toString()?.toDoubleOrNull()
        if (y == null) {
            sendError(connection, "Invalid MOVE_PADDLE: y required")
            return
        }
        roomRegistry.get(roomId(connection)).dispatch(GameIntent.Reliable(GameAction.MovePaddle(y, connectionSide(connection))))
    }

    private fun handleActivateTurbo(connection: WebSocketConnection) {
        roomRegistry.get(roomId(connection)).dispatch(GameIntent.Reliable(GameAction.ActivateTurbo(connectionSide(connection))))
    }

    private fun handleTogglePause(connection: WebSocketConnection) {
        val roomId = roomId(connection)
        val room = roomRegistry.get(roomId)
        val wasPaused = room.reliableState.value.paused
        room.dispatch(GameIntent.Reliable(GameAction.TogglePause))
        if (!wasPaused) {
            val now = System.nanoTime()
            pauseAnchorNsByRoom[roomId] = now
            historyFor(roomId).add(
                room.reliableState.value
                    .toDelta()
                    .toByteArray(),
                now,
            )
        } else {
            pauseAnchorNsByRoom.remove(roomId)
        }
    }

    private fun handleReset(connection: WebSocketConnection) {
        val roomId = roomId(connection)
        pauseAnchorNsByRoom.remove(roomId)
        roomRegistry.get(roomId).dispatch(GameIntent.Reliable(GameAction.Reset))
    }

    private fun handleClearLines(connection: WebSocketConnection) {
        roomRegistry.get(roomId(connection)).dispatch(GameIntent.Reliable(GameAction.ClearLines))
    }

    private fun handleStartLine(
        data: Map<*, *>,
        connection: WebSocketConnection,
    ) {
        val x = data["x"]?.toString()?.toDoubleOrNull()
        val y = data["y"]?.toString()?.toDoubleOrNull()
        if (x == null || y == null) {
            sendError(connection, "Invalid START_LINE: x and y required")
            return
        }
        if (updateTimeshiftDraftLine(connection, LineDraftCommand.START, x, y)) return

        val lineId = UUID.randomUUID().toString()
        connectionDrawingLines[connection.id()] = MviLine(id = lineId, points = listOf(MviPoint(x, y)))
        val roomId = roomId(connection)
        roomRegistry.get(roomId).dispatch(GameIntent.Ephemeral(EphemeralEvent.LineDraft(lineId, x, y)))
    }

    private fun handleUpdateLine(
        data: Map<*, *>,
        connection: WebSocketConnection,
    ) {
        val x = data["x"]?.toString()?.toDoubleOrNull()
        val y = data["y"]?.toString()?.toDoubleOrNull()
        if (x == null || y == null) {
            sendError(connection, "Invalid UPDATE_LINE: x and y required")
            return
        }
        if (updateTimeshiftDraftLine(connection, LineDraftCommand.UPDATE, x, y)) return

        val current = connectionDrawingLines[connection.id()] ?: return
        connectionDrawingLines[connection.id()] = current.copy(points = current.points + MviPoint(x, y))
        roomRegistry.get(roomId(connection)).dispatch(
            GameIntent.Ephemeral(EphemeralEvent.LineDraft(current.id, x, y)),
        )
    }

    private fun handleFinishLine(connection: WebSocketConnection) {
        if (updateTimeshiftDraftLine(connection, LineDraftCommand.FINISH, null, null)) return

        val line = connectionDrawingLines.remove(connection.id()) ?: return
        val roomId = roomId(connection)
        val room = roomRegistry.get(roomId)
        room.dispatch(GameIntent.Reliable(GameAction.CommitLine(line)))
        room.dispatch(GameIntent.Ephemeral(EphemeralEvent.LineFinished(line.id)))
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
        val roomId = roomId(connection)
        val room = roomRegistry.get(roomId)
        room.dispatch(GameIntent.Reliable(GameAction.EraseLine(lineId)))
        room.dispatch(GameIntent.Ephemeral(EphemeralEvent.EraseLineDraft(lineId)))
    }

    private fun handleTimeshift(
        data: Map<*, *>,
        connection: WebSocketConnection,
    ) {
        val roomId = roomId(connection)
        val roomHistory = historyFor(roomId)
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
        val paused =
            roomRegistry
                .get(roomId)
                .reliableState.value.paused
        val pausedAwareSnapshot = snapshot.withCurrentPauseState(paused)
        timeshiftSessions.add(connection.id())
        timeshiftDrafts[connection.id()] = GameStateDelta.parseFrom(pausedAwareSnapshot)
        val turboSnapshot =
            roomRegistry.get(roomId).turboHistory.getByOffsetSeconds(offset, rewindReferenceNs(roomId))
                ?: roomRegistry.get(roomId).turboSnapshot()
        timeshiftTurboDrafts[connection.id()] = turboSnapshot
        sendTurboState(connection, turboSnapshot.toHudState(turboSnapshot.elapsedNs))
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
            val mviState = mviStateFromDelta(GameStateDelta.parseFrom(snapshot))
            val room = roomRegistry.get(roomId)
            val turboSnapshot =
                timeshiftTurboDrafts[connection.id()] ?: room.turboHistory.getByOffsetSeconds(offset, rewindReferenceNs(roomId))
            room.dispatch(GameIntent.Reliable(GameAction.RestoreSnapshot(mviState)))
            turboSnapshot?.let { room.restoreTurbo(it) }
            roomHistory.clear()
            room.turboHistory.clear()
            lastFullStateByRoom.remove(roomId)
            timeshiftSessions.clear()
            timeshiftDrafts.clear()
            timeshiftTurboDrafts.clear()
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
        timeshiftTurboDrafts.remove(connection.id())
        log.debugf("Connection %s resumed live stream", connection.id())
        sendTurboState(connection, roomRegistry.get(roomId(connection)).turboState.value)
        connection.sendBinary(currentStateBinary(roomId(connection))).subscribe().with(
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
            streamGhostFrames(connection, ghostFrames)
        }
    }

    private suspend fun streamGhostFrames(
        connection: WebSocketConnection,
        ghostFrames: List<Pair<Long, ByteArray>>,
    ) {
        try {
            var previousTimestampNs: Long? = null
            for ((timestampNs, bytes) in ghostFrames.asReversed()) {
                if (connection.isClosed) return
                previousTimestampNs?.let { delay(frameDelay(timestampNs, it)) }
                sendGhostFrame(connection, bytes)
                previousTimestampNs = timestampNs
            }
        } finally {
            timeshiftSessions.remove(connection.id())
        }
    }

    private fun frameDelay(
        timestampNs: Long,
        previousTimestampNs: Long,
    ) = (timestampNs - previousTimestampNs).coerceAtLeast(0L).nanoseconds

    private fun sendGhostFrame(
        connection: WebSocketConnection,
        bytes: ByteArray,
    ) {
        connection.sendBinary(bytes).subscribe().with(
            {},
            { t -> log.warn("Failed to send ghost frame to ${connection.id()}: ${t.message}") },
        )
    }

    private fun handleP2pTelemetry(
        data: Map<*, *>,
        connection: WebSocketConnection,
    ) {
        val status = data["status"]?.toString()?.lowercase()
        if (status != "success" && status != "failure") {
            sendError(connection, "P2P_TELEMETRY requires 'status' of 'success' or 'failure'")
            return
        }
        val peerId = data["peerId"]?.toString()?.takeIf { it.isNotBlank() } ?: "unknown"
        log.infof(
            "P2P telemetry room=%s connection=%s peer=%s status=%s",
            roomId(connection),
            connection.id(),
            peerId,
            status,
        )
    }

    private fun rewindReferenceNs(roomId: String): Long = pauseAnchorNsByRoom[roomId] ?: System.nanoTime()

    private fun tick() {
        val rooms = roomRegistry.activeRooms()
        if (rooms.isEmpty()) return
        rooms.forEach { room ->
            val elapsedNs = (room.reliableState.value.elapsedSeconds * 1_000_000_000L).toLong()
            room.dispatch(GameIntent.Reliable(GameAction.Tick(0.016, elapsedNs)))
            tickRoom(room)
        }
    }

    private fun tickRoom(room: GameRoom) {
        val roomId = room.id
        val roomHistory = room.history

        val now = System.nanoTime()
        val currentState = room.reliableState.value.toDelta()
        val lastFullState = lastFullStateByRoom[roomId]

        val liveBytes =
            if (lastFullState == null) {
                currentState.toByteArray()
            } else {
                computeDelta(lastFullState, currentState).toByteArray()
            }
        val fullBytes = currentState.toByteArray()

        lastFullStateByRoom[roomId] = currentState

        if (!room.reliableState.value.paused) {
            roomHistory.add(fullBytes, now)
            room.turboHistory.add(room.turboSnapshot(), now)
        }

        if (sessions.isEmpty()) return

        if (!room.tryEmit(liveBytes)) {
            log.warnf("State flow buffer full for room %s; oldest frame dropped", roomId)
        }
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
            turboCollectorJobs.remove(it)?.cancel()
            timeshiftSessions.remove(it)
            timeshiftTurboDrafts.remove(it)
        }
    }

    private fun broadcastFullState(roomId: String) {
        broadcastBytes(roomId, currentStateBinary(roomId))
    }

    private fun currentStateBinary(roomId: String): ByteArray {
        val state = roomRegistry.get(roomId).reliableState.value
        val delta = state.toDelta()
        lastFullStateByRoom[roomId] = delta
        return delta.toByteArray()
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

    private fun connectionSide(connection: WebSocketConnection): PaddleSide = connectionSides[connection.id()] ?: PaddleSide.B

    private fun sendTurboState(
        connection: WebSocketConnection,
        state: TurboHudState,
    ) {
        val payload =
            try {
                mapper.writeValueAsString(state.toClientMessage())
            } catch (t: Throwable) {
                log.warn("Failed to serialize turbo state for ${connection.id()}: ${t.message}")
                return
            }
        connection.sendText(payload).subscribe().with(
            {},
            { t -> log.warn("Failed to send turbo state to ${connection.id()}: ${t.message}") },
        )
    }

    private fun TurboHudState.toClientMessage(): TurboStateMessage = TurboStateMessage(states = states.map { it.toClientMessage() })

    private fun ru.rkhamatyarov.service.turbo.TurboSideHudState.toClientMessage(): TurboSideStateMessage =
        TurboSideStateMessage(
            side = side.name,
            charge = charge,
            status = status.name.lowercase(),
            activeMs = activeMs,
            cooldownMs = cooldownMs,
        )

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

@RegisterForReflection
private data class TurboStateMessage(
    val type: String = "TURBO_STATE",
    val states: List<TurboSideStateMessage>,
)

@RegisterForReflection
private data class TurboSideStateMessage(
    val side: String,
    val charge: Double,
    val status: String,
    val activeMs: Long,
    val cooldownMs: Long,
)
