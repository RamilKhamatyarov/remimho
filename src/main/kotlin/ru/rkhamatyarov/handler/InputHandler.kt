package ru.rkhamatyarov.handler

import io.quarkus.runtime.Quarkus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import org.jboss.logging.Logger
import ru.rkhamatyarov.model.GameState

@ApplicationScoped
class InputHandler {
    private val log = Logger.getLogger(InputHandler::class.java)

    @Inject
    lateinit var gameState: GameState

    val keysPressed = mutableSetOf<KeyCode>()
    var useMouseControl = true
    private var spaceWasPressed = false

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
            KeyCode.Q -> {
                if (event.isControlDown) {
                    exitGame()
                }
            }
            KeyCode.ESCAPE -> {
                exitGame()
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

    fun handleMouseMove(event: javafx.scene.input.MouseEvent) {
        if (useMouseControl) {
            gameState.paddle2Y = event.y - gameState.paddleHeight / 2
        }
    }

    fun update() {
        if (!useMouseControl) {
            if (KeyCode.UP in keysPressed) {
                gameState.paddle2Y -= 5.0
            }
            if (KeyCode.DOWN in keysPressed) {
                gameState.paddle2Y += 5.0
            }
        }

        gameState.paddle2Y = gameState.paddle2Y.coerceIn(0.0, gameState.canvasHeight - gameState.paddleHeight)
    }

    fun exitGame() {
        Thread {
            try {
                Thread.sleep(500)
                Quarkus.asyncExit()

                Thread.sleep(1000)
                System.exit(0)
            } catch (e: InterruptedException) {
                log.error("Error during shutdown", e)
                System.exit(1)
            }
        }.start()
    }
}
