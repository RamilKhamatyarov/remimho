package ru.rkhamatyarov.service.mvi

import org.junit.jupiter.api.Test
import ru.rkhamatyarov.mapping.proto.mviStateFromDelta
import ru.rkhamatyarov.mapping.proto.toDelta
import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.model.SpeedConfig
import ru.rkhamatyarov.replay.ReplayConverter
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RabbitModeTest {
    private val effect = MviActivePowerUp(PowerUpType.RABBIT_MODE, 0L, 6_000_000_000L)
    private val puck = MviPuck(vx = 100.0, vy = 0.0)

    @Test
    fun `turns alternate every 300 milliseconds without changing speed`() {
        val before = RabbitPhysics.apply(puck, listOf(effect), 299_999_999L)
        assertEquals(puck, before.puck)
        val first = RabbitPhysics.apply(before.puck, before.active, 300_000_000L)
        assertEquals(PI / 12.0, atan2(first.puck.vy, first.puck.vx), 1e-12)
        assertEquals(100.0, hypot(first.puck.vx, first.puck.vy), 1e-12)
        assertEquals(first, RabbitPhysics.apply(first.puck, first.active, 300_000_000L))
        val second = RabbitPhysics.apply(first.puck, first.active, 600_000_000L)
        assertEquals(0.0, atan2(second.puck.vy, second.puck.vx), 1e-12)
        assertEquals(100.0, hypot(second.puck.vx, second.puck.vy), 1e-12)
        assertEquals(2L, second.active.single().rabbitTurnsApplied)
    }

    @Test
    fun `missed intervals apply only their net turn`() {
        val odd = RabbitPhysics.apply(puck, listOf(effect), 900_000_000L)
        assertEquals(PI / 12.0, atan2(odd.puck.vy, odd.puck.vx), 1e-12)
        assertEquals(3L, odd.active.single().rabbitTurnsApplied)
        val even = RabbitPhysics.apply(puck, listOf(effect), 1_200_000_000L)
        assertEquals(puck, even.puck)
        assertEquals(4L, even.active.single().rabbitTurnsApplied)
    }

    @Test
    fun `collection refreshes one effect without stacking strength`() {
        val state =
            state().copy(
                activePowerUps = listOf(effect.copy(rabbitTurnsApplied = 3)),
                powerUps = listOf(pickup("first"), pickup("second")),
            )
        val next = reduce(state, GameAction.Tick(0.001, 1_000_000_000L))
        assertTrue(next.powerUps.isEmpty())
        assertEquals(listOf(effect.copy(activatedNs = 1_000_000_000L)), next.activePowerUps)
        assertEquals(100.0, next.puck.vx)
        assertEquals(0.0, next.puck.vy)
        assertEquals(2, next.touchLedger.entries.count { it.source == TouchSource.POWER_UP })
    }

    @Test
    fun `expiry is exact and pause and reset do not advance rabbit turns`() {
        val state = state()
        val expired = reduce(state, GameAction.Tick(0.001, 6_000_000_000L))
        assertTrue(expired.activePowerUps.isEmpty())
        assertEquals(state.puck.vx, expired.puck.vx)
        val paused = state.copy(paused = true)
        assertEquals(paused, reduce(paused, GameAction.Tick(0.016, 900_000_000L)))
        assertTrue(reduce(state, GameAction.Reset).activePowerUps.isEmpty())
        assertEquals(state, reduce(state, GameAction.Tick(0.0, 900_000_000L)))
    }

    @Test
    fun `rabbit and stale ghost evidence never disable either live paddle`() {
        for (side in PaddleSide.entries) {
            val direction = if (side == PaddleSide.A) -1.0 else 1.0
            val state =
                state().copy(
                    puck = MviPuck(x = if (side == PaddleSide.A) 50.0 else 750.0, vx = direction * 2000.0, vy = 0.0),
                    ghostMode = true,
                    activePowerUps = listOf(effect, effect.copy(type = PowerUpType.GHOST_MODE)),
                )
            val next = reduce(state, GameAction.Tick(0.016, 300_000_000L))
            assertEquals(state.score, next.score, side.name)
            assertTrue(next.puck.vx * direction < 0.0, side.name)
            assertTrue(next.touchLedger.entries.any { it.source == TouchSource.PADDLE && it.ownerSide == side })
            assertFalse(next.ghostMode)
            assertTrue(next.activePowerUps.none { it.type == PowerUpType.GHOST_MODE })
        }
    }

    @Test
    fun `live restore and spawn cannot reintroduce ghost mode`() {
        val ghost = pickup("ghost").copy(type = PowerUpType.GHOST_MODE)
        val state = state()
        assertEquals(state, reduce(state, GameAction.SpawnPowerUp(ghost)))
        val legacy =
            state.copy(
                ghostMode = true,
                powerUps = listOf(ghost),
                activePowerUps = listOf(effect.copy(type = PowerUpType.GHOST_MODE)),
            )
        val restored = reduce(state, GameAction.RestoreSnapshot(legacy))
        assertFalse(restored.ghostMode)
        assertTrue(restored.powerUps.isEmpty())
        assertTrue(restored.activePowerUps.isEmpty())
    }

    @Test
    fun `history and full snapshots preserve rabbit phase for continuation`() {
        val before = reduce(state(), GameAction.Tick(0.3, 300_000_000L))
        val full = ReplayConverter.snapshotToState(ReplayConverter.stateToSnapshot(before))
        val history = mviStateFromDelta(before.toDelta())
        assertEquals(before.activePowerUps, full.activePowerUps)
        assertEquals(before.activePowerUps, history.activePowerUps)
        val tick = GameAction.Tick(0.3, 600_000_000L)
        assertEquals(reduce(before, tick), reduce(full, tick))
        assertEquals(reduce(before, tick), reduce(history, tick))
    }

    @Test
    fun `rabbit countdown keeps fractional seconds on the wire`() {
        val delta = state().copy(elapsedSeconds = 5.75).toDelta()
        assertEquals(250L, delta.activePowerUpsList.single().remainingMs)
    }

    @Test
    fun `rabbit preserves wall line and shield contacts`() {
        val wall = state().copy(puck = MviPuck(x = 400.0, y = 20.0, vx = 0.0, vy = -1000.0))
        val wallHit = reduce(wall, GameAction.Tick(0.016, 300_000_000L))
        assertTrue(wallHit.puck.vy > 0.0)
        assertTrue(wallHit.touchLedger.entries.any { it.source == TouchSource.WALL })
        val line =
            state().copy(
                lines = listOf(MviLine("line", listOf(MviPoint(410.0, 200.0), MviPoint(410.0, 400.0)))),
            )
        val lineHit = reduce(line, GameAction.Tick(0.016, 300_000_000L))
        assertTrue(lineHit.puck.vx < 0.0)
        assertTrue(lineHit.touchLedger.entries.any { it.source == TouchSource.DRAWN_LINE })
        val shield =
            state().copy(
                puck = MviPuck(x = 25.0, y = 100.0, vx = -2000.0, vy = 0.0),
                paddleShield = true,
                activePowerUps = listOf(effect, MviActivePowerUp(PowerUpType.PADDLE_SHIELD, 0L, 10_000_000_000L)),
            )
        val shieldHit = reduce(shield, GameAction.Tick(0.016, 300_000_000L))
        assertEquals(shield.score, shieldHit.score)
        assertTrue(shieldHit.puck.vx > 0.0)
    }

    @Test
    fun `scoring retains active rabbit timing for the next serve`() {
        val before = state().copy(puck = MviPuck(x = 799.0, y = 100.0, vx = 100.0, vy = 0.0))
        val after = reduce(before, GameAction.Tick(0.016, 16_000_000L))
        assertEquals(before.score.playerA + 1, after.score.playerA)
        assertEquals(before.activePowerUps, after.activePowerUps)
    }

    private fun state(): MviGameState =
        MviGameState(
            puck = puck,
            activePowerUps = listOf(effect),
            speedConfig = SpeedConfig(timeAccelerationRate = 0.0),
        )

    private fun pickup(id: String): MviPowerUp = MviPowerUp(id, puck.x, puck.y, PowerUpType.RABBIT_MODE, 0L)
}
