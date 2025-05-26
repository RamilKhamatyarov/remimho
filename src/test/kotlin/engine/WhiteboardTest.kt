package engine

import javafx.scene.paint.Color
import javafx.stage.Stage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.testfx.api.FxRobot
import org.testfx.assertions.api.Assertions
import org.testfx.framework.junit5.ApplicationExtension
import org.testfx.framework.junit5.Start
import ru.rkhamatyarov.engine.Whiteboard
import ru.rkhamatyarov.service.WhiteboardService

@ExtendWith(ApplicationExtension::class)
class WhiteboardTest {

    private lateinit var stage: Stage
    private lateinit var whiteboardService: WhiteboardService

    @SuppressWarnings("unused")
    @Start
    fun start(stage: Stage) {
        this.stage = stage
        val app = Whiteboard()
        app.init()
        app.start(stage)
        whiteboardService = app.getWhiteboardService()
    }

    @Test
    fun testGameInitialization(robot: FxRobot) {
        Assertions.assertThat(stage.title).isEqualTo("Whiteboard")

        val scene = stage.scene
        Assertions.assertThat(scene).isNotNull()
        Assertions.assertThat(scene.width).isEqualTo(800.0)
        Assertions.assertThat(scene.height).isEqualTo(650.0)
        Assertions.assertThat(scene.fill).isEqualTo(Color.WHITE)
    }
}