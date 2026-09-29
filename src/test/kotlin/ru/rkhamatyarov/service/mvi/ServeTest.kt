package ru.rkhamatyarov.service.mvi

import org.junit.jupiter.api.Test
import ru.rkhamatyarov.mapping.proto.mviStateFromDelta
import ru.rkhamatyarov.mapping.proto.toDelta
import ru.rkhamatyarov.proto.ReplayFile
import ru.rkhamatyarov.replay.HeadlessReplayImporter
import ru.rkhamatyarov.replay.ReplayConverter
import kotlin.test.assertEquals

class ServeTest {
    @Test
    fun `restarts alternate both directions independently of rally velocity`() {
        var state = MviGameState()
        repeat(4) { index ->
            state = reduce(state.copy(puck = state.puck.copy(vx = -950.0, vy = 760.0)), GameAction.Reset)

            assertServe(state, if (index % 2 == 0) PaddleSide.A else PaddleSide.B)
        }
    }

    @Test
    fun `goals alternate both directions regardless of who scores`() {
        var state = MviGameState()
        repeat(4) { index ->
            state =
                reduce(
                    state.copy(puck = MviPuck(x = 795.0, y = 100.0, vx = 300.0, vy = 0.0)),
                    GameAction.Tick(0.016, (index + 1) * 16_000_000L),
                )

            assertEquals(index + 1, state.score.playerA)
            assertServe(state, if (index % 2 == 0) PaddleSide.A else PaddleSide.B)
        }
    }

    @Test
    fun `two point goal advances the serve only once`() {
        val contacts =
            listOf(
                PuckTouch(TouchSource.PADDLE, PaddleSide.A, "paddle:A", 1L, 600.0),
                PuckTouch(TouchSource.DRAWN_LINE, PaddleSide.A, "line-1", 2L, 600.0),
                PuckTouch(TouchSource.PADDLE, PaddleSide.A, "paddle:A", 3L, 600.0),
                PuckTouch(TouchSource.DRAWN_LINE, PaddleSide.A, "line-2", 4L, 600.0),
            )
        val state =
            MviGameState(
                puck = MviPuck(x = 795.0, y = 100.0, vx = 300.0, vy = 0.0),
                touchLedger = TouchLedger(contacts),
            )

        val next = reduce(state, GameAction.Tick(0.016, 16_000_000L))

        assertEquals(2, next.score.playerA)
        assertServe(next, PaddleSide.A)
    }

    @Test
    fun `ordinary ticks and pause do not change serve side`() {
        val state = reduce(MviGameState(), GameAction.Reset)
        val advanced = reduce(state, GameAction.Tick(0.016, 16_000_000L))
        val paused = reduce(advanced, GameAction.TogglePause)

        assertEquals(PaddleSide.A, reduce(paused, GameAction.Tick(0.016, 32_000_000L)).serveSide)
    }

    @Test
    fun `history and replay snapshots preserve next serve after rally direction changes`() {
        val state =
            reduce(MviGameState(), GameAction.Reset)
                .copy(puck = MviPuck(vx = 600.0, vy = 400.0))
        val history = mviStateFromDelta(state.toDelta())
        val replay = ReplayConverter.snapshotToState(ReplayConverter.stateToSnapshot(state))

        assertServe(reduce(history, GameAction.Reset), PaddleSide.B)
        assertServe(reduce(replay, GameAction.Reset), PaddleSide.B)
        val restored = reduce(MviGameState(), GameAction.RestoreSnapshot(state))
        assertServe(reduce(restored, GameAction.Reset), PaddleSide.B)
    }

    @Test
    fun `legacy snapshots default to the original opening side`() {
        val state = MviGameState()
        val history = state.toDelta().toBuilder().clearServeSide().build()
        val replay = ReplayConverter.stateToSnapshot(state).toBuilder().clearServeSide().build()

        assertEquals(PaddleSide.B, mviStateFromDelta(history).serveSide)
        assertEquals(PaddleSide.B, ReplayConverter.snapshotToState(replay).serveSide)
    }

    @Test
    fun `headless replay reproduces goals and repeated restarts`() {
        val initial = MviGameState(puck = MviPuck(x = 795.0, y = 100.0, vx = 300.0, vy = 0.0))
        val actions =
            listOf(
                GameAction.Tick(0.016, 16_000_000L),
                GameAction.Reset,
                GameAction.Tick(0.016, 16_000_000L),
                GameAction.Reset,
            )
        val live = actions.fold(initial, ::reduce)
        val replay =
            ReplayFile.newBuilder()
                .setStartingState(ReplayConverter.stateToSnapshot(initial))
                .addAllIntents(actions.map { ReplayConverter.toProto(GameIntent.Reliable(it))!! })
                .build()

        val importer = HeadlessReplayImporter()
        assertEquals(live, importer.import(replay).finalState)
        assertEquals(live, importer.import(replay).finalState)
    }

    private fun assertServe(
        state: MviGameState,
        side: PaddleSide,
    ) {
        val direction = if (side == PaddleSide.B) 1.0 else -1.0
        assertEquals(side, state.serveSide)
        assertEquals(300.0 * direction, state.puck.vx)
        assertEquals(200.0 * direction, state.puck.vy)
        assertEquals(state.canvasWidth / 2.0, state.puck.x)
        assertEquals(state.canvasHeight / 2.0, state.puck.y)
    }
}
