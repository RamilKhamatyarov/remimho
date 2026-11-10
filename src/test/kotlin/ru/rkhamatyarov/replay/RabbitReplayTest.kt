package ru.rkhamatyarov.replay

import org.junit.jupiter.api.Test
import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.proto.ReplayFile
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.GameIntent
import ru.rkhamatyarov.service.mvi.MviActivePowerUp
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviPowerUp
import ru.rkhamatyarov.service.mvi.MviPuck
import ru.rkhamatyarov.service.mvi.TouchSource
import ru.rkhamatyarov.service.mvi.reduce
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RabbitReplayTest {
    @Test
    fun `rabbit replay matches live reduction exactly`() {
        val start = MviGameState(puck = MviPuck(vx = 30.0, vy = 0.0))
        val actions =
            listOf(GameAction.SpawnPowerUp(MviPowerUp("rabbit", 400.0, 300.0, PowerUpType.RABBIT_MODE, 0L))) +
                (1..400).map { GameAction.Tick(0.016, it * 16_000_000L) }
        val live = actions.fold(start, ::reduce)
        val replay = replay("2", start, actions)
        val importer = HeadlessReplayImporter()
        assertEquals(live, importer.import(replay).finalState)
        assertEquals(live, importer.import(ReplayFile.parseFrom(replay.toByteArray())).finalState)
    }

    @Test
    fun `only legacy replay versions reproduce ghost pass through on both sides`() {
        for (left in listOf(true, false)) {
            val start =
                MviGameState(
                    puck = MviPuck(x = if (left) 40.0 else 760.0, vx = if (left) -2000.0 else 2000.0, vy = 0.0),
                    ghostMode = true,
                    activePowerUps = listOf(MviActivePowerUp(PowerUpType.GHOST_MODE, 0L, 6_000_000_000L)),
                )
            val actions = listOf(GameAction.Tick(0.032, 32_000_000L))
            for (version in listOf("", "1")) {
                val old = HeadlessReplayImporter().import(replay(version, start, actions)).finalState
                assertEquals(1, old.score.playerA + old.score.playerB)
                assertTrue(old.ghostMode)
            }
            val current = HeadlessReplayImporter().import(replay("2", start, actions)).finalState
            assertEquals(start.score, current.score)
            assertTrue(current.touchLedger.entries.any { it.source == TouchSource.PADDLE })
        }
    }

    @Test
    fun `legacy ghost pickup intents still activate the historical effect`() {
        val start = MviGameState(puck = MviPuck(vx = 0.0, vy = 0.0))
        val actions =
            listOf(
                GameAction.SpawnPowerUp(MviPowerUp("ghost", 400.0, 300.0, PowerUpType.GHOST_MODE, 0L)),
                GameAction.Tick(0.016, 16_000_000L),
            )
        val legacy = HeadlessReplayImporter().import(replay("1", start, actions)).finalState
        assertTrue(legacy.ghostMode)
        assertEquals(PowerUpType.GHOST_MODE, legacy.activePowerUps.single().type)
    }

    @Test
    fun `unsupported replay rules fail explicitly`() {
        assertFailsWith<IllegalArgumentException> {
            HeadlessReplayImporter().import(replay("4", MviGameState(), emptyList()))
        }
    }

    @Test
    fun `current shield saves do not change historical shield scoring`() {
        val start =
            MviGameState(
                puck = MviPuck(x = 25.0, y = 100.0, vx = -2000.0, vy = 0.0),
                paddleShield = true,
                activePowerUps = listOf(MviActivePowerUp(PowerUpType.PADDLE_SHIELD, 0L, 10_000_000_000L)),
            )
        val actions = listOf(GameAction.Tick(0.016, 16_000_000L))
        val importer = HeadlessReplayImporter()
        assertEquals(1, importer.import(replay("1", start, actions)).finalState.score.playerB)
        val current = importer.import(replay("2", start, actions)).finalState
        assertEquals(start.score, current.score)
        assertTrue(current.puck.vx > 0.0)
    }

    private fun replay(
        version: String,
        start: MviGameState,
        actions: List<GameAction>,
    ): ReplayFile =
        ReplayFile.newBuilder()
            .setVersion(version)
            .setStartingState(ReplayConverter.stateToSnapshot(start))
            .addAllIntents(actions.mapNotNull { ReplayConverter.toProto(GameIntent.Reliable(it)) })
            .build()
}
