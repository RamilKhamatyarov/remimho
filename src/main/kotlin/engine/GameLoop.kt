package ru.rkhamatyarov.engine

import javafx.animation.AnimationTimer
import javafx.scene.canvas.GraphicsContext
import javafx.scene.paint.Color
import javafx.scene.text.Font
import org.springframework.stereotype.Component
import ru.rkhamatyarov.handler.InputHandler
import ru.rkhamatyarov.model.GameState

@Component
class GameLoop(
    private val gameState: GameState,
    private val inputHandler: InputHandler
) : AnimationTimer() {

    var gc: GraphicsContext? = null
    var player1Score = 0
    var player2Score = 0

    override fun handle(now: Long) {
        inputHandler.update()

        gc?.let {
            clearCanvas(it)
            renderScore(it)
            renderObjects(it)

            if (gameState.paused) {
                renderPauseOverlay(it)
                return
            }
        }

        if (!gameState.paused) {
            updatePuckPosition()
            validateCollisions()
            updateAI()
            applySpeedMultiplier()
        }
    }

    fun togglePause() {
        gameState.togglePause()
    }

    private fun renderPauseOverlay(gc: GraphicsContext) {
        gc.save()
        gc.fill = Color.rgb(0, 0, 0, 0.5)
        gc.fillRect(0.0, 0.0, 800.0, 600.0)
        gc.fill = Color.WHITE
        gc.font = Font.font(48.0)
        gc.fillText("PAUSED", 350.0, 300.0)
        gc.restore()
    }

    private fun clearCanvas(gc: GraphicsContext) {
        gc.clearRect(0.0, 0.0, 800.0, 600.0)
    }

    private fun renderScore(gc: GraphicsContext) {
        gc.save()
        gc.fill = Color.BLACK
        gc.stroke = Color.BLACK
        gc.lineWidth = 1.0
        gc.font = Font.font(20.0)
        gc.fillText("AI: $player1Score", 100.0, 30.0)
        gc.fillText("Player: $player2Score", 700.0, 30.0)
        gc.restore()
    }

    private fun renderObjects(gc: GraphicsContext) {
        gc.fill = Color.BLUEVIOLET
        gc.fillRect(20.0, gameState.paddle1Y, 10.0, 100.0)
        gc.fillRect(770.0, gameState.paddle2Y, 10.0, 100.0)
        gc.fillOval(gameState.puckX, gameState.puckY, 20.0, 20.0)

        gc.stroke = Color.DARKGRAY

        gameState.lines.forEach { line -> renderLine(gc, line) }
        gameState.currentLine?.let { line -> renderLine(gc, line) }
    }

    private fun renderLine(gc: GraphicsContext, line: GameState.Line) {
        if (line.points.size > 1) {
            gc.lineWidth = line.width
            gc.beginPath()
            gc.moveTo(line.points[0].x, line.points[0].y)
            for (i in 1 until line.points.size) {
                gc.lineTo(line.points[i].x, line.points[i].y)
            }
            gc.stroke()
        }
    }

    private fun updatePuckPosition() {
        gameState.puckX += gameState.puckVX
        gameState.puckY += gameState.puckVY
    }

    private fun validateCollisions() {
        validateWallCollision()
        validateScore()
        validatePaddleCollision()
        validateLineCollision()
    }

    private fun validateWallCollision() {
        if (gameState.puckY <= 0 || gameState.puckY >= 580) {
            gameState.puckVY *= -1
        }
    }

    private fun validateScore() {
        when {
            gameState.puckX < 0 -> {
                player2Score++
                gameState.reset()
            }

            gameState.puckX > 800 -> {
                player1Score++
                gameState.reset()
            }
        }
    }

    private fun validatePaddleCollision() {
        val withinPaddle1 = gameState.puckX <= 30 && gameState.puckY in gameState.paddle1Y..(gameState.paddle1Y + 100)
        val withinPaddle2 = gameState.puckX >= 750 && gameState.puckY in gameState.paddle2Y..(gameState.paddle2Y + 100)

        if (withinPaddle1) {
            gameState.puckVX *= -1
            gameState.puckVY += (Math.random() - 0.5) * 2
        }

        if (withinPaddle2) {
            gameState.puckVX *= -1
        }
    }

    private fun validateLineCollision() {
        gameState.lines.forEach { line ->
            for (i in 0 until line.points.size - 1) {
                val p1 = line.points[i]
                val p2 = line.points[i + 1]

                if (checkLineCircleCollision(
                        p1.x,
                        p1.y,
                        p2.x,
                        p2.y,
                        gameState.puckX + 10,
                        gameState.puckY + 10,
                        line.width
                    )
                ) {
                    handleLineCollision(p1, p2)
                    return
                }
            }
        }
    }

    private fun handleLineCollision(p1: GameState.Line.Point, p2: GameState.Line.Point) {
        val lineVecX = p2.x - p1.x
        val lineVecY = p2.y - p1.y

        val lineLength = Math.hypot(lineVecX, lineVecY)
        if (lineLength == 0.0) return

        val unitLineX = lineVecX / lineLength
        val unitLineY = lineVecY / lineLength

        val normalX = -unitLineY
        val normalY = unitLineX

        val dotProduct = gameState.puckVX * normalX + gameState.puckVY * normalY

        if (dotProduct < 0) {
            gameState.puckVX -= 2 * dotProduct * normalX
            gameState.puckVY -= 2 * dotProduct * normalY

            val speed = Math.hypot(gameState.puckVX, gameState.puckVY)
            gameState.puckVX += (Math.random() - 0.5) * 0.5
            gameState.puckVY += (Math.random() - 0.5) * 0.5

            val newSpeed = Math.hypot(gameState.puckVX, gameState.puckVY)
            gameState.puckVX = gameState.puckVX / newSpeed * speed
            gameState.puckVY = gameState.puckVY / newSpeed * speed

            gameState.puckX += normalX * 2
            gameState.puckY += normalY * 2
        }
    }


    private fun checkLineCircleCollision(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        cx: Double,
        cy: Double,
        lineWidth: Double
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

    private fun applySpeedMultiplier() {
        gameState.puckX += gameState.puckVX * (gameState.speedMultiplier - 1)
        gameState.puckY += gameState.puckVY * (gameState.speedMultiplier - 1)
    }

    private fun updateAI() {
        val paddleCenter = gameState.paddle1Y + 50

        if (gameState.puckVX < 0) {
            val predictedY = gameState.puckY + (gameState.puckVY * ((gameState.puckX - 30) / -gameState.puckVX))
            movePaddleToward(paddleCenter, predictedY, speed = 5.0)
        } else {
            movePaddleToward(paddleCenter, 300.0, speed = 3.0)
        }

        gameState.paddle1Y = gameState.paddle1Y.coerceIn(0.0, 500.0)
    }

    private fun movePaddleToward(current: Double, target: Double, speed: Double) {
        when {
            current < target - 10 -> gameState.paddle1Y += speed
            current > target + 10 -> gameState.paddle1Y -= speed
        }
    }
}
