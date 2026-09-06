package ru.rkhamatyarov.service.mvi

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.rkhamatyarov.mapping.proto.mviStateFromDelta
import ru.rkhamatyarov.mapping.proto.toDelta
import ru.rkhamatyarov.mapping.proto.toProto
import ru.rkhamatyarov.proto.GameStateDelta
import ru.rkhamatyarov.proto.ReplayFile
import ru.rkhamatyarov.replay.HeadlessReplayImporter
import ru.rkhamatyarov.replay.ReplayConverter
import ru.rkhamatyarov.service.GameRoom
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ComboReplayTest {
    @Test
    fun `recorded paddle line paddle rally derives combo without seeded touches`() {
        val line =
            MviLine(
                "wall-pass",
                listOf(MviPoint(100.0, 200.0), MviPoint(100.0, 400.0)),
                ownerSide = PaddleSide.A,
            )
        val initial = returnState().copy(touchLedger = TouchLedger(), lines = listOf(line))
        val actions =
            listOf(
                GameAction.Tick(0.01, 1_000_000_000L),
                GameAction.Tick(0.3, 1_300_000_000L),
                GameAction.Tick(0.3, 1_600_000_000L),
            )
        val live = MviDomainEvents.capture { actions.fold(initial, ::reduce) }
        val replay =
            ReplayFile
                .newBuilder()
                .setStartingState(ReplayConverter.stateToSnapshot(initial))
                .addAllIntents(actions.map { ReplayConverter.toProto(GameIntent.Reliable(it))!! })
                .build()
        val imported = HeadlessReplayImporter().import(replay)
        assertEquals(live.value, imported.finalState)
        assertEquals(listOf(MviDomainEvent.GiveAndGoCompleted(PaddleSide.A)), imported.comboHighlights)
        assertEquals(3, imported.finalState.touchLedger.entries.size)
    }

    @Test
    fun `headless replay reproduces both combo rewards and highlights`() {
        for (initial in listOf(returnState(), goalState())) {
            val live = MviDomainEvents.capture { reduce(initial, tick()) }
            val replay = replay(initial)
            val decoded = ReplayFile.parseFrom(replay.toByteArray())
            val imported = HeadlessReplayImporter().import(decoded)
            assertEquals(live.value, imported.finalState)
            assertEquals(live.events.filter { it !is MviDomainEvent.PaddleHit }, imported.comboHighlights)
            assertEquals(1, decoded.intentsCount)
            assertEquals(imported, HeadlessReplayImporter().import(decoded))
        }
    }

    @Test
    fun `custom balance survives export and overrides defaults`() {
        val config = Combo(giveAndGoMultiplier = 1.2, maximumRawSpeed = 700.0, superGoalThreshold = 5)
        for (initial in listOf(returnState(), goalState())) {
            val state = initial.copy(combo = config)
            val imported = HeadlessReplayImporter().import(replay(state))
            assertEquals(reduce(state, tick()), imported.finalState)
            assertEquals(config, imported.finalState.combo)
        }
        val file = ReplayFile.newBuilder().setCombo(config.toProto()).build()
        assertEquals(config, HeadlessReplayImporter().import(file).finalState.combo)
    }

    @Test
    fun `legacy replays do not acquire new physics or scoring`() {
        for (initial in listOf(returnState(), goalState())) {
            val legacy =
                replay(initial)
                    .toBuilder()
                    .setStartingState(ReplayConverter.stateToSnapshot(initial).toBuilder().clearCombo())
                    .clearCombo()
                    .build()
            val result = HeadlessReplayImporter().import(legacy)
            assertEquals(reduce(initial.copy(combo = Combo.DISABLED), tick()), result.finalState)
            assertTrue(result.comboHighlights.isEmpty())
        }
        val empty = HeadlessReplayImporter().import(ReplayFile.getDefaultInstance())
        assertFalse(empty.finalState.combo.enabled)
    }

    @Test
    fun `history delta restores balance ledger and ownership`() {
        val state =
            returnState().copy(
                combo = Combo(giveAndGoMultiplier = 1.8, giveAndGoWindowNs = 5_000_000_000L),
                lines = listOf(MviLine("owned", listOf(MviPoint(200.0, 100.0)), ownerSide = PaddleSide.A)),
            )
        val restored = mviStateFromDelta(GameStateDelta.parseFrom(state.toDelta().toByteArray()))
        assertEquals(state.combo, restored.combo)
        assertEquals(state.touchLedger, restored.touchLedger)
        assertEquals(state.lines, restored.lines)
        assertEquals(
            MviDomainEvents.capture { reduce(state, tick()) },
            MviDomainEvents.capture { reduce(restored, tick()) },
        )
        assertFalse(
            mviStateFromDelta(
                state
                    .toDelta()
                    .toBuilder()
                    .clearCombo()
                    .build(),
            ).combo.enabled,
        )
    }

    @Test
    fun `actor delivers combo feedback without appending highlight intents`() =
        runTest {
            for (initial in listOf(returnState(), goalState())) {
                val scope = TestScope(StandardTestDispatcher(testScheduler))
                val room = GameRoom("combo-test", scope = scope, autoPowerUpsEnabled = false)
                room.registerHumanSide(PaddleSide.A)
                val events = mutableListOf<EphemeralEvent>()
                val collector =
                    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                        room.ephemeralEvents.collect { events += it }
                    }
                try {
                    val restore = GameIntent.Reliable(GameAction.RestoreSnapshot(initial))
                    val advance = GameIntent.Reliable(tick().copy(playerAControlledByHuman = true))
                    room.dispatch(restore)
                    room.dispatch(advance)
                    runCurrent()
                    assertEquals(listOf(restore, advance), room.getReplayLog())
                    assertEquals(reduce(initial, tick()), room.reliableState.value)
                    val expected =
                        if (initial.touchLedger.entries.size == 2) {
                            EphemeralEvent.GiveAndGoCompleted(PaddleSide.A)
                        } else {
                            EphemeralEvent.SuperGoalScored(PaddleSide.A, 4)
                        }
                    assertEquals(listOf(expected), events)
                } finally {
                    collector.cancel()
                    room.shutdown()
                }
            }
        }

    private fun replay(state: MviGameState): ReplayFile =
        ReplayFile
            .newBuilder()
            .setStartingState(ReplayConverter.stateToSnapshot(state))
            .addIntents(ReplayConverter.toProto(GameIntent.Reliable(tick()))!!)
            .build()
}
