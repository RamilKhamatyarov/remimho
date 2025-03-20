package ru.rkhamatyarov.engine

import javafx.animation.AnimationTimer
import javafx.scene.canvas.GraphicsContext
import javafx.scene.paint.Color
import org.springframework.stereotype.Component
import ru.rkhamatyarov.model.GameState

@Component
class GameLoop(private val gameState: GameState) : AnimationTimer() {
    var gc: GraphicsContext? = null

    override fun handle(now: Long) {
        gc?.clearRect(0.0, 0.0, 800.0, 600.0)
        gc?.fill = Color.BLUEVIOLET
        gc?.fillRect(20.0, gameState.paddle1Y, 10.0, 100.0)
        gc?.fillRect(770.0, gameState.paddle2Y, 10.0, 100.0)
        gc?.fillOval(gameState.puckX, gameState.puckY, 20.0, 20.0)

        gameState.puckX += gameState.puckVX
        gameState.puckY += gameState.puckVY

        if (gameState.puckY <= 0 || gameState.puckY >= 580) gameState.puckVY *= -1
        if ((gameState.puckX <= 30 && gameState.puckY in gameState.paddle1Y..(gameState.paddle1Y + 100)) ||
            (gameState.puckX >= 750 && gameState.puckY in gameState.paddle2Y..(gameState.paddle2Y + 100))) {
            gameState.puckVX *= -1
        }
    }
}
