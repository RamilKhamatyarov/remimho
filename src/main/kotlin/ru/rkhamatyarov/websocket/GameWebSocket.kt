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
import ru.rkhamatyarov.model.GameState
import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.service.GameEngine
import ru.rkhamatyarov.service.PowerUpManager
import ru.rkhamatyarov.websocket.dto.ActivePowerUpDTO
import ru.rkhamatyarov.websocket.dto.PowerUpDTO
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

    fun onStart(
        @Observes event: StartupEvent,
    ) {
        vertx.setPeriodic(16L) { tick() }
    }

    @OnOpen
    fun onOpen(connection: WebSocketConnection) {
        sessions[connection.id()] = connection
        log.info("Client connected: ${connection.id()} (total: ${sessions.size})")
        connection.sendTextAndAwait(currentStateJson())
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
                handleSetSpeed(data)
            }

            "SPAWN_POWERUP" -> {
                handleSpawnPowerUp(data)
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
        val xStr = data["x"]?.toString()
        val yStr = data["y"]?.toString()
        val x = xStr?.toDoubleOrNull()
        val y = yStr?.toDoubleOrNull()

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
        val xStr = data["x"]?.toString()
        val yStr = data["y"]?.toString()
        val x = xStr?.toDoubleOrNull()
        val y = yStr?.toDoubleOrNull()

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

    private fun handleSpawnPowerUp(data: Map<*, *>) {
        val type = data["type"]?.toString()
        log.info("SPAWN_POWERUP received: $type")
    }

    private fun tick() {
        engine.tick(deltaSeconds = 0.016)
        powerUpManager.update(0.016)
        if (sessions.isEmpty()) return
        val json = currentStateJson()
        val dead = mutableListOf<String>()
        sessions.forEach { (id, conn) ->
            conn.sendText(json).subscribe().with({}, { t ->
                log.warn("Failed to send to $id: ${t.message}")
                dead.add(id)
            })
        }
        dead.forEach { sessions.remove(it) }
    }

    fun getActiveSessionsCount(): Int = sessions.size

    private fun currentStateJson(): String {
        val nowNs = System.nanoTime()
        return mapper.writeValueAsString(
            GameState(
                puck = engine.puck,
                score = engine.score,
                canvasWidth = engine.canvasWidth,
                canvasHeight = engine.canvasHeight,
                paddleHeight = engine.paddleHeight,
                paddle1Y = engine.paddle1Y,
                paddle2Y = engine.paddle2Y,
                paused = engine.paused,
                lines = engine.lines.toList(),
                powerUps =
                    engine.powerUps
                        .filter { it.isActive }
                        .map { pu ->
                            PowerUpDTO(
                                x = pu.x,
                                y = pu.y,
                                radius = pu.radius,
                                type = pu.type.name,
                                emoji = pu.type.emoji,
                                color = PowerUpType.getColorCode(pu.type),
                            )
                        },
                activePowerUpEffects =
                    engine.activePowerUpEffects
                        .filter { !it.isExpired() }
                        .map { eff ->
                            ActivePowerUpDTO(
                                type = eff.type.name,
                                emoji = eff.type.emoji,
                                remainingSeconds =
                                    ((eff.duration - (nowNs - eff.activationTime)) / 1_000_000)
                                        .coerceAtLeast(0),
                            )
                        },
            ),
        )
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
