package ru.rkhamatyarov.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.rkhamatyarov.proto.ReplayFile
import ru.rkhamatyarov.replay.HeadlessReplayImporter
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.GameIntent
import ru.rkhamatyarov.service.mvi.MviDomainEvent
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.PaddleSide
import ru.rkhamatyarov.service.mvi.reduce
import ru.rkhamatyarov.telemetry.CaptureDossier
import ru.rkhamatyarov.telemetry.CaptureRecorder
import ru.rkhamatyarov.telemetry.DisabledCapture
import ru.rkhamatyarov.telemetry.RoomCapture
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureRoomTest {
    @Test
    fun `capture includes human and bot actions without mutating gameplay`() =
        runTest {
            for (humanA in listOf(false, true)) {
                val room =
                    GameRoom(
                        "capture",
                        scope = TestScope(StandardTestDispatcher(testScheduler)),
                        autoPowerUpsEnabled = false,
                        captureRecorder = CaptureRecorder(),
                    )
                try {
                    room.registerHumanSide(PaddleSide.B)
                    if (humanA) room.registerHumanSide(PaddleSide.A)
                    repeat(40) { index ->
                        assertTrue(
                            room.dispatch(GameIntent.Reliable(GameAction.Tick(0.016, (index + 1) * 16_000_000L))),
                        )
                        runCurrent()
                    }
                    val state = room.reliableState.value
                    val log = room.getReplayLog()
                    val dossier = CaptureDossier.build(room.id, "test", room.captureRecorder.capture())
                    assertEquals(state, room.reliableState.value)
                    assertEquals(log, room.getReplayLog())
                    assertEquals(humanA, dossier.frames.first().humanSides.contains("A"))
                    assertTrue(dossier.frames.first().humanSides.contains("B"))
                    if (!humanA) assertTrue(dossier.frames.any { it.action == "MOVE_PADDLE" })
                    val replay =
                        ReplayFile.parseFrom(
                            Base64.getDecoder().decode(dossier.segments.single().replayBase64),
                        )
                    assertEquals(state, HeadlessReplayImporter().import(replay).finalState)
                } finally {
                    room.shutdown()
                }
            }
        }

    @Test
    fun `diagnostic failures cannot kill actor or diverge replay`() =
        runTest {
            for (recorderFailure in listOf(true, false)) {
                val failingRecorder =
                    object : RoomCapture by DisabledCapture {
                        override val enabled = true

                        override fun record(
                            before: MviGameState,
                            after: MviGameState,
                            action: GameAction,
                            humanSides: List<PaddleSide>,
                            domainEvents: List<MviDomainEvent>,
                        ) {
                            throw AssertionError("Injected recorder failure")
                        }
                    }
                val room =
                    GameRoom(
                        "failure",
                        scope = TestScope(StandardTestDispatcher(testScheduler)),
                        autoPowerUpsEnabled = false,
                        captureRecorder = if (recorderFailure) failingRecorder else DisabledCapture,
                        paddleDiagnostics = { _, _, _, _ -> error("Injected logging failure") },
                    )
                try {
                    val first = GameAction.MovePaddle(120.0, PaddleSide.B)
                    val second = GameAction.MovePaddle(450.0, PaddleSide.A)
                    room.dispatch(GameIntent.Reliable(first))
                    runCurrent()
                    assertEquals(70.0, room.reliableState.value.paddle2Y)
                    room.dispatch(GameIntent.Reliable(second))
                    runCurrent()
                    assertEquals(400.0, room.reliableState.value.paddle1Y)
                    val replayed =
                        room.getReplayLog().fold(
                            MviGameState(),
                        ) { state, intent -> reduce(state, intent.action) }
                    assertEquals(replayed, room.reliableState.value)
                } finally {
                    room.shutdown()
                }
            }
        }

    @Test
    fun `rooms default to no-op capture`() =
        runTest {
            val room = GameRoom("disabled", scope = backgroundScope, autoPowerUpsEnabled = false)
            try {
                room.dispatch(GameIntent.Reliable(GameAction.MovePaddle(120.0)))
                runCurrent()
                assertEquals(false, room.captureRecorder.enabled)
                assertTrue(room.captureRecorder.capture().entries.isEmpty())
                assertEquals(70.0, room.reliableState.value.paddle2Y)
            } finally {
                room.shutdown()
            }
        }
}
