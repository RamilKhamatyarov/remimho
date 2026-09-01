package ru.rkhamatyarov.replay

import org.junit.jupiter.api.Test
import ru.rkhamatyarov.proto.ReplayFile
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.GameIntent
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviPuck
import ru.rkhamatyarov.service.mvi.OneTimerConfig
import ru.rkhamatyarov.service.mvi.PaddleSide
import ru.rkhamatyarov.service.mvi.PuckTouch
import ru.rkhamatyarov.service.mvi.TouchLedger
import ru.rkhamatyarov.service.mvi.TouchSource
import kotlin.math.hypot
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeadlessReplayImportTest {
    private val importer = HeadlessReplayImporter()

    @Test
    fun `empty intent list returns starting state unchanged`() {
        // g
        val startingState = MviGameState(elapsedSeconds = 10.0)
        val replayFile =
            ReplayFile
                .newBuilder()
                .setStartingState(ReplayConverter.stateToSnapshot(startingState))
                .build()

        // w
        val result = importer.import(replayFile)

        // t
        assertEquals(0, result.frameCount)
        assertEquals(10.0, result.finalState.elapsedSeconds, 1e-9)
        assertTrue(result.snapshots.isEmpty())
    }

    @Test
    fun `tick sequence advances elapsedSeconds by cumulative deltaSeconds`() {
        // g
        val intents =
            (0 until 60).map { i ->
                GameIntent.Reliable(GameAction.Tick(0.016, i * 16_000_000L))
            }
        val replayFile = buildReplayFile(MviGameState(), intents)

        // w
        val result = importer.import(replayFile)

        // t
        assertEquals(60, result.frameCount)
        assertEquals(60 * 0.016, result.finalState.elapsedSeconds, 1e-6)
    }

    @Test
    fun `same replay imported twice yields identical final states`() {
        // g
        val intents =
            (0 until 120).map { i ->
                GameIntent.Reliable(GameAction.Tick(0.016, i * 16_000_000L))
            }
        val replayFile = buildReplayFile(MviGameState(), intents)

        // w
        val first = importer.import(replayFile)
        val second = importer.import(replayFile)

        // t
        assertEquals(first.finalState, second.finalState, "headless replay must be deterministic")
    }

    @Test
    fun `snapshots are captured at configured interval`() {
        // g
        importer.snapshotIntervalFrames = 10
        val intents =
            (0 until 35).map { i ->
                GameIntent.Reliable(GameAction.Tick(0.016, i * 16_000_000L))
            }
        val replayFile = buildReplayFile(MviGameState(), intents)

        // w
        val result = importer.import(replayFile)

        // t
        assertEquals(3, result.snapshots.size)
    }

    @Test
    fun `headless replay starting from branch-point state carries forward elapsedSeconds`() {
        // g
        val branchState = MviGameState(elapsedSeconds = 30.0)
        val intents =
            listOf(
                GameIntent.Reliable(GameAction.Tick(0.016, 30_000_000_000L)),
            )
        val replayFile = buildReplayFile(branchState, intents)

        // w
        val result = importer.import(replayFile)

        // t
        assertTrue(result.finalState.elapsedSeconds > 30.0, "elapsedSeconds should advance past branch point")
        assertEquals(30.016, result.finalState.elapsedSeconds, 1e-6)
    }

    @Test
    fun `snapshot logical timestamps align with state elapsedSeconds`() {
        // g
        importer.snapshotIntervalFrames = 5
        val intents =
            (0 until 10).map { i ->
                GameIntent.Reliable(GameAction.Tick(0.016, i * 16_000_000L))
            }
        val replayFile = buildReplayFile(MviGameState(), intents)

        // w
        val result = importer.import(replayFile)

        // t
        assertEquals(2, result.snapshots.size)
        val (ts5, _) = result.snapshots[0]
        val (ts10, _) = result.snapshots[1]
        assertTrue(ts5 > 0L, "first snapshot timestamp must be positive")
        assertTrue(ts10 > ts5, "second snapshot must be later than first")
    }

    @Test
    fun `sampled states include starting state and every nth frame`() {
        // g
        val intents =
            (0 until 12).map { i ->
                GameIntent.Reliable(GameAction.Tick(0.016, i * 16_000_000L))
            }
        val replayFile = buildReplayFile(MviGameState(), intents)

        // w
        val states = importer.importSampledStates(replayFile, sampleEveryFrames = 5)

        // t
        assertEquals(3, states.size)
        assertEquals(0.0, states.first().elapsedSeconds, 1e-9)
        assertEquals(10 * 0.016, states.last().elapsedSeconds, 1e-6)
    }

    @Test
    fun `headless replay preserves paddle movement spin`() {
        // g
        val startingState =
            MviGameState(
                puck = MviPuck(x = 775.0, y = 250.0, vx = 100.0, vy = 0.0),
                paddle2Y = 250.0,
            )
        val intents =
            listOf(
                GameIntent.Reliable(GameAction.MovePaddle(200.0, PaddleSide.B)),
                GameIntent.Reliable(GameAction.Tick(0.016, 16_000_000L)),
            )
        val replayFile = buildReplayFile(startingState, intents)

        // w
        val result = importer.import(replayFile)

        // t
        assertTrue(result.finalState.puck.vx < 0.0)
        assertTrue(result.finalState.puck.spin < 0.0)
        assertTrue(result.finalState.puck.spinRemainingNs > 0L)
    }

    @Test
    fun `headless replay re-derives one timer highlight from reliable state`() {
        val startingState =
            MviGameState(
                puck = MviPuck(x = 25.0, y = 300.0, vx = -500.0, vy = 0.0),
                touchLedger =
                    TouchLedger(
                        listOf(
                            PuckTouch(
                                TouchSource.PADDLE,
                                PaddleSide.B,
                                "paddle:B",
                                0L,
                                500.0,
                            ),
                        ),
                    ),
            )
        val replayFile =
            buildReplayFile(
                startingState,
                listOf(GameIntent.Reliable(GameAction.Tick(0.016, 1_000_000_000L))),
            )

        val result = importer.import(replayFile)

        assertEquals(PaddleSide.A, result.oneTimerHighlights.single().side)
        assertEquals(800.0, hypot(result.finalState.puck.vx, result.finalState.puck.vy), 0.0001)
    }

    @Test
    fun `replay-level one timer config overrides future defaults without starting snapshot`() {
        val expected = OneTimerConfig(minimumIncomingSpeed = 725.0, maximumRawSpeed = 950.0)
        val replayFile =
            ReplayFile
                .newBuilder()
                .setOneTimerConfig(ReplayConverter.oneTimerConfigToProto(expected))
                .build()

        val result = importer.import(replayFile)

        assertEquals(expected, result.finalState.oneTimerConfig)
    }

    private fun buildReplayFile(
        startingState: MviGameState,
        intents: List<GameIntent.Reliable>,
    ): ReplayFile =
        ReplayFile
            .newBuilder()
            .setVersion("1")
            .setRoomId("test-room")
            .setStartingState(ReplayConverter.stateToSnapshot(startingState))
            .addAllIntents(intents.mapNotNull { ReplayConverter.toProto(it) })
            .build()
}
