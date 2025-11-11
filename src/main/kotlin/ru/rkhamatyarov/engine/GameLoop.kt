package ru.rkhamatyarov.engine

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import javafx.animation.AnimationTimer
import javafx.scene.canvas.GraphicsContext
import javafx.scene.paint.Color
import javafx.scene.text.Font
import ru.rkhamatyarov.handler.InputHandler
import ru.rkhamatyarov.model.AdditionalPuck
import ru.rkhamatyarov.model.GameOfLifeGrid
import ru.rkhamatyarov.model.GameState
import ru.rkhamatyarov.model.Point
import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.service.PowerUpManager
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Main game loop handling physics, rendering, and game state updates.
 *
 * Manages puck movement, collisions, power-ups, AI paddle, and rendering of all game elements.
 * Extends JavaFX AnimationTimer for frame-based updates.
 *
 * @property gc Graphics context for rendering game elements
 * @property player1Score AI player score
 * @property player2Score Human player score
 *
 * @see AnimationTimer
 * @see GameState
 */
@ApplicationScoped
class GameLoop : AnimationTimer() {
    @Inject
    lateinit var gameState: GameState

    @Inject
    lateinit var inputHandler: InputHandler

    @Inject
    lateinit var lifeGrid: GameOfLifeGrid

    @Inject
    lateinit var powerUpManager: PowerUpManager

    var gc: GraphicsContext? = null
    var player1Score = 0
    var player2Score = 0
    private var lastUpdateTime = 0L

    /**
     * Main game loop called each frame.
     * Updates game state, physics, and renders all elements.
     *
     * @param now Current timestamp in nanoseconds
     */
    override fun handle(now: Long) {
        val deltaTime = lastUpdateTime.takeIf { it > 0 }?.let { now - it } ?: 0
        lastUpdateTime = now

        gameState.updatePuckMovingTime(deltaTime)

        if (!gameState.paused) {
            inputHandler.update()
            updatePuckPosition()
            updateAIPaddle()

            powerUpManager.update(deltaTime)
            gameState.updateAdditionalPucks()
            gameState.updateAnimations()

            (now - lifeGrid.lastUpdate)
                .takeIf { it >= lifeGrid.updateInterval }
                ?.let { lifeGrid.update() }
        }
        render()
    }

    /**
     * Updates main puck and additional pucks positions and collisions
     */
    private fun updatePuckPosition() {
        updateSinglePuck()
        validateWallCollision()

        val pucksCopy = gameState.additionalPucks.toList()

        pucksCopy.forEach { puck ->
            updateAdditionalPuck(puck)
        }
    }

    /**
     * Updates main puck physics, collisions, and scoring
     */
    private fun updateSinglePuck() {
        updatePuckPositionCoordinates()

        listOf(
            ::handleVerticalBoundary,
            ::handlePaddleCollision,
            ::handleLineCollisionIfNotGhost,
            ::validateBlockCollision,
            ::handleHorizontalBoundary,
        ).forEach { it() }
    }

    /**
     * When the puck reaches the top wall (Y <= 10) or the bottom wall (Y >= canvasHeight - 10),
     * its vertical velocity is reversed to simulate a realistic bounce-back effect.
     *
     * **Example:**
     * - Puck at Y=8 moving upward (VY=-3) → reversed to VY=3 (bounces down)
     * - Puck at Y=592 moving downward (VY=3) on 600px canvas → reversed to VY=-3 (bounces up)
     */
    private fun handleVerticalBoundary() {
        if (gameState.puckY <= 10 || gameState.puckY >= gameState.canvasHeight - 10) {
            gameState.puckVY = -gameState.puckVY
        }
    }

    /**
     *
     * The speed multiplier allows for temporary speed boosts (e.g., power-ups) without modifying
     * the base velocity. This method applies physics-based position updates:
     *
     * ```
     * puckX += puckVX * speedMultiplier
     * puckY += puckVY * speedMultiplier
     * ```
     */
    private fun updatePuckPositionCoordinates() {
        gameState.puckX += gameState.puckVX * gameState.speedMultiplier
        gameState.puckY += gameState.puckVY * gameState.speedMultiplier
    }

    /**
     * Handles collision between the puck and the vertical boundaries (top and bottom walls).
     *
     * When the puck reaches the top wall (Y <= 10) or the bottom wall (Y >= canvasHeight - 10),
     * its vertical velocity is reversed to simulate a realistic bounce-back effect.
     *
     */
    private fun handleLineCollisionIfNotGhost() {
        if (!gameState.isGhostMode) {
            handleLineCollision()
        }
    }

    /**
     * Handles line collision detection only if the puck is not in ghost mode.
     *
     * When ghost mode is inactive (`!gameState.isGhostMode`), this method delegates to
     * [handleLineCollision] which performs the full collision detection and reflection
     * calculations against all game lines.
     *
     */
    private fun handleHorizontalBoundary() {
        when {
            gameState.puckX <= 10 -> {
                player2Score++
                resetPuck()
            }
            gameState.puckX >= gameState.canvasWidth - 10 -> {
                if (gameState.hasPaddleShield) {
                    gameState.puckVX = -abs(gameState.puckVX)
                    gameState.puckX = gameState.canvasWidth - 20
                } else {
                    player1Score++
                    resetPuck()
                }
            }
        }
    }

    /**
     * Updates additional puck physics and collisions
     *
     * @param puck Additional puck to update
     */
    private fun updateAdditionalPuck(puck: AdditionalPuck) {
        puck.x += puck.vx * gameState.speedMultiplier
        puck.y += puck.vy * gameState.speedMultiplier

        if (puck.y <= 10 || puck.y >= gameState.canvasHeight - 10) puck.vy = -puck.vy
        handleAdditionalPuckPaddleCollision(puck)
        if (!gameState.isGhostMode) handleAdditionalPuckLineCollision(puck)

        if (puck.x <= 10) {
            player2Score++
            gameState.additionalPucks.remove(puck)
        } else if (puck.x >= gameState.canvasWidth - 10) {
            if (!gameState.hasPaddleShield) {
                player1Score++
                gameState.additionalPucks.remove(puck)
            } else {
                puck.vx = -abs(puck.vx)
                puck.x = gameState.canvasWidth - 20
            }
        }
    }

    /**
     * Handles collision between additional puck and paddles
     *
     * @param puck Additional puck to check collision for
     */
    private fun handleAdditionalPuckPaddleCollision(puck: AdditionalPuck) {
        val paddleWidth = 20.0

        if (
            puck.x <= paddleWidth + 10 &&
            puck.x >= paddleWidth - 10 &&
            puck.y >= gameState.paddle1Y &&
            puck.y <= gameState.paddle1Y + gameState.paddleHeight
        ) {
            puck.vx = abs(puck.vx)
            puck.x = paddleWidth + 10
            val hitPosition = (puck.y - gameState.paddle1Y) / gameState.paddleHeight
            val angleVariation = (hitPosition - 0.5) * 2.0
            puck.vy += angleVariation
        }

        if (
            puck.x >= gameState.canvasWidth - paddleWidth - 10 &&
            puck.x <= gameState.canvasWidth - paddleWidth + 10 &&
            puck.y >= gameState.paddle2Y &&
            puck.y <= gameState.paddle2Y + gameState.paddleHeight
        ) {
            puck.vx = -abs(puck.vx)
            puck.x = gameState.canvasWidth - paddleWidth - 10
            val hitPosition = (puck.y - gameState.paddle2Y) / gameState.paddleHeight
            val angleVariation = (hitPosition - 0.5) * 2.0
            puck.vy += angleVariation
        }
    }

    /**
     * Handles collision between additional puck and game lines
     *
     * @param puck Additional puck to check collision for
     */
    private fun handleAdditionalPuckLineCollision(puck: AdditionalPuck) {
        gameState.lines.forEach { line ->
            line.flattenedPoints?.let { points ->
                points
                    .windowed(2) { segment ->
                        val (point1, point2) = segment
                        checkAndHandleAdditionalPuckLineCollision(puck, point1, point2)
                    }.firstOrNull { it }
                    ?.let { return }
            }
        }
    }

    /**
     * Checks and handles collision between additional puck and line segment
     *
     * @param puck Additional puck
     * @param point1 First point of line segment
     * @param point2 Second point of line segment
     * @return true if collision occurred
     */
    private fun checkAndHandleAdditionalPuckLineCollision(
        puck: AdditionalPuck,
        point1: Point,
        point2: Point,
    ): Boolean {
        val distance = distanceToLineSegment(puck.x, puck.y, point1, point2)
        val collisionThreshold = 8.0 + (gameState.currentLine?.width ?: 5.0) / 2

        return (distance <= collisionThreshold).takeIf { it }?.let { _ ->
            calculateAdditionalPuckLineReflection(puck, point1, point2)
            true
        } ?: false
    }

    /**
     * Calculates reflection for additional puck after line collision
     *
     * @param puck Additional puck
     * @param point1 First point of line segment
     * @param point2 Second point of line segment
     */
    private fun calculateAdditionalPuckLineReflection(
        puck: AdditionalPuck,
        point1: Point,
        point2: Point,
    ) {
        val dx = point2.x - point1.x
        val dy = point2.y - point1.y
        val length = hypot(dx, dy)

        if (length > 0) {
            val normalX = -dy / length
            val normalY = dx / length
            val dotProduct = puck.vx * normalX + puck.vy * normalY

            puck.vx -= 2 * dotProduct * normalX
            puck.vy -= 2 * dotProduct * normalY

            puck.vx *= 1.05
            puck.vy *= 1.05
        }
    }

    /**
     * Handles collision between main puck and paddles
     */
    private fun handlePaddleCollision() {
        val paddleWidth = 20.0

        if (
            gameState.puckX <= paddleWidth + 10 &&
            gameState.puckX >= paddleWidth - 10 &&
            gameState.puckY >= gameState.paddle1Y &&
            gameState.puckY <= gameState.paddle1Y + gameState.paddleHeight
        ) {
            gameState.puckVX = abs(gameState.puckVX)
            gameState.puckX = paddleWidth + 10
            val hitPosition = (gameState.puckY - gameState.paddle1Y) / gameState.paddleHeight
            val angleVariation = (hitPosition - 0.5) * 2.0
            gameState.puckVY += angleVariation
        }

        if (
            gameState.puckX >= gameState.canvasWidth - paddleWidth - 10 &&
            gameState.puckX <= gameState.canvasWidth - paddleWidth + 10 &&
            gameState.puckY >= gameState.paddle2Y &&
            gameState.puckY <= gameState.paddle2Y + gameState.paddleHeight
        ) {
            gameState.puckVX = -abs(gameState.puckVX)
            gameState.puckX = gameState.canvasWidth - paddleWidth - 10
            val hitPosition = (gameState.puckY - gameState.paddle2Y) / gameState.paddleHeight
            val angleVariation = (hitPosition - 0.5) * 2.0
            gameState.puckVY += angleVariation
        }
    }

    /**
     * Handles collision between main puck and game lines
     */
    private fun handleLineCollision() {
        gameState.lines.forEach { line ->
            line.flattenedPoints?.let { points ->
                points
                    .windowed(2) { segment ->
                        val (point1, point2) = segment
                        checkAndHandleLineCollision(point1, point2)
                    }.firstOrNull { it }
                    ?.let { return }
            }
        }

        gameState.currentLine?.let { currentLine ->
            currentLine.controlPoints
                .windowed(2) { segment ->
                    val (point1, point2) = segment
                    checkAndHandleLineCollision(point1, point2)
                }.firstOrNull { it }
                ?.let { return }
        }
    }

    /**
     * Checks and handles collision between main puck and line segment
     *
     * @param point1 First point of line segment
     * @param point2 Second point of line segment
     * @return true if collision occurred
     */
    private fun checkAndHandleLineCollision(
        point1: Point,
        point2: Point,
    ): Boolean {
        val distance = distanceToLineSegment(gameState.puckX, gameState.puckY, point1, point2)

        val collisionThreshold = 10.0 + (gameState.currentLine?.width ?: 5.0) / 2

        return (distance <= collisionThreshold).takeIf { it }?.let { _ ->
            calculateLineReflection(point1, point2)
            true
        } ?: false
    }

    /**
     * Calculates reflection for main puck after line collision
     *
     * @param point1 First point of line segment
     * @param point2 Second point of line segment
     */
    private fun calculateLineReflection(
        point1: Point,
        point2: Point,
    ) {
        val dx = point2.x - point1.x
        val dy = point2.y - point1.y
        val length = hypot(dx, dy)

        if (length > 0) {
            val normalX = -dy / length
            val normalY = dx / length

            val dotProduct = gameState.puckVX * normalX + gameState.puckVY * normalY

            gameState.puckVX -= 2 * dotProduct * normalX
            gameState.puckVY -= 2 * dotProduct * normalY

            gameState.puckVX *= 1.05
            gameState.puckVY *= 1.05
        }
    }

    /**
     * Renders all game elements to the graphics context
     */
    private fun render() {
        gc?.let { gc ->
            gc.clearRect(0.0, 0.0, gameState.canvasWidth, gameState.canvasHeight)
            renderPaddles(gc)
            renderPucks(gc)
            renderLines(gc)
            renderPowerUps(gc)
            renderPowerUpEffects(gc)
            renderScore(gc, gameState.canvasWidth)
            renderSpeedIndicator(gc, gameState.canvasWidth)
            renderLifeGrid(gc)
        }
    }

    /**
     * Renders main and additional pucks
     *
     * @param gc Graphics context
     */
    private fun renderPucks(gc: GraphicsContext) {
        val puckColor = if (gameState.isGhostMode) Color.LIGHTBLUE else Color.RED
        gc.fill = puckColor
        gc.fillOval(gameState.puckX - 10, gameState.puckY - 10, 20.0, 20.0)
        gameState.additionalPucks.forEach { puck ->
            val additionalPuckColor = if (gameState.isGhostMode) Color.LIGHTCYAN else Color.ORANGE
            gc.fill = additionalPuckColor
            gc.fillOval(puck.x - 8, puck.y - 8, 16.0, 16.0)
        }
    }

    /**
     * Renders active power-ups on the field
     *
     * @param gc Graphics context
     */
    private fun renderPowerUps(gc: GraphicsContext) {
        gameState.powerUps.forEach { powerUp ->
            if (powerUp.isActive) {
                gc.fill = powerUp.getColor()
                gc.fillOval(
                    powerUp.x - powerUp.radius,
                    powerUp.y - powerUp.radius,
                    powerUp.radius * 2,
                    powerUp.radius * 2,
                )
                gc.stroke = Color.WHITE
                gc.lineWidth = 2.0
                gc.strokeOval(
                    powerUp.x - powerUp.radius,
                    powerUp.y - powerUp.radius,
                    powerUp.radius * 2,
                    powerUp.radius * 2,
                )
                gc.fill = Color.WHITE
                gc.font = Font.font(10.0)
                val iconText =
                    when (powerUp.type) {
                        PowerUpType.SPEED_BOOST -> "S"
                        PowerUpType.MAGNET_BALL -> "M"
                        PowerUpType.GHOST_MODE -> "G"
                        PowerUpType.MULTI_BALL -> "×"
                        PowerUpType.PADDLE_SHIELD -> "⌐"
                    }
                gc.fillText(iconText, powerUp.x - 4, powerUp.y + 4)

                val timeRemaining = powerUp.lifetime - (System.nanoTime() - powerUp.creationTime)
                if (timeRemaining < 5_000_000_000L) {
                    val alpha = (sin(System.nanoTime() / 200_000_000.0) + 1) / 4 + 0.5
                    gc.globalAlpha = alpha
                    gc.fill = Color.RED
                    gc.fillOval(
                        powerUp.x - powerUp.radius - 2,
                        powerUp.y - powerUp.radius - 2,
                        (powerUp.radius + 2) * 2,
                        (powerUp.radius + 2) * 2,
                    )
                    gc.globalAlpha = 1.0
                }
            }
        }
    }

    /**
     * Renders active power-up effects UI indicators
     *
     * @param gc Graphics context
     */
    private fun renderPowerUpEffects(gc: GraphicsContext) {
        var indicatorX = 20.0
        val indicatorY = 20.0
        gameState.activePowerUpEffects.forEach { effect ->
            if (effect.isActive) {
                gc.fill = Color.DARKGRAY.deriveColor(0.0, 1.0, 1.0, 0.8)
                gc.fillRoundRect(indicatorX, indicatorY, 80.0, 30.0, 5.0, 5.0)
                gc.fill =
                    when (effect.type) {
                        PowerUpType.SPEED_BOOST -> Color.YELLOW
                        PowerUpType.MAGNET_BALL -> Color.MAGENTA
                        PowerUpType.GHOST_MODE -> Color.LIGHTBLUE
                        PowerUpType.MULTI_BALL -> Color.ORANGE
                        PowerUpType.PADDLE_SHIELD -> Color.GREEN
                    }
                val effectName =
                    when (effect.type) {
                        PowerUpType.SPEED_BOOST -> "SPEED"
                        PowerUpType.MAGNET_BALL -> "MAGNET"
                        PowerUpType.GHOST_MODE -> "GHOST"
                        PowerUpType.MULTI_BALL -> "MULTI"
                        PowerUpType.PADDLE_SHIELD -> "SHIELD"
                    }
                gc.font = Font.font(10.0)
                gc.fillText(effectName, indicatorX + 5, indicatorY + 15)
                val timeRemaining = effect.duration - (System.nanoTime() - effect.startTime)
                val timeProgress = (timeRemaining.toDouble() / effect.duration).coerceIn(0.0, 1.0)
                gc.fill = Color.WHITE
                gc.fillRect(indicatorX + 5, indicatorY + 20, 70.0, 5.0)
                gc.fill =
                    when (effect.type) {
                        PowerUpType.SPEED_BOOST -> Color.YELLOW
                        PowerUpType.MAGNET_BALL -> Color.MAGENTA
                        PowerUpType.GHOST_MODE -> Color.LIGHTBLUE
                        PowerUpType.MULTI_BALL -> Color.ORANGE
                        PowerUpType.PADDLE_SHIELD -> Color.GREEN
                    }
                gc.fillRect(indicatorX + 5, indicatorY + 20, 70.0 * timeProgress, 5.0)
                indicatorX += 90.0
            }
        }
    }

    /**
     * Renders player paddles with shield effects
     *
     * @param gc Graphics context
     */
    private fun renderPaddles(gc: GraphicsContext) {
        val paddleWidth = 20.0
        if (gameState.hasPaddleShield) {
            gc.fill = Color.GREEN.deriveColor(0.0, 1.0, 1.0, 0.3)
            gc.fillRoundRect(
                gameState.canvasWidth - paddleWidth - 5,
                gameState.paddle2Y - 5,
                paddleWidth + 10,
                gameState.paddleHeight + 10,
                10.0,
                10.0,
            )
            val pulseAlpha = (sin(System.nanoTime() / 300_000_000.0) + 1) / 4 + 0.3
            gc.stroke = Color.GREEN.deriveColor(0.0, 1.0, 1.0, pulseAlpha)
            gc.lineWidth = 3.0
            gc.strokeRoundRect(
                gameState.canvasWidth - paddleWidth - 5,
                gameState.paddle2Y - 5,
                paddleWidth + 10,
                gameState.paddleHeight + 10,
                10.0,
                10.0,
            )
        }
        gc.fill = Color.BLUE
        gc.fillRoundRect(
            gameState.canvasWidth - paddleWidth,
            gameState.paddle2Y,
            paddleWidth,
            gameState.paddleHeight,
            5.0,
            5.0,
        )
        gc.fill = Color.BLACK
        gc.fillRoundRect(0.0, gameState.paddle1Y, paddleWidth, gameState.paddleHeight, 5.0, 5.0)
    }

    /**
     * Renders game lines with animation support
     *
     * @param gc Graphics context
     */
    private fun renderLines(gc: GraphicsContext) {
        gameState.lines.forEach { line ->
            line.flattenedPoints?.let { points ->
                if (points.isNotEmpty()) {
                    gc.stroke = Color.DARKGRAY
                    gc.lineWidth = line.width
                    gc.beginPath()
                    val animProgress = if (line.isAnimating) line.animationProgress else 1.0
                    val pointsToRender = (points.size * animProgress).toInt()
                    if (pointsToRender > 0) {
                        val firstPoint = points[0]
                        gc.moveTo(firstPoint.x, firstPoint.y)
                        for (i in 1 until pointsToRender.coerceAtMost(points.size)) {
                            val point = points[i]
                            gc.lineTo(point.x, point.y)
                        }
                    }
                    gc.stroke()
                }
            }
        }
        gameState.currentLine?.let { currentLine ->
            if (currentLine.controlPoints.size > 1) {
                gc.stroke = Color.DARKGRAY
                gc.lineWidth = currentLine.width
                gc.beginPath()
                val firstPoint = currentLine.controlPoints[0]
                gc.moveTo(firstPoint.x, firstPoint.y)
                for (i in 1 until currentLine.controlPoints.size) {
                    val point = currentLine.controlPoints[i]
                    gc.lineTo(point.x, point.y)
                }
                gc.stroke()
            }
        }
    }

    /**
     * Renders Game of Life grid cells
     *
     * @param gc Graphics context
     */
    private fun renderLifeGrid(gc: GraphicsContext) {
        gc.fill = Color.GRAY
        lifeGrid.getAliveCells().forEach { cell ->
            gc.fillRect(cell.x, cell.y, 2.0, 2.0)
        }
    }

    /**
     * Renders speed boost indicator
     *
     * @param gc Graphics context
     * @param width Canvas width for positioning
     */
    fun renderSpeedIndicator(
        gc: GraphicsContext,
        width: Double,
    ) {
        if (gameState.timeSpeedBoost > 1.0) {
            gc.save()
            gc.fill = Color.RED
            gc.font = Font.font(12.0)
            gc.fillText("Speed boost: ${gameState.timeSpeedBoost}x", (width - 160) / 2, 50.0)
            gc.restore()
        }
    }

    /**
     * Resets puck to center with random velocity
     */
    private fun resetPuck() {
        gameState.puckX = gameState.canvasWidth / 2
        gameState.puckY = gameState.canvasHeight / 2
        gameState.puckVX = if (gameState.puckVX > 0) -3.0 else 3.0
        gameState.puckVY = (Math.random() - 0.5) * 5
        gameState.puckMovingTime = 0L
        gameState.timeSpeedBoost = 1.0
    }

    /**
     * Calculates distance from point to line segment
     *
     * @param px Point X coordinate
     * @param py Point Y coordinate
     * @param p1 Line segment start point
     * @param p2 Line segment end point
     * @return Distance to the closest point on the segment
     */
    private fun distanceToLineSegment(
        px: Double,
        py: Double,
        p1: Point,
        p2: Point,
    ): Double {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y

        return (dx to dy)
            .takeIf { (dxVal, dyVal) ->
                dxVal != 0.0 || dyVal != 0.0
            }?.let { (dxVal, dyVal) ->
                val t = ((px - p1.x) * dxVal + (py - p1.y) * dyVal) / (dxVal * dxVal + dyVal * dyVal)
                val clampedT = t.coerceIn(0.0, 1.0)
                val closestX = p1.x + clampedT * dxVal
                val closestY = p1.y + clampedT * dyVal
                hypot(px - closestX, py - closestY)
            } ?: hypot(px - p1.x, py - p1.y)
    }

    /** Toggles game pause state */
    fun togglePause() {
        gameState.togglePause()
    }

    /**
     * Renders score and active power-ups count
     *
     * @param gc Graphics context
     * @param width Canvas width for positioning
     */
    fun renderScore(
        gc: GraphicsContext,
        width: Double,
    ) {
        gc.fill = Color.BLACK
        gc.font = Font.font(24.0)
        gc.fillText("AI: $player1Score", 50.0, 30.0)
        gc.fillText("Player: $player2Score", width - 150, 30.0)
        val activePowerUps = gameState.activePowerUpEffects.size
        if (activePowerUps > 0) {
            gc.font = Font.font(12.0)
            gc.fill = Color.BLUE
            gc.fillText("Active Power-ups: $activePowerUps", width / 2 - 60, 15.0)
        }
    }

    /**
     * Renders all game objects (paddles, pucks, lines, power-ups, effects, grid)
     *
     * @param gc Graphics context
     */
    fun renderObjects(gc: GraphicsContext) {
        renderPaddles(gc)
        renderPucks(gc)
        renderLines(gc)
        renderPowerUps(gc)
        renderPowerUpEffects(gc)
        renderLifeGrid(gc)
    }

    /**
     * Updates AI paddle position to track the puck
     */
    private fun updateAIPaddle() {
        val targetY = gameState.puckY - gameState.paddleHeight / 2
        val aiSpeed = 4.0
        val currentY = gameState.paddle1Y
        when {
            targetY > currentY + aiSpeed -> gameState.paddle1Y += aiSpeed
            targetY < currentY - aiSpeed -> gameState.paddle1Y -= aiSpeed
            else -> gameState.paddle1Y = targetY
        }
        gameState.paddle1Y = gameState.paddle1Y.coerceIn(0.0, gameState.canvasHeight - gameState.paddleHeight)
    }

    /**
     * Validates collision between puck and Game of Life blocks
     */
    private fun validateBlockCollision() {
        for (i in 0 until lifeGrid.rows) {
            for (j in 0 until lifeGrid.cols) {
                if (lifeGrid.grid[i][j]) {
                    val cellX = lifeGrid.gridX + j * lifeGrid.cellSize
                    val cellY = lifeGrid.gridY + i * lifeGrid.cellSize
                    val cx = gameState.puckX
                    val cy = gameState.puckY
                    if (
                        cx >= cellX &&
                        cx <= cellX + lifeGrid.cellSize &&
                        cy >= cellY &&
                        cy <= cellY + lifeGrid.cellSize
                    ) {
                        lifeGrid.grid[i][j] = false
                        gameState.puckVX = -gameState.puckVX
                        gameState.puckVY = -gameState.puckVY
                        return
                    }
                }
            }
        }
    }

    /**
     * Validates collision between puck and walls
     */
    private fun validateWallCollision() {
        when {
            gameState.puckY <= 10 -> {
                gameState.puckVY = abs(gameState.puckVY)
                gameState.puckY = 10.0
            }
            gameState.puckY >= gameState.canvasHeight - 10 -> {
                gameState.puckVY = -abs(gameState.puckVY)
                gameState.puckY = gameState.canvasHeight - 10.0
            }
        }
    }
}
