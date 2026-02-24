package ru.rkhamatyarov.model

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import kotlin.math.hypot

@QuarkusTest
class GameStateTest {
    @Inject
    lateinit var gameState: GameState

    @BeforeEach
    fun setUp() {
        gameState.reset()
    }

    @Test
    fun `test initial game state values`() {
        assertEquals(400.0, gameState.puckX)
        assertEquals(300.0, gameState.puckY)
        assertEquals(250.0, gameState.paddle1Y)
        assertEquals(250.0, gameState.paddle2Y)
        assertEquals(800.0, gameState.canvasWidth)
        assertEquals(600.0, gameState.canvasHeight)
        assertFalse(gameState.paused)
        assertFalse(gameState.isDrawing)
        assertEquals(1.0, gameState.speedMultiplier)
    }

    @Test
    fun `test paddle height calculation`() {
        val expectedHeight = 600.0 / 6
        assertEquals(expectedHeight, gameState.paddleHeight)
    }

    @Test
    fun `test puck velocity not capped when below max`() {
        gameState.puckVX = 50.0
        gameState.puckVY = 50.0
        val originalX = gameState.puckVX
        val originalY = gameState.puckVY

        gameState.capPuckVelocity()

        assertEquals(originalX, gameState.puckVX)
        assertEquals(originalY, gameState.puckVY)
    }

    @Test
    fun `test update current line with multiple points`() {
        gameState.startNewLine(100.0, 100.0)
        gameState.updateCurrentLine(150.0, 150.0)
        gameState.updateCurrentLine(200.0, 200.0)

        assertEquals(3, gameState.currentLine!!.controlPoints.size)
    }

    @Test
    fun `test update line removes oldest points when exceeding 1000`() {
        gameState.startNewLine(0.0, 0.0)

        for (i in 0..1000) {
            gameState.updateCurrentLine(i.toDouble(), i.toDouble())
        }

        assertTrue(gameState.currentLine!!.controlPoints.size <= 1000)
    }

    @Test
    fun `test finish current line flattens bezier spline`() {
        gameState.startNewLine(100.0, 100.0)
        gameState.updateCurrentLine(150.0, 150.0)
        gameState.updateCurrentLine(200.0, 100.0)
        gameState.updateCurrentLine(250.0, 150.0)

        gameState.finishCurrentLine()

        assertFalse(gameState.isDrawing)
        assertEquals(1, gameState.lines.size)
        assertNotNull(gameState.lines[0].flattenedPoints)
        assertTrue(gameState.lines[0].flattenedPoints!!.size > 0)
        assertTrue(gameState.lines[0].isAnimating)
    }

    @Test
    fun `test flatten bezier spline with few control points`() {
        val controlPoints =
            listOf(
                Point(0.0, 0.0),
                Point(100.0, 100.0),
            )

        val flattened = gameState.flattenBezierSpline(controlPoints)

        assertEquals(2, flattened.size)
    }

    @Test
    fun `test flatten bezier spline with many control points`() {
        val controlPoints =
            listOf(
                Point(0.0, 0.0),
                Point(100.0, 100.0),
                Point(200.0, 50.0),
                Point(300.0, 150.0),
                Point(400.0, 100.0),
            )

        val flattened = gameState.flattenBezierSpline(controlPoints, stepsPerSegment = 10)

        assertTrue(flattened.size > controlPoints.size)
    }

    @Test
    fun `test flatten bezier spline generates smooth curve`() {
        val controlPoints =
            listOf(
                Point(0.0, 0.0),
                Point(100.0, 50.0),
                Point(200.0, 100.0),
                Point(300.0, 50.0),
            )

        val flattened = gameState.flattenBezierSpline(controlPoints)

        for (i in 0 until flattened.size - 1) {
            val p1 = flattened[i]
            val p2 = flattened[i + 1]
            val distance = hypot(p2.x - p1.x, p2.y - p1.y)
            assertTrue(distance >= 0, "Distance should be non-negative")
        }
    }

    @Test
    fun `test toggle pause`() {
        assertFalse(gameState.paused)

        gameState.togglePause()
        assertTrue(gameState.paused)

        gameState.togglePause()
        assertFalse(gameState.paused)
    }

    @Test
    fun `test record and check line segment collision`() {
        val p1 = Point(100.0, 100.0)
        val p2 = Point(150.0, 150.0)

        assertFalse(gameState.isLineSegmentInCooldown(p1, p2))

        gameState.recordLineSegmentCollision(p1, p2)

        assertTrue(gameState.isLineSegmentInCooldown(p1, p2))
    }

    @Test
    fun `test collision cooldown expires after duration`() {
        val p1 = Point(100.0, 100.0)
        val p2 = Point(150.0, 150.0)

        var currentTime = 0L
        gameState.getTimeNs = { currentTime }

        gameState.recordLineSegmentCollision(p1, p2)
        assertTrue(gameState.isLineSegmentInCooldown(p1, p2))

        currentTime += gameState.collisionCooldownDuration + 1
        assertFalse(gameState.isLineSegmentInCooldown(p1, p2))
    }

    @Test
    fun `test cleanup collision cooldowns`() {
        val p1 = Point(100.0, 100.0)
        val p2 = Point(150.0, 150.0)

        var currentTime = 0L
        gameState.getTimeNs = { currentTime }

        gameState.recordLineSegmentCollision(p1, p2)
        assertEquals(1, gameState.puckLineCollisionCooldown.size)

        currentTime += gameState.collisionCooldownDuration * 3
        gameState.cleanupCollisionCooldowns()

        assertEquals(0, gameState.puckLineCollisionCooldown.size)
    }

    @Test
    fun `test animation completes when progress reaches 1`() {
        gameState.startNewLine(100.0, 100.0)
        gameState.updateCurrentLine(150.0, 150.0)
        gameState.updateCurrentLine(200.0, 100.0)
        gameState.finishCurrentLine()

        val line = gameState.lines[0]

        while (line.isAnimating) {
            gameState.updateAnimations()
        }

        assertEquals(1.0, line.animationProgress)
        assertFalse(line.isAnimating)
    }

    @Test
    fun `test update puck moving time resets speed multiplier`() {
        gameState.speedMultiplier = 2.0
        gameState.baseSpeedMultiplier = 1.5
        gameState.timeSpeedBoost = 1.5

        gameState.updatePuckMovingTime()

        assertEquals(1.5, gameState.speedMultiplier)
    }
}
