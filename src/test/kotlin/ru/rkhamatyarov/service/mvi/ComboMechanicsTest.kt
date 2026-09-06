package ru.rkhamatyarov.service.mvi

import org.junit.jupiter.api.Test
import ru.rkhamatyarov.model.PowerUpType
import kotlin.math.hypot
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComboMechanicsTest {
    private val config = Combo()

    @Test
    fun `give and go boosts either paddle and preserves its ledger`() {
        for (side in PaddleSide.entries) {
            val state = returnState(side)
            val result = MviDomainEvents.capture { reduce(state, tick()) }
            assertEquals(500.0, hypot(result.value.puck.vx, result.value.puck.vy), 0.0001)
            assertEquals(side == PaddleSide.A, result.value.puck.vx > 0)
            assertEquals(3, result.value.touchLedger.entries.size)
            assertTrue(MviDomainEvent.GiveAndGoCompleted(side) in result.events)
            assertEquals(2, state.touchLedger.entries.size)
        }
    }

    @Test
    fun `give and go caps raw speed without changing aim`() {
        val state = returnState().copy(puck = MviPuck(x = 31.0, y = 320.0, vx = -500.0, vy = 0.0))
        val ordinary = reduce(state.copy(combo = Combo.DISABLED), tick()).puck
        val boosted = reduce(state, tick()).puck
        assertEquals(800.0, hypot(boosted.vx, boosted.vy), 0.0001)
        assertEquals(ordinary.vy / ordinary.vx, boosted.vy / boosted.vx, 0.0001)
    }

    @Test
    fun `give and go rejects intervening contacts and wrong ownership`() {
        val first = touch(TouchSource.PADDLE)
        val last = touch(TouchSource.DRAWN_LINE)
        val interruptions =
            listOf(
                touch(TouchSource.WALL, owner = null),
                touch(TouchSource.POWER_UP, owner = null),
                touch(TouchSource.BUMPER, owner = null),
                touch(TouchSource.PADDLE, owner = PaddleSide.B),
                touch(TouchSource.DRAWN_LINE, owner = PaddleSide.B),
                touch(TouchSource.DRAWN_LINE, owner = null),
            )
        for (interruption in interruptions) {
            assertFalse(eligible(listOf(first, interruption, last)))
            assertFalse(eligible(listOf(first, last, interruption)))
        }
    }

    @Test
    fun `give and go freshness boundary is inclusive and duplicates do not refresh it`() {
        val paddle = touch(TouchSource.PADDLE, at = 0L)
        val line = touch(TouchSource.DRAWN_LINE, at = 1_000_000_000L)
        assertTrue(eligible(listOf(paddle, line), 3_000_000_000L))
        assertFalse(eligible(listOf(paddle, line), 3_000_000_001L))
        assertTrue(eligible(listOf(paddle, line, line.copy(elapsedNs = 2_000_000_000L))))
        assertFalse(eligible(listOf(paddle, paddle.copy(elapsedNs = 2_000_000_000L), line), 4_000_000_000L))
        assertFalse(eligible(listOf(paddle, line.copy(elapsedNs = 4_000_000_000L))))
    }

    @Test
    fun `owned teleport line supports give and go`() {
        val entry = MviLine("entry", listOf(MviPoint(200.0, 200.0), MviPoint(200.0, 400.0)), ownerSide = PaddleSide.A)
        val exit = MviLine("exit", listOf(MviPoint(400.0, 200.0), MviPoint(400.0, 400.0)), ownerSide = PaddleSide.A)
        val state =
            MviGameState(
                puck = MviPuck(x = 199.0, y = 300.0, vx = 100.0, vy = 0.0),
                lines = listOf(entry, exit),
                teleports = mapOf("entry" to "exit"),
                touchLedger = TouchLedger(listOf(touch(TouchSource.PADDLE))),
            )
        val teleported = reduce(state, tick(1_000_000_000L))
        assertEquals(
            "entry",
            teleported.touchLedger.entries
                .last()
                .identifier,
        )
        assertEquals(
            PaddleSide.A,
            teleported.touchLedger.entries
                .last()
                .ownerSide,
        )
        assertEquals(400.0, teleported.puck.x)
        val returning = teleported.copy(puck = returnState().puck)
        val result = MviDomainEvents.capture { reduce(returning, tick()) }
        assertTrue(result.events.any { it is MviDomainEvent.GiveAndGoCompleted })
    }

    @Test
    fun `give and go can repeat in a rally`() {
        val first = reduce(returnState(), tick())
        val next =
            first.copy(
                puck = MviPuck(x = 31.0, y = 300.0, vx = -500.0, vy = 0.0),
                touchLedger = first.touchLedger.append(touch(TouchSource.DRAWN_LINE, at = 2_000_000_000L)),
            )
        val result = MviDomainEvents.capture { reduce(next, tick(3_000_000_000L)) }
        assertEquals(800.0, hypot(result.value.puck.vx, result.value.puck.vy), 0.0001)
        assertTrue(result.events.any { it is MviDomainEvent.GiveAndGoCompleted })
    }

    @Test
    fun `super goal awards two points for either side and clears history`() {
        for (side in PaddleSide.entries) {
            val state = goalState(side)
            val result = MviDomainEvents.capture { reduce(state, tick()) }
            assertEquals(if (side == PaddleSide.A) MviScore(2, 0) else MviScore(0, 2), result.value.score)
            assertEquals(TouchLedger(), result.value.touchLedger)
            assertTrue(MviDomainEvent.SuperGoalScored(side, 4) in result.events)
        }
    }

    @Test
    fun `powerup at serve position cannot leave a touch after scoring`() {
        val state =
            goalState().copy(
                powerUps = listOf(MviPowerUp("center", 400.0, 300.0, PowerUpType.SPEED_BOOST, 0L)),
            )
        assertEquals(TouchLedger(), reduce(state, tick()).touchLedger)
    }

    @Test
    fun `super goal uses suffix after old interruption`() {
        val chain = goalState().touchLedger.entries
        val wall = touch(TouchSource.WALL, owner = null)
        assertEquals(4, chainLength(listOf(wall) + chain))
        assertEquals(0, chainLength(chain.take(2) + wall + chain.takeLast(2)))
        assertEquals(0, chainLength(chain + wall))
    }

    @Test
    fun `opponent and ownerless line contacts break super goal chains`() {
        val chain = goalState().touchLedger.entries
        for (contact in listOf(
            touch(TouchSource.PADDLE, owner = PaddleSide.B),
            touch(TouchSource.DRAWN_LINE, owner = PaddleSide.B),
            touch(TouchSource.DRAWN_LINE, owner = null),
        )) {
            assertEquals(0, chainLength(chain + contact))
        }
    }

    @Test
    fun `neutral contacts do not count or separate duplicates`() {
        val chain = goalState().touchLedger.entries
        val neutral = touch(TouchSource.POWER_UP, owner = null)
        assertEquals(4, chainLength(chain.take(2) + neutral + chain.takeLast(2)))
        assertEquals(0, chainLength(chain.take(3) + neutral))
        assertEquals(0, chainLength(chain.take(3) + neutral + chain[2]))
        assertEquals(0, chainLength(chain.take(3) + chain[2]))
    }

    @Test
    fun `super goal requires both paddle and line`() {
        val onlyLines = (1..4).map { touch(TouchSource.DRAWN_LINE, id = "line:$it") }
        assertEquals(0, chainLength(onlyLines))
        assertEquals(0, chainLength(listOf(touch(TouchSource.PADDLE))))
    }

    @Test
    fun `recent suffix qualifies after expired contacts`() {
        val chain = goalState().touchLedger.entries.map { it.copy(elapsedNs = 5_000_000_000L) }
        assertEquals(4, chainLength(listOf(touch(TouchSource.DRAWN_LINE)) + chain, 12_000_000_000L))
        assertEquals(4, chainLength(chain, 15_000_000_000L))
        assertEquals(0, chainLength(chain, 15_000_000_001L))
        assertEquals(0, chainLength(chain, 4_000_000_000L))
    }

    @Test
    fun `disabled rules preserve ordinary paddle and goal behavior`() {
        val paddle = MviDomainEvents.capture { reduce(returnState().copy(combo = Combo.DISABLED), tick()) }
        assertEquals(200.0, hypot(paddle.value.puck.vx, paddle.value.puck.vy), 0.0001)
        assertFalse(paddle.events.any { it is MviDomainEvent.GiveAndGoCompleted })
        val goal = MviDomainEvents.capture { reduce(goalState().copy(combo = Combo.DISABLED), tick()) }
        assertEquals(1, goal.value.score.playerA)
        assertFalse(goal.events.any { it is MviDomainEvent.SuperGoalScored })
    }

    @Test
    fun `snapshot restore rederives identical reward and match reset clears chain`() {
        val state = returnState()
        val first = MviDomainEvents.capture { reduce(state, tick()) }
        val restored = reduce(first.value, GameAction.RestoreSnapshot(state))
        assertEquals(first, MviDomainEvents.capture { reduce(restored, tick()) })
        val reset = reduce(first.value, GameAction.Reset)
        assertEquals(TouchLedger(), reset.touchLedger)
        assertEquals(config, reset.combo)
    }

    @Test
    fun `invalid balance configuration fails early`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { Combo(giveAndGoMultiplier = Double.NaN) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { Combo(maximumRawSpeed = 0.0) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { Combo(superGoalThreshold = 9) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { Combo(superGoalWindowNs = -1L) }
    }

    private fun eligible(
        contacts: List<PuckTouch>,
        elapsedNs: Long = 2_000_000_000L,
    ): Boolean = ComboMechanics.isGiveAndGo(TouchLedger(contacts), PaddleSide.A, elapsedNs, config)

    private fun chainLength(
        contacts: List<PuckTouch>,
        elapsedNs: Long = 2_000_000_000L,
    ): Int = ComboMechanics.superGoalChainLength(TouchLedger(contacts), PaddleSide.A, elapsedNs, config)
}

internal fun touch(
    source: TouchSource,
    owner: PaddleSide? = PaddleSide.A,
    id: String = source.name,
    at: Long = 0L,
): PuckTouch = PuckTouch(source, owner, id, at, 200.0)

internal fun tick(elapsedNs: Long = 2_000_000_000L): GameAction.Tick = GameAction.Tick(0.01, elapsedNs)

internal fun returnState(side: PaddleSide = PaddleSide.A): MviGameState =
    MviGameState(
        puck =
            MviPuck(
                x = if (side == PaddleSide.A) 31.0 else 769.0,
                y = 300.0,
                vx = if (side == PaddleSide.A) -200.0 else 200.0,
                vy = 0.0,
            ),
        touchLedger = TouchLedger(listOf(touch(TouchSource.PADDLE, side), touch(TouchSource.DRAWN_LINE, side))),
    )

internal fun goalState(side: PaddleSide = PaddleSide.A): MviGameState =
    MviGameState(
        puck =
            MviPuck(
                x = if (side == PaddleSide.A) 795.0 else 5.0,
                y = 100.0,
                vx = if (side == PaddleSide.A) 200.0 else -200.0,
                vy = 0.0,
            ),
        touchLedger =
            TouchLedger(
                listOf(
                    touch(TouchSource.PADDLE, side),
                    touch(TouchSource.DRAWN_LINE, side),
                    touch(TouchSource.PADDLE, side),
                    touch(TouchSource.DRAWN_LINE, side),
                ),
            ),
    )
