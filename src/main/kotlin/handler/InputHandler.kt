package ru.rkhamatyarov.handler

import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import org.springframework.stereotype.Component
import ru.rkhamatyarov.model.GameState

@Component
class InputHandler(private val gameState: GameState) {
    fun handleKeyPress(event: KeyEvent) {

        when (event.code) {
            KeyCode.UP -> gameState.paddle2Y -= 20
            KeyCode.DOWN -> gameState.paddle2Y += 20
            else -> {}
        }
        gameState.paddle2Y = gameState.paddle2Y.coerceIn(0.0, 500.0)
    }
}