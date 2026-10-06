package ru.rkhamatyarov.service.mvi

import org.junit.jupiter.api.Test
import ru.rkhamatyarov.api.v1.payload.CombinationLineRequest
import ru.rkhamatyarov.api.v1.payload.CombinationPointRequest
import ru.rkhamatyarov.api.v1.payload.CombinationRequest
import ru.rkhamatyarov.mapping.proto.mviStateFromDelta
import ru.rkhamatyarov.mapping.proto.toDelta
import ru.rkhamatyarov.mapping.toAction
import ru.rkhamatyarov.proto.ReplayFile
import ru.rkhamatyarov.replay.HeadlessReplayImporter
import ru.rkhamatyarov.replay.ReplayConverter
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PassingCombinationTest {
    @Test
    fun `switching and disabling preserve freehand and opponent lines and pause`() {
        val manual = line("manual", PaddleSide.A)
        val opponent = line("opponent", PaddleSide.B, "triangle")
        val previous = line("previous", PaddleSide.A, "triangle")
        val replacement = line("new", PaddleSide.A, "cutback").copy(width = 6.0)
        val initial = MviGameState(lines = listOf(manual, opponent, previous), elapsedSeconds = 10.0)
        val next = reduce(initial, GameAction.ApplyCombination(PaddleSide.A, listOf(replacement)))
        assertEquals(listOf(manual, opponent, replacement), next.lines)
        assertTrue(next.paused)
        assertEquals(initial.puck, next.puck)
        assertEquals(initial.score, next.score)
        assertEquals(initial.elapsedSeconds, next.elapsedSeconds)
        assertEquals(next, reduce(next, GameAction.Tick(0.016, 10_016_000_000L)))
        assertEquals(
            listOf(manual, opponent),
            reduce(next, GameAction.ApplyCombination(PaddleSide.A, emptyList())).lines,
        )
    }

    @Test
    fun `saving existing own lines does not double their geometry on apply`() {
        val manual = line("manual", PaddleSide.B)
        val preset = manual.copy(id = "preset", combinationId = "saved")
        val next =
            reduce(
                MviGameState(lines = listOf(manual)),
                GameAction.ApplyCombination(PaddleSide.B, listOf(preset)),
            )
        assertEquals(listOf(manual), next.lines)
    }

    @Test
    fun `server assigns side and mirrors canonical geometry`() {
        val state = MviGameState()
        val a = request().toAction(PaddleSide.A, state, "a")
        val b = request().toAction(PaddleSide.B, state, "b")
        assertEquals(PaddleSide.A, a.lines.single().ownerSide)
        assertEquals(PaddleSide.B, b.lines.single().ownerSide)
        assertNotEquals(a.lines.single().id, b.lines.single().id)
        a.lines.single().points.zip(b.lines.single().points).forEach { (left, right) ->
            assertEquals(state.canvasWidth, left.x + right.x, 1e-9)
            assertEquals(left.y, right.y)
        }
    }

    @Test
    fun `malformed and oversized custom layouts are rejected`() {
        val base = request()
        val segment = base.lines.single()
        val invalidPoints =
            listOf(
                listOf(CombinationPointRequest(0.5, 0.5)),
                List(2) { CombinationPointRequest(-0.1, 0.5) },
                List(2) { CombinationPointRequest(0.5, 0.5) },
            )
        val invalid =
            listOf(
                base.copy(id = ""),
                base.copy(lines = List(33) { segment }),
                base.copy(lines = listOf(segment.copy(width = Double.NaN))),
            ) + invalidPoints.map { base.copy(lines = listOf(segment.copy(points = it))) }
        for (value in invalid) {
            assertFailsWith<IllegalArgumentException> { value.toAction(PaddleSide.A, MviGameState(), "invalid") }
        }
    }

    @Test
    fun `geometry and grouping survive replay and rewind before replacement`() {
        val initial = MviGameState(lines = listOf(line("freehand", PaddleSide.B)))
        val first = request().toAction(PaddleSide.A, initial, "first")
        val applied = reduce(initial, first)
        val restored = mviStateFromDelta(applied.toDelta())
        assertEquals(applied.lines, restored.lines)
        assertEquals(applied, ReplayConverter.snapshotToState(ReplayConverter.stateToSnapshot(applied)))
        val second = request().copy(id = "second").toAction(PaddleSide.A, initial, "second")
        val actions = listOf(first, GameAction.TogglePause, GameAction.Tick(0.016, 16_000_000L), second)
        val replay =
            ReplayFile.newBuilder()
                .setStartingState(ReplayConverter.stateToSnapshot(initial))
                .addAllIntents(actions.map { ReplayConverter.toProto(GameIntent.Reliable(it))!! })
                .build()
        val imported = HeadlessReplayImporter().import(ReplayFile.parseFrom(replay.toByteArray()))
        assertEquals(actions.fold(initial, ::reduce), imported.finalState)
        assertEquals(
            reduce(applied, second),
            reduce(reduce(imported.finalState, GameAction.RestoreSnapshot(applied)), second),
        )
        assertEquals(first, ReplayConverter.fromProto(replay.intentsList.first()).first.action)
    }

    private fun line(
        id: String,
        side: PaddleSide,
        combinationId: String? = null,
    ): MviLine =
        MviLine(
            id = id,
            points = listOf(MviPoint(200.0, 200.0), MviPoint(260.0, 240.0)),
            ownerSide = side,
            combinationId = combinationId,
        )

    private fun request(): CombinationRequest =
        CombinationRequest(
            id = "triangle",
            lines =
                listOf(
                    CombinationLineRequest(
                        listOf(CombinationPointRequest(0.2, 0.3), CombinationPointRequest(0.3, 0.4)),
                    ),
                ),
        )
}
