package ru.rkhamatyarov.service.mvi

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.filter
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.math.hypot
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MviInvariantsTest {
    @Test
    fun `puck never escapes canvas bounds after many ticks`() {
        runBlocking {
            checkAll(
                iterations = 500,
                genA = Arb.double(-500.0, 500.0).filter { it.isFinite() },
                genB = Arb.double(-500.0, 500.0).filter { it.isFinite() },
            ) { vx, vy ->
                var state = MviGameState(puck = MviPuck(x = 400.0, y = 300.0, vx = vx, vy = vy))
                repeat(120) {
                    state = reduce(state, GameAction.Tick(0.016))
                }
                assertTrue(
                    state.puck.x >= 0.0 && state.puck.x <= state.canvasWidth,
                    "puck.x=${state.puck.x} escaped [0, ${state.canvasWidth}]",
                )
                assertTrue(
                    state.puck.y >= 0.0 && state.puck.y <= state.canvasHeight,
                    "puck.y=${state.puck.y} escaped [0, ${state.canvasHeight}]",
                )
            }
        }
    }

    @Test
    fun `reducer is deterministic same input same output`() {
        runBlocking {
            checkAll(
                iterations = 300,
                genA = Arb.double(0.001, 0.1).filter { it.isFinite() },
            ) { delta ->
                val state = MviGameState()
                val first = reduce(state, GameAction.Tick(delta))
                val second = reduce(state, GameAction.Tick(delta))
                assertEquals(first, second, "reduce is not deterministic for delta=$delta")
            }
        }
    }

    @Test
    fun `teleport translates puck to partner line midpoint`() {
        val lineA = MviLine("portal-a", listOf(MviPoint(100.0, 290.0), MviPoint(100.0, 310.0)))
        val lineB = MviLine("portal-b", listOf(MviPoint(700.0, 290.0), MviPoint(700.0, 310.0)))
        val state =
            MviGameState(
                puck = MviPuck(x = 105.0, y = 300.0, vx = -200.0, vy = 0.0),
                lines = listOf(lineA, lineB),
                teleports = mapOf("portal-a" to "portal-b", "portal-b" to "portal-a"),
            )

        val next = reduce(state, GameAction.Tick(0.016))

        assertEquals(700.0, next.puck.x, 2.0)
        assertEquals(300.0, next.puck.y, 2.0)
    }

    @Test
    fun `teleport chain does not cause stack overflow or infinite loop`() {
        val lineA = MviLine("portal-a", listOf(MviPoint(100.0, 290.0), MviPoint(100.0, 310.0)))
        val lineB = MviLine("portal-b", listOf(MviPoint(100.0, 290.0), MviPoint(100.0, 310.0)))
        var state =
            MviGameState(
                puck = MviPuck(x = 105.0, y = 300.0, vx = -200.0, vy = 0.0),
                lines = listOf(lineA, lineB),
                teleports = mapOf("portal-a" to "portal-b", "portal-b" to "portal-a"),
            )

        repeat(10) { index ->
            state = reduce(state, GameAction.Tick(0.016, nowNs = 1_000_000L + index * 16_000_000L))
        }

        assertTrue(state.puck.x.isFinite())
        assertTrue(state.puck.x > 110.0, "puck should move out of the adjacent portal zone")
    }

    @Test
    fun `teleport preserves kinetic energy magnitude`() {
        val lineA = MviLine("portal-a", listOf(MviPoint(100.0, 290.0), MviPoint(100.0, 310.0)))
        val lineB = MviLine("portal-b", listOf(MviPoint(690.0, 300.0), MviPoint(710.0, 300.0)))
        val puck = MviPuck(x = 105.0, y = 300.0, vx = -120.0, vy = 160.0)
        val state =
            MviGameState(
                puck = puck,
                lines = listOf(lineA, lineB),
                teleports = mapOf("portal-a" to "portal-b", "portal-b" to "portal-a"),
            )

        val next = reduce(state, GameAction.Tick(0.016, nowNs = 1_000_000L))

        assertEquals(hypot(puck.vx, puck.vy), hypot(next.puck.vx, next.puck.vy), 0.0001)
    }

    @Test
    fun `teleport missing partner line falls back to reflection`() {
        val orphan = MviLine("orphan", listOf(MviPoint(100.0, 290.0), MviPoint(100.0, 310.0)))
        val state =
            MviGameState(
                puck = MviPuck(x = 105.0, y = 300.0, vx = -200.0, vy = 0.0),
                lines = listOf(orphan),
                teleports = mapOf("orphan" to "nonexistent"),
            )

        val next = reduce(state, GameAction.Tick(0.016))

        assertTrue(next.puck.x < 200.0, "puck should stay near left wall, got x=${next.puck.x}")
    }

    @Test
    fun `apply teleports stores portal map`() {
        val portals = mapOf("a" to "b", "b" to "a")
        val state = MviGameState()

        val next = reduce(state, GameAction.ApplyTeleports(portals))

        assertEquals(portals, next.teleports)
    }

    @Test
    fun `clear lines removes all lines`() {
        val state =
            MviGameState(
                lines =
                    listOf(
                        MviLine("l1", listOf(MviPoint(10.0, 10.0), MviPoint(20.0, 20.0))),
                        MviLine("l2", listOf(MviPoint(30.0, 30.0), MviPoint(40.0, 40.0))),
                    ),
            )

        val next = reduce(state, GameAction.ClearLines)

        assertTrue(next.lines.isEmpty())
    }

    @Test
    fun `restore snapshot replaces state completely`() {
        val target =
            MviGameState(
                puck = MviPuck(x = 111.0, y = 222.0, vx = 0.0, vy = 0.0),
                score = MviScore(playerA = 3, playerB = 5),
            )
        val current = MviGameState()

        val next = reduce(current, GameAction.RestoreSnapshot(target))

        assertEquals(target, next)
    }

    @Test
    fun `line collision deflects velocity`() {
        val wall = MviLine("wall", listOf(MviPoint(200.0, 0.0), MviPoint(200.0, 600.0)))
        val state =
            MviGameState(
                puck = MviPuck(x = 205.0, y = 300.0, vx = -300.0, vy = 0.0),
                lines = listOf(wall),
            )

        val next = reduce(state, GameAction.Tick(0.016))

        assertTrue(next.puck.vx > 0, "puck should reverse x-velocity after hitting vertical wall")
    }
}
