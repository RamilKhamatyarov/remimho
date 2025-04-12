package ru.rkhamatyarov.handler

import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import org.springframework.stereotype.Component
import ru.rkhamatyarov.model.GameState

@Component
class InputHandler(private val gameState: GameState) {
    private val keysPressed = mutableSetOf<KeyCode>()

    fun handleKeyPress(event: KeyEvent) {
        keysPressed.add(event.code)
    }

    fun handleKeyRelease(event: KeyEvent) {
        keysPressed.remove(event.code)
    }

    fun update() {
        keysPressed.forEach { key ->
            when (key) {
                KeyCode.UP -> gameState.paddle2Y -= 10
                KeyCode.DOWN -> gameState.paddle2Y += 10
                else -> {}
            }
        }
        gameState.paddle2Y = gameState.paddle2Y.coerceIn(0.0, 500.0)
    }
}