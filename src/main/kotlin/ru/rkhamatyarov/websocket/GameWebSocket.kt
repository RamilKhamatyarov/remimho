package ru.example.game.websocket

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
import ru.example.game.service.GameEngine
import ru.rkhamatyarov.model.GameInnerState
import ru.rkhamatyarov.model.GameState
import ru.rkhamatyarov.model.PowerUpType
import java.util.concurrent.ConcurrentHashMap

@WebSocket(path = "/game")
@ApplicationScoped
class GameWebSocket {
    private val log = Logger.getLogger(javaClass)

    @Inject
    lateinit var engine: GameEngine

    @Inject
    lateinit var gameState: GameInnerState

    @Inject
    lateinit var mapper: ObjectMapper

    @Inject
    lateinit var vertx: Vertx

    private val sessions = ConcurrentHashMap<String, WebSocketConnection>()

    fun onStart(
        @Observes event: StartupEvent,
    ) {
        vertx.setPeriodic(16L) {
            tick()
        }
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
            val cmd = mapper.readValue<Map<String, Any>>(message)
            handleCommand(cmd, connection)
        } catch (e: Exception) {
            log.error("Failed to handle message: $message", e)
            sendError(connection, "Invalid command format")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleCommand(
        cmd: Map<String, Any>,
        connection: WebSocketConnection,
    ) {
        val data = cmd["data"] as? Map<String, Any> ?: cmd

        when (cmd["type"]?.toString()?.uppercase()) {
            "MOVE_PADDLE" -> {
                val y = data["y"]?.toString()?.toDoubleOrNull()
                if (y != null) {
                    engine.movePaddle2(y)
                    gameState.paddle2Y =
                        y
                            .coerceAtMost(gameState.canvasHeight - gameState.paddleHeight)
                            .coerceAtLeast(0.0)
                } else {
                    sendError(connection, "Invalid MOVE_PADDLE: y required")
                }
            }

            "TOGGLE_PAUSE" -> {
                gameState.togglePause()
                engine.paused = gameState.paused
            }

            "RESET" -> {
                gameState.reset()
                engine.resetPuck()
            }

            "CLEAR_LINES" -> {
                gameState.clearLines()
            }

            "START_LINE" -> {
                val x = data["x"]?.toString()?.toDoubleOrNull()
                val y = data["y"]?.toString()?.toDoubleOrNull()
                if (x != null && y != null) {
                    gameState.startNewLine(x, y)
                } else {
                    sendError(connection, "Invalid START_LINE: x and y required")
                }
            }

            "UPDATE_LINE" -> {
                val x = data["x"]?.toString()?.toDoubleOrNull()
                val y = data["y"]?.toString()?.toDoubleOrNull()
                if (x != null && y != null) {
                    gameState.updateCurrentLine(x, y)
                } else {
                    sendError(connection, "Invalid UPDATE_LINE: x and y required")
                }
            }

            "FINISH_LINE" -> {
                gameState.finishCurrentLine()
            }

            "SET_SPEED" -> {
                val speed = data["speed"]?.toString()?.toDoubleOrNull()
                if (speed != null && speed in 0.1..20.0) {
                    gameState.baseSpeedMultiplier = speed
                    gameState.speedMultiplier = speed
                } else {
                    sendError(connection, "Invalid SET_SPEED: speed must be 0.1-20.0")
                }
            }

            "SPAWN_POWERUP" -> {
                val typeStr = data["type"]?.toString()
                if (typeStr != null) {
                    try {
                        PowerUpType.valueOf(typeStr)
                        log.info("Power-up spawn requested: $typeStr")
                    } catch (e: IllegalArgumentException) {
                        sendError(connection, "Invalid power-up type: $typeStr")
                    }
                } else {
                    sendError(connection, "Invalid SPAWN_POWERUP: type required")
                }
            }

            else -> {
                log.warn("Unknown command: ${cmd["type"]}")
                sendError(connection, "Unknown command: ${cmd["type"]}")
            }
        }
    }

    private fun tick() {
        engine.tick(deltaSeconds = 0.016)
        if (sessions.isEmpty()) return

        val json = currentStateJson()
        val dead = mutableListOf<String>()

        sessions.forEach { (id, conn) ->
            conn
                .sendText(json)
                .subscribe()
                .with(
                    { /* success */ },
                    { throwable ->
                        log.warn("Failed to send to $id: ${throwable.message}")
                        dead.add(id)
                    },
                )
        }
        dead.forEach { sessions.remove(it) }
    }

    fun getActiveSessionsCount(): Int = sessions.size

    private fun currentStateJson(): String =
        mapper.writeValueAsString(
            GameState(
                puck = engine.puck,
                score = engine.score,
                canvasWidth = engine.canvasWidth,
                canvasHeight = engine.canvasHeight,
                paddleHeight = engine.paddleHeight,
                paddle1Y = engine.paddle1Y,
                paddle2Y = engine.paddle2Y,
                paused = engine.paused,
            ),
        )

    private fun sendError(
        connection: WebSocketConnection,
        message: String,
    ) {
        try {
            val err = mapper.writeValueAsString(mapOf("type" to "ERROR", "message" to message))
            connection.sendTextAndAwait(err)
        } catch (e: Exception) {
            log.error("Failed to send error to ${connection.id()}", e)
        }
    }
}
