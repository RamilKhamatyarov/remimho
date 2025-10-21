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

    override fun handle(now: Long) {
        val deltaTime = if (lastUpdateTime > 0) now - lastUpdateTime else 0
        lastUpdateTime = now

        gameState.updatePuckMovingTime(deltaTime)

        if (!gameState.paused) {
            inputHandler.update()
            updatePuckPosition()
            updateAIPaddle()

            powerUpManager.update(deltaTime)
            gameState.updateAdditionalPucks()

            gameState.updateAnimations()

            if (now - lifeGrid.lastUpdate >= lifeGrid.updateInterval) {
                lifeGrid.update()
            }
        }
        render()
    }

    private fun updatePuckPosition() {
        updateSinglePuck()
        validateWallCollision()

        val pucksCopy = gameState.additionalPucks.toList()

        pucksCopy.forEach { puck ->
            updateAdditionalPuck(puck)
        }
    }

    private fun updateSinglePuck() {
        gameState.puckX += gameState.puckVX * gameState.speedMultiplier
        gameState.puckY += gameState.puckVY * gameState.speedMultiplier

        if (gameState.puckY <= 10 || gameState.puckY >= gameState.canvasHeight - 10) {
            gameState.puckVY = -gameState.puckVY
        }

        handlePaddleCollision()

        if (!gameState.isGhostMode) {
            handleLineCollision()
        }

        validateBlockCollision()

        when {
            gameState.puckX <= 10 -> {
                player2Score++
                resetPuck()
            }

            gameState.puckX >= gameState.canvasWidth - 10 -> {
                if (!gameState.hasPaddleShield) {
                    player1Score++
                    resetPuck()
                } else {
                    gameState.puckVX = -abs(gameState.puckVX)
                    gameState.puckX = gameState.canvasWidth - 20
                }
            }
        }
    }

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

    private fun checkAndHandleAdditionalPuckLineCollision(
        puck: AdditionalPuck,
        point1: Point,
        point2: Point,
    ): Boolean {
        val distance = distanceToLineSegment(puck.x, puck.y, point1, point2)
        val collisionThreshold = 8.0 + (gameState.currentLine?.width ?: 5.0) / 2

        return (distance <= collisionThreshold).takeIf { it }?.let { isColliding ->
            calculateAdditionalPuckLineReflection(puck, point1, point2)
            true
        } ?: false
    }

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

    private fun renderLifeGrid(gc: GraphicsContext) {
        gc.fill = Color.GRAY
        lifeGrid.getAliveCells().forEach { cell ->
            gc.fillRect(cell.x, cell.y, 2.0, 2.0)
        }
    }

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

    private fun resetPuck() {
        gameState.puckX = gameState.canvasWidth / 2
        gameState.puckY = gameState.canvasHeight / 2
        gameState.puckVX = if (gameState.puckVX > 0) -3.0 else 3.0
        gameState.puckVY = (Math.random() - 0.5) * 5
        gameState.puckMovingTime = 0L
        gameState.timeSpeedBoost = 1.0
    }

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

    fun togglePause() {
        gameState.togglePause()
    }

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

    fun renderObjects(
        gc: GraphicsContext,
        width: Double,
    ) {
        renderPaddles(gc)
        renderPucks(gc)
        renderLines(gc)
        renderPowerUps(gc)
        renderPowerUpEffects(gc)
        renderLifeGrid(gc)
    }

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

    private fun validateLineCollision() {
        gameState.lines.forEach { line ->
            val points = line.flattenedPoints ?: line.controlPoints
            points.forEachIndexed { index, point ->
                if (index < points.size - 1) {
                    val nextPoint = points[index + 1]
                    val distance = distanceToLineSegment(gameState.puckX, gameState.puckY, point, nextPoint)
                    if (distance <= 10.0) {
                        val dx = nextPoint.x - point.x
                        val dy = nextPoint.y - point.y
                        val length = hypot(dx, dy)
                        if (length > 0) {
                            val normalX = -dy / length
                            val normalY = dx / length
                            val dotProduct = gameState.puckVX * normalX + gameState.puckVY * normalY
                            gameState.puckVX -= 2 * dotProduct * normalX
                            gameState.puckVY -= 2 * dotProduct * normalY
                        }
                        return
                    }
                }
            }
        }
    }

    private fun validatePaddleCollision() {
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

    private fun validateScore() {
        if (gameState.puckX <= 10) {
            player2Score++
            gameState.reset()
        } else if (gameState.puckX >= gameState.canvasWidth - 10) {
            if (!gameState.hasPaddleShield) {
                player1Score++
                gameState.reset()
            } else {
                gameState.puckVX = -abs(gameState.puckVX)
                gameState.puckX = gameState.canvasWidth - 20
            }
        }
    }

    private fun validateWallCollision() {
        if (gameState.puckY <= 10) {
            gameState.puckVY = -gameState.puckVY
        } else if (gameState.puckY >= gameState.canvasHeight - 10) {
            gameState.puckVY = -gameState.puckVY
        }
    }
}
