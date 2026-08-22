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
import ru.rkhamatyarov.model.AiOpponentConfig
import ru.rkhamatyarov.service.mvi.EphemeralEvent
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.GameIntent
import ru.rkhamatyarov.service.mvi.MviLine
import ru.rkhamatyarov.service.mvi.MviPoint
import ru.rkhamatyarov.service.mvi.PaddleSide
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GameRoomActorTest {
    @Test
    fun `reliable intent updates state flow`() =
        runTest {
            val room = testRoom()

            room.dispatch(GameIntent.Reliable(GameAction.MovePaddle(150.0)))
            advanceUntilIdle()

            assertEquals(150.0, room.reliableState.value.paddle2Y, 0.001)
            room.shutdown()
        }

    @Test
    fun `ephemeral intent bypasses replay log and emits to shared flow`() =
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
    fun `cursor move bypasses replay log and emits to shared flow`() =
        runTest {
            val room = testRoom()
            val cursorEvent = EphemeralEvent.CursorMove("player-1", 42.0, 84.0)
            val received = async { room.ephemeralEvents.first() }

            runCurrent()
            room.dispatch(GameIntent.Ephemeral(cursorEvent))
            advanceUntilIdle()

            assertEquals(cursorEvent, received.await())
            assertTrue(room.getReplayLog().isEmpty())
            room.shutdown()
        }

    @Test
    fun `commit line intent updates reliable state`() =
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
    fun `replay log ring buffer evicts oldest reliable intent`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = TestScope(dispatcher)
            val room = GameRoom("test-room", scope = scope, autoPowerUpsEnabled = false, replayLogCapacity = 1024)

            repeat(1030) { index ->
                room.dispatch(GameIntent.Reliable(GameAction.MovePaddle(index.toDouble())))
                runCurrent()
            }
            advanceUntilIdle()

            val oldestLogged = room.getReplayLog().first()
            assertEquals(1024, room.getReplayLog().size)
            assertEquals(6.0, (oldestLogged.action as GameAction.MovePaddle).y, 0.001)
            room.shutdown()
        }

    @Test
    fun `intent channel drops oldest actions under backpressure`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = TestScope(dispatcher)
            val room = GameRoom("test-room", scope = scope, autoPowerUpsEnabled = false)

            repeat(70) { index ->
                assertTrue(room.dispatch(GameIntent.Reliable(GameAction.MovePaddle(index.toDouble()))))
            }
            advanceUntilIdle()

            val oldestLogged = room.getReplayLog().first()
            assertEquals(64, room.getReplayLog().size)
            assertEquals(6.0, (oldestLogged.action as GameAction.MovePaddle).y, 0.001)
            assertEquals(69.0, room.reliableState.value.paddle2Y, 0.001)
            room.shutdown()
        }

    @Test
    fun `automatic powerup spawner follows reliable logical ticks`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = TestScope(dispatcher)
            val room =
                GameRoom(
                    "test-room",
                    scope = scope,
                    autoPowerUpsEnabled = true,
                )
            room.registerHumanSide(PaddleSide.A)

            room.dispatch(GameIntent.Reliable(GameAction.Tick(9.0, elapsedNs = 9_000_000_000L)))
            advanceUntilIdle()
            assertEquals(0, room.reliableState.value.powerUps.size)

            room.dispatch(GameIntent.Reliable(GameAction.Tick(1.0, elapsedNs = 10_000_000_000L)))
            advanceUntilIdle()

            assertEquals(1, room.reliableState.value.powerUps.size)
            assertTrue(room.getReplayLog().last().action is GameAction.SpawnPowerUp)
            room.shutdown()
        }

    @Test
    fun `bot emits reliable paddle intents recorded in replay log`() =
        runTest {
            val room = testRoom()
            room.dispatch(
                GameIntent.Reliable(
                    GameAction.RestoreSnapshot(
                        room.reliableState.value.copy(
                            aiConfig = AiOpponentConfig(reactionDelayMs = 0, aimError = 0.0, predictionDepth = 1),
                        ),
                    ),
                ),
            )
            room.dispatch(GameIntent.Reliable(GameAction.Tick(0.016, elapsedNs = 16_000_000L)))
            room.dispatch(GameIntent.Reliable(GameAction.Tick(0.016, elapsedNs = 32_000_000L)))
            advanceUntilIdle()

            val botMoves =
                room
                    .getReplayLog()
                    .map { it.action }
                    .filterIsInstance<GameAction.MovePaddle>()
                    .filter { it.side == PaddleSide.A }
            assertTrue(botMoves.isNotEmpty())
            assertTrue(room.reliableState.value.paddle1Y != 250.0)
            room.shutdown()
        }

    @Test
    fun `human ownership suppresses bot intents`() =
        runTest {
            val room = testRoom()
            room.registerHumanSide(PaddleSide.A)
            room.dispatch(GameIntent.Reliable(GameAction.Tick(0.016, elapsedNs = 16_000_000L)))
            room.dispatch(GameIntent.Reliable(GameAction.Tick(0.016, elapsedNs = 32_000_000L)))
            advanceUntilIdle()

            assertTrue(
                room
                    .getReplayLog()
                    .map { it.action }
                    .filterIsInstance<GameAction.MovePaddle>()
                    .isEmpty(),
            )
            room.shutdown()
        }

    private fun TestScope.testRoom(): GameRoom {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        return GameRoom("test-room", scope = scope, autoPowerUpsEnabled = false)
    }
}
