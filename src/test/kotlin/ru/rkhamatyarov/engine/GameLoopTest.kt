package ru.rkhamatyarov.engine

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.smallrye.common.constraint.Assert.assertFalse
import javafx.scene.canvas.GraphicsContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.rkhamatyarov.handler.InputHandler
import ru.rkhamatyarov.model.ActivePowerUpEffect
import ru.rkhamatyarov.model.GameOfLifeGrid
import ru.rkhamatyarov.model.GameState
import ru.rkhamatyarov.model.Point
import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.service.PowerUpManager
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GameLoopTest {
    private lateinit var gameLoop: GameLoop
    private lateinit var gameState: GameState
    private lateinit var inputHandler: InputHandler
    private lateinit var lifeGrid: GameOfLifeGrid
    private lateinit var graphicsContext: GraphicsContext

    @BeforeEach
    fun setUp() {
        gameState = mockk(relaxed = true)
        every { gameState.updateAnimations() } returns Unit

        inputHandler = mockk(relaxed = true)
        lifeGrid = mockk(relaxed = true)
        graphicsContext = mockk(relaxed = true)
        val powerUpManager = mockk<PowerUpManager>(relaxed = true)

        gameLoop = GameLoop()
        gameLoop.gameState = gameState
        gameLoop.inputHandler = inputHandler
        gameLoop.lifeGrid = lifeGrid
        gameLoop.gc = graphicsContext
        gameLoop.powerUpManager = powerUpManager
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
        verify { gameState.puckVY = 5.0 }
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
        verify { gameState.puckVY = -3.0 }
    }

    @Test
    fun `puck collides with a live block and clears it via reflection`() {
        // g
        every { lifeGrid.rows } returns 1
        every { lifeGrid.cols } returns 1
        every { lifeGrid.cellSize } returns 40.0
        every { lifeGrid.gridX } returns 100.0
        every { lifeGrid.gridY } returns 100.0
        every { lifeGrid.grid } returns arrayOf(booleanArrayOf(true))

        every { gameState.puckX } returns 115.0
        every { gameState.puckY } returns 115.0
        every { gameState.puckVX } returns 3.0
        every { gameState.puckVY } returns 2.0

        val blockMethod = GameLoop::class.java.getDeclaredMethod("validateBlockCollision")
        blockMethod.isAccessible = true

        // w
        blockMethod.invoke(gameLoop)

        // t
        assertFalse(lifeGrid.grid[0][0])

        verify { gameState.puckVX = any() }
        verify { gameState.puckVY = any() }
    }

    @Test
    fun `handle method updates game state and renders when not paused`() {
        // g
        val now = 1_000_000_000L
        val deltaTime = 16_666_666L

        every { gameState.paused } returns false
        every { gameState.canvasWidth } returns 800.0
        every { gameState.canvasHeight } returns 600.0
        every { lifeGrid.lastUpdate } returns now - 1000
        every { lifeGrid.updateInterval } returns 500L

        // w
        gameLoop.handle(now)
        gameLoop.handle(now + deltaTime)

        // t
        verify { gameState.updateAnimations() }
        verify { gameState.updatePuckMovingTime(deltaTime) }
        verify { inputHandler.update() }
        verify { lifeGrid.update() }
    }

    @Test
    fun `handle method pauses rendering and updates w paused`() {
        // g
        val now = 1_000_000_000L
        every { gameState.paused } returns false
        every { gameState.canvasWidth } returns 800.0
        every { gameState.canvasHeight } returns 600.0

        // w
        gameLoop.handle(now)

        // t
        verify(exactly = 1) { lifeGrid.update() }
        verify { gameState.updateAnimations() }
    }

    @Test
    fun `block collision clears block and changes puck direction`() {
        // g
        every { lifeGrid.rows } returns 1
        every { lifeGrid.cols } returns 1
        every { lifeGrid.cellSize } returns 40.0
        every { lifeGrid.gridX } returns 100.0
        every { lifeGrid.gridY } returns 100.0
        every { lifeGrid.grid } returns arrayOf(booleanArrayOf(true))
        every { gameState.puckX } returns 115.0
        every { gameState.puckY } returns 115.0
        every { gameState.puckVX } returns 3.0
        every { gameState.puckVY } returns 2.0

        // w
        val method = GameLoop::class.java.getDeclaredMethod("validateBlockCollision")
        method.isAccessible = true
        method.invoke(gameLoop)

        // t
        assertFalse(lifeGrid.grid[0][0])
        verify { gameState.puckVX = any() }
        verify { gameState.puckVY = any() }
    }

    @Test
    fun `AI moves paddle toward predicted puck position`() {
        // g
        every { gameState.puckVX } returns -5.0
        every { gameState.puckY } returns 200.0
        every { gameState.puckX } returns 400.0
        every { gameState.paddle1Y } returns 150.0
        every { gameState.paddleHeight } returns 100.0
        every { gameState.canvasHeight } returns 600.0

        // w
        val method = GameLoop::class.java.getDeclaredMethod("updateAIPaddle")
        method.isAccessible = true
        method.invoke(gameLoop)

        // t
        verify { gameState.paddle1Y = 150.0 }
    }

    @Test
    fun `AI moves paddle to center w puck moving away`() {
        // g
        every { gameState.puckVX } returns 5.0
        every { gameState.paddle1Y } returns 100.0
        every { gameState.paddleHeight } returns 100.0
        every { gameState.canvasHeight } returns 600.0

        // w
        val method = GameLoop::class.java.getDeclaredMethod("updateAIPaddle")
        method.isAccessible = true
        method.invoke(gameLoop)

        // t
        verify { gameState.paddle1Y = 100.0 }
    }

    @Test
    fun `renderScore displays correct scores and speed`() {
        // g
        gameLoop.player1Score = 3
        gameLoop.player2Score = 5
        every { gameState.speedMultiplier } returns 1.5
        every { graphicsContext.fillText(any(), any(), any()) } returns Unit
        every { graphicsContext.save() } returns Unit
        every { graphicsContext.restore() } returns Unit
        every { graphicsContext.fill = any() } returns Unit
        every { graphicsContext.stroke = any() } returns Unit
        every { graphicsContext.lineWidth = any() } returns Unit
        every { graphicsContext.font = any() } returns Unit
        every { gameState.activePowerUpEffects } returns mutableListOf(ActivePowerUpEffect(type = PowerUpType.SPEED_BOOST, duration = 10))

        // w
        val method =
            GameLoop::class.java.getDeclaredMethod("renderScore", GraphicsContext::class.java, Double::class.java)
        method.isAccessible = true
        method.invoke(gameLoop, graphicsContext, 800.0)

        // t
        verify { graphicsContext.fillText("AI: 3", 50.0, 30.0) }
        verify { graphicsContext.fillText("Player: 5", 650.0, 30.0) }
        verify { graphicsContext.fillText(any<String>(), eq(340.0), eq(15.0)) }
    }

    @Test
    fun `renderSpeedIndicator shows boost w timeSpeedBoost more than one`() {
        // g
        every { gameState.timeSpeedBoost } returns 2.5
        every { graphicsContext.save() } returns Unit
        every { graphicsContext.restore() } returns Unit
        every { graphicsContext.fill = any() } returns Unit
        every { graphicsContext.font = any() } returns Unit
        every { graphicsContext.fillText(any(), any(), any()) } returns Unit

        // w
        val method =
            GameLoop::class.java.getDeclaredMethod(
                "renderSpeedIndicator",
                GraphicsContext::class.java,
                Double::class.java,
            )
        method.isAccessible = true
        method.invoke(gameLoop, graphicsContext, 800.0)

        // t
        verify {
            graphicsContext.fillText(
                match { it.matches(Regex("Speed boost: [0-9]+[.,][0-9]+x")) },
                320.0,
                50.0,
            )
        }
    }

    @Test
    fun `paddle position is constrained within canvas bounds`() {
        // g
        every { gameState.paddle1Y } returns -10.0
        every { gameState.canvasHeight } returns 600.0
        every { gameState.paddleHeight } returns 100.0

        // w
        val method = GameLoop::class.java.getDeclaredMethod("updateAIPaddle")
        method.isAccessible = true
        method.invoke(gameLoop)

        // t
        verify { gameState.paddle1Y = 0.0 }
    }

    @Test
    fun `life grid updates only w interval elapsed`() {
        // g
        val now = 1_000_000_000L
        every { gameState.paused } returns false
        every { gameState.canvasWidth } returns 800.0
        every { gameState.canvasHeight } returns 600.0
        every { lifeGrid.lastUpdate } returns now - 1000
        every { lifeGrid.updateInterval } returns 500L

        // w
        gameLoop.handle(now)

        // t
        verify { lifeGrid.update() }
    }

    @Test
    fun `life grid does not update w interval not elapsed`() {
        // g
        val now = 1_000_000_000L
        every { gameState.paused } returns false
        every { gameState.canvasWidth } returns 800.0
        every { gameState.canvasHeight } returns 600.0
        every { lifeGrid.lastUpdate } returns now - 400
        every { lifeGrid.updateInterval } returns 500L

        // w
        gameLoop.handle(now)

        // t
        verify(exactly = 0) { lifeGrid.update() }
    }

    @Test
    fun `togglePause changes game state pause status`() {
        // g
        every { gameState.togglePause() } returns Unit

        // w
        gameLoop.togglePause()

        // t
        verify { gameState.togglePause() }
    }

    @Test
    fun `resetPuck places puck at center with random velocity`() {
        // g
        every { gameState.canvasWidth } returns 800.0
        every { gameState.canvasHeight } returns 600.0
        every { gameState.puckVX } returns 5.0
        every { gameState.puckMovingTime } returns 1000L
        every { gameState.timeSpeedBoost } returns 2.0

        // w
        val method = GameLoop::class.java.getDeclaredMethod("resetPuck")
        method.isAccessible = true
        method.invoke(gameLoop)

        // t
        verify { gameState.puckX = 400.0 }
        verify { gameState.puckY = 300.0 }
        verify { gameState.puckVX = -3.0 }
        verify { gameState.puckMovingTime = 0L }
        verify { gameState.timeSpeedBoost = 1.0 }
    }

    @Test
    fun `distanceToLineSegment calculates correct distance`() {
        // g
        val point1 = Point(0.0, 0.0)
        val point2 = Point(10.0, 0.0)
        val testX = 5.0
        val testY = 5.0

        // w
        val method =
            GameLoop::class.java.getDeclaredMethod(
                "distanceToLineSegment",
                Double::class.java,
                Double::class.java,
                Point::class.java,
                Point::class.java,
            )
        method.isAccessible = true
        val result = method.invoke(gameLoop, testX, testY, point1, point2) as Double

        // t
        assertEquals(5.0, result, 0.001)
    }

    @Test
    fun `renderObjects calls all rendering methods`() {
        // g
        every { graphicsContext.clearRect(any(), any(), any(), any()) } returns Unit
        every { graphicsContext.fill = any() } returns Unit
        every { graphicsContext.stroke = any() } returns Unit
        every { graphicsContext.lineWidth = any() } returns Unit
        every { graphicsContext.font = any() } returns Unit
        every { graphicsContext.fillText(any(), any(), any()) } returns Unit
        every { graphicsContext.fillOval(any(), any(), any(), any()) } returns Unit
        every { graphicsContext.strokeOval(any(), any(), any(), any()) } returns Unit
        every { graphicsContext.fillRect(any(), any(), any(), any()) } returns Unit
        every { graphicsContext.fillRoundRect(any(), any(), any(), any(), any(), any()) } returns Unit
        every { graphicsContext.strokeRoundRect(any(), any(), any(), any(), any(), any()) } returns Unit
        every { graphicsContext.beginPath() } returns Unit
        every { graphicsContext.moveTo(any(), any()) } returns Unit
        every { graphicsContext.lineTo(any(), any()) } returns Unit
        every { graphicsContext.stroke() } returns Unit
        every { graphicsContext.save() } returns Unit
        every { graphicsContext.restore() } returns Unit
        every { graphicsContext.globalAlpha = any() } returns Unit

        // w
        val method = GameLoop::class.java.getDeclaredMethod("renderObjects", GraphicsContext::class.java)
        method.isAccessible = true
        method.invoke(gameLoop, graphicsContext)

        // t
        assertNotNull(graphicsContext)
    }

    @Test
    fun `updateSinglePuck - puck bounces off top wall`() {
        // g
        every { gameState.canvasHeight } returns 600.0
        every { gameState.puckX } returns 400.0
        every { gameState.puckY } returns 5.0 // near top wall
        every { gameState.puckVX } returns 3.0
        every { gameState.puckVY } returns -2.0 // moving up
        every { gameState.speedMultiplier } returns 1.0
        every { gameState.isGhostMode } returns false
        every { gameState.lines } returns mutableListOf()
        every { gameState.hasPaddleShield } returns false
        every { gameState.canvasWidth } returns 800.0

        val method = GameLoop::class.java.getDeclaredMethod("updateSinglePuck")
        method.isAccessible = true

        // w
        method.invoke(gameLoop)

        // t
        verify { gameState.puckVY = 2.0 }
    }

    @Test
    fun `updateSinglePuck - puck bounces off bottom wall`() {
        // g
        every { gameState.canvasHeight } returns 600.0
        every { gameState.puckX } returns 400.0
        every { gameState.puckY } returns 595.0
        every { gameState.puckVX } returns 3.0
        every { gameState.puckVY } returns 2.0
        every { gameState.speedMultiplier } returns 1.0
        every { gameState.isGhostMode } returns false
        every { gameState.lines } returns mutableListOf()
        every { gameState.hasPaddleShield } returns false
        every { gameState.canvasWidth } returns 800.0

        val method = GameLoop::class.java.getDeclaredMethod("updateSinglePuck")
        method.isAccessible = true

        // w
        method.invoke(gameLoop)

        // t
        verify { gameState.puckVY = -2.0 }
    }

    @Test
    fun `updateSinglePuck - player2 scores w puck passes left edge`() {
        // g
        every { gameState.canvasWidth } returns 800.0
        every { gameState.canvasHeight } returns 600.0
        every { gameState.puckX } returns 5.0
        every { gameState.puckY } returns 300.0
        every { gameState.puckVX } returns -3.0
        every { gameState.puckVY } returns 0.0
        every { gameState.speedMultiplier } returns 1.0
        every { gameState.isGhostMode } returns false
        every { gameState.lines } returns mutableListOf()
        every { gameState.hasPaddleShield } returns false

        val initialScore = gameLoop.player2Score

        val method = GameLoop::class.java.getDeclaredMethod("updateSinglePuck")
        method.isAccessible = true

        // w
        method.invoke(gameLoop)

        // t
        assertEquals(initialScore + 1, gameLoop.player2Score)
    }

    @Test
    fun `updateSinglePuck - player1 scores w puck passes right edge without shield`() {
        // g
        every { gameState.canvasWidth } returns 800.0
        every { gameState.canvasHeight } returns 600.0
        every { gameState.puckX } returns 795.0
        every { gameState.puckY } returns 300.0
        every { gameState.puckVX } returns 3.0
        every { gameState.puckVY } returns 0.0
        every { gameState.speedMultiplier } returns 1.0
        every { gameState.isGhostMode } returns false
        every { gameState.lines } returns mutableListOf()
        every { gameState.hasPaddleShield } returns false

        val initialScore = gameLoop.player1Score

        val method = GameLoop::class.java.getDeclaredMethod("updateSinglePuck")
        method.isAccessible = true

        // w
        method.invoke(gameLoop)

        // t
        assertEquals(initialScore + 1, gameLoop.player1Score)
    }

    @Test
    fun `updateSinglePuck - puck bounces w hitting right edge with shield active`() {
        // g
        every { gameState.canvasWidth } returns 800.0
        every { gameState.canvasHeight } returns 600.0
        every { gameState.puckX } returns 795.0
        every { gameState.puckY } returns 300.0
        every { gameState.puckVX } returns 3.0
        every { gameState.puckVY } returns 0.0
        every { gameState.speedMultiplier } returns 1.0
        every { gameState.isGhostMode } returns false
        every { gameState.lines } returns mutableListOf()
        every { gameState.hasPaddleShield } returns true

        val initialScore = gameLoop.player1Score

        val method = GameLoop::class.java.getDeclaredMethod("updateSinglePuck")
        method.isAccessible = true

        // w
        method.invoke(gameLoop)

        // t
        assertEquals(initialScore, gameLoop.player1Score)
        verify { gameState.puckVX = any() }
        verify { gameState.puckX = 780.0 }
    }

    @Test
    fun `updateSinglePuck - puck position updated with speed multiplier`() {
        // g
        every { gameState.canvasWidth } returns 800.0
        every { gameState.canvasHeight } returns 600.0
        every { gameState.puckX } returns 400.0
        every { gameState.puckY } returns 300.0
        every { gameState.puckVX } returns 5.0
        every { gameState.puckVY } returns 3.0
        every { gameState.speedMultiplier } returns 2.0
        every { gameState.isGhostMode } returns false
        every { gameState.lines } returns mutableListOf()
        every { gameState.hasPaddleShield } returns false
        every { gameState.paddle1Y } returns 100.0
        every { gameState.paddle2Y } returns 100.0
        every { gameState.paddleHeight } returns 100.0

        val method = GameLoop::class.java.getDeclaredMethod("updateSinglePuck")
        method.isAccessible = true

        // w
        method.invoke(gameLoop)

        // t
        verify { gameState.puckX = 410.0 }
        verify { gameState.puckY = 306.0 }
    }

    @Test
    fun `updateSinglePuck - puck in middle of field continues moving`() {
        // g
        every { gameState.canvasWidth } returns 800.0
        every { gameState.canvasHeight } returns 600.0
        every { gameState.puckX } returns 400.0
        every { gameState.puckY } returns 300.0
        every { gameState.puckVX } returns 3.0
        every { gameState.puckVY } returns 2.0
        every { gameState.speedMultiplier } returns 1.0
        every { gameState.isGhostMode } returns false
        every { gameState.lines } returns mutableListOf()
        every { gameState.hasPaddleShield } returns false
        every { gameState.paddle1Y } returns 100.0
        every { gameState.paddle2Y } returns 100.0
        every { gameState.paddleHeight } returns 100.0

        val initialPlayer1Score = gameLoop.player1Score
        val initialPlayer2Score = gameLoop.player2Score

        val method = GameLoop::class.java.getDeclaredMethod("updateSinglePuck")
        method.isAccessible = true

        // w
        method.invoke(gameLoop)

        // t
        assertEquals(initialPlayer1Score, gameLoop.player1Score)
        assertEquals(initialPlayer2Score, gameLoop.player2Score)
    }
}
