package ru.rkhamatyarov.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.websocket.OnClose
import jakarta.websocket.OnError
import jakarta.websocket.OnMessage
import jakarta.websocket.OnOpen
import jakarta.websocket.Session
import jakarta.websocket.server.ServerEndpoint
import org.jboss.logging.Logger
import ru.rkhamatyarov.model.GameState
import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.service.GameEngine
import java.util.concurrent.ConcurrentHashMap

/**
 * WebSocket API v1 for real-time game updates
 * Endpoint: /api/v1/game/ws
 */
@ServerEndpoint("/api/v1/game/ws")
@ApplicationScoped
class GameWebSocket {
    private val log = Logger.getLogger(javaClass)

    @Inject
    lateinit var gameEngine: GameEngine

    @Inject
    lateinit var gameState: GameState

    @Inject
    lateinit var objectMapper: ObjectMapper

    companion object {
        private val sessions = ConcurrentHashMap<String, Session>()
    }

    @OnOpen
    fun onOpen(session: Session) {
        sessions[session.id] = session
        log.info("WebSocket connected: ${session.id} (Total: ${sessions.size})")

        // Send initial game state
        sendGameState(session)
    }

    @OnClose
    fun onClose(session: Session) {
        sessions.remove(session.id)
        log.info("WebSocket disconnected: ${session.id} (Remaining: ${sessions.size})")
    }

    @OnError
    fun onError(
        session: Session,
        throwable: Throwable,
    ) {
        log.error("WebSocket error for session ${session.id}", throwable)
        sessions.remove(session.id)
    }

    @OnMessage
    fun onMessage(
        message: String,
        session: Session,
    ) {
        try {
            val command = objectMapper.readValue(message, GameCommand::class.java)
            handleCommand(command, session)
        } catch (e: Exception) {
            log.error("Failed to process message: $message", e)
            sendError(session, "Invalid command format")
        }
    }

    private fun handleCommand(
        command: GameCommand,
        session: Session,
    ) {
        log.debug("Received command: ${command.type} from session ${session.id}")

        when (command.type) {
            "MOVE_PADDLE" -> {
                val y = command.data["y"]?.toString()?.toDoubleOrNull()
                if (y != null) {
                    gameState.paddle2Y = y.coerceIn(0.0, gameState.canvasHeight - gameState.paddleHeight)
                }
            }

            "TOGGLE_PAUSE" -> {
                gameState.togglePause()
                log.info("Game ${if (gameState.paused) "paused" else "resumed"} by ${session.id}")
            }

            "RESET" -> {
                gameState.reset()
                log.info("Game reset by ${session.id}")
            }

            "CLEAR_LINES" -> {
                val count = gameState.lines.size
                gameState.clearLines()
                log.info("Cleared $count lines by ${session.id}")
            }

            "START_LINE" -> {
                val x = command.data["x"]?.toString()?.toDoubleOrNull()
                val y = command.data["y"]?.toString()?.toDoubleOrNull()
                if (x != null && y != null) {
                    gameState.startNewLine(x, y)
                }
            }

            "UPDATE_LINE" -> {
                val x = command.data["x"]?.toString()?.toDoubleOrNull()
                val y = command.data["y"]?.toString()?.toDoubleOrNull()
                if (x != null && y != null) {
                    gameState.updateCurrentLine(x, y)
                }
            }

            "FINISH_LINE" -> {
                gameState.finishCurrentLine()
            }

            "SET_SPEED" -> {
                val speed = command.data["speed"]?.toString()?.toDoubleOrNull()
                if (speed != null && speed in 0.1..10.0) {
                    gameState.baseSpeedMultiplier = speed
                    log.info("Speed set to ${speed}x by ${session.id}")
                }
            }

            "SPAWN_POWERUP" -> {
                val typeStr = command.data["type"]?.toString()
                if (typeStr != null) {
                    try {
                        val type = PowerUpType.valueOf(typeStr)
                        gameEngine.spawnTestPowerUp(type)
                        log.info("Spawned test power-up: $type by ${session.id}")
                    } catch (e: IllegalArgumentException) {
                        sendError(session, "Invalid power-up type: $typeStr")
                    }
                }
            }

            else -> {
                log.warn("Unknown command type: ${command.type}")
                sendError(session, "Unknown command type: ${command.type}")
            }
        }
    }

    fun broadcastGameState() {
        // Guard: Don't broadcast if no sessions or ObjectMapper not ready
        if (sessions.isEmpty() || !::objectMapper.isInitialized) {
            return
        }

        try {
            val state = createGameStateDTO()
            val json = objectMapper.writeValueAsString(state)

            sessions.values.forEach { session ->
                try {
                    if (session.isOpen) {
                        session.asyncRemote.sendText(json)
                    }
                } catch (e: Exception) {
                    log.error("Failed to send to session ${session.id}", e)
                }
            }
        } catch (e: Exception) {
            log.error("Failed to broadcast game state", e)
        }
    }

    private fun sendGameState(session: Session) {
        try {
            val state = createGameStateDTO()
            val json = objectMapper.writeValueAsString(state)
            session.asyncRemote.sendText(json)
        } catch (e: Exception) {
            log.error("Failed to send initial state to ${session.id}", e)
        }
    }

    private fun sendError(
        session: Session,
        message: String,
    ) {
        try {
            val error =
                mapOf(
                    "type" to "ERROR",
                    "message" to message,
                )
            val json = objectMapper.writeValueAsString(error)
            session.asyncRemote.sendText(json)
        } catch (e: Exception) {
            log.error("Failed to send error to ${session.id}", e)
        }
    }

    private fun createGameStateDTO(): GameStateDTO =
        GameStateDTO(
            puckX = gameState.puckX,
            puckY = gameState.puckY,
            paddle1Y = gameState.paddle1Y,
            paddle2Y = gameState.paddle2Y,
            paddleHeight = gameState.paddleHeight,
            canvasWidth = gameState.canvasWidth,
            canvasHeight = gameState.canvasHeight,
            lines =
                gameState.lines.map { line ->
                    LineDTO(
                        points = line.flattenedPoints?.map { PointDTO(it.x, it.y) } ?: emptyList(),
                        width = line.width,
                        animationProgress = line.animationProgress,
                        isAnimating = line.isAnimating,
                    )
                },
            powerUps =
                gameState.powerUps.map { powerUp ->
                    PowerUpDTO(
                        x = powerUp.x,
                        y = powerUp.y,
                        type = powerUp.type.name,
                        emoji = powerUp.type.emoji,
                        color = PowerUpType.getColorCode(powerUp.type),
                        radius = powerUp.radius,
                    )
                },
            activePowerUps =
                gameState.activePowerUpEffects.map { effect ->
                    ActivePowerUpDTO(
                        type = effect.type.name,
                        emoji = effect.type.emoji,
                        description = effect.type.description,
                        remainingSeconds = (effect.remainingTime() / 1_000_000_000).toInt(),
                        color = PowerUpType.getColorCode(effect.type),
                    )
                },
            additionalPucks =
                gameState.additionalPucks.map { puck ->
                    PuckDTO(puck.x, puck.y)
                },
            lifeGridCells =
                gameState.lifeGrid.getAliveCells().map { cell ->
                    CellDTO(cell.x, cell.y, gameState.lifeGrid.cellSize)
                },
            paused = gameState.paused,
            speedMultiplier = gameState.speedMultiplier,
        )

    fun getActiveSessionsCount(): Int = sessions.size
}

// WebSocket DTOs
data class GameCommand(
    val type: String,
    val data: Map<String, Any> = emptyMap(),
)

data class GameStateDTO(
    val puckX: Double,
    val puckY: Double,
    val paddle1Y: Double,
    val paddle2Y: Double,
    val paddleHeight: Double,
    val canvasWidth: Double,
    val canvasHeight: Double,
    val lines: List<LineDTO>,
    val powerUps: List<PowerUpDTO>,
    val activePowerUps: List<ActivePowerUpDTO>,
    val additionalPucks: List<PuckDTO>,
    val lifeGridCells: List<CellDTO>,
    val paused: Boolean,
    val speedMultiplier: Double,
)

data class LineDTO(
    val points: List<PointDTO>,
    val width: Double,
    val animationProgress: Double,
    val isAnimating: Boolean,
)

data class PointDTO(
    val x: Double,
    val y: Double,
)

data class PowerUpDTO(
    val x: Double,
    val y: Double,
    val type: String,
    val emoji: String,
    val color: String,
    val radius: Double,
)

data class ActivePowerUpDTO(
    val type: String,
    val emoji: String,
    val description: String,
    val remainingSeconds: Int,
    val color: String,
)

data class PuckDTO(
    val x: Double,
    val y: Double,
)

data class CellDTO(
    val x: Double,
    val y: Double,
    val size: Double,
)
