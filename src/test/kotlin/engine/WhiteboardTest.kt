package engine

import javafx.scene.paint.Color
import javafx.stage.Stage
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.testfx.api.FxRobot
import org.testfx.framework.junit5.ApplicationExtension
import org.testfx.framework.junit5.Start
import org.testfx.util.WaitForAsyncUtils
import ru.rkhamatyarov.engine.GameLoop
import ru.rkhamatyarov.handler.InputHandler
import ru.rkhamatyarov.model.GameState
import ru.rkhamatyarov.service.WhiteboardService
import kotlin.test.assertEquals

@ExtendWith(ApplicationExtension::class)
class WhiteboardTest {
    private lateinit var stage: Stage
    private val robot = FxRobot()

    @Start
    fun start(stage: Stage) {
        this.stage = stage
    }

    @Test
    fun testStartGame() {
        // g
        val gameState = GameState()

        val inputHandler =
            InputHandler().apply {
                this.gameState = gameState
            }

        val gameLoop =
            GameLoop().apply {
                this.gameState = gameState
                this.inputHandler = inputHandler
            }

        val service =
            WhiteboardService().apply {
                this.inputHandler = inputHandler
                this.gameLoop = gameLoop
                this.gameState = gameState
            }

        robot.interact {
            service.startGame(stage)
        }

        WaitForAsyncUtils.waitForFxEvents()

        robot.interact {
            assertEquals("Whiteboard", stage.title)
            assertNotNull(stage.scene)
            assertEquals(800.0, stage.scene.width, 0.01)
            assertEquals(650.0, stage.scene.height, 0.01)
            assertEquals(Color.WHITE, stage.scene.fill)
        }
    }
}
