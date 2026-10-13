package ru.rkhamatyarov.service.mvi

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.filter
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import ru.rkhamatyarov.model.SpeedConfig
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PaddleTunnelingPropertyTest {
    @Test
    fun `horizontal outward motion cannot tunnel through top or bottom paddle edges`() =
        runBlocking<Unit> {
            checkAll(
                iterations = 5000,
                genA = Arb.double(100.0, 10000.0).filter { it.isFinite() },
                genB = Arb.double(1.0, 100.0).filter { it.isFinite() },
            ) { verticalSpeed, horizontalSpeed ->
                for (side in PaddleSide.entries) {
                    for (fromAbove in listOf(true, false)) {
                        val state = PaddleInterception.edgeApproach(side, fromAbove, verticalSpeed, horizontalSpeed)
                        val next = reduce(state, GameAction.Tick(TICK_SECONDS, TICK_NS))
                        PaddleInterception.verifyEdge(state, next, side, fromAbove, TICK_NS)
                    }
                }
            }
        }

    @Test
    fun `puck never tunnels through a paddle across generated speeds`() =
        runBlocking {
            val verifiedContacts = mutableMapOf(PaddleSide.A to 0, PaddleSide.B to 0)
            checkAll(
                iterations = 5000,
                genA = Arb.double(-10000.0, 10000.0).filter { it.isFinite() },
                genB = Arb.double(-10000.0, 10000.0).filter { it.isFinite() },
            ) { vx, vy ->
                var state =
                    MviGameState(
                        puck = MviPuck(x = 400.0, y = 300.0, vx = vx, vy = vy),
                        paddle1Y = 250.0,
                        paddle2Y = 250.0,
                        speedConfig = SpeedConfig(timeAccelerationRate = 0.0, levelAccelerationPerLine = 0.0),
                    )
                repeat(200) { index ->
                    val elapsedNs = (index + 1) * TICK_NS
                    val tick = GameAction.Tick(TICK_SECONDS, elapsedNs)
                    val next = reduce(state, tick)
                    val contact = PaddleInterception.verify(state, next, tick)
                    if (contact != null) {
                        verifiedContacts[contact] = verifiedContacts.getValue(contact) + 1
                    }
                    state = next
                }
            }
            for (side in PaddleSide.entries) {
                assertTrue(verifiedContacts.getValue(side) > 0, "Generator never exercised paddle $side")
            }
        }

    @Test
    fun `moving either paddle catches aimed shots with wall bounces and delayed ticks`() =
        runBlocking<Unit> {
            checkAll(
                iterations = 5000,
                genA = Arb.double(100.0, 10000.0).filter { it.isFinite() },
                genB = Arb.double(-10000.0, 10000.0).filter { it.isFinite() },
                genC = Arb.double(0.001, 0.25).filter { it.isFinite() },
                genD = Arb.double(10.0, 590.0).filter { it.isFinite() },
            ) { speed, vy, seconds, y ->
                for (side in PaddleSide.entries) {
                    val direction = if (side == PaddleSide.A) -1.0 else 1.0
                    val face = if (side == PaddleSide.A) 30.0 else 770.0
                    val distance = minOf(200.0, speed * seconds / 2)
                    val initial =
                        MviGameState(
                            puck = MviPuck(x = face - direction * distance, y = y, vx = direction * speed, vy = vy),
                        )
                    val tick = GameAction.Tick(seconds, (seconds * 1_000_000_000L).toLong())
                    val contact = assertNotNull(PaddleInterception.contact(initial, tick))
                    val positioned = reduce(initial, GameAction.MovePaddle(contact.y, side))
                    assertNotNull(PaddleInterception.verify(positioned, reduce(positioned, tick), tick))
                }
            }
        }

    private companion object {
        const val TICK_SECONDS = 0.016
        const val TICK_NS = 16_000_000L
    }
}
