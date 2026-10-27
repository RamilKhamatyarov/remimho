package ru.rkhamatyarov.telemetry

import org.junit.jupiter.api.Test
import ru.rkhamatyarov.proto.ReplayFile
import ru.rkhamatyarov.replay.HeadlessReplayImporter
import ru.rkhamatyarov.replay.ReplayConverter
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviLine
import ru.rkhamatyarov.service.mvi.MviPoint
import ru.rkhamatyarov.service.mvi.MviPuck
import ru.rkhamatyarov.service.mvi.PaddleSide
import ru.rkhamatyarov.service.mvi.reduce
import java.util.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureRecorderTest {
    @Test
    fun `evicted window replays from exact baseline`() {
        val recorder = CaptureRecorder(maxEntries = 2)
        var state = MviGameState(puck = MviPuck(vx = 2000.0, vy = -1200.0))
        repeat(5) { index ->
            val action = GameAction.Tick(0.016, (index + 1) * 16_000_000L)
            val next = reduce(state, action)
            recorder.record(state, next, action, listOf(PaddleSide.B))
            state = next
        }
        val window = recorder.capture()
        assertEquals(2, window.entries.size)
        assertTrue(window.capacityLimited)
        val dossier = CaptureDossier.build("test", "test", window)
        val replay = ReplayFile.parseFrom(Base64.getDecoder().decode(dossier.segments.single().replayBase64))
        assertEquals(state, HeadlessReplayImporter().import(replay).finalState)
    }

    @Test
    fun `reset and rewind preserve independently reproducible segments`() {
        val recorder = CaptureRecorder()
        var state = MviGameState()
        val actions =
            listOf(
                GameAction.Tick(0.016, 16_000_000L),
                GameAction.Reset,
                GameAction.Tick(0.016, 16_000_000L),
                GameAction.RestoreSnapshot(MviGameState(paddle1Y = 90.0, elapsedSeconds = 8.0)),
                GameAction.MovePaddle(120.0, PaddleSide.B),
                GameAction.Tick(0.016, 8_016_000_000L),
            )
        for (action in actions) {
            val next = reduce(state, action)
            recorder.record(state, next, action, PaddleSide.entries)
            state = next
        }
        val dossier = CaptureDossier.build("test", "test", recorder.capture())
        assertEquals(listOf("window-start", "RESET", "RESTORE"), dossier.segments.map { it.reason })
        assertEquals(2, dossier.frames.count { it.boundary != "NONE" })
        for ((_, _, _, _, replayBase64, expectedSnapshotBase64) in dossier.segments) {
            val replay = ReplayFile.parseFrom(Base64.getDecoder().decode(replayBase64))
            val actual = ReplayConverter.stateToSnapshot(HeadlessReplayImporter().import(replay).finalState)
            assertContentEquals(Base64.getDecoder().decode(expectedSnapshotBase64), actual.toByteArray())
        }
    }

    @Test
    fun `retention expires by observation clock even while logical clock is reset`() {
        var now = 0L
        val recorder = CaptureRecorder(clock = { now })
        val state = MviGameState()
        recorder.record(state, state, GameAction.Reset, emptyList())
        now = 15_000_000_000L
        assertEquals(1, recorder.capture().entries.size)
        now++
        assertTrue(recorder.capture().entries.isEmpty())
        assertFalse(recorder.capture().capacityLimited)
    }

    @Test
    fun `oversized record preserves earlier evidence and starts a replayable gap segment`() {
        val recorder = CaptureRecorder(maxBytes = 3000)
        var state = MviGameState()
        val actions =
            listOf(
                GameAction.Tick(0.016, 16_000_000L),
                GameAction.Tick(0.016, 32_000_000L),
                GameAction.CommitLine(MviLine("large", List(1000) { MviPoint(it.toDouble(), 100.0) })),
                GameAction.ClearLines,
                GameAction.Tick(0.016, 48_000_000L),
            )
        for (action in actions) {
            val next = reduce(state, action)
            recorder.record(state, next, action, emptyList())
            state = next
        }
        val window = recorder.capture()
        assertTrue(window.capacityLimited)
        assertEquals(listOf(1L, 2L, 5L), window.entries.map { it.sequence })
        assertNull(window.entries[1].baseline, "Continuous entries must not duplicate baselines")
        assertTrue(window.entries.sumOf { it.size } <= 3000)
        val dossier = CaptureDossier.build("test", "test", window)
        assertEquals(listOf("window-start", "recording-gap"), dossier.segments.map { it.reason })
        for ((_, _, _, _, replayBase64, expectedSnapshotBase64) in dossier.segments) {
            val replay = ReplayFile.parseFrom(Base64.getDecoder().decode(replayBase64))
            val actual = ReplayConverter.stateToSnapshot(HeadlessReplayImporter().import(replay).finalState)
            assertContentEquals(Base64.getDecoder().decode(expectedSnapshotBase64), actual.toByteArray())
        }
    }
}
