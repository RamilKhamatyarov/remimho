package ru.rkhamatyarov.handler

import io.quarkus.runtime.Quarkus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import javafx.application.Platform
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import org.jboss.logging.Logger
import ru.rkhamatyarov.model.GameState
import ru.rkhamatyarov.model.PowerUp
import ru.rkhamatyarov.model.PowerUpType
import kotlin.system.exitProcess

@ApplicationScoped
class InputHandler {
    private val log = Logger.getLogger(javaClass.name)

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
                log.debug("Control mode changed to: ${if (useMouseControl) "Mouse" else "Keyboard"};")
            }
            KeyCode.Q -> {
                if (event.isControlDown) {
                    exitGame()
                } else {
                    keysPressed.add(event.code)
                }
            }
            KeyCode.ESCAPE -> {
                exitGame()
            }
            KeyCode.DIGIT1 -> {
                if (event.isControlDown) {
                    spawnTestPowerUp(PowerUpType.SPEED_BOOST)
                }
            }
            KeyCode.DIGIT2 -> {
                if (event.isControlDown) {
                    spawnTestPowerUp(PowerUpType.MAGNET_BALL)
                }
            }
            KeyCode.DIGIT3 -> {
                if (event.isControlDown) {
                    spawnTestPowerUp(PowerUpType.GHOST_MODE)
                }
            }
            KeyCode.DIGIT4 -> {
                if (event.isControlDown) {
                    spawnTestPowerUp(PowerUpType.MULTI_BALL)
                }
            }
            KeyCode.DIGIT5 -> {
                if (event.isControlDown) {
                    spawnTestPowerUp(PowerUpType.PADDLE_SHIELD)
                }
            }
            else -> keysPressed.add(event.code)
        }
    }

    private fun spawnTestPowerUp(type: PowerUpType) {
        val powerUp =
            PowerUp(
                x = gameState.canvasWidth / 2,
                y = gameState.canvasHeight / 2,
                type = type,
            )
        gameState.powerUps.add(powerUp)
        log.debug("Test power-up spawned: $type;")
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
        log.debug("Shutting down application...;")

        Platform.exit()

        Thread {
            try {
                Thread.sleep(200)
                Quarkus.asyncExit()
                Thread.sleep(500)
                exitProcess(0)
            } catch (e: InterruptedException) {
                log.error("Error during shutdown;", e)
                exitProcess(1)
            }
        }.start()
    }
}
