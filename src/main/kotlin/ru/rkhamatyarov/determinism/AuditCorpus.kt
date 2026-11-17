package ru.rkhamatyarov.determinism

import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.model.SpeedConfig
import ru.rkhamatyarov.proto.ReplayFile
import ru.rkhamatyarov.proto.ReplayIntent
import ru.rkhamatyarov.replay.ReplayConverter
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.GameIntent
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviLine
import ru.rkhamatyarov.service.mvi.MviPoint
import ru.rkhamatyarov.service.mvi.MviPowerUp
import ru.rkhamatyarov.service.mvi.MviPuck
import ru.rkhamatyarov.service.mvi.OneTimerConfig
import ru.rkhamatyarov.service.mvi.PaddleSide

/**
 * Scripted 2D recordings for the determinism audit.
 *
 * Inputs never depend on simulated state and are built from integer arithmetic plus exact
 * conversions only, so the recordings themselves are byte-identical on every platform; whatever
 * differs between two machines must then come from the reducer.
 */
object AuditCorpus {
    data class Recording(
        val name: String,
        val purpose: String,
        val replay: ReplayFile,
    )

    fun recordings(): List<Recording> =
        listOf(
            Recording("bounce-angles", "paddle face returns at many angles (sin, cos, hypot)", bounceAngles("3")),
            Recording("bounce-angles-v1", "version 1 rules with legacy ghost pickups", bounceAngles("1")),
            Recording("bounce-angles-v2", "version 2 rules with per-tick spin decay", bounceAngles("2")),
            Recording("line-collisions", "drawn-line reflections (atan2, sin, cos)", lineCollisions()),
            Recording(
                "rabbit-turns",
                "rabbit-mode redirects (sin, cos)",
                powerUps(listOf(PowerUpType.RABBIT_MODE), 7L),
            ),
            Recording("power-ups", "every live power-up type in turn", powerUps(LIVE_POWER_UPS, 11L)),
            Recording("spin-decay", "long-lived spin with half-life decay (StrictMath.pow)", spinDecay()),
            Recording("edge-hit-swipe", "paddle edge contact with a swiping paddle", edgeHit(EDGE_SWIPE)),
            Recording("edge-hit-retreat", "face contact with a retreating paddle", edgeHit(EDGE_RETREAT)),
            Recording("edge-hit-spinning", "spinning puck against the top wall", edgeHit(EDGE_SPINNING)),
            Recording("extreme-speed", "largest speeds the workshop accepts", extremeSpeed()),
        )

    private val LIVE_POWER_UPS =
        listOf(
            PowerUpType.SPEED_BOOST,
            PowerUpType.MAGNET_BALL,
            PowerUpType.RABBIT_MODE,
            PowerUpType.MULTI_BALL,
            PowerUpType.PADDLE_SHIELD,
        )

    private val EDGE_SWIPE =
        MviGameState(puck = MviPuck(x = 15.0, y = 224.0, vx = 10.0, vy = 2000.0), paddle1Velocity = 20.0)
    private val EDGE_RETREAT = MviGameState(puck = MviPuck(x = 40.0, vx = -1000.0, vy = 0.0), paddle1Velocity = -20.0)
    private val EDGE_SPINNING =
        MviGameState(puck = MviPuck(y = 20.0, vx = 100.0, vy = -200.0, spin = 0.5, spinRemainingNs = 750_000_000L))

    private fun bounceAngles(version: String): ReplayFile {
        val script = Script(seed = 1L)
        repeat(LONG_RUN_TICKS) { tick ->
            if (version == "1" && tick % GHOST_INTERVAL_TICKS == 0) {
                script.spawn(PowerUpType.GHOST_MODE, "ghost-$tick")
            }
            script.movePaddles(tick, jitter = 60)
            script.tick()
        }
        return script.build(version, MviGameState(puck = MviPuck(vx = -420.0, vy = 260.0)), withOneTimer = true)
    }

    private fun lineCollisions(): ReplayFile {
        val script = Script(seed = 3L)
        val lines =
            listOf(
                listOf(200 to 100, 320 to 220),
                listOf(600 to 120, 480 to 260),
                listOf(250 to 500, 400 to 380, 550 to 500),
                listOf(380 to 60, 420 to 61),
                listOf(150 to 300, 151 to 420),
                listOf(640 to 330, 700 to 470, 610 to 540),
            )
        lines.forEachIndexed { index, points ->
            script.act(
                GameAction.CommitLine(
                    MviLine(
                        id = "audit-line-$index",
                        points = points.map { (x, y) -> MviPoint(x.toDouble(), y.toDouble()) },
                        width = (4 + index).toDouble(),
                        ownerSide = if (index % 2 == 0) PaddleSide.A else PaddleSide.B,
                    ),
                ),
            )
        }
        repeat(LONG_RUN_TICKS) { tick ->
            script.movePaddles(tick, jitter = 30)
            script.tick()
        }
        return script.build("3", MviGameState(puck = MviPuck(vx = 350.0, vy = -310.0)))
    }

    private fun powerUps(
        types: List<PowerUpType>,
        seed: Long,
    ): ReplayFile {
        val script = Script(seed)
        repeat(LONG_RUN_TICKS) { tick ->
            if (tick % POWER_UP_INTERVAL_TICKS == 0) {
                val spawned = tick / POWER_UP_INTERVAL_TICKS
                script.spawn(types[spawned % types.size], "power-$spawned")
            }
            script.movePaddles(tick, jitter = 40)
            script.tick()
        }
        return script.build("3", MviGameState(puck = MviPuck(vx = 390.0, vy = 240.0)))
    }

    private fun spinDecay(): ReplayFile {
        val script = Script(seed = 5L)
        repeat(LONG_RUN_TICKS) { tick ->
            script.movePaddles(tick, jitter = 140)
            script.tick()
        }
        val start = MviGameState(puck = MviPuck(vx = -260.0, vy = 150.0, spin = 1.5, spinRemainingNs = 2_000_000_000L))
        return script.build("3", start)
    }

    private fun edgeHit(start: MviGameState): ReplayFile {
        val script = Script(seed = 9L)
        repeat(SHORT_RUN_TICKS) { tick ->
            if (tick >= EDGE_SETTLE_TICKS) script.movePaddles(tick, jitter = 90)
            script.tick()
        }
        return script.build("3", start)
    }

    private fun extremeSpeed(): ReplayFile {
        val script = Script(seed = 13L)
        script.act(
            GameAction.ApplySpeedConfig(
                SpeedConfig(
                    maxRallySpeed = SpeedConfig.MAX_ALLOWED_RALLY_SPEED,
                    maxEffectiveSpeed = SpeedConfig.MAX_ALLOWED_EFFECTIVE_SPEED,
                ),
            ),
        )
        repeat(LONG_RUN_TICKS) { tick ->
            script.movePaddles(tick, jitter = 200)
            script.tick()
        }
        return script.build("3", MviGameState(puck = MviPuck(vx = -5000.0, vy = 3100.0)))
    }

    /** Appends intents on a logical clock; all randomness comes from one integer generator. */
    private class Script(
        seed: Long,
    ) {
        private val intents = ArrayList<ReplayIntent>()
        private var elapsedNs = 0L
        private var random = seed

        fun act(action: GameAction) {
            intents +=
                checkNotNull(
                    ReplayConverter.toProto(GameIntent.Reliable(action)),
                ) { "unrecordable action $action" }
        }

        fun tick() {
            val deltaNs = TICK_DURATIONS_NS[next(TICK_DURATIONS_NS.size)]
            elapsedNs += deltaNs
            act(GameAction.Tick(deltaSeconds = deltaNs / NANOS_PER_SECOND, elapsedNs = elapsedNs))
        }

        fun movePaddles(
            tick: Int,
            jitter: Int,
        ) {
            act(GameAction.MovePaddle(y = sweep(tick, PADDLE_A_PERIOD, jitter), side = PaddleSide.A))
            act(GameAction.MovePaddle(y = sweep(tick + PADDLE_B_PHASE, PADDLE_B_PERIOD, jitter), side = PaddleSide.B))
        }

        fun spawn(
            type: PowerUpType,
            id: String,
        ) {
            act(
                GameAction.SpawnPowerUp(
                    MviPowerUp(
                        id = id,
                        x = (SPAWN_MARGIN + next(FIELD_WIDTH - 2 * SPAWN_MARGIN)).toDouble(),
                        y = (SPAWN_MARGIN + next(FIELD_HEIGHT - 2 * SPAWN_MARGIN)).toDouble(),
                        type = type,
                        createdNs = elapsedNs,
                        radius = SPAWN_RADIUS,
                    ),
                ),
            )
        }

        fun build(
            version: String,
            start: MviGameState,
            withOneTimer: Boolean = false,
        ): ReplayFile {
            val builder =
                ReplayFile
                    .newBuilder()
                    .setVersion(version)
                    .setStartingState(ReplayConverter.stateToSnapshot(start))
                    .addAllIntents(intents)
            if (withOneTimer) builder.oneTimerConfig = ReplayConverter.oneTimerConfigToProto(OneTimerConfig())
            return builder.build()
        }

        /** Triangle wave across the field height with a bounded pseudo-random offset. */
        private fun sweep(
            tick: Int,
            period: Int,
            jitter: Int,
        ): Double {
            val phase = tick % period
            val half = period / 2
            val rising = if (phase < half) phase else period - phase
            val base = rising * FIELD_HEIGHT / half
            return (base + next(2 * jitter + 1) - jitter).toDouble()
        }

        private fun next(bound: Int): Int {
            random = random * LCG_MULTIPLIER + LCG_INCREMENT
            return ((random ushr LCG_SHIFT) % bound).toInt()
        }
    }

    private const val LONG_RUN_TICKS = 2400
    private const val SHORT_RUN_TICKS = 600
    private const val EDGE_SETTLE_TICKS = 60
    private const val GHOST_INTERVAL_TICKS = 400
    private const val POWER_UP_INTERVAL_TICKS = 90
    private const val PADDLE_A_PERIOD = 180
    private const val PADDLE_B_PERIOD = 230
    private const val PADDLE_B_PHASE = 57
    private const val FIELD_WIDTH = 800
    private const val FIELD_HEIGHT = 600
    private const val SPAWN_MARGIN = 120
    private const val SPAWN_RADIUS = 45.0
    private const val NANOS_PER_SECOND = 1_000_000_000.0
    private const val LCG_MULTIPLIER = 6364136223846793005L
    private const val LCG_INCREMENT = 1442695040888963407L
    private const val LCG_SHIFT = 33
    private val TICK_DURATIONS_NS = longArrayOf(8_000_000L, 16_000_000L, 16_666_667L, 33_000_000L)
}
