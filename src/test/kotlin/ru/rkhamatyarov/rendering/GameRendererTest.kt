package ru.rkhamatyarov.rendering

import org.junit.jupiter.api.Test
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviLine
import ru.rkhamatyarov.service.mvi.MviPoint
import ru.rkhamatyarov.service.mvi.MviPuck
import java.awt.Color
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class GameRendererTest {
    private val renderer = GameRenderer()

    @Test
    fun `render returns image with state canvas dimensions`() {
        val image = renderer.render(MviGameState(canvasWidth = 320.0, canvasHeight = 180.0))

        assertEquals(320, image.width)
        assertEquals(180, image.height)
    }

    @Test
    fun `render maps puck and lines into image coordinates`() {
        val state =
            MviGameState(
                canvasWidth = 100.0,
                canvasHeight = 80.0,
                puck = MviPuck(x = 20.0, y = 30.0, radius = 5.0),
                lines = listOf(MviLine("line", listOf(MviPoint(40.0, 10.0), MviPoint(40.0, 70.0)), width = 4.0)),
            )

        val image = renderer.render(state)
        val background = Color(0x1A, 0x1A, 0x2E).rgb

        assertNotEquals(background, image.getRGB(20, 30), "puck center should be painted at matching coordinates")
        assertNotEquals(background, image.getRGB(40, 40), "line should be painted at matching coordinates")
    }
}
