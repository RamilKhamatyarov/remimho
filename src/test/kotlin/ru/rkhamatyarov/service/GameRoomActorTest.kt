package ru.rkhamatyarov.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.rkhamatyarov.service.mvi.EphemeralEvent
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.GameIntent
import ru.rkhamatyarov.service.mvi.MviLine
import ru.rkhamatyarov.service.mvi.MviPoint
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GameRoomActorTest {
    @Test
    fun test_reliableIntent_updatesStateFlow() =
        runTest {
            val room = testRoom()

            room.dispatch(GameIntent.Reliable(GameAction.MovePaddle(150.0)))
            advanceUntilIdle()

            assertEquals(150.0, room.reliableState.value.paddle2Y, 0.001)
            room.shutdown()
        }

    @Test
    fun test_ephemeralIntent_bypassesReplayLogAndEmitsToSharedFlow() =
        runTest {
            val room = testRoom()
            val draftEvent = EphemeralEvent.LineDraft("line-1", 10.0, 20.0)
            val received = async { room.ephemeralEvents.first() }

            runCurrent()
            room.dispatch(GameIntent.Ephemeral(draftEvent))
            advanceUntilIdle()

            assertEquals(draftEvent, received.await())
            assertTrue(room.getReplayLog().isEmpty())
            room.shutdown()
        }

    @Test
    fun test_commitLineIntent_updatesReliableState() =
        runTest {
            val room = testRoom()
            val line =
                MviLine(
                    id = "line-1",
                    points = listOf(MviPoint(1.0, 2.0), MviPoint(3.0, 4.0)),
                )

            room.dispatch(GameIntent.Reliable(GameAction.CommitLine(line)))
            advanceUntilIdle()

            assertEquals(listOf(line), room.reliableState.value.lines)
            room.shutdown()
        }

    @Test
    fun test_replayLogRingBuffer_evictsOldestReliableIntent() =
        runTest {
            val room = testRoom()

            repeat(1030) { index ->
                room.dispatch(GameIntent.Reliable(GameAction.MovePaddle(index.toDouble())))
            }
            advanceUntilIdle()

            val oldestLogged = room.getReplayLog().first() as GameIntent.Reliable
            assertEquals(1024, room.getReplayLog().size)
            assertEquals(6.0, (oldestLogged.action as GameAction.MovePaddle).y, 0.001)
            room.shutdown()
        }

    private fun TestScope.testRoom(): GameRoom {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        return GameRoom("test-room", scope = scope)
    }
}
