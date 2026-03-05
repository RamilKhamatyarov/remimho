package ru.rkhamatyarov.service

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.rkhamatyarov.model.AdditionalPuck
import ru.rkhamatyarov.model.GameInnerState
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@QuarkusTest
class GameEngineTest {
    @Inject
    lateinit var gameEngine: GameEngine

    @Inject
    lateinit var gameState: GameInnerState

    companion object {
        private const val TICK_DT = 0.016
    }

    @BeforeEach
    fun setup() {
        gameState.reset()
        gameState.paused = false

        gameEngine.paused = false
        gameEngine.resetPuck()
        gameEngine.puck.vx = 0.0
        gameEngine.puck.vy = 0.0
        gameEngine.paddle1Y = (gameEngine.canvasHeight - gameEngine.paddleHeight) / 2
        gameEngine.paddle2Y = (gameEngine.canvasHeight - gameEngine.paddleHeight) / 2
    }

    @Test
    fun `test tick respects pause state`() {
        gameEngine.puck.vx = 100.0
        gameEngine.puck.vy = 50.0
        val initialX = gameEngine.puck.x
        val initialY = gameEngine.puck.y

        gameEngine.paused = true
        gameEngine.tick(TICK_DT)

        assertEquals(initialX, gameEngine.puck.x, 0.0001)
        assertEquals(initialY, gameEngine.puck.y, 0.0001)
    }

    @Test
    fun `test pause and resume maintains consistency`() {
        gameEngine.puck.vx = 100.0
        gameEngine.puck.vy = 50.0
        val initialX = gameEngine.puck.x

        gameEngine.paused = true
        gameEngine.tick(TICK_DT)
        assertEquals(initialX, gameEngine.puck.x, 0.0001, "Puck must not move while paused")

        gameEngine.paused = false
        gameEngine.tick(TICK_DT)
        assertNotEquals(initialX, gameEngine.puck.x, "Puck should have moved after resume")
    }

    @Test
    fun `test puck with zero velocity stays put`() {
        val startX = gameEngine.puck.x
        val startY = gameEngine.puck.y

        gameEngine.tick(TICK_DT)

        assertEquals(startX, gameEngine.puck.x, 0.0001)
        assertEquals(startY, gameEngine.puck.y, 0.0001)
    }

    @Test
    fun `test bottom wall collision reverses Y velocity`() {
        gameEngine.puck.y = gameEngine.canvasHeight - 5.0
        gameEngine.puck.vy = 10.0

        gameEngine.tick(TICK_DT)

        assertTrue(gameEngine.puck.vy < 0.0, "vy must be negative after bottom-wall bounce")
        assertNotEquals(0.0, gameEngine.puck.vy, "vy must not be zero after bounce")
        assertEquals(10.0, abs(gameEngine.puck.vy), 0.5, "Speed magnitude approximately preserved")
    }

    @Test
    fun `test top wall collision clamps puck and reflects velocity`() {
        val radius = gameEngine.puck.radius
        gameEngine.puck.y = -5.0
        gameEngine.puck.vy = -20.0

        gameEngine.tick(TICK_DT)

        assertTrue(gameEngine.puck.y >= radius, "Puck must be clamped at top wall")
        assertTrue(gameEngine.puck.vy > 0.0, "vy must be reflected positive after top-wall bounce")
    }

    @Test
    fun `test multiple wall bounces keep puck in bounds`() {
        gameEngine.puck.x = 200.0
        gameEngine.puck.y = 5.0
        gameEngine.puck.vx = 5.0
        gameEngine.puck.vy = -20.0

        repeat(6) { gameEngine.tick(TICK_DT) }

        assertTrue(gameEngine.puck.y >= 10.0)
        assertTrue(gameEngine.puck.y <= gameEngine.canvasHeight - 10.0)
    }

    @Test
    fun `test right paddle collision reverses X velocity`() {
        gameEngine.puck.x = gameEngine.canvasWidth - 25.0
        gameEngine.puck.y = gameEngine.paddle2Y + gameEngine.paddleHeight / 2
        gameEngine.puck.vx = 10.0

        gameEngine.tick(TICK_DT)

        assertTrue(gameEngine.puck.vx < 0.0, "puckVX must reverse after right-paddle hit")
    }

    @Test
    fun `test no collision when puck misses paddle vertically`() {
        gameEngine.puck.x = 25.0
        gameEngine.puck.y = gameEngine.paddle1Y - 50.0
        gameEngine.puck.vx = -10.0
        gameEngine.puck.vy = 0.0
        val vxBefore = gameEngine.puck.vx

        gameEngine.tick(TICK_DT)

        assertEquals(vxBefore, gameEngine.puck.vx, 0.0001)
    }

    @Test
    fun `test AI paddle tracks puck upward`() {
        gameEngine.puck.y = 100.0
        gameEngine.paddle1Y = 400.0

        gameEngine.tick(TICK_DT)

        assertTrue(gameEngine.paddle1Y < 400.0, "AI paddle should move up toward puck")
    }

    @Test
    fun `test AI paddle stays within canvas bounds after many ticks`() {
        gameEngine.puck.y = 10.0
        gameEngine.paddle1Y = 0.0

        repeat(200) { gameEngine.tick(TICK_DT) }

        assertTrue(gameEngine.paddle1Y >= 0.0)
        assertTrue(gameEngine.paddle1Y <= gameEngine.canvasHeight - gameEngine.paddleHeight)
    }

    @Test
    fun `test rapid frame sequence keeps puck inside canvas`() {
        gameEngine.puck.vx = 300.0
        gameEngine.puck.vy = 200.0

        repeat(60) { gameEngine.tick(TICK_DT) }

        assertTrue(gameEngine.puck.x > 0.0)
        assertTrue(gameEngine.puck.x < gameEngine.canvasWidth)
        assertTrue(gameEngine.puck.y > 0.0)
        assertTrue(gameEngine.puck.y < gameEngine.canvasHeight)
    }

    @Test
    fun `test additional puck moves when velocity is non-zero`() {
        val puck = AdditionalPuck(x = 300.0, y = 300.0, vx = 10.0, vy = 5.0)
        gameState.additionalPucks.add(puck)

        gameState.updateAdditionalPucks()

        assertTrue(puck.x > 300.0 || puck.y > 300.0, "Additional puck should have moved")
    }

    @Test
    fun `test expired additional pucks are removed`() {
        val expired =
            AdditionalPuck(
                x = 300.0,
                y = 300.0,
                vx = 10.0,
                vy = 5.0,
                creationTime = System.nanoTime() - 16_000_000_000L,
            )
        gameState.additionalPucks.add(expired)

        gameState.updateAdditionalPucks()

        assertTrue(gameState.additionalPucks.isEmpty(), "Expired puck should be removed")
    }

    @Test
    fun `test multiple additional pucks managed independently`() {
        val p1 = AdditionalPuck(100.0, 100.0, 5.0, 5.0)
        val p2 = AdditionalPuck(500.0, 500.0, -5.0, -5.0)
        gameState.additionalPucks.add(p1)
        gameState.additionalPucks.add(p2)

        gameState.updateAdditionalPucks()

        assertEquals(2, gameState.additionalPucks.size)
    }
}
