package ru.rkhamatyarov.websocket

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
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
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.java

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
        private const val BROADCAST_INTERVAL = 8L
        private val sessions = ConcurrentHashMap<String, Session>()
        private val lastBroadcastTime = AtomicLong(0)
        private val cachedDTO = AtomicReference<String?>(null)
        private var lastStateHash = 0
        private var broadcastCount = 0L
    }

    @OnOpen
    fun onOpen(session: Session) {
        sessions[session.id] = session
        log.info("WebSocket connected: ${session.id}. Total: ${sessions.size}")
        sendGameState(session)
    }

    @OnClose
    fun onClose(session: Session) {
        sessions.remove(session.id)
        log.info("WebSocket disconnected: ${session.id}. Remaining: ${sessions.size}")
    }

    @OnError
    fun onError(
        session: Session,
        throwable: Throwable,
    ) {
        log.error("WebSocket error for session: ${session.id}", throwable)
        sessions.remove(session.id)
    }

    @OnMessage
    fun onMessage(
        message: String,
        session: Session,
    ) {
        try {
            if (message.isBlank()) {
                sendError(session, "Empty message")
                return
            }

            val command =
                try {
                    objectMapper.readValue(message, GameCommand::class.java)
                } catch (e: Exception) {
                    log.error("Failed to parse message as JSON: $message", e)
                    sendError(session, "Invalid JSON format")
                    return
                }

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
        when (command.type) {
            "MOVEPADDLE" -> {
                val y = command.data["y"]?.toString()?.toDoubleOrNull()
                if (y != null) {
                    gameState.paddle2Y = y.coerceAtMost(gameState.canvasHeight - gameState.paddleHeight).coerceAtLeast(0.0)
                    log.debug("COMMAND MOVEPADDLE: y=$y")
                }
            }

            "TOGGLEPAUSE" -> {
                gameState.togglePause()
                log.info("Game ${if (gameState.paused) "paused" else "resumed"} by ${session.id}")
            }

            "RESET" -> {
                gameState.reset()
                log.info("Game reset by ${session.id}")
                log.debug(
                    "COMMAND RESET - " +
                        "puckX=${gameState.puckX}, puckY=${gameState.puckY}, " +
                        "vX=${gameState.puckVX}, vY=${gameState.puckVY}",
                )
                cachedDTO.set(null)
            }

            "CLEARLINES" -> {
                val count = gameState.lines.size
                gameState.clearLines()
                log.info("Cleared $count lines by ${session.id}")
                cachedDTO.set(null)
            }

            "STARTLINE" -> {
                val x = command.data["x"]?.toString()?.toDoubleOrNull()
                val y = command.data["y"]?.toString()?.toDoubleOrNull()
                if (x != null && y != null) {
                    gameState.startNewLine(x, y)
                    log.debug("COMMAND STARTLINE: x=$x, y=$y")
                }
            }

            "UPDATELINE" -> {
                val x = command.data["x"]?.toString()?.toDoubleOrNull()
                val y = command.data["y"]?.toString()?.toDoubleOrNull()
                if (x != null && y != null) {
                    gameState.updateCurrentLine(x, y)
                    log.debug("COMMAND UPDATELINE: x=$x, y=$y")
                }
            }

            "FINISHLINE" -> {
                gameState.finishCurrentLine()
                log.debug("COMMAND FINISHLINE")
                cachedDTO.set(null)
            }

            "SETSPEED" -> {
                val speed = command.data["speed"]?.toString()?.toDoubleOrNull()
                if (speed != null && speed in 0.1..20.0) {
                    gameState.baseSpeedMultiplier = speed
                    gameState.speedMultiplier = speed
                    log.info("Speed set to $speed x by ${session.id}")
                    log.debug("COMMAND SETSPEED: speed=$speed")
                    cachedDTO.set(null)
                }
            }

            "SPAWNPOWERUP" -> {
                val typeStr = command.data["type"]?.toString()
                if (typeStr != null) {
                    try {
                        val type = PowerUpType.valueOf(typeStr)
                        gameEngine.spawnTestPowerUp(type)
                        log.info("Spawned test power-up: $type by ${session.id}")
                        cachedDTO.set(null)
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
        val now = System.currentTimeMillis()
        if (now - lastBroadcastTime.get() < BROADCAST_INTERVAL) return
        if (sessions.isEmpty()) return

        try {
            broadcastCount++
            val dto = createGameStateDTO()
            val json = objectMapper.writeValueAsString(dto)

            if (broadcastCount % 50 == 0L) {
                log.debug(
                    "BROADCAST count=$broadcastCount, " +
                        "puckX=${dto.puckX}, puckY=${dto.puckY}, " +
                        "vX=${dto.puckVX}, vY=${dto.puckVY}, " +
                        "speedMult=${dto.speedMultiplier}, sessions=${sessions.size}",
                )
            }

            sendToAllSessions(json)
            lastBroadcastTime.set(now)
        } catch (e: Exception) {
            log.error("Failed to broadcast game state", e)
        }
    }

    private fun sendToAllSessions(json: String) {
        sessions.values.forEach { session ->
            try {
                if (session.isOpen) {
                    session.asyncRemote.sendText(json)
                }
            } catch (e: Exception) {
                log.error("Failed to send to session: ${session.id}", e)
                sessions.remove(session.id)
            }
        }
    }

    private fun sendGameState(session: Session) {
        try {
            val state = createGameStateDTO()
            val json = objectMapper.writeValueAsString(state)
            log.debug("SENDING INITIAL - puckX=${state.puckX}, puckY=${state.puckY}, vX=${state.puckVX}, vY=${state.puckVY}")
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
            val error = mapOf("type" to "ERROR", "message" to message)
            val json = objectMapper.writeValueAsString(error)
            session.asyncRemote.sendText(json)
        } catch (e: Exception) {
            log.error("Failed to send error to ${session.id}", e)
        }
    }

    private fun createGameStateDTO(): GameStateDTO {
        val lines = ArrayList<LineDTO>(gameState.lines.size)
        for (line in gameState.lines) {
            val points = ArrayList<PointDTO>(line.flattenedPoints?.size ?: 0)
            line.flattenedPoints?.forEach { point ->
                points.add(PointDTO(point.x, point.y))
            }
            lines.add(LineDTO(points, line.width, line.animationProgress, line.isAnimating))
        }

        val powerUps = ArrayList<PowerUpDTO>(gameState.powerUps.size)
        for (powerUp in gameState.powerUps) {
            powerUps.add(
                PowerUpDTO(
                    powerUp.x,
                    powerUp.y,
                    powerUp.type.name,
                    powerUp.type.emoji,
                    PowerUpType.getColorCode(powerUp.type),
                    powerUp.radius,
                ),
            )
        }

        val activePowerUps = ArrayList<ActivePowerUpDTO>(gameState.activePowerUpEffects.size)
        for (effect in gameState.activePowerUpEffects) {
            activePowerUps.add(
                ActivePowerUpDTO(
                    effect.type.name,
                    effect.type.emoji,
                    effect.type.description,
                    (effect.remainingTime() / 1_000_000_000L).toInt(),
                    PowerUpType.getColorCode(effect.type),
                ),
            )
        }

        val additionalPucks = ArrayList<PuckDTO>(gameState.additionalPucks.size)
        for (puck in gameState.additionalPucks) {
            additionalPucks.add(PuckDTO(puck.x, puck.y))
        }

        val lifeGridCells = ArrayList<CellDTO>(gameState.lifeGrid.getAliveCells().size)
        for (cell in gameState.lifeGrid.getAliveCells()) {
            lifeGridCells.add(CellDTO(cell.x, cell.y, gameState.lifeGrid.cellSize))
        }

        return GameStateDTO(
            puckX = gameState.puckX,
            puckY = gameState.puckY,
            puckVX = gameState.puckVX,
            puckVY = gameState.puckVY,
            paddle1Y = gameState.paddle1Y,
            paddle2Y = gameState.paddle2Y,
            paddleHeight = gameState.paddleHeight,
            canvasWidth = gameState.canvasWidth,
            canvasHeight = gameState.canvasHeight,
            lines = lines,
            powerUps = powerUps,
            activePowerUps = activePowerUps,
            additionalPucks = additionalPucks,
            lifeGridCells = lifeGridCells,
            paused = gameState.paused,
            speedMultiplier = gameState.speedMultiplier,
        )
    }

    fun getActiveSessionsCount(): Int = sessions.size

    data class GameStateDTO(
        val puckX: Double,
        val puckY: Double,
        val puckVX: Double,
        val puckVY: Double,
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
}

data class GameCommand
    @JsonCreator
    constructor(
        @JsonProperty("type")
        val type: String,
        @JsonProperty("data")
        val data: Map<String, Any> = emptyMap(),
    )
