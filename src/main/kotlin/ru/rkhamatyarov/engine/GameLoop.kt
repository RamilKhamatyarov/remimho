package ru.rkhamatyarov.engine

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import javafx.animation.AnimationTimer
import javafx.scene.canvas.GraphicsContext
import javafx.scene.paint.Color
import javafx.scene.text.Font
import ru.rkhamatyarov.handler.InputHandler
import ru.rkhamatyarov.model.GameOfLifeGrid
import ru.rkhamatyarov.model.GameState
import ru.rkhamatyarov.model.Line
import ru.rkhamatyarov.model.Point
import kotlin.math.absoluteValue
import kotlin.math.hypot
import kotlin.math.sqrt

@ApplicationScoped
class GameLoop : AnimationTimer() {
    @Inject
    lateinit var gameState: GameState

    @Inject
    lateinit var inputHandler: InputHandler

    @Inject
    lateinit var lifeGrid: GameOfLifeGrid

    var gc: GraphicsContext? = null
    var player1Score = 0
    var player2Score = 0

    private var lastUpdateTime = 0L

    override fun handle(now: Long) {
        val deltaTime = if (lastUpdateTime > 0) now - lastUpdateTime else 0
        lastUpdateTime = now

        gameState.updateAnimations()

        if (deltaTime > 0) {
            gameState.updatePuckMovingTime(deltaTime)
        }

        inputHandler.update()

        val width = gameState.canvasWidth
        val height = gameState.canvasHeight

        gc?.let {
            clearCanvas(it, width, height)
            renderScore(it, width)
            renderSpeedIndicator(it, width)
            renderObjects(it, width)

            if (gameState.paused) {
                renderPauseOverlay(it, width, height)
                return
            }
        }

        if (!gameState.paused) {
            if (now - lifeGrid.lastUpdate > lifeGrid.updateInterval) {
                lifeGrid.update()
                lifeGrid.lastUpdate = now
            }

            updatePuckPosition()
            validateCollisions()
            updateAI()
        }
    }

    private fun renderSpeedIndicator(
        gc: GraphicsContext,
        width: Double,
    ) {
        if (gameState.timeSpeedBoost > 1.0) {
            gc.save()
            gc.fill = Color.LIME
            gc.font = Font.font(14.0)

            val speedText = String.format("Speed boost: %.1fx", gameState.timeSpeedBoost)
            gc.fillText(speedText, width * 0.4, 50.0)

            gc.restore()
        }
    }

    fun togglePause() {
        gameState.togglePause()
    }

    private fun renderPauseOverlay(
        gc: GraphicsContext,
        width: Double,
        height: Double,
    ) {
        gc.save()
        gc.fill = Color.rgb(0, 0, 0, 0.5)
        gc.fillRect(0.0, 0.0, width, height)

        gc.fill = Color.WHITE
        gc.font = Font.font(48.0)

        val text = "PAUSED"
        val textWidth = gc.font.size * text.length * 0.6
        gc.fillText(text, (width - textWidth) / 2, height / 2)

        gc.restore()
    }

    private fun clearCanvas(
        gc: GraphicsContext,
        width: Double,
        height: Double,
    ) {
        gc.clearRect(0.0, 0.0, width, height)
    }

    fun renderScore(
        gc: GraphicsContext,
        width: Double,
    ) {
        gc.save()
        gc.fill = Color.BLACK
        gc.stroke = Color.BLACK
        gc.lineWidth = 1.0
        gc.font = Font.font(20.0)
        gc.fillText("AI: $player1Score", width * 0.1, 30.0)
        gc.fillText("Player: $player2Score", width * 0.8, 30.0)

        val speedText = String.format("Speed: %.1fx", gameState.speedMultiplier)
        gc.fillText(speedText, width * 0.45, 30.0)

        gc.restore()
    }

    fun renderObjects(
        gc: GraphicsContext,
        width: Double,
    ) {
        gc.fill = Color.BLUEVIOLET
        gc.fillRect(20.0, gameState.paddle1Y, 10.0, gameState.paddleHeight)
        gc.fillRect(width - 30.0, gameState.paddle2Y, 10.0, gameState.paddleHeight)
        gc.fillOval(gameState.puckX, gameState.puckY, 20.0, 20.0)

        for (i in 0 until lifeGrid.rows) {
            for (j in 0 until lifeGrid.cols) {
                if (lifeGrid.grid[i][j]) {
                    val x = lifeGrid.gridX + j * lifeGrid.cellSize
                    val y = lifeGrid.gridY + i * lifeGrid.cellSize
                    gc.fill = Color.DARKSLATEGRAY
                    gc.fillRect(x, y, lifeGrid.cellSize, lifeGrid.cellSize)
                }
            }
        }

        gc.stroke = Color.DARKGRAY

        gameState.lines.forEach { line -> renderLine(gc, line) }
        gameState.currentLine?.let { line -> renderLine(gc, line) }
    }

    private fun renderLine(
        gc: GraphicsContext,
        line: Line,
    ) {
        if (line.isAnimating) {
            renderAnimatedLine(gc, line)
        } else if (line.controlPoints.size < 4) {
            renderPolyline(gc, line.controlPoints, line.width)
        } else {
            renderBezierSpline(gc, line.controlPoints, line.width)
        }
    }

    private fun renderAnimatedLine(
        gc: GraphicsContext,
        line: Line,
    ) {
        val points = line.flattenedPoints ?: return
        if (points.isEmpty()) return

        gc.save()
        gc.stroke = Color.DARKGRAY
        gc.lineWidth = line.width

        val totalPoints = points.size
        val pointsToDraw = (totalPoints * line.animationProgress).toInt().coerceAtLeast(1)

        gc.beginPath()
        gc.moveTo(points[0].x, points[0].y)

        for (i in 1 until pointsToDraw) {
            gc.lineTo(points[i].x, points[i].y)
        }
        gc.stroke()
        gc.restore()
    }

    private fun renderPolyline(
        gc: GraphicsContext,
        points: List<Point>,
        width: Double,
    ) {
        if (points.size < 2) return

        gc.save()
        gc.lineWidth = width
        gc.beginPath()
        gc.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            gc.lineTo(points[i].x, points[i].y)
        }
        gc.stroke()
        gc.restore()
    }

    private fun renderBezierSpline(
        gc: GraphicsContext,
        points: List<Point>,
        lineWidth: Double,
    ) {
        if (points.size < 4) {
            renderPolyline(gc, points, lineWidth)
            return
        }

        gc.save()
        gc.lineWidth = lineWidth
        gc.beginPath()
        gc.moveTo(points[0].x, points[0].y)

        val tension = 0.5
        val divisor = 6 * tension

        for (i in 0 until points.size - 1) {
            if (i + 3 >= points.size) break

            val p0 = points[i]
            val p1 = points[i + 1]
            val p2 = points[i + 2]
            val p3 = points[i + 3]

            val dx1 = (p2.x - p0.x) / divisor
            val dy1 = (p2.y - p0.y) / divisor
            val dx2 = (p3.x - p1.x) / divisor
            val dy2 = (p3.y - p1.y) / divisor

            val b1x = p1.x + dx1
            val b1y = p1.y + dy1
            val b2x = p2.x - dx2
            val b2y = p2.y - dy2

            gc.bezierCurveTo(b1x, b1y, b2x, b2y, p2.x, p2.y)
        }

        gc.stroke()
        gc.restore()
    }

    private fun updatePuckPosition() {
        gameState.puckX += gameState.puckVX * gameState.speedMultiplier
        gameState.puckY += gameState.puckVY * gameState.speedMultiplier
    }

    private fun validateCollisions() {
        validateWallCollision()
        validateScore()
        validatePaddleCollision()
        validateLineCollision()
        validateBlockCollision()
    }

    private fun validateWallCollision() {
        if (gameState.puckY <= 0 || gameState.puckY >= gameState.canvasHeight - 20) {
            gameState.puckVY *= -1
        }
    }

    private fun validateScore() {
        when {
            gameState.puckX < 0 -> player2Score++
            gameState.puckX > gameState.canvasWidth -> player1Score++
        }
        if (gameState.puckX < 0 || gameState.puckX > gameState.canvasWidth) {
            gameState.reset()
        }
    }

    private fun validatePaddleCollision() {
        data class CollisionCheck(
            val condition: () -> Boolean,
            val action: () -> Unit,
        )

        val paddleHeight = gameState.paddleHeight

        sequenceOf(
            CollisionCheck(
                condition = {
                    gameState.puckX <= 30 &&
                        gameState.puckY in gameState.paddle1Y..(gameState.paddle1Y + paddleHeight)
                },
                action = {
                    gameState.puckVX *= -1
                    gameState.puckVY += (Math.random() - 0.5) * 2
                },
            ),
            CollisionCheck(
                condition = {
                    gameState.puckX >= gameState.canvasWidth - 30 &&
                        gameState.puckY in gameState.paddle2Y..(gameState.paddle2Y + paddleHeight)
                },
                action = {
                    gameState.puckVX *= -1
                },
            ),
            CollisionCheck(
                condition = {
                    gameState.puckX in 20.0..30.0 &&
                        gameState.puckY + 20 >= gameState.paddle1Y &&
                        gameState.puckY <= gameState.paddle1Y + paddleHeight
                },
                action = {
                    gameState.puckVX = gameState.puckVX.absoluteValue
                    gameState.puckVY += (Math.random() - 0.5) * 2
                },
            ),
            CollisionCheck(
                condition = {
                    gameState.puckX in (gameState.canvasWidth - 30)..(gameState.canvasWidth - 20) &&
                        gameState.puckY + 20 >= gameState.paddle2Y &&
                        gameState.puckY <= gameState.paddle2Y + paddleHeight
                },
                action = {
                    gameState.puckVX = -gameState.puckVX.absoluteValue
                },
            ),
        ).filter { it.condition() }
            .forEach { it.action() }
    }

    private fun validateLineCollision() {
        gameState.lines.forEach { line ->
            val points = line.flattenedPoints ?: line.controlPoints
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]

                if (checkLineCircleCollision(
                        p1.x,
                        p1.y,
                        p2.x,
                        p2.y,
                        gameState.puckX + 10,
                        gameState.puckY + 10,
                        line.width,
                    )
                ) {
                    handleLineCollision(p1, p2)
                    return
                }
            }
        }
    }

    private fun handleLineCollision(
        p1: Point,
        p2: Point,
    ) {
        val lineVecX = p2.x - p1.x
        val lineVecY = p2.y - p1.y

        val lineLength = hypot(lineVecX, lineVecY)
        if (lineLength == 0.0) return

        val unitLineX = lineVecX / lineLength
        val unitLineY = lineVecY / lineLength

        val normalX = -unitLineY
        val normalY = unitLineX

        val dotProduct = gameState.puckVX * normalX + gameState.puckVY * normalY

        gameState.puckVX -= 2 * dotProduct * normalX
        gameState.puckVY -= 2 * dotProduct * normalY

        val speed = hypot(gameState.puckVX, gameState.puckVY)
        gameState.puckVX += (Math.random() - 0.5) * 0.5
        gameState.puckVY += (Math.random() - 0.5) * 0.5

        val newSpeed = hypot(gameState.puckVX, gameState.puckVY)
        gameState.puckVX = gameState.puckVX / newSpeed * speed
        gameState.puckVY = gameState.puckVY / newSpeed * speed

        gameState.puckX += normalX * 2
        gameState.puckY += normalY * 2
    }

    private fun checkLineCircleCollision(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        cx: Double,
        cy: Double,
        lineWidth: Double,
    ): Boolean {
        val lineVecX = x2 - x1
        val lineVecY = y2 - y1

        val circleVecX = cx - x1
        val circleVecY = cy - y1

        val lineLengthSquared = lineVecX * lineVecX + lineVecY * lineVecY
        val dotProduct = circleVecX * lineVecX + circleVecY * lineVecY

        val normalizedDistance = dotProduct / lineLengthSquared
        val closestX: Double
        val closestY: Double

        if (normalizedDistance < 0) {
            closestX = x1
            closestY = y1
        } else if (normalizedDistance > 1) {
            closestX = x2
            closestY = y2
        } else {
            closestX = x1 + normalizedDistance * lineVecX
            closestY = y1 + normalizedDistance * lineVecY
        }

        val distanceX = cx - closestX
        val distanceY = cy - closestY
        val distanceSquared = distanceX * distanceX + distanceY * distanceY

        val totalRadius = 10.0 + lineWidth / 2
        return distanceSquared < totalRadius * totalRadius
    }

    private fun updateAI() {
        val paddleCenter = gameState.paddle1Y + gameState.paddleHeight / 2

        if (gameState.puckVX < 0) {
            val predictedY = gameState.puckY + (gameState.puckVY * ((gameState.puckX - 30) / -gameState.puckVX))
            movePaddleToward(paddleCenter, predictedY, speed = 5.0)
        } else {
            movePaddleToward(paddleCenter, gameState.canvasHeight / 2, speed = 3.0)
        }

        gameState.paddle1Y = gameState.paddle1Y.coerceIn(0.0, gameState.canvasHeight - gameState.paddleHeight)
    }

    private fun movePaddleToward(
        current: Double,
        target: Double,
        speed: Double,
    ) {
        when {
            current < target - 10 -> gameState.paddle1Y += speed
            current > target + 10 -> gameState.paddle1Y -= speed
        }
    }

    private fun validateBlockCollision() {
        for (i in 0 until lifeGrid.rows) {
            for (j in 0 until lifeGrid.cols) {
                if (lifeGrid.grid[i][j]) {
                    val x = lifeGrid.gridX + j * lifeGrid.cellSize
                    val y = lifeGrid.gridY + i * lifeGrid.cellSize
                    val size = lifeGrid.cellSize

                    if (checkBlockCollision(x, y, size)) {
                        handleBlockCollision(i, j)
                        return
                    }
                }
            }
        }
    }

    private fun checkBlockCollision(
        blockX: Double,
        blockY: Double,
        blockSize: Double,
    ): Boolean {
        val puckCenterX = gameState.puckX + 10
        val puckCenterY = gameState.puckY + 10
        val puckRadius = 10.0

        val closestX = puckCenterX.coerceIn(blockX, blockX + blockSize)
        val closestY = puckCenterY.coerceIn(blockY, blockY + blockSize)

        val distanceX = puckCenterX - closestX
        val distanceY = puckCenterY - closestY

        return (distanceX * distanceX + distanceY * distanceY) < (puckRadius * puckRadius)
    }

    private fun handleBlockCollision(
        i: Int,
        j: Int,
    ) {
        val blockCenterX = j * lifeGrid.cellSize + lifeGrid.cellSize / 2
        val blockCenterY = i * lifeGrid.cellSize + lifeGrid.cellSize / 2
        val puckCenterX = gameState.puckX + 10
        val puckCenterY = gameState.puckY + 10

        val dx = puckCenterX - blockCenterX
        val dy = puckCenterY - blockCenterY
        val distance = sqrt(dx * dx + dy * dy)

        if (distance == 0.0) return

        val normalX = dx / distance
        val normalY = dy / distance

        val dot = gameState.puckVX * normalX + gameState.puckVY * normalY
        gameState.puckVX = gameState.puckVX - 2 * dot * normalX
        gameState.puckVY = gameState.puckVY - 2 * dot * normalY

        gameState.puckVX += (Math.random() - 0.5) * 0.5
        gameState.puckVY += (Math.random() - 0.5) * 0.5

        lifeGrid.grid[i][j] = false

        gameState.puckX += normalX * 2
        gameState.puckY += normalY * 2
    }
}
