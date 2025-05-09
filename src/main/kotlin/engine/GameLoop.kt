package ru.rkhamatyarov.engine

import javafx.animation.AnimationTimer
import javafx.scene.canvas.GraphicsContext
import javafx.scene.paint.Color
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
        }

        updatePuckPosition()
        validateCollisions()
        updateAI()
        applySpeedMultiplier()
    }

    private fun clearCanvas(gc: GraphicsContext) {
        gc.clearRect(0.0, 0.0, 800.0, 600.0)
    }

    private fun renderScore(gc: GraphicsContext) {
        gc.fill = Color.BLACK
        gc.fillText("AI: $player1Score", 100.0, 30.0)
        gc.fillText("Player: $player2Score", 700.0, 30.0)
    }

    private fun renderObjects(gc: GraphicsContext) {
        gc.fill = Color.BLUEVIOLET
        gc.fillRect(20.0, gameState.paddle1Y, 10.0, 100.0)
        gc.fillRect(770.0, gameState.paddle2Y, 10.0, 100.0)
        gc.fillOval(gameState.puckX, gameState.puckY, 20.0, 20.0)

        gc.fill = Color.DARKGRAY
        gc.fillRect(gameState.blockX, gameState.blockY, gameState.blockWidth, gameState.blockHeight)
    }

    private fun updatePuckPosition() {
        gameState.puckX += gameState.puckVX
        gameState.puckY += gameState.puckVY
    }

    private fun validateCollisions() {
        validateWallCollision()
        validateScore()
        validatePaddleCollision()
        validateBlockCollision()
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
        val withinPaddle1 = gameState.puckX <= 30 &&
                gameState.puckY in gameState.paddle1Y..(gameState.paddle1Y + 100)

        val withinPaddle2 = gameState.puckX >= 750 &&
                gameState.puckY in gameState.paddle2Y..(gameState.paddle2Y + 100)

        if (withinPaddle1) {
            gameState.puckVX *= -1
            gameState.puckVY += (Math.random() - 0.5) * 2
        }

        if (withinPaddle2) {
            gameState.puckVX *= -1
        }
    }

    private fun validateBlockCollision() {
        if (gameState.puckX + 20 >= gameState.blockX &&
            gameState.puckX <= gameState.blockX + gameState.blockWidth &&
            gameState.puckY + 20 >= gameState.blockY &&
            gameState.puckY <= gameState.blockY + gameState.blockHeight) {
            gameState.puckVX *= -1
        }
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
