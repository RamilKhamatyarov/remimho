package ru.rkhamatyarov.service

import org.junit.jupiter.api.Test
import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviPowerUp
import ru.rkhamatyarov.service.mvi.MviPuck
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PowerUpEscalationStrategyTest {
    @Test
    fun `does not spawn before first logical threshold`() {
        val strategy = PowerUpEscalationStrategy(roomId = "room-a")

        val spawn = strategy.nextSpawn(MviGameState(), elapsedNs = 9_999_999_999L)

        assertNull(spawn)
    }

    @Test
    fun `spawns deterministically at logical threshold`() {
        val first = PowerUpEscalationStrategy(roomId = "room-a")
        val second = PowerUpEscalationStrategy(roomId = "room-a")

        val firstSpawn = first.nextSpawn(MviGameState(), elapsedNs = 10_000_000_000L)
        val secondSpawn = second.nextSpawn(MviGameState(), elapsedNs = 10_000_000_000L)

        assertNotNull(firstSpawn)
        assertEquals(firstSpawn, secondSpawn)
        assertTrue(firstSpawn.type in listOf(PowerUpType.SPEED_BOOST, PowerUpType.MAGNET_BALL))
    }

    @Test
    fun `skips capped spawn without banking it`() {
        val strategy = PowerUpEscalationStrategy(roomId = "room-a")
        val cappedState =
            MviGameState(
                powerUps =
                    listOf(
                        MviPowerUp("one", 100.0, 100.0, PowerUpType.SPEED_BOOST, 0L),
                        MviPowerUp("two", 200.0, 100.0, PowerUpType.MAGNET_BALL, 0L),
                    ),
            )

        val cappedSpawn = strategy.nextSpawn(cappedState, elapsedNs = 10_000_000_000L)
        val immediateAfterCap = strategy.nextSpawn(MviGameState(), elapsedNs = 11_000_000_000L)
        val nextScheduled = strategy.nextSpawn(MviGameState(), elapsedNs = 20_000_000_000L)

        assertNull(cappedSpawn)
        assertNull(immediateAfterCap)
        assertNotNull(nextScheduled)
    }

    @Test
    fun `late tier allows higher board density`() {
        val strategy = PowerUpEscalationStrategy(roomId = "room-a")
        val state =
            MviGameState(
                powerUps =
                    listOf(
                        MviPowerUp("one", 100.0, 100.0, PowerUpType.SPEED_BOOST, 0L),
                        MviPowerUp("two", 200.0, 100.0, PowerUpType.MAGNET_BALL, 0L),
                        MviPowerUp("three", 300.0, 100.0, PowerUpType.GHOST_MODE, 0L),
                    ),
            )

        val spawn = strategy.nextSpawn(state, elapsedNs = 120_000_000_000L)

        assertNotNull(spawn)
    }

    @Test
    fun `safe position selector can decline a crowded board`() {
        val crowdedState =
            MviGameState(
                puck = MviPuck(x = 400.0, y = 300.0),
                canvasWidth = 120.0,
                canvasHeight = 120.0,
                powerUps =
                    listOf(
                        MviPowerUp("one", 60.0, 60.0, PowerUpType.SPEED_BOOST, 0L),
                    ),
            )

        val spawn = createDeterministicPowerUp(PowerUpType.SPEED_BOOST, crowdedState, elapsedNs = 10_000_000_000L)

        assertNull(spawn)
    }
}
