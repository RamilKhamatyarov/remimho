package ru.rkhamatyarov.engine

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.smallrye.common.constraint.Assert.assertFalse
import io.smallrye.common.constraint.Assert.assertTrue
import jakarta.enterprise.context.ApplicationScoped
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import ru.rkhamatyarov.handler.InputHandler
import ru.rkhamatyarov.model.GameOfLifeGrid
import ru.rkhamatyarov.model.GameState
import ru.rkhamatyarov.model.Line
import ru.rkhamatyarov.model.Point
import kotlin.test.Test

@ApplicationScoped
class GameLoopTest {
    private lateinit var gameLoop: GameLoop
    private lateinit var gameState: GameState
    private lateinit var inputHandler: InputHandler
    private lateinit var lifeGrid: GameOfLifeGrid

    @BeforeEach
    fun setUp() {
        gameState = mockk(relaxed = true)
        inputHandler = mockk(relaxed = true)
        lifeGrid = mockk(relaxed = true)

        gameLoop = GameLoop()
        gameLoop.gameState = gameState
        gameLoop.inputHandler = inputHandler
        gameLoop.lifeGrid = lifeGrid
    }

    @Test
    fun `puck bounces off top wall via reflection`() {
        // g
        every { gameState.puckY } returns 0.0
        every { gameState.puckVY } returns 5.0

        // w
        val method = GameLoop::class.java.getDeclaredMethod("validateWallCollision")
        method.isAccessible = true
        method.invoke(gameLoop)

        // t
        verify { gameState.puckVY = -5.0 }
    }

    @Test
    fun `puck bounces off top wall`() {
        // g
        every { gameState.canvasHeight } returns 400.0
        every { gameState.puckY } returns 390.0
        every { gameState.puckVY } returns -3.0

        // w
        val method = GameLoop::class.java.getDeclaredMethod("validateWallCollision")
        method.isAccessible = true
        method.invoke(gameLoop)

        // t
        verify { gameState.puckVY = 3.0 }
    }

    @Test
    fun `score increments when puck passes left edge`() {
        // g
        every { gameState.puckX } returns -5.0
        every { gameState.canvasWidth } returns 800.0

        // w
        val method = GameLoop::class.java.getDeclaredMethod("validateScore")
        method.isAccessible = true
        method.invoke(gameLoop)

        // t
        assertEquals(1, gameLoop.player2Score)
        verify { gameState.reset() }
    }

    @Test
    fun `score increments when puck passes right edge`() {
        // g
        every { gameState.puckX } returns 805.0
        every { gameState.canvasWidth } returns 800.0
        // w
        val method = GameLoop::class.java.getDeclaredMethod("validateScore")
        method.isAccessible = true
        method.invoke(gameLoop)
        // t
        assertEquals(1, gameLoop.player1Score)
        verify { gameState.reset() }
    }

    @Test
    fun `puck reflects off left paddle`() {
        // g
        every { gameState.puckX } returns 25.0
        every { gameState.puckY } returns 150.0
        every { gameState.paddle1Y } returns 100.0
        every { gameState.paddleHeight } returns 100.0
        every { gameState.puckVX } returns -4.0
        every { gameState.puckVY } returns 0.0
        // w
        val method = GameLoop::class.java.getDeclaredMethod("validatePaddleCollision")
        method.isAccessible = true
        method.invoke(gameLoop)
        // t
        verify { gameState.puckVX = 4.0 }
        verify { gameState.puckVY = any() }
    }

    @Test
    fun `puck reflects off right paddle`() {
        // g
        every { gameState.canvasWidth } returns 800.0
        every { gameState.puckX } returns 775.0
        every { gameState.puckY } returns 300.0
        every { gameState.paddle2Y } returns 250.0
        every { gameState.paddleHeight } returns 100.0
        every { gameState.puckVX } returns 5.0
        // w
        val method = GameLoop::class.java.getDeclaredMethod("validatePaddleCollision")
        method.isAccessible = true
        method.invoke(gameLoop)
        // t
        verify { gameState.puckVX = -5.0 }
    }

    @Test
    fun `detects collision between puck and line segment via reflection`() {
        // g
        val line =
            Line(
                controlPoints = mutableListOf(Point(100.0, 200.0), Point(200.0, 200.0)),
                width = 5.0,
                isAnimating = false,
            )
        every { gameState.lines } returns mutableListOf(line)
        every { gameState.puckX } returns 150.0
        every { gameState.puckY } returns 190.0
        every { gameState.puckVX } returns 2.0
        every { gameState.puckVY } returns 0.0

        val checkMethod =
            GameLoop::class.java.getDeclaredMethod(
                "checkLineCircleCollision",
                Double::class.java,
                Double::class.java,
                Double::class.java,
                Double::class.java,
                Double::class.java,
                Double::class.java,
                Double::class.java,
            )
        checkMethod.isAccessible = true

        val handleMethod =
            GameLoop::class.java.getDeclaredMethod(
                "handleLineCollision",
                Point::class.java,
                Point::class.java,
            )
        handleMethod.isAccessible = true
        // w
        val method = GameLoop::class.java.getDeclaredMethod("validateLineCollision")
        method.isAccessible = true
        method.invoke(gameLoop)
        // t
        verify { gameState.puckVX = any() }
    }

    @Test
    fun `puck collides with a live block and clears it via reflection`() {
        // ---------- arrange -------------------------------------------------
        every { lifeGrid.rows } returns 1
        every { lifeGrid.cols } returns 1
        every { lifeGrid.cellSize } returns 40.0
        every { lifeGrid.gridX } returns 100.0
        every { lifeGrid.gridY } returns 100.0
        // a single live cell
        every { lifeGrid.grid } returns arrayOf(booleanArrayOf(true))

        // place the puck so its centre (115,115) lies inside the 40×40 block
        every { gameState.puckX } returns 115.0
        every { gameState.puckY } returns 115.0
        every { gameState.puckVX } returns 3.0
        every { gameState.puckVY } returns 2.0

        // ---------- obtain the private method --------------------------------
        val blockMethod = GameLoop::class.java.getDeclaredMethod("validateBlockCollision")
        blockMethod.isAccessible = true

        // ---------- act ----------------------------------------------------
        blockMethod.invoke(gameLoop)

        // ---------- assert -------------------------------------------------
        // The block must have been cleared
        assertFalse(lifeGrid.grid[0][0])

        // The collision handler changes the puck velocity (random offset is added)
        verify { gameState.puckVX = any() }
        verify { gameState.puckVY = any() }
    }

    @Test
    fun `checkLineCircleCollision returns true for intersecting geometry`() {
        // g
        val method =
            GameLoop::class.java.getDeclaredMethod(
                "checkLineCircleCollision",
                Double::class.java,
                Double::class.java,
                Double::class.java,
                Double::class.java,
                Double::class.java,
                Double::class.java,
                Double::class.java,
            )
        method.isAccessible = true

        // w
        val result =
            method.invoke(
                gameLoop,
                0.0,
                0.0,
                100.0,
                0.0,
                50.0,
                8.0,
                4.0,
            ) as Boolean
        // t
        assertTrue(result)
    }

    @Test
    fun `checkLineCircleCollision returns false when far away`() {
        // g
        val method =
            GameLoop::class.java.getDeclaredMethod(
                "checkLineCircleCollision",
                Double::class.java,
                Double::class.java,
                Double::class.java,
                Double::class.java,
                Double::class.java,
                Double::class.java,
                Double::class.java,
            )
        method.isAccessible = true

        // w
        val result =
            method.invoke(
                gameLoop,
                0.0,
                0.0,
                100.0,
                0.0,
                50.0,
                30.0,
                4.0,
            ) as Boolean
        // t
        assertFalse(result)
    }
}
