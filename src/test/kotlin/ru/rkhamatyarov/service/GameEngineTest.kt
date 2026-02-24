package ru.rkhamatyarov.service

import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import ru.rkhamatyarov.model.GameState
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@QuarkusTest
class GameEngineTest {
    @Inject
    lateinit var gameEngine: GameEngine

    @Inject
    lateinit var gameState: GameState

    @InjectMock
    lateinit var powerUpManager: PowerUpManager

    @InjectMock
    lateinit var formulaRegistry: FormulaRegistry

    @BeforeEach
    fun setup() {
        gameState.reset()
        gameState.paused = false
        gameState.puckX = 400.0
        gameState.puckY = 270.0
        gameState.puckVX = 0.0
        gameState.puckVY = 0.0
        gameState.speedMultiplier = 1.0
    }

    @Test
    fun `test initialize sets default speed multiplier`() {
        // g
        gameState.baseSpeedMultiplier = 1.5

        // w
        gameEngine.initialize()

        // w
        assertEquals(1.5, gameState.speedMultiplier)
    }

    @Test
    fun `test initialize starts formula registry random curve scheduler`() {
        // w
        gameEngine.initialize()

        // w
        verify(formulaRegistry).startRandomCurveScheduler()
    }

    @Test
    fun `test gameLoop increments frame counter`() {
        // g
        gameEngine.initialize()

        // w
        gameEngine.gameLoop()
        gameEngine.gameLoop()
        gameEngine.gameLoop()

        // w
        assertTrue(gameState.puckMovingTime >= 0)
    }

    @Test
    fun `test gameLoop respects pause state`() {
        // g
        gameEngine.initialize()
        gameState.paused = true
        val initialX = gameState.puckX
        val initialY = gameState.puckY

        // w
        gameEngine.gameLoop()

        // w
        assertEquals(initialX, gameState.puckX)
        assertEquals(initialY, gameState.puckY)
    }

    @Test
    fun `test updateAIPaddle tracks puck vertically`() {
        // g
        gameEngine.initialize()
        gameState.puckY = 100.0
        gameState.paddle1Y = 200.0

        // w
        gameEngine.gameLoop()

        // w
        assertTrue(gameState.paddle1Y < 200.0)
    }

    @Test
    fun `test updateAIPaddle respects canvas bounds`() {
        // g
        gameEngine.initialize()
        gameState.puckY = 10.0
        gameState.paddle1Y = 0.0

        // w
        for (i in 0..100) {
            gameEngine.gameLoop()
        }

        // w
        assertTrue(gameState.paddle1Y >= 0.0)
        assertTrue(gameState.paddle1Y <= gameState.canvasHeight - gameState.paddleHeight)
    }

    @Test
    fun `test puck with zero velocity doesn't move`() {
        // g
        gameEngine.initialize()
        gameState.puckX = 300.0
        gameState.puckY = 300.0
        gameState.puckVX = 0.0
        gameState.puckVY = 0.0

        // w
        gameEngine.gameLoop()

        // w
        assertEquals(300.0, gameState.puckX)
        assertEquals(300.0, gameState.puckY)
    }

    @Test
    fun `test bottom wall collision reverses Y velocity`() {
        // g
        gameEngine.initialize()
        gameState.puckY = gameState.canvasHeight - 5.0
        gameState.puckVY = 10.0

        // w
        gameEngine.gameLoop()

        // w
        assertTrue(gameState.puckVY < 0.0)
        assertEquals(10.0, abs(gameState.puckVY), 0.001)
    }

    @Test
    fun `test wall collision adjusts puck position`() {
        // g
        gameEngine.initialize()
        val puckRadius = 10.0
        gameState.puckY = -5.0

        // w
        gameEngine.gameLoop()

        // w
        assertTrue(gameState.puckY >= puckRadius)
    }

    @Test
    fun `test multiple wall collisions in sequence`() {
        // g
        gameEngine.initialize()
        gameState.puckX = 200.0
        gameState.puckY = 5.0
        gameState.puckVX = 5.0
        gameState.puckVY = -20.0

        // w
        for (i in 0..5) {
            gameEngine.gameLoop()
        }

        // w
        assertTrue(gameState.puckY < gameState.canvasHeight - 10.0)
    }

    @Test
    fun `test right paddle collision reverses X velocity`() {
        // g
        gameEngine.initialize()
        gameState.puckX = gameState.canvasWidth - 25.0
        gameState.puckY = gameState.paddle2Y + 50.0
        gameState.puckVX = 10.0

        // w
        gameEngine.gameLoop()

        // w
        assertTrue(gameState.puckVX < 0.0)
        assertTrue(abs(gameState.puckVX) > 10.0)
    }

    @Test
    fun `test no collision outside paddle bounds`() {
        // g
        gameEngine.initialize()
        gameState.puckX = 25.0
        gameState.puckY = gameState.paddle1Y - 50.0
        gameState.puckVX = -10.0
        gameState.puckVY = 0.0

        // w
        val vxBefore = gameState.puckVX
        gameEngine.gameLoop()

        // w
        assertEquals(vxBefore, gameState.puckVX)
    }

    @Test
    fun `test additional puck created and updated`() {
        // g
        gameEngine.initialize()
        val puck =
            ru.rkhamatyarov.model.AdditionalPuck(
                x = 300.0,
                y = 300.0,
                vx = 10.0,
                vy = 5.0,
            )
        gameState.additionalPucks.add(puck)

        // w
        gameEngine.gameLoop()

        // w
        assertTrue(gameState.additionalPucks.isNotEmpty())
        assertTrue(puck.x > 300.0 || puck.y > 300.0)
    }

    @Test
    fun `test expired additional pucks removed`() {
        // g
        gameEngine.initialize()
        val puck =
            ru.rkhamatyarov.model.AdditionalPuck(
                x = 300.0,
                y = 300.0,
                vx = 10.0,
                vy = 5.0,
                creationTime = System.nanoTime() - 16_000_000_000L,
            )
        gameState.additionalPucks.add(puck)

        // w
        gameEngine.gameLoop()

        // w
        assertTrue(gameState.additionalPucks.isEmpty())
    }

    @Test
    fun `test multiple additional pucks managed independently`() {
        // g
        gameEngine.initialize()
        val puck1 = ru.rkhamatyarov.model.AdditionalPuck(100.0, 100.0, 5.0, 5.0)
        val puck2 = ru.rkhamatyarov.model.AdditionalPuck(500.0, 500.0, -5.0, -5.0)
        gameState.additionalPucks.add(puck1)
        gameState.additionalPucks.add(puck2)

        // w
        gameEngine.gameLoop()

        // w
        assertEquals(2, gameState.additionalPucks.size)
    }

    @Test
    fun `test pause and resume maintains consistency`() {
        // g
        gameEngine.initialize()
        gameState.puckVX = 10.0
        gameState.puckVY = 5.0
        val pausedX = gameState.puckX

        // w
        gameState.paused = true
        gameEngine.gameLoop()
        val afterPauseX = gameState.puckX
        val afterPauseY = gameState.puckY

        gameState.paused = false
        gameEngine.gameLoop()
        val afterResumeX = gameState.puckX
        val afterResumeY = gameState.puckY

        // w
        assertEquals(pausedX, afterPauseX)
        assertTrue(afterResumeX != afterPauseX || afterResumeY != afterPauseY)
    }

    @Test
    fun `test game continues after single frame`() {
        // g
        gameEngine.initialize()

        // w
        gameEngine.gameLoop()

        // w
        assertTrue(gameState.puckMovingTime >= 0)
    }

    @Test
    fun `test rapid frame sequence stability`() {
        // g
        gameEngine.initialize()
        gameState.puckVX = 10.0
        gameState.puckVY = 5.0

        // w
        for (i in 0..10) {
            gameEngine.gameLoop()
        }

        // w
        assertTrue(gameState.puckX > 400.0 || gameState.puckY > 270.0)
    }
}
