package ru.rkhamatyarov.service

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.rkhamatyarov.model.AiOpponentConfig
import ru.rkhamatyarov.model.Line
import ru.rkhamatyarov.model.Point
import ru.rkhamatyarov.model.PowerUp
import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.model.SpeedConfig
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@QuarkusTest
class GameEngineTest {
    @Inject
    lateinit var gameEngine: GameEngine

    companion object {
        private const val TICK_DT = 0.016
    }

    @BeforeEach
    fun setup() {
        gameEngine.paused = false
        gameEngine.resetPuck()
        gameEngine.puck.vx = 0.0
        gameEngine.puck.vy = 0.0
        gameEngine.paddle1Y = (gameEngine.canvasHeight - gameEngine.paddleHeight) / 2
        gameEngine.paddle2Y = (gameEngine.canvasHeight - gameEngine.paddleHeight) / 2
        gameEngine.score.playerA = 0
        gameEngine.score.playerB = 0
        gameEngine.clearLines()
        gameEngine.powerUps.clear()
        gameEngine.activePowerUpEffects.clear()
        gameEngine.elapsedSeconds = 0.0
        gameEngine.speedConfig = SpeedConfig()
        gameEngine.aiOpponentConfig = AiOpponentConfig(reactionDelayMs = 0)
        gameEngine.powerUpSpeedMultiplier = 1.0
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
        gameEngine.puck.vx = -200.0
        gameEngine.puck.x = 100.0
        gameEngine.paddle1Y = 400.0

        gameEngine.tick(TICK_DT)

        assertTrue(gameEngine.paddle1Y < 400.0, "AI paddle should move up toward puck")
    }

    @Test
    fun `test AI paddle stays within canvas bounds after many ticks`() {
        gameEngine.puck.y = 10.0
        gameEngine.puck.vx = -200.0
        gameEngine.puck.x = 100.0
        gameEngine.paddle1Y = 0.0

        repeat(200) { gameEngine.tick(TICK_DT) }

        assertTrue(gameEngine.paddle1Y >= 0.0)
        assertTrue(gameEngine.paddle1Y <= gameEngine.canvasHeight - gameEngine.paddleHeight)
    }

    @Test
    fun `test AI paddle waits for configured reaction delay`() {
        gameEngine.aiOpponentConfig = AiOpponentConfig(reactionDelayMs = 250, maxSpeed = 300.0, trackingError = 0.0, reactZoneRatio = 1.0)
        gameEngine.puck.x = 100.0
        gameEngine.puck.y = 100.0
        gameEngine.puck.vx = -200.0
        gameEngine.paddle1Y = 250.0

        gameEngine.tick(0.1)

        assertEquals(250.0, gameEngine.paddle1Y, 0.0001, "AI should not move before its reaction delay elapses")

        repeat(3) { gameEngine.tick(0.1) }

        assertTrue(gameEngine.paddle1Y < 250.0, "AI should track the delayed puck position once delay has elapsed")
    }

    @Test
    fun `test disabled AI opponent leaves left paddle untouched`() {
        gameEngine.aiOpponentConfig = AiOpponentConfig(enabled = false)
        gameEngine.puck.x = 100.0
        gameEngine.puck.y = 100.0
        gameEngine.puck.vx = -200.0
        gameEngine.paddle1Y = 300.0

        repeat(10) { gameEngine.tick(TICK_DT) }

        assertEquals(300.0, gameEngine.paddle1Y, 0.0001)
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
    fun `test eraseLine removes line by id and returns true`() {
        gameEngine.startNewLine(10.0, 10.0)
        gameEngine.updateCurrentLine(20.0, 20.0)
        gameEngine.finishCurrentLine()
        gameEngine.startNewLine(40.0, 40.0)
        gameEngine.finishCurrentLine()
        assertEquals(2, gameEngine.lines.size)

        val targetId = gameEngine.lines.first().id
        val survivorId = gameEngine.lines.last().id

        val erased = gameEngine.eraseLine(targetId)

        assertTrue(erased, "eraseLine must return true when line exists")
        assertEquals(1, gameEngine.lines.size)
        assertEquals(survivorId, gameEngine.lines.single().id, "Other lines must be preserved")
    }

    @Test
    fun `test eraseLine returns false for unknown id`() {
        gameEngine.startNewLine(10.0, 10.0)
        gameEngine.finishCurrentLine()

        val erased = gameEngine.eraseLine("non-existent-id")

        assertFalse(erased, "eraseLine must return false when no line matches")
        assertEquals(1, gameEngine.lines.size, "Unknown id must not affect existing lines")
    }

    @Test
    fun `test eraseLine on empty list is a no-op`() {
        assertFalse(gameEngine.eraseLine("any-id"))
        assertEquals(0, gameEngine.lines.size)
    }

    @Test
    fun `test eraseLine rejects blank id`() {
        gameEngine.startNewLine(10.0, 10.0)
        gameEngine.finishCurrentLine()

        assertFalse(gameEngine.eraseLine(""))
        assertFalse(gameEngine.eraseLine("   "))
        assertEquals(1, gameEngine.lines.size)
    }

    @Test
    fun `test eraseLine clears in-progress currentLine`() {
        gameEngine.startNewLine(10.0, 10.0)
        gameEngine.updateCurrentLine(15.0, 15.0)
        val inProgressId = gameEngine.lines.single().id

        val erased = gameEngine.eraseLine(inProgressId)

        assertTrue(erased)
        assertEquals(0, gameEngine.lines.size)
        gameEngine.updateCurrentLine(20.0, 20.0)
        assertEquals(0, gameEngine.lines.size)
    }

    @Test
    fun `test line id round-trips through proto serialization`() {
        gameEngine.startNewLine(10.0, 10.0)
        gameEngine.updateCurrentLine(20.0, 20.0)
        gameEngine.finishCurrentLine()
        val originalId = gameEngine.lines.single().id

        val snapshot = gameEngine.toGameStateDelta()
        gameEngine.clearLines()
        gameEngine.restoreFromDelta(snapshot)

        assertEquals(1, gameEngine.lines.size)
        assertEquals(originalId, gameEngine.lines.single().id, "Line id must survive serialization")
    }

    @Test
    fun `test restoreFromDelta synthesizes id for legacy snapshot lines`() {
        val legacy =
            ru.rkhamatyarov.proto.GameStateDelta
                .newBuilder()
                .addLines(
                    ru.rkhamatyarov.proto.Line
                        .newBuilder()
                        .setWidth(5.0)
                        .addPoints(
                            ru.rkhamatyarov.proto.Point
                                .newBuilder()
                                .setX(0.0)
                                .setY(0.0),
                        ).addPoints(
                            ru.rkhamatyarov.proto.Point
                                .newBuilder()
                                .setX(10.0)
                                .setY(10.0),
                        ),
                ).build()

        gameEngine.restoreFromDelta(legacy)

        val restored = gameEngine.lines.single()
        assertNotNull(restored.id)
        assertTrue(restored.id.isNotBlank(), "Legacy lines must get a synthesized id")
    }

    @Test
    fun `progressive multiplier equals base when no elapsed time or lines`() {
        assertEquals(1.0, gameEngine.computeProgressiveSpeedMultiplier(), 0.0001)
    }

    @Test
    fun `progressive multiplier increases with elapsed time`() {
        gameEngine.elapsedSeconds = 60.0
        val expected = 1.0 + (60.0 / 60.0) * 0.05
        assertEquals(expected, gameEngine.computeProgressiveSpeedMultiplier(), 0.0001)
    }

    @Test
    fun `progressive multiplier increases with lines on whiteboard`() {
        repeat(5) {
            gameEngine.startNewLine(10.0 * it + 10.0, 10.0)
            gameEngine.updateCurrentLine(10.0 * it + 20.0, 20.0)
            gameEngine.finishCurrentLine()
        }
        val expected = 1.0 + 5 * 0.02
        assertEquals(expected, gameEngine.computeProgressiveSpeedMultiplier(), 0.0001)
    }

    @Test
    fun `progressive multiplier combines time and level factors`() {
        gameEngine.elapsedSeconds = 120.0
        repeat(3) {
            gameEngine.startNewLine(10.0 * it + 10.0, 10.0)
            gameEngine.updateCurrentLine(10.0 * it + 20.0, 20.0)
            gameEngine.finishCurrentLine()
        }
        val expected = 1.0 + (120.0 / 60.0) * 0.05 + 3 * 0.02
        assertEquals(expected, gameEngine.computeProgressiveSpeedMultiplier(), 0.0001)
    }

    @Test
    fun `progressive multiplier is capped at maxMultiplier`() {
        gameEngine.elapsedSeconds = 100_000.0
        assertEquals(gameEngine.speedConfig.maxMultiplier, gameEngine.computeProgressiveSpeedMultiplier(), 0.0001)
    }

    @Test
    fun `custom base multiplier applied from speed config`() {
        gameEngine.speedConfig =
            SpeedConfig(baseMultiplier = 2.0, timeAccelerationRate = 0.0, levelAccelerationPerLine = 0.0, maxMultiplier = 5.0)
        assertEquals(2.0, gameEngine.computeProgressiveSpeedMultiplier(), 0.0001)
    }

    @Test
    fun `zero time acceleration rate keeps multiplier at base regardless of elapsed time`() {
        gameEngine.speedConfig = SpeedConfig(timeAccelerationRate = 0.0)
        gameEngine.elapsedSeconds = 3600.0
        assertEquals(1.0, gameEngine.computeProgressiveSpeedMultiplier(), 0.0001)
    }

    @Test
    fun `zero level acceleration keeps multiplier at base regardless of line count`() {
        gameEngine.speedConfig = SpeedConfig(levelAccelerationPerLine = 0.0)
        repeat(10) {
            gameEngine.startNewLine(10.0 * it + 10.0, 10.0)
            gameEngine.updateCurrentLine(10.0 * it + 20.0, 20.0)
            gameEngine.finishCurrentLine()
        }
        assertEquals(1.0, gameEngine.computeProgressiveSpeedMultiplier(), 0.0001)
    }

    @Test
    fun `tick advances elapsedSeconds when not paused`() {
        gameEngine.paused = false
        val before = gameEngine.elapsedSeconds
        gameEngine.tick(1.0)
        assertEquals(before + 1.0, gameEngine.elapsedSeconds, 0.0001)
    }

    @Test
    fun `tick does NOT advance elapsedSeconds when paused`() {
        gameEngine.paused = true
        val before = gameEngine.elapsedSeconds
        gameEngine.tick(1.0)
        assertEquals(before, gameEngine.elapsedSeconds, 0.0001)
    }

    @Test
    fun `puck moves faster with higher elapsed time`() {
        gameEngine.puck.vx = 100.0
        gameEngine.puck.vy = 0.0
        gameEngine.puck.x = 200.0
        gameEngine.puck.y = gameEngine.canvasHeight / 2

        gameEngine.tick(0.1)
        val xAfterBaseSpeed = gameEngine.puck.x

        gameEngine.puck.x = 200.0
        gameEngine.elapsedSeconds = 600.0
        gameEngine.tick(0.1)
        val xAfterFasterSpeed = gameEngine.puck.x

        assertTrue(xAfterFasterSpeed > xAfterBaseSpeed, "Puck should move further with higher elapsed time")
    }

    @Test
    fun `puck moves faster with more lines on whiteboard`() {
        gameEngine.puck.vx = 100.0
        gameEngine.puck.vy = 0.0
        gameEngine.puck.x = 200.0
        gameEngine.puck.y = gameEngine.canvasHeight / 2

        gameEngine.tick(0.1)
        val xWithNoLines = gameEngine.puck.x

        gameEngine.puck.x = 200.0
        gameEngine.elapsedSeconds = 0.0
        repeat(10) {
            gameEngine.startNewLine(10.0 * it + 10.0, 10.0)
            gameEngine.updateCurrentLine(10.0 * it + 20.0, 20.0)
            gameEngine.finishCurrentLine()
        }
        gameEngine.tick(0.1)
        val xWithLines = gameEngine.puck.x

        assertTrue(xWithLines > xWithNoLines, "Puck should move further with more whiteboard lines")
    }

    @Test
    fun `test restoreFromDelta branches game state from snapshot`() {
        gameEngine.puck.x = 123.0
        gameEngine.puck.y = 234.0
        gameEngine.puck.vx = -321.0
        gameEngine.puck.vy = 111.0
        gameEngine.paddle1Y = 44.0
        gameEngine.paddle2Y = 333.0
        gameEngine.score.playerA = 2
        gameEngine.score.playerB = 5
        gameEngine.paused = true
        gameEngine.lines.add(
            Line(
                controlPoints = mutableListOf(Point(10.0, 10.0), Point(20.0, 20.0)),
                flattenedPoints = mutableListOf(Point(10.0, 10.0), Point(20.0, 20.0)),
            ),
        )
        gameEngine.powerUps.add(PowerUp(300.0, 200.0, PowerUpType.SPEED_BOOST))
        val snapshot = gameEngine.toGameStateDelta()

        gameEngine.puck.x = 700.0
        gameEngine.puck.y = 500.0
        gameEngine.score.playerA = 99
        gameEngine.lines.clear()
        gameEngine.powerUps.clear()
        gameEngine.paused = false

        gameEngine.restoreFromDelta(snapshot)

        assertEquals(123.0, gameEngine.puck.x, 0.0001)
        assertEquals(234.0, gameEngine.puck.y, 0.0001)
        assertEquals(-321.0, gameEngine.puck.vx, 0.0001)
        assertEquals(111.0, gameEngine.puck.vy, 0.0001)
        assertEquals(44.0, gameEngine.paddle1Y, 0.0001)
        assertEquals(333.0, gameEngine.paddle2Y, 0.0001)
        assertEquals(2, gameEngine.score.playerA)
        assertEquals(5, gameEngine.score.playerB)
        assertTrue(gameEngine.paused)
        assertEquals(1, gameEngine.lines.size)
        assertEquals(1, gameEngine.powerUps.size)
        assertEquals(PowerUpType.SPEED_BOOST, gameEngine.powerUps.first().type)
    }
}
