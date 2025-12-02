package ru.rkhamatyarov.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertNotEquals

class AdditionalPuckTest {
    private lateinit var puck: AdditionalPuck

    @BeforeEach
    fun setUp() {
        puck =
            AdditionalPuck(
                x = 100.0,
                y = 200.0,
                vx = 2.0,
                vy = -1.0,
                creationTime = 0L,
                lifetime = 10_000_000_000L,
            )
    }

    @Test
    fun `test constructor initializes properties correctly`() {
        // g // w // t
        assertEquals(100.0, puck.x, 0.001)
        assertEquals(200.0, puck.y, 0.001)
        assertEquals(2.0, puck.vx, 0.001)
        assertEquals(-1.0, puck.vy, 0.001)
        assertEquals(0L, puck.creationTime)
        assertEquals(10_000_000_000L, puck.lifetime)
    }

    @Test
    fun `test update method moves puck correctly`() {
        // g
        val speedMultiplier = 1.5

        // w
        puck.update(speedMultiplier)

        // t
        assertEquals(103.0, puck.x, 0.001)
        assertEquals(198.5, puck.y, 0.001)

        // w
        puck.update(speedMultiplier)

        // t
        assertEquals(106.0, puck.x, 0.001)
        assertEquals(197.0, puck.y, 0.001)
    }

    @Test
    fun `test update with zero speed multiplier does not move puck`() {
        // g
        val speedMultiplier = 0.0
        val initialX = puck.x
        val initialY = puck.y

        // w
        puck.update(speedMultiplier)

        // t
        assertEquals(initialX, puck.x, 0.001)
        assertEquals(initialY, puck.y, 0.001)
    }

    @Test
    fun `test update with negative speed multiplier moves puck in opposite direction`() {
        // g
        val speedMultiplier = -0.5

        // w
        puck.update(speedMultiplier)

        // t
        assertEquals(99.0, puck.x, 0.001)
        assertEquals(200.5, puck.y, 0.001)
    }

    @Test
    fun `test isExpired returns false when within lifetime`() {
        // g
        val currentTime = 5_000_000_000L

        // w // t
        assertFalse(puck.isExpired(currentTime))
    }

    @Test
    fun `test isExpired returns true when past lifetime`() {
        // g // w // t
        assertTrue(puck.isExpired(11_000_000_000L))
    }

    @Test
    fun `test isExpired returns false exactly at lifetime boundary`() {
        // g
        val currentTime = 10_000_000_000L

        // w // t
        assertFalse(puck.isExpired(currentTime))
    }

    @Test
    fun `test isExpired returns true when just past lifetime`() {
        // g // w // t
        assertTrue(puck.isExpired(10_000_000_001L))
    }

    @Test
    fun `test isExpired uses System nanoTime by default`() {
        // g
        val puckWithCurrentTime = AdditionalPuck(0.0, 0.0, 0.0, 0.0)

        // t
        assertFalse(puckWithCurrentTime.isExpired())
    }

    @Test
    fun `test data class properties are mutable for x and y`() {
        // w
        puck.x = 150.0
        puck.y = 250.0

        // t
        assertEquals(150.0, puck.x, 0.001)
        assertEquals(250.0, puck.y, 0.001)
    }

    @Test
    fun `test data class properties are mutable for velocity`() {
        // w
        puck.vx = 3.0
        puck.vy = -2.0

        // t
        assertEquals(3.0, puck.vx, 0.001)
        assertEquals(-2.0, puck.vy, 0.001)
    }

    @Test
    fun `test update uses current velocity values`() {
        // g
        puck.vx = 3.0
        puck.vy = 2.0

        // w
        puck.update(1.0)

        // t
        assertEquals(103.0, puck.x, 0.001) // 100 + 3 * 1 = 103
        assertEquals(202.0, puck.y, 0.001) // 200 + 2 * 1 = 202
    }

    @Test
    fun `test equals and hashCode based on properties`() {
        // g
        val puck1 = AdditionalPuck(100.0, 200.0, 2.0, -1.0, 0L, 10_000_000_000L)
        val puck2 = AdditionalPuck(100.0, 200.0, 2.0, -1.0, 0L, 10_000_000_000L)
        val puck3 = AdditionalPuck(150.0, 200.0, 2.0, -1.0, 0L, 10_000_000_000L)

        // t
        assertEquals(puck1, puck2)
        assertNotEquals(puck1, puck3)
        assertEquals(puck1.hashCode(), puck2.hashCode())
        assertNotEquals(puck1.hashCode(), puck3.hashCode())
    }

    @Test
    fun `test toString contains all properties`() {
        // w
        val stringRepresentation = puck.toString()

        // t
        assertTrue(stringRepresentation.contains("100.0"))
        assertTrue(stringRepresentation.contains("200.0"))
        assertTrue(stringRepresentation.contains("2.0"))
        assertTrue(stringRepresentation.contains("-1.0"))
        assertTrue(stringRepresentation.contains("0"))
        assertTrue(stringRepresentation.contains("10000000000"))
    }

    @Test
    fun `test copy method creates new instance with modified properties`() {
        // g
        val copiedPuck = puck.copy(x = 150.0, vx = 3.0)

        // w // t
        assertEquals(150.0, copiedPuck.x, 0.001)
        assertEquals(200.0, copiedPuck.y, 0.001)
        assertEquals(3.0, copiedPuck.vx, 0.001)
        assertEquals(-1.0, copiedPuck.vy, 0.001)
        assertEquals(0L, copiedPuck.creationTime)
        assertEquals(10_000_000_000L, copiedPuck.lifetime)

        assertEquals(100.0, puck.x, 0.001)
        assertEquals(2.0, puck.vx, 0.001)
    }

    @Test
    fun `test isExpired with different lifetime values`() {
        // g
        val baseTime = 1000L
        val lifetime = 5_000_000_000L

        val shortLivedPuck =
            AdditionalPuck(
                x = 0.0,
                y = 0.0,
                vx = 0.0,
                vy = 0.0,
                creationTime = baseTime,
                lifetime = lifetime,
            )
        // w // t
        assertFalse(
            shortLivedPuck.isExpired(currentTime = baseTime),
            "At creation time: NOT expired",
        )

        assertFalse(
            shortLivedPuck.isExpired(currentTime = baseTime + 3_000_000_000L),
            "At 3 seconds: NOT expired",
        )

        assertFalse(
            shortLivedPuck.isExpired(currentTime = baseTime + 4_999_999_999L),
            "At 4.99 seconds: NOT expired",
        )

        assertFalse(
            shortLivedPuck.isExpired(currentTime = baseTime + 5_000_000_000L),
            "At exactly 5 seconds: NOT expired",
        )

        assertTrue(
            shortLivedPuck.isExpired(currentTime = baseTime + 5_000_000_001L),
            "At 5.01 seconds: EXPIRED",
        )
    }

    @Test
    fun `test default values in constructor`() {
        // g // w
        val defaultPuck = AdditionalPuck(0.0, 0.0, 0.0, 0.0)

        // t
        assertEquals(15_000_000_000L, defaultPuck.lifetime)

        val currentTime = System.nanoTime()
        assertTrue(defaultPuck.creationTime > currentTime - 1_000_000_000L)
        assertTrue(defaultPuck.creationTime <= currentTime)
    }
}
