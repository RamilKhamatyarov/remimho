package ru.rkhamatyarov.handler

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import ru.rkhamatyarov.model.GameState

@ApplicationScoped
class InputHandler {
    @Inject
    lateinit var gameState: GameState

    private val keysPressed = mutableSetOf<KeyCode>()
    private var spaceWasPressed = false

    fun handleKeyPress(event: KeyEvent) {
        when (event.code) {
            KeyCode.SPACE -> {
                if (!spaceWasPressed) {
                    gameState.togglePause()
                    spaceWasPressed = true
                }
            }
            else -> keysPressed.add(event.code)
        }
    }

    fun handleKeyRelease(event: KeyEvent) {
        when (event.code) {
            KeyCode.SPACE -> spaceWasPressed = false
            else -> keysPressed.remove(event.code)
        }
    }

    fun update() {
        if (gameState.paused) {
            return
        }

        keysPressed.forEach { key ->
            when (key) {
                KeyCode.UP -> gameState.paddle2Y = (gameState.paddle2Y - 10).coerceAtLeast(0.0)
                KeyCode.DOWN ->
                    gameState.paddle2Y =
                        (gameState.paddle2Y + 10).coerceAtMost(
                            gameState.canvasHeight - gameState.paddleHeight,
                        )
                KeyCode.SPACE -> gameState.togglePause()
                else -> {}
            }
        }
    }
}
