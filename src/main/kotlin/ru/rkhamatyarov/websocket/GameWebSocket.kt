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

    private val sessions = ConcurrentHashMap<String, WebSocketConnection>()
    private var lastFullState: GameStateDelta? = null

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
        val type = cmd["type"]?.toString()?.uppercase() ?: return sendError(connection, "Missing 'type'")
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
                handleSetSpeed()
            }

            "SPAWN_POWERUP" -> {
                handleSpawnPowerUp()
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
        val yStr = data["y"]?.toString()
        val y = yStr?.toDoubleOrNull()
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

    private fun handleSetSpeed() {
        log.info("SET_SPEED received")
    }

    private fun handleSpawnPowerUp() {
        log.info("SPAWN_POWERUP received")
    }

    private fun tick() {
        engine.tick(deltaSeconds = 0.016)
        powerUpManager.update(0.016)
        if (sessions.isEmpty()) return
        val binary = currentStateBinary(false)
        val dead = mutableListOf<String>()
        sessions.forEach { (id, conn) ->
            conn.sendBinary(binary).subscribe().with({}, { t ->
                log.warn("Failed to send to $id: ${t.message}")
                dead.add(id)
            })
        }
        dead.forEach { sessions.remove(it) }
    }

    fun getActiveSessionsCount(): Int = sessions.size

    private fun currentStateBinary(full: Boolean): ByteArray {
        val now = System.nanoTime()
        val newState = buildGameStateDelta(now)
        val delta = if (full || lastFullState == null) newState else computeDelta(lastFullState!!, newState)
        lastFullState = newState
        return delta.toByteArray()
    }

    private fun buildGameStateDelta(nowNs: Long): GameStateDelta {
        val puck = engine.puck
        val score = engine.score

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

        engine.lines.forEach { line ->
            val lineBuilder =
                ru.rkhamatyarov.proto.Line
                    .newBuilder()
                    .setWidth(line.width)
                    .setAnimationProgress(line.animationProgress)
                    .setIsAnimating(line.isAnimating)
            line.flattenedPoints?.forEach { pt ->
                lineBuilder.addPoints(
                    ru.rkhamatyarov.proto.Point
                        .newBuilder()
                        .setX(pt.x)
                        .setY(pt.y),
                )
            }
            builder.addLines(lineBuilder)
        }

        engine.powerUps.filter { it.isActive }.forEach { pu ->
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

        engine.activePowerUpEffects.filter { !it.isExpired() }.forEach { eff ->
            val remainingSec = ((eff.duration - (nowNs - eff.activationTime)) / 1_000_000_000).coerceAtLeast(0)
            builder.addActivePowerUps(
                ru.rkhamatyarov.proto.ActivePowerUp
                    .newBuilder()
                    .setType(eff.type.name)
                    .setEmoji(eff.type.emoji)
                    .setRemainingSeconds(remainingSec),
            )
        }
        return builder.build()
    }

    private fun computeDelta(
        prev: GameStateDelta,
        curr: GameStateDelta,
    ): GameStateDelta {
        val builder = GameStateDelta.newBuilder()
        if (curr.puckX != prev.puckX) builder.puckX = curr.puckX
        if (curr.puckY != prev.puckY) builder.puckY = curr.puckY
        if (curr.puckVx != prev.puckVx) builder.puckVx = curr.puckVx
        if (curr.puckVy != prev.puckVy) builder.puckVy = curr.puckVy
        if (curr.paddle1Y != prev.paddle1Y) builder.paddle1Y = curr.paddle1Y
        if (curr.paddle2Y != prev.paddle2Y) builder.paddle2Y = curr.paddle2Y
        if (curr.scoreA != prev.scoreA) builder.scoreA = curr.scoreA
        if (curr.scoreB != prev.scoreB) builder.scoreB = curr.scoreB
        if (curr.paused != prev.paused) builder.paused = curr.paused
        if (curr.linesList != prev.linesList) builder.addAllLines(curr.linesList)
        if (curr.powerUpsList != prev.powerUpsList) builder.addAllPowerUps(curr.powerUpsList)
        if (curr.activePowerUpsList != prev.activePowerUpsList) builder.addAllActivePowerUps(curr.activePowerUpsList)
        return builder.build()
    }

    private fun sendError(
        connection: WebSocketConnection,
        message: String,
    ) {
        try {
            connection.sendTextAndAwait(mapper.writeValueAsString(mapOf("type" to "ERROR", "message" to message)))
        } catch (e: Exception) {
            log.error("Failed to send error to ${connection.id()}", e)
        }
    }
}
