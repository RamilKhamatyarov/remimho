package ru.rkhamatyarov.service

import io.quarkus.runtime.Startup
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jboss.logging.Logger
import ru.rkhamatyarov.model.AdditionalPuck
import ru.rkhamatyarov.model.GameState
import ru.rkhamatyarov.model.Point
import ru.rkhamatyarov.model.PowerUp
import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.websocket.GameWebSocket
import kotlin.math.abs
import kotlin.math.hypot

@Startup
@ApplicationScoped
class GameEngine {
    private val log = Logger.getLogger(javaClass)

    @Inject
    lateinit var gameState: GameState

    @Inject
    lateinit var powerUpManager: PowerUpManager

    @Inject
    lateinit var gameWebSocket: jakarta.enterprise.inject.Instance<GameWebSocket>

    @Inject
    lateinit var formulaRegistry: FormulaRegistry

    private var lastUpdateTime = System.nanoTime()
    private var initialized = false
    private var frameCount = 0L
    private var debugFrameCount = 0L

    @jakarta.annotation.PostConstruct
    fun initialize() {
        log.info("GameEngine initialized")
        gameState.speedMultiplier = gameState.baseSpeedMultiplier
        formulaRegistry.startRandomCurveScheduler()
        initialized = true
        log.debug(
            "INIT: puckVX=${gameState.puckVX}, puckVY=${gameState.puckVY}, " +
                "speedMultiplier=${gameState.speedMultiplier}, puckX=${gameState.puckX}, puckY=${gameState.puckY}",
        )
    }

    @Scheduled(every = "0.008s")
    fun gameLoop() {
        if (!initialized) return

        try {
            val currentTime = System.nanoTime()
            val deltaTime = currentTime - lastUpdateTime
            lastUpdateTime = currentTime

            frameCount++
            debugFrameCount++

            if (frameCount % 120 == 0L) {
                val fps = 1_000_000_000.0 / deltaTime
                log.debug(
                    "GAMELOOP: fps=${fps.toInt()}, " +
                        "puckX=${gameState.puckX.toInt()}, puckY=${gameState.puckY.toInt()}, " +
                        "vX=${gameState.puckVX.toInt()}, vY=${gameState.puckVY.toInt()}, " +
                        "speedMult=${gameState.speedMultiplier}",
                )
            }

            if (!gameState.paused) {
                updateGame(deltaTime)
            }

            if (gameWebSocket.isResolvable) {
                gameWebSocket.get().broadcastGameState()
            }
        } catch (e: Exception) {
            log.error("Error in game loop: ${e.message}", e)
        }
    }

    private fun updateGame(deltaTime: Long) {
        gameState.updatePuckMovingTime(deltaTime)
        val oldX = gameState.puckX
        val oldY = gameState.puckY

        updateAIPaddle()
        updatePuckPosition()

        val deltaX = gameState.puckX - oldX
        val deltaY = gameState.puckY - oldY

        if (debugFrameCount % 50 == 0L) {
            log.debug(
                "PUCK_MOVE: frame=$debugFrameCount, oldX=$oldX, oldY=$oldY, " +
                    "newX=${gameState.puckX}, newY=${gameState.puckY}, deltaX=$deltaX, deltaY=$deltaY, " +
                    "vX=${gameState.puckVX}, vY=${gameState.puckVY}, speedMult=${gameState.speedMultiplier}",
            )
        }

        checkWallCollisions()
        checkPaddleCollisions()
        checkLineCollisions()
        gameState.updateAdditionalPucks()
        checkAdditionalPuckCollisions()
        powerUpManager.update(deltaTime)
        updateGameOfLife()
        gameState.updateAnimations()
        gameState.cleanupCollisionCooldowns()
    }

    private fun updateAIPaddle() {
        val aiSpeed = 15.0
        val targetY = gameState.puckY - gameState.paddleHeight / 2
        if (abs(gameState.paddle1Y - targetY) > 5) {
            if (gameState.paddle1Y < targetY) {
                gameState.paddle1Y += aiSpeed * gameState.speedMultiplier
            } else {
                gameState.paddle1Y -= aiSpeed * gameState.speedMultiplier
            }
        }
        gameState.paddle1Y = gameState.paddle1Y.coerceIn(0.0, gameState.canvasHeight - gameState.paddleHeight)
    }

    private fun updatePuckPosition() {
        val moveX = gameState.puckVX * gameState.speedMultiplier
        val moveY = gameState.puckVY * gameState.speedMultiplier

        gameState.puckX += moveX
        gameState.puckY += moveY

        if (debugFrameCount % 50 == 0L) {
            log.debug(
                "PUCK_UPDATE: moveX=$moveX, moveY=$moveY, vX=${gameState.puckVX}, vY=${gameState.puckVY}, " +
                    "speedMult=${gameState.speedMultiplier}, newX=${gameState.puckX}, newY=${gameState.puckY}",
            )
        }

        gameState.capPuckVelocity()
    }

    private fun checkWallCollisions() {
        val puckRadius = 10.0

        if (gameState.puckY <= puckRadius) {
            gameState.puckY = puckRadius
            gameState.puckVY = abs(gameState.puckVY) * 1.15
            log.debug("COLLISION: top wall, vY=${gameState.puckVY}")
        } else if (gameState.puckY >= gameState.canvasHeight - puckRadius) {
            gameState.puckY = gameState.canvasHeight - puckRadius
            gameState.puckVY = -abs(gameState.puckVY) * 1.15
            log.debug("COLLISION: bottom wall, vY=${gameState.puckVY}")
        }

        if (gameState.puckX <= puckRadius) {
            log.debug("RESET: puck out of bounds (left), x=${gameState.puckX}")
            gameState.reset()
            return
        }

        if (gameState.puckX >= gameState.canvasWidth - puckRadius) {
            log.debug("RESET: puck out of bounds (right), x=${gameState.puckX}")
            gameState.reset()
            return
        }
    }

    private fun checkPaddleCollisions() {
        val puckRadius = 10.0
        val paddleWidth = 20.0

        if (gameState.puckX <= paddleWidth + puckRadius &&
            gameState.puckY >= gameState.paddle1Y &&
            gameState.puckY <= gameState.paddle1Y + gameState.paddleHeight
        ) {
            gameState.puckX = paddleWidth + puckRadius
            gameState.puckVX = abs(gameState.puckVX) * 1.3
            val relativeY = (gameState.puckY - gameState.paddle1Y) / gameState.paddleHeight
            gameState.puckVY = (relativeY - 0.5) * 30.0
            log.debug("COLLISION: paddle1, vX=${gameState.puckVX}, vY=${gameState.puckVY}")
        }

        if (gameState.puckX >= gameState.canvasWidth - paddleWidth - puckRadius &&
            gameState.puckY >= gameState.paddle2Y &&
            gameState.puckY <= gameState.paddle2Y + gameState.paddleHeight
        ) {
            gameState.puckX = gameState.canvasWidth - paddleWidth - puckRadius
            gameState.puckVX = -abs(gameState.puckVX) * 1.3
            val relativeY = (gameState.puckY - gameState.paddle2Y) / gameState.paddleHeight
            gameState.puckVY = (relativeY - 0.5) * 30.0
            log.debug("COLLISION: paddle2, vX=${gameState.puckVX}, vY=${gameState.puckVY}")
        }
    }

    private fun checkLineCollisions() {
        if (gameState.isGhostMode) return
        val puckRadius = 10.0

        gameState.lines.forEach { line ->
            val points = line.flattenedPoints ?: return@forEach
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                if (gameState.isLineSegmentInCooldown(p1, p2)) continue

                val distance = distanceToLineSegment(gameState.puckX, gameState.puckY, p1.x, p1.y, p2.x, p2.y)
                if (distance < puckRadius + line.width / 2) {
                    handleLineCollision(p1, p2)
                    gameState.recordLineSegmentCollision(p1, p2)
                    log.debug("COLLISION: line, vX=${gameState.puckVX}, vY=${gameState.puckVY}")
                }
            }
        }
    }

    private fun handleLineCollision(
        p1: Point,
        p2: Point,
    ) {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val length = hypot(dx, dy)
        if (length < 0.001) return

        val nx = -dy / length
        val ny = dx / length
        val dot = gameState.puckVX * nx + gameState.puckVY * ny
        gameState.puckVX -= 2 * dot * nx * 1.15
        gameState.puckVY -= 2 * dot * ny * 1.15
        gameState.puckX += nx * 2
        gameState.puckY += ny * 2
    }

    private fun distanceToLineSegment(
        px: Double,
        py: Double,
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
    ): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        val lengthSq = dx * dx + dy * dy
        if (lengthSq < 0.0001) {
            return hypot(px - x1, py - y1)
        }

        val t = ((px - x1) * dx + (py - y1) * dy) / lengthSq
        val clampedT = t.coerceIn(0.0, 1.0)
        val closestX = x1 + clampedT * dx
        val closestY = y1 + clampedT * dy
        return hypot(px - closestX, py - closestY)
    }

    private fun checkAdditionalPuckCollisions() {
        gameState.additionalPucks.forEach { puck ->
            checkAdditionalPuckWalls(puck)
            checkAdditionalPuckPaddles(puck)
            if (!gameState.isGhostMode) {
                checkAdditionalPuckLines(puck)
            }
        }
    }

    private fun checkAdditionalPuckWalls(puck: AdditionalPuck) {
        val radius = 10.0
        if (puck.y <= radius) {
            puck.y = radius
            puck.vy = abs(puck.vy) * 1.15
        } else if (puck.y >= gameState.canvasHeight - radius) {
            puck.y = gameState.canvasHeight - radius
            puck.vy = -abs(puck.vy) * 1.15
        }

        if (puck.x <= radius || puck.x >= gameState.canvasWidth - radius) {
            puck.vx = -puck.vx * 1.15
        }
    }

    private fun checkAdditionalPuckPaddles(puck: AdditionalPuck) {
        val radius = 10.0
        val paddleWidth = 20.0

        if (puck.x <= paddleWidth + radius &&
            puck.y >= gameState.paddle1Y &&
            puck.y <= gameState.paddle1Y + gameState.paddleHeight
        ) {
            puck.x = paddleWidth + radius
            puck.vx = abs(puck.vx) * 1.2
        }

        if (puck.x >= gameState.canvasWidth - paddleWidth - radius &&
            puck.y >= gameState.paddle2Y &&
            puck.y <= gameState.paddle2Y + gameState.paddleHeight
        ) {
            puck.x = gameState.canvasWidth - paddleWidth - radius
            puck.vx = -abs(puck.vx) * 1.2
        }
    }

    private fun checkAdditionalPuckLines(puck: AdditionalPuck) {
        val radius = 10.0
        gameState.lines.forEach { line ->
            val points = line.flattenedPoints ?: return@forEach
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                val distance = distanceToLineSegment(puck.x, puck.y, p1.x, p1.y, p2.x, p2.y)
                if (distance < radius + line.width / 2) {
                    val dx = p2.x - p1.x
                    val dy = p2.y - p1.y
                    val length = hypot(dx, dy)
                    if (length > 0.001) {
                        val nx = -dy / length
                        val ny = dx / length
                        val dot = puck.vx * nx + puck.vy * ny
                        puck.vx -= 2 * dot * nx * 1.15
                        puck.vy -= 2 * dot * ny * 1.15
                        puck.x += nx * 2
                        puck.y += ny * 2
                    }
                }
            }
        }
    }

    private fun updateGameOfLife() {
        val currentTime = System.nanoTime()
        if (currentTime - gameState.lifeGrid.lastUpdate > gameState.lifeGrid.updateInterval) {
            gameState.lifeGrid.update()
            gameState.lifeGrid.lastUpdate = currentTime
        }
    }

    fun spawnTestPowerUp(type: PowerUpType) {
        val powerUp = PowerUp(x = gameState.canvasWidth / 2, y = gameState.canvasHeight / 2, type = type)
        gameState.powerUps.add(powerUp)
        log.info("Test power-up spawned: $type")
    }
}
