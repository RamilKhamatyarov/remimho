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

        gc?.clearRect(0.0, 0.0, 800.0, 600.0)

        gc?.fill = Color.BLACK
        gc?.fillText("Player: $player1Score", 100.0, 30.0)
        gc?.fillText("AI: $player2Score", 700.0, 30.0)

        gc?.fill = Color.BLUEVIOLET
        gc?.fillRect(20.0, gameState.paddle1Y, 10.0, 100.0)
        gc?.fillRect(770.0, gameState.paddle2Y, 10.0, 100.0)
        gc?.fillOval(gameState.puckX, gameState.puckY, 20.0, 20.0)

        gameState.puckX += gameState.puckVX
        gameState.puckY += gameState.puckVY

        updateAI()

        if (gameState.puckY <= 0 || gameState.puckY >= 580) {
            gameState.puckVY *= -1
        }
        if (gameState.puckX < 0) {
            player2Score++
            gameState.reset()
        }
        if (gameState.puckX > 800) {
            player1Score++
            gameState.reset()
        }
        if (gameState.puckX <= 30 &&
            gameState.puckY >= gameState.paddle1Y &&
            gameState.puckY <= gameState.paddle1Y + 100) {
            gameState.puckVX *= -1

            gameState.puckVY += (Math.random() - 0.5) * 2
        }

        if (gameState.puckX >= 750 &&
            gameState.puckY >= gameState.paddle2Y &&
            gameState.puckY <= gameState.paddle2Y + 100) {
            gameState.puckVX *= -1
        }
    }

    private fun updateAI() {
        val paddleCenter = gameState.paddle1Y + 50

        if (gameState.puckVX < 0) {

            val predictedY = gameState.puckY + (gameState.puckVY * ((gameState.puckX - 30) / -gameState.puckVX))

            if (paddleCenter < predictedY - 10) {
                gameState.paddle1Y += 5.0
            } else if (paddleCenter > predictedY + 10) {
                gameState.paddle1Y -= 5.0
            }
        } else {

            if (paddleCenter < 300 - 10) {
                gameState.paddle1Y += 3.0
            } else if (paddleCenter > 300 + 10) {
                gameState.paddle1Y -= 3.0
            }
        }
        gameState.paddle1Y = gameState.paddle1Y.coerceIn(0.0, 500.0)
    }
}
