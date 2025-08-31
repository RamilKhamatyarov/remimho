package ru.rkhamatyarov.handler

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import org.jboss.logging.Logger
import ru.rkhamatyarov.model.GameState

@ApplicationScoped
class InputHandler {
    private val log = Logger.getLogger(InputHandler::class.java)

    @Inject
    lateinit var gameState: GameState

    private val keysPressed = mutableSetOf<KeyCode>()
    private var spaceWasPressed = false
    private var useMouseControl = true
    private var mouseY = 0.0

    fun handleKeyPress(event: KeyEvent) {
        when (event.code) {
            KeyCode.SPACE -> {
                if (!spaceWasPressed) {
                    gameState.togglePause()
                    spaceWasPressed = true
                }
            }
            KeyCode.M -> {
                useMouseControl = !useMouseControl
                log.debug("Control mode changed to: ${if (useMouseControl) "Mouse" else "Keyboard"}")
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

    fun handleMouseMove(event: MouseEvent) {
        mouseY = event.y
    }

    fun update() {
        if (gameState.paused) {
            return
        }

        if (useMouseControl) {
            val targetY = mouseY - gameState.paddleHeight / 2
            gameState.paddle2Y = targetY.coerceIn(0.0, gameState.canvasHeight - gameState.paddleHeight)
        } else {
            keysPressed.forEach { key ->
                when (key) {
                    KeyCode.UP -> gameState.paddle2Y = (gameState.paddle2Y - 10).coerceAtLeast(0.0)
                    KeyCode.DOWN ->
                        gameState.paddle2Y =
                            (gameState.paddle2Y + 10).coerceAtMost(
                                gameState.canvasHeight - gameState.paddleHeight,
                            )
                    else -> {}
                }
            }
        }
    }
}
