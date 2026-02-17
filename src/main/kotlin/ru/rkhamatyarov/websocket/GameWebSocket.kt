package ru.rkhamatyarov.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.scheduler.Scheduled
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
import ru.rkhamatyarov.websocket.dto.ActivePowerUpDTO
import ru.rkhamatyarov.websocket.dto.CellDTO
import ru.rkhamatyarov.websocket.dto.GameCommand
import ru.rkhamatyarov.websocket.dto.GameStateDTO
import ru.rkhamatyarov.websocket.dto.LineDTO
import ru.rkhamatyarov.websocket.dto.PointDTO
import ru.rkhamatyarov.websocket.dto.PowerUpDTO
import ru.rkhamatyarov.websocket.dto.PuckDTO
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

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
        private const val BROADCAST_INTERVAL = 16L
        private val sessions: ConcurrentHashMap<String, Session> = ConcurrentHashMap()
        private val lastBroadcastTime: AtomicLong = AtomicLong(0)
        private val cachedGameStateJson: AtomicReference<String?> = AtomicReference(null)
        private var lastGameStateHash: Int = 0
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
        val startNano = System.nanoTime()

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
        } finally {
            val latencyMs = (System.nanoTime() - startNano) / 1_000_000.0
            if (latencyMs > 10) {
                log.warn("Slow message: ${"%.2f".format(latencyMs)}ms")
            }
        }
    }

    private fun handleCommand(
        command: GameCommand,
        session: Session,
    ) {
        when (command.type.uppercase()) {
            "MOVE_PADDLE" -> {
                val y = command.data["y"]?.toString()?.toDoubleOrNull()
                if (y != null) {
                    gameState.paddle2Y =
                        y
                            .coerceAtMost(gameState.canvasHeight - gameState.paddleHeight)
                            .coerceAtLeast(0.0)
                    if (log.isDebugEnabled) {
                        log.debug("COMMAND MOVE_PADDLE: y=$y")
                    }
                } else {
                    sendError(session, "Invalid MOVE_PADDLE data: y is required")
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
                    if (log.isDebugEnabled) {
                        log.debug("COMMAND START_LINE: x=$x, y=$y")
                    }
                } else {
                    sendError(session, "Invalid START_LINE data: x and y are required")
                }
            }

            "UPDATE_LINE" -> {
                val x = command.data["x"]?.toString()?.toDoubleOrNull()
                val y = command.data["y"]?.toString()?.toDoubleOrNull()
                if (x != null && y != null) {
                    gameState.updateCurrentLine(x, y)
                    if (log.isDebugEnabled) {
                        log.debug("COMMAND UPDATE_LINE: x=$x, y=$y")
                    }
                } else {
                    sendError(session, "Invalid UPDATE_LINE data: x and y are required")
                }
            }

            "FINISH_LINE" -> {
                gameState.finishCurrentLine()
                if (log.isDebugEnabled) {
                    log.debug("COMMAND FINISH_LINE")
                }
            }

            "SET_SPEED" -> {
                val speed = command.data["speed"]?.toString()?.toDoubleOrNull()
                if (speed != null && speed in 0.1..20.0) {
                    gameState.baseSpeedMultiplier = speed
                    gameState.speedMultiplier = speed
                    log.info("Speed set to $speed x by ${session.id}")
                } else {
                    sendError(session, "Invalid SET_SPEED data: speed must be between 0.1 and 20.0")
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
                        log.error(e.message, e)
                        sendError(session, "Invalid power-up type: $typeStr")
                    }
                } else {
                    sendError(session, "Invalid SPAWN_POWERUP data: type is required")
                }
            }

            else -> {
                log.warn("Unknown command type: ${command.type}")
                sendError(session, "Unknown command type: ${command.type}")
            }
        }
    }

    @Scheduled(every = "0.016s")
    fun scheduledBroadcast() {
        broadcastGameState()
    }

    fun broadcastGameState() {
        val now = System.currentTimeMillis()

        if (now - lastBroadcastTime.get() < BROADCAST_INTERVAL) return
        if (sessions.isEmpty()) return

        try {
            val broadcastStartNano = System.nanoTime()

            val dto = createGameStateDTO()
            val currentHash = dto.hashCode()

            val json =
                if (lastGameStateHash != currentHash) {
                    objectMapper.writeValueAsString(dto).also {
                        lastGameStateHash = currentHash
                        cachedGameStateJson.set(it)
                    }
                } else {
                    cachedGameStateJson.get() ?: objectMapper.writeValueAsString(dto)
                }

            sessions.values.parallelStream().forEach { session ->
                try {
                    if (session.isOpen) {
                        session.asyncRemote.sendText(json)
                    }
                } catch (e: Exception) {
                    log.error("Failed to send to session: ${session.id}", e)
                    sessions.remove(session.id)
                }
            }

            lastBroadcastTime.set(now)

            val broadcastMs = (System.nanoTime() - broadcastStartNano) / 1_000_000.0
            if (broadcastMs > 50) {
                log.warn("Slow broadcast: ${"%.2f".format(broadcastMs)}ms to ${sessions.size} clients")
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
}
