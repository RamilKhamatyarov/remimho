package ru.rkhamatyarov.service

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.GameIntent
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviPuck
import ru.rkhamatyarov.service.mvi.PaddleInterception
import ru.rkhamatyarov.service.mvi.PaddleSide
import ru.rkhamatyarov.service.mvi.reduce
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class PaddleTunnelingIntegrationTest {
    @Test
    fun `edge tunneling regression occurs for human and bot controlled paddles`() =
        runTest {
            for (humanA in listOf(true, false)) {
                for (side in PaddleSide.entries) {
                    for (fromAbove in listOf(true, false)) {
                        val room =
                            GameRoom(
                                id = "edge-contact",
                                scope = TestScope(StandardTestDispatcher(testScheduler)),
                                autoPowerUpsEnabled = false,
                            )
                        try {
                            room.registerHumanSide(PaddleSide.B)
                            if (humanA) room.registerHumanSide(PaddleSide.A)
                            val state = PaddleInterception.edgeApproach(side, fromAbove)
                            room.send(GameAction.RestoreSnapshot(state))
                            room.send(GameAction.Tick(0.016, 16_000_000L))
                            runCurrent()
                            PaddleInterception.verifyEdge(state, room.reliableState.value, side, fromAbove, 16_000_000L)
                        } finally {
                            room.shutdown()
                            runCurrent()
                        }
                    }
                }
            }
        }

    @Test
    fun `generated human versus human rallies cannot tunnel through either paddle`() =
        runTest(timeout = 2.minutes) {
            checkRallies(humanA = true)
        }

    @Test
    fun `generated human versus real bot rallies cannot tunnel through either paddle`() =
        runTest(timeout = 2.minutes) {
            checkRallies(humanA = false)
        }

    private suspend fun TestScope.checkRallies(humanA: Boolean) {
        val contacts = mutableMapOf(PaddleSide.A to 0, PaddleSide.B to 0)
        var botMoves = 0
        checkAll(
            iterations = 500,
            genA = Arb.double(-10000.0, 10000.0).filter { it.isFinite() },
            genB = Arb.double(-10000.0, 10000.0).filter { it.isFinite() },
            genC = Arb.int(8..80),
        ) { vx, vy, millis ->
            val observations = mutableListOf<TickObservation>()
            val room =
                GameRoom(
                    id = "tunneling-property",
                    scope = TestScope(StandardTestDispatcher(testScheduler)),
                    reducer = { state, action ->
                        reduce(state, action).also { next ->
                            if (action is GameAction.Tick) observations.add(TickObservation(state, next, action))
                        }
                    },
                    autoPowerUpsEnabled = false,
                    replayLogCapacity = 2048,
                )
            try {
                room.registerHumanSide(PaddleSide.B)
                if (humanA) room.registerHumanSide(PaddleSide.A)
                room.send(GameAction.RestoreSnapshot(MviGameState(puck = MviPuck(vx = vx, vy = vy))))
                runCurrent()
                repeat(200) { index ->
                    val targetY = room.reliableState.value.puck.y
                    room.send(GameAction.MovePaddle(targetY, PaddleSide.B))
                    if (humanA) room.send(GameAction.MovePaddle(targetY, PaddleSide.A))
                    room.send(GameAction.Tick(millis / 1000.0, (index + 1) * millis * 1_000_000L))
                    runCurrent()
                    // Inspect the state at collision time, before the room moves its bot for the next tick.
                    val observation = observations.single()
                    val side = PaddleInterception.verify(observation.before, observation.after, observation.tick)
                    if (side != null) contacts[side] = contacts.getValue(side) + 1
                    observations.clear()
                }
                val actions = room.getReplayLog().map { it.action }
                if (!humanA) {
                    botMoves += actions.filterIsInstance<GameAction.MovePaddle>().count { it.side == PaddleSide.A }
                }
                assertEquals(room.reliableState.value, actions.fold(MviGameState(), ::reduce))
            } finally {
                room.shutdown()
                runCurrent()
            }
        }
        for (side in PaddleSide.entries) {
            assertTrue(contacts.getValue(side) > 0, "No paddle $side interceptions tested (humanA=$humanA)")
        }
        if (!humanA) assertTrue(botMoves > 0, "The real bot never emitted a move")
    }

    private fun GameRoom.send(action: GameAction) {
        assertTrue(dispatch(GameIntent.Reliable(action)), "Room rejected $action")
    }

    private data class TickObservation(
        val before: MviGameState,
        val after: MviGameState,
        val tick: GameAction.Tick,
    )
}
