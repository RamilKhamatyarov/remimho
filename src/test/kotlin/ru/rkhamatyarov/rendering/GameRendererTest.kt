package ru.rkhamatyarov.rendering

import org.junit.jupiter.api.Test
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviLine
import ru.rkhamatyarov.service.mvi.MviPoint
import ru.rkhamatyarov.service.mvi.MviPuck
import ru.rkhamatyarov.service.mvi.MviScore
import java.awt.Color
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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

    @Test
    fun `score is drawn as seven-segment digits`() {
        val background = Color(0x1A, 0x1A, 0x2E).rgb
        val eight = renderer.render(MviGameState(score = MviScore(playerA = 8)))
        val zero = renderer.render(MviGameState(score = MviScore(playerA = 0)))

        assertNotEquals(background, eight.getRGB(200, 33), "8 lights the middle segment")
        assertEquals(background, zero.getRGB(200, 33), "0 leaves the middle segment dark")
    }

    @Test
    fun `rendering the same state is byte-identical`() {
        val state = MviGameState(score = MviScore(playerA = 3, playerB = 12))

        val first = renderer.render(state)
        val second = renderer.render(state)

        val pixels = { image: java.awt.image.BufferedImage ->
            image.getRGB(
                0,
                0,
                image.width,
                image.height,
                null,
                0,
                image.width,
            )
        }
        assertTrue(pixels(first).contentEquals(pixels(second)))
    }
}
