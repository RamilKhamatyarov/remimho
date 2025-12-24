package ru.rkhamatyarov.model

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GameStateTest {
    private lateinit var gameState: GameState
    private lateinit var lifeGrid: GameOfLifeGrid

    @BeforeEach
    fun setUp() {
        lifeGrid = mockk(relaxed = true)
        gameState = GameState()
        gameState.lifeGrid = lifeGrid

        gameState.canvasWidth = 800.0
        gameState.canvasHeight = 600.0
    }

    @Test
    fun `paddleHeight returns correct proportion of canvas height`() {
        // g
        gameState.canvasHeight = 600.0

        // w
        val result = gameState.paddleHeight

        // t
        assertEquals(100.0, result)
    }

    @Test
    fun `updatePuckMovingTime increases speed boost after time thresholds`() {
        // g
        gameState.puckVX = 3.0
        gameState.puckVY = 2.0
        gameState.paused = false

        // w - Simulate 1.5 seconds of movement
        gameState.updatePuckMovingTime(1_500_000_000L)

        // t
        assertEquals(1.5, gameState.timeSpeedBoost)
    }

    @Test
    fun `updatePuckMovingTime resets when puck stops moving`() {
        // g
        gameState.puckVX = 0.0
        gameState.puckVY = 0.0
        gameState.puckMovingTime = 3_000_000_000L
        gameState.timeSpeedBoost = 2.5

        // w
        gameState.updatePuckMovingTime(100_000_000L)

        // t
        assertEquals(0L, gameState.puckMovingTime)
        assertEquals(1.0, gameState.timeSpeedBoost)
    }

    @Test
    fun `updatePuckMovingTime calculates correct speed multiplier`() {
        // g
        gameState.baseSpeedMultiplier = 2.0
        gameState.timeSpeedBoost = 1.5
        gameState.powerUpSpeedMultiplier = 1.2
        gameState.puckVX = 3.0

        // w
        gameState.updatePuckMovingTime(100_000_000L)

        // t
        assertEquals(3.6, gameState.speedMultiplier, 0.001)
    }

    @Test
    fun `startNewLine initializes current line with first point`() {
        // g
        val startX = 100.0
        val startY = 200.0

        // w
        gameState.startNewLine(startX, startY)

        // t
        assertTrue(gameState.isDrawing)
        assertTrue(gameState.currentLine != null)
        assertEquals(1, gameState.currentLine!!.controlPoints.size)
        assertEquals(startX, gameState.currentLine!!.controlPoints[0].x)
        assertEquals(startY, gameState.currentLine!!.controlPoints[0].y)
    }

    @Test
    fun `updateCurrentLine adds points to current line`() {
        // g
        gameState.startNewLine(100.0, 100.0)
        val newX = 150.0
        val newY = 150.0

        // w
        gameState.updateCurrentLine(newX, newY)

        // t
        assertEquals(2, gameState.currentLine!!.controlPoints.size)
        assertEquals(newX, gameState.currentLine!!.controlPoints[1].x)
        assertEquals(newY, gameState.currentLine!!.controlPoints[1].y)
    }

    @Test
    fun `finishCurrentLine adds completed line to lines list`() {
        // g
        gameState.startNewLine(100.0, 100.0)
        gameState.updateCurrentLine(200.0, 200.0)
        gameState.updateCurrentLine(300.0, 300.0)

        // w
        gameState.finishCurrentLine()

        // t
        assertFalse(gameState.isDrawing)
        assertEquals(1, gameState.lines.size)
        assertTrue(gameState.currentLine != null)
    }

    @Test
    fun `clearLines removes all lines and resets state`() {
        // g
        gameState.startNewLine(100.0, 100.0)
        gameState.updateCurrentLine(200.0, 200.0)
        gameState.finishCurrentLine()
        gameState.startNewLine(300.0, 300.0)

        // w
        gameState.clearLines()

        // t
        assertEquals(0, gameState.lines.size)
        assertTrue(gameState.currentLine == null)
        assertFalse(gameState.isDrawing)
    }

    @Test
    fun `togglePause switches pause state`() {
        // g
        val initialPaused = gameState.paused

        // w
        gameState.togglePause()

        // t
        assertEquals(!initialPaused, gameState.paused)

        // w
        gameState.togglePause()

        // t
        assertEquals(initialPaused, gameState.paused)
    }

    @Test
    fun `reset returns game to initial state`() {
        // g
        gameState.puckX = 100.0
        gameState.puckY = 100.0
        gameState.puckVX = 10.0
        gameState.puckVY = 10.0
        gameState.paddle1Y = 500.0
        gameState.paddle2Y = 500.0
        gameState.puckMovingTime = 5_000_000_000L
        gameState.timeSpeedBoost = 2.5
        gameState.baseSpeedMultiplier = 3.0
        gameState.powerUpSpeedMultiplier = 1.5
        gameState.speedMultiplier = 4.5
        gameState.paused = true
        gameState.lines.add(Line().apply { controlPoints.add(Point(1.0, 1.0)) })
        gameState.isGhostMode = true
        gameState.hasPaddleShield = true
        gameState.powerUps.add(PowerUp(100.0, 100.0, PowerUpType.SPEED_BOOST))

        val activeEffect =
            ActivePowerUpEffect(
                PowerUpType.SPEED_BOOST,
                10_000_000_000L,
                System.nanoTime(),
                true,
            )
        gameState.activePowerUpEffects.add(activeEffect)

        gameState.additionalPucks.add(AdditionalPuck(200.0, 200.0, 2.0, 2.0))

        // w
        gameState.reset()

        // t
        assertEquals(400.0, gameState.puckX, 0.001)
        assertEquals(300.0, gameState.puckY, 0.001)
        assertEquals(-3.0, gameState.puckVX, 0.001)
        assertEquals(250.0, gameState.paddle1Y, 0.001)
        assertEquals(250.0, gameState.paddle2Y, 0.001)
        assertEquals(0L, gameState.puckMovingTime)
        assertEquals(1.0, gameState.timeSpeedBoost, 0.001)
        assertEquals(1.0, gameState.baseSpeedMultiplier, 0.001)
        assertEquals(1.0, gameState.powerUpSpeedMultiplier, 0.001)
        assertEquals(1.0, gameState.speedMultiplier, 0.001)

        assertFalse(gameState.isGhostMode)
        assertFalse(gameState.hasPaddleShield)
        assertTrue(gameState.powerUps.isEmpty())
        assertTrue(gameState.activePowerUpEffects.isEmpty())
        assertTrue(gameState.additionalPucks.isEmpty())

        verify { lifeGrid.reset() }
        verify { lifeGrid.repositionGrid(800.0, 600.0) }
    }

    @Test
    fun `flattenBezierSpline returns control points for small input`() {
        // g
        val controlPoints =
            listOf(
                Point(0.0, 0.0),
                Point(10.0, 10.0),
            )

        // w
        val result = gameState.flattenBezierSpline(controlPoints)

        // t
        assertEquals(2, result.size)
        assertEquals(controlPoints[0].x, result[0].x)
        assertEquals(controlPoints[1].x, result[1].x)
    }

    @Test
    fun `flattenBezierSpline generates bezier curve for sufficient points`() {
        // g
        val controlPoints =
            listOf(
                Point(0.0, 0.0),
                Point(10.0, 20.0),
                Point(20.0, 10.0),
                Point(30.0, 30.0),
            )

        // w
        val result = gameState.flattenBezierSpline(controlPoints, stepsPerSegment = 5)

        // t
        assertTrue(result.size > controlPoints.size)
        assertEquals(0.0, result[0].x)
        assertEquals(30.0, result.last().x)
    }

    @Test
    fun `updateAdditionalPucks moves pucks and handles boundaries`() {
        // g
        val puck = AdditionalPuck(100.0, 100.0, 5.0, 3.0)
        gameState.additionalPucks.add(puck)
        gameState.speedMultiplier = 1.0

        // w
        gameState.updateAdditionalPucks()

        // t
        assertEquals(105.0, puck.x)
        assertEquals(103.0, puck.y)
    }

    @Test
    fun `updateAdditionalPucks removes expired pucks`() {
        // g
        val puck1 = AdditionalPuck(100.0, 100.0, 5.0, 3.0)
        val puck2 =
            AdditionalPuck(
                200.0,
                200.0,
                5.0,
                3.0,
                creationTime = System.nanoTime() - 20_000_000_000,
            )
        gameState.additionalPucks.addAll(listOf(puck1, puck2))

        // w
        gameState.updateAdditionalPucks()

        // t
        assertEquals(1, gameState.additionalPucks.size)
        assertTrue(gameState.additionalPucks.contains(puck1))
        assertFalse(gameState.additionalPucks.contains(puck2))
    }

    @Test
    fun `updateAdditionalPucks bounces pucks off vertical boundaries`() {
        // g
        val puckTop = AdditionalPuck(100.0, 5.0, 2.0, -3.0)
        val puckBottom = AdditionalPuck(100.0, 595.0, 2.0, 3.0)
        gameState.additionalPucks.addAll(listOf(puckTop, puckBottom))
        gameState.speedMultiplier = 1.0

        // w
        gameState.updateAdditionalPucks()

        // t
        assertEquals(3.0, puckTop.vy)
        assertEquals(-3.0, puckBottom.vy)
    }

    @Test
    fun `updateAnimations progresses line animations`() {
        // g
        val line =
            Line().apply {
                isAnimating = true
                animationProgress = 0.5
            }
        gameState.lines.add(line)

        // w
        gameState.updateAnimations()

        // t
        assertEquals(0.52, line.animationProgress, 0.01)
    }

    @Test
    fun `updateAnimations completes animation when progress reaches 1`() {
        // g
        val line =
            Line().apply {
                isAnimating = true
                animationProgress = 0.99
            }
        gameState.lines.add(line)

        // w
        gameState.updateAnimations()

        // t
        assertEquals(1.0, line.animationProgress, 0.01)
        assertFalse(line.isAnimating)
    }

    @Test
    fun `speed multiplier components combine correctly`() {
        // g
        gameState.baseSpeedMultiplier = 2.0
        gameState.timeSpeedBoost = 1.5
        gameState.powerUpSpeedMultiplier = 1.2

        // w
        gameState.updatePuckMovingTime(100_000_000L)

        // t
        assertEquals(3.6, gameState.speedMultiplier, 0.001)
    }

    @Test
    fun `paddle positions are correctly initialized after reset`() {
        // g
        gameState.canvasHeight = 600.0

        // w
        gameState.reset()

        // t
        assertEquals(250.0, gameState.paddle1Y)
        assertEquals(250.0, gameState.paddle2Y)
    }

    @Test
    fun `current line width is set during creation`() {
        // g
        val startX = 50.0
        val startY = 50.0

        // w
        gameState.startNewLine(startX, startY)

        // t
        assertEquals(5.0, gameState.currentLine!!.width)
    }

    @Test
    fun `line control points are limited to prevent memory issues`() {
        // g
        gameState.startNewLine(0.0, 0.0)

        // w
        for (i in 1..1500) {
            gameState.updateCurrentLine(i.toDouble(), i.toDouble())
        }

        // t
        assertTrue(gameState.currentLine!!.controlPoints.size <= 1000)
    }

    @Test
    fun `additional puck expiration works correctly`() {
        // g
        val puck =
            AdditionalPuck(
                100.0,
                100.0,
                2.0,
                2.0,
                creationTime = System.nanoTime() - 16_000_000_000,
            )

        // w
        val isExpired = puck.isExpired()

        // t
        assertTrue(isExpired)
    }

    @Test
    fun `additional puck not expired when recently created`() {
        // g
        val puck =
            AdditionalPuck(
                100.0,
                100.0,
                2.0,
                2.0,
                creationTime = System.nanoTime() - 5_000_000_000,
            )

        // w
        val isExpired = puck.isExpired()

        // t
        assertFalse(isExpired)
    }

    @Test
    fun `game state initializes with correct default values`() {
        // t
        assertEquals(800.0, gameState.canvasWidth)
        assertEquals(600.0, gameState.canvasHeight)
        assertEquals(390.0, gameState.puckX)
        assertEquals(290.0, gameState.puckY)
        assertEquals(3.0, gameState.puckVX)
        assertEquals(250.0, gameState.paddle1Y)
        assertEquals(250.0, gameState.paddle2Y)
        assertEquals(1.0, gameState.speedMultiplier)
        assertEquals(1.0, gameState.baseSpeedMultiplier)
        assertEquals(1.0, gameState.timeSpeedBoost)
        assertEquals(10.0, gameState.powerUpSpeedMultiplier)
        assertEquals(0L, gameState.puckMovingTime)
        assertFalse(gameState.paused)
        assertTrue(gameState.lines.isEmpty())
        assertTrue(gameState.powerUps.isEmpty())
        assertTrue(gameState.activePowerUpEffects.isEmpty())
        assertTrue(gameState.additionalPucks.isEmpty())
        assertFalse(gameState.isGhostMode)
        assertFalse(gameState.hasPaddleShield)
        assertFalse(gameState.isDrawing)
        assertTrue(gameState.currentLine == null)
    }
}
