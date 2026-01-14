package ru.rkhamatyarov.model

import io.mockk.mockk
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
    private var virtualTimeNs: Long = 0L

    @BeforeEach
    fun setUp() {
        lifeGrid = mockk(relaxed = true)
        gameState = GameState()
        gameState.lifeGrid = lifeGrid

        gameState.canvasWidth = 800.0
        gameState.canvasHeight = 600.0

        virtualTimeNs = 0L
    }

    private fun setVirtualTimeMs(ms: Long) {
        virtualTimeNs = ms * 1_000_000L
        gameState.getTimeNs = { virtualTimeNs }
    }

    @Test
    fun `isLineSegmentInCooldown returns false for new segment`() {
        val point1 = Point(0.0, 0.0)
        val point2 = Point(100.0, 100.0)
        val result = gameState.isLineSegmentInCooldown(point1, point2)
        assertFalse(result)
    }

    @Test
    fun `recordLineSegmentCollision marks segment as collided`() {
        setVirtualTimeMs(0)
        val point1 = Point(0.0, 0.0)
        val point2 = Point(100.0, 100.0)
        gameState.recordLineSegmentCollision(point1, point2)
        assertTrue(gameState.isLineSegmentInCooldown(point1, point2))
    }

    @Test
    fun `isLineSegmentInCooldown returns true immediately after collision`() {
        setVirtualTimeMs(0)
        val point1 = Point(50.0, 50.0)
        val point2 = Point(150.0, 150.0)

        gameState.recordLineSegmentCollision(point1, point2)
        val result = gameState.isLineSegmentInCooldown(point1, point2)

        assertTrue(result)
    }

    @Test
    fun `isLineSegmentInCooldown returns false after cooldown expires`() {
        val point1 = Point(25.0, 25.0)
        val point2 = Point(75.0, 75.0)

        setVirtualTimeMs(0)
        gameState.recordLineSegmentCollision(point1, point2)

        setVirtualTimeMs(150)
        val result = gameState.isLineSegmentInCooldown(point1, point2)

        assertFalse(result)
    }

    @Test
    fun `different line segments have different cooldowns`() {
        val point1a = Point(0.0, 0.0)
        val point2a = Point(100.0, 100.0)

        val point1b = Point(200.0, 200.0)
        val point2b = Point(300.0, 300.0)

        gameState.recordLineSegmentCollision(point1a, point2a)

        assertTrue(gameState.isLineSegmentInCooldown(point1a, point2a))
        assertFalse(gameState.isLineSegmentInCooldown(point1b, point2b))
    }

    @Test
    fun `capPuckVelocity limits velocity magnitude`() {
        gameState.puckVX = 6.0
        gameState.puckVY = 8.0

        gameState.capPuckVelocity()

        val magnitude = kotlin.math.hypot(gameState.puckVX, gameState.puckVY)
        assertEquals(8.0, magnitude, 0.001)
    }

    @Test
    fun `capPuckVelocity preserves direction`() {
        gameState.puckVX = 6.0
        gameState.puckVY = 8.0

        gameState.capPuckVelocity()

        val ratio = gameState.puckVX / gameState.puckVY
        assertEquals(0.75, ratio, 0.001)
    }

    @Test
    fun `capPuckVelocity does not affect velocity below limit`() {
        gameState.puckVX = 2.0
        gameState.puckVY = 3.0

        gameState.capPuckVelocity()

        assertEquals(2.0, gameState.puckVX)
        assertEquals(3.0, gameState.puckVY)
    }

    @Test
    fun `capAdditionalPuckVelocity limits additional puck magnitude`() {
        val puck = AdditionalPuck(100.0, 100.0, 4.0, 5.0)

        gameState.capAdditionalPuckVelocity(puck)

        val magnitude = kotlin.math.hypot(puck.vx, puck.vy)
        assertEquals(6.0, magnitude, 0.001)
    }

    @Test
    fun `capAdditionalPuckVelocity preserves puck direction`() {
        val puck = AdditionalPuck(100.0, 100.0, 4.0, 5.0)
        val originalRatio = puck.vx / puck.vy

        gameState.capAdditionalPuckVelocity(puck)

        val newRatio = puck.vx / puck.vy
        assertEquals(originalRatio, newRatio, 0.001)
    }

    @Test
    fun `cleanupCollisionCooldowns removes expired entries`() {
        val point1 = Point(0.0, 0.0)
        val point2 = Point(100.0, 100.0)

        setVirtualTimeMs(0)
        gameState.recordLineSegmentCollision(point1, point2)

        setVirtualTimeMs(250)
        gameState.cleanupCollisionCooldowns()

        val result = gameState.isLineSegmentInCooldown(point1, point2)
        assertFalse(result)
    }

    @Test
    fun `cleanupCollisionCooldowns keeps recent entries`() {
        setVirtualTimeMs(0)
        val point1 = Point(10.0, 10.0)
        val point2 = Point(110.0, 110.0)
        gameState.recordLineSegmentCollision(point1, point2)
        gameState.cleanupCollisionCooldowns()
        val result = gameState.isLineSegmentInCooldown(point1, point2)
        assertTrue(result)
    }

    @Test
    fun `reset clears collision cooldown tracking`() {
        setVirtualTimeMs(0)
        val point1 = Point(5.0, 5.0)
        val point2 = Point(105.0, 105.0)
        gameState.recordLineSegmentCollision(point1, point2)
        assertTrue(gameState.isLineSegmentInCooldown(point1, point2))
        gameState.reset()
        assertFalse(gameState.isLineSegmentInCooldown(point1, point2))
    }

    @Test
    fun `multiple collisions can be tracked independently`() {
        setVirtualTimeMs(0)
        val seg1Point1 = Point(0.0, 0.0)
        val seg1Point2 = Point(50.0, 50.0)

        val seg2Point1 = Point(100.0, 100.0)
        val seg2Point2 = Point(150.0, 150.0)

        val seg3Point1 = Point(200.0, 200.0)
        val seg3Point2 = Point(250.0, 250.0)

        gameState.recordLineSegmentCollision(seg1Point1, seg1Point2)
        gameState.recordLineSegmentCollision(seg2Point1, seg2Point2)

        assertTrue(gameState.isLineSegmentInCooldown(seg1Point1, seg1Point2))
        assertTrue(gameState.isLineSegmentInCooldown(seg2Point1, seg2Point2))
        assertFalse(gameState.isLineSegmentInCooldown(seg3Point1, seg3Point2))
    }

    @Test
    fun `velocity cap applies to negative velocities`() {
        gameState.puckVX = -6.0
        gameState.puckVY = -8.0

        gameState.capPuckVelocity()

        val magnitude = kotlin.math.hypot(gameState.puckVX, gameState.puckVY)
        assertEquals(8.0, magnitude, 0.001)
    }

    @Test
    fun `zero velocity is not affected by cap`() {
        gameState.puckVX = 0.0
        gameState.puckVY = 0.0

        gameState.capPuckVelocity()

        assertEquals(0.0, gameState.puckVX)
        assertEquals(0.0, gameState.puckVY)
    }

    @Test
    fun `collision cooldown duration is 100 milliseconds`() {
        val point1 = Point(1.0, 1.0)
        val point2 = Point(101.0, 101.0)

        setVirtualTimeMs(0)
        gameState.recordLineSegmentCollision(point1, point2)

        setVirtualTimeMs(50)
        assertTrue(gameState.isLineSegmentInCooldown(point1, point2))

        setVirtualTimeMs(125)
        assertFalse(gameState.isLineSegmentInCooldown(point1, point2))
    }

    @Test
    fun `line segment id generation is consistent`() {
        val point1 = Point(10.5, 20.5)
        val point2 = Point(30.5, 40.5)

        setVirtualTimeMs(0)
        gameState.recordLineSegmentCollision(point1, point2)

        setVirtualTimeMs(10)
        val firstCheck = gameState.isLineSegmentInCooldown(point1, point2)
        gameState.recordLineSegmentCollision(point1, point2)
        val secondCheck = gameState.isLineSegmentInCooldown(point1, point2)

        assertTrue(firstCheck)
        assertTrue(secondCheck)
    }

    @Test
    fun `maxVelocityMagnitude constant is 8 dot 0`() {
        assertEquals(8.0, gameState.maxVelocityMagnitude)
    }

    @Test
    fun `additionalPuckMaxVelocity constant is 6 dot 0`() {
        assertEquals(6.0, gameState.additionalPuckMaxVelocity)
    }

    @Test
    fun `capAdditionalPuckVelocity does not affect velocity below limit`() {
        val puck = AdditionalPuck(100.0, 100.0, 2.0, 3.0)

        gameState.capAdditionalPuckVelocity(puck)

        assertEquals(2.0, puck.vx)
        assertEquals(3.0, puck.vy)
    }

    @Test
    fun `collision tracking memory usage is reasonable`() {
        setVirtualTimeMs(0)
        for (i in 0..99) {
            val point1 = Point(i.toDouble() * 10, i.toDouble() * 10)
            val point2 = Point(i.toDouble() * 10 + 50, i.toDouble() * 10 + 50)
            gameState.recordLineSegmentCollision(point1, point2)
        }

        for (i in 0..99) {
            val point1 = Point(i.toDouble() * 10, i.toDouble() * 10)
            val point2 = Point(i.toDouble() * 10 + 50, i.toDouble() * 10 + 50)
            assertTrue(gameState.isLineSegmentInCooldown(point1, point2))
        }

        setVirtualTimeMs(250)
        gameState.cleanupCollisionCooldowns()
        for (i in 0..99) {
            val point1 = Point(i.toDouble() * 10, i.toDouble() * 10)
            val point2 = Point(i.toDouble() * 10 + 50, i.toDouble() * 10 + 50)
            assertFalse(gameState.isLineSegmentInCooldown(point1, point2))
        }
    }
}
