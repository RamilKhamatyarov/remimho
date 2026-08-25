package ru.rkhamatyarov.service.mvi

import ru.rkhamatyarov.model.PowerUpType
import kotlin.math.hypot

internal object TickReducer {
    /** Tick order is replay-sensitive and must remain stable. */
    fun reduce(
        state: MviGameState,
        deltaSeconds: Double,
        elapsedNs: Long,
        turboSpeedMultiplier: Double,
    ): MviGameState {
        check(deltaSeconds.isFinite()) { "Tick delta must be finite" }
        if (state.paused || deltaSeconds <= 0.0) return state

        val effectiveSpeed = effectiveSpeed(state, turboSpeedMultiplier)
        var frame = TickFrame(PuckPhysics.advance(state.puck, deltaSeconds, effectiveSpeed), state.touchLedger)
        frame = PuckPhysics.resolveWalls(frame, state.canvasHeight, elapsedNs, effectiveSpeed)
        frame = PaddlePhysics.resolve(frame, state, effectiveSpeed, elapsedNs)
        frame = resolveLines(frame, state, elapsedNs, effectiveSpeed)

        val scoring = resolveScore(frame, state)
        val powerUps = resolvePowerUps(scoring.frame, state, elapsedNs, deltaSeconds, effectiveSpeed)
        return finalize(state, scoring.score, powerUps, deltaSeconds)
    }

    private fun effectiveSpeed(
        state: MviGameState,
        turboSpeedMultiplier: Double,
    ): Double {
        val turbo = turboSpeedMultiplier.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        return state.speedMultiplier * progressiveSpeed(state) * turbo
    }

    private fun progressiveSpeed(state: MviGameState): Double {
        val timeFactor = (state.elapsedSeconds / SECONDS_PER_MINUTE) * state.speedConfig.timeAccelerationRate
        val levelFactor = state.lines.size * state.speedConfig.levelAccelerationPerLine
        return (state.speedConfig.baseMultiplier + timeFactor + levelFactor)
            .coerceAtMost(state.speedConfig.maxMultiplier)
    }

    private fun resolveLines(
        frame: TickFrame,
        state: MviGameState,
        elapsedNs: Long,
        effectiveSpeed: Double,
    ): TickFrame {
        val collision =
            LineCollisionPhysics.resolve(
                puck = frame.puck,
                lines = state.lines,
                teleports = state.teleports,
                elapsedNs = elapsedNs,
            )
        val line = collision.line ?: return frame.copy(puck = collision.puck)
        val touch =
            frame.puck.touch(
                source = TouchSource.DRAWN_LINE,
                ownerSide = line.ownerSide,
                identifier = line.id,
                elapsedNs = elapsedNs,
                effectiveSpeed = effectiveSpeed,
            )
        return TickFrame(collision.puck, frame.touchLedger.append(touch))
    }

    private fun resolveScore(
        frame: TickFrame,
        state: MviGameState,
    ): ScoringResult {
        val score =
            when {
                frame.puck.x - frame.puck.radius <= 0.0 -> state.score.copy(playerB = state.score.playerB + 1)
                frame.puck.x + frame.puck.radius >= state.canvasWidth -> state.score.copy(playerA = state.score.playerA + 1)
                else -> state.score
            }
        if (score == state.score) return ScoringResult(frame, score)

        return ScoringResult(
            frame =
                TickFrame(
                    puck = frame.puck.resetForServe(state.canvasWidth, state.canvasHeight),
                    touchLedger = TouchLedger(),
                ),
            score = score,
        )
    }

    private fun resolvePowerUps(
        frame: TickFrame,
        state: MviGameState,
        elapsedNs: Long,
        deltaSeconds: Double,
        effectiveSpeed: Double,
    ): PowerUpResult {
        val active = state.activePowerUps.filterNot { it.isExpired(elapsedNs) }
        val field = state.powerUps.filter { elapsedNs - it.createdNs <= it.lifetimeNs }
        val (remaining, collected) = field.partition { !it.intersects(frame.puck) }
        val nextActive = active + collected.map { it.activate(elapsedNs) }
        val ledger = appendPowerUpTouches(frame, collected, elapsedNs, effectiveSpeed)
        val puck = applyActiveEffects(frame.puck, state, nextActive, deltaSeconds)
        return PowerUpResult(
            frame = TickFrame(puck, ledger),
            field = remaining,
            active = nextActive,
        )
    }

    private fun appendPowerUpTouches(
        frame: TickFrame,
        collected: List<MviPowerUp>,
        elapsedNs: Long,
        effectiveSpeed: Double,
    ): TouchLedger =
        collected.fold(frame.touchLedger) { ledger, powerUp ->
            ledger.append(
                frame.puck.touch(
                    source = TouchSource.POWER_UP,
                    ownerSide = null,
                    identifier = powerUp.id,
                    elapsedNs = elapsedNs,
                    effectiveSpeed = effectiveSpeed,
                ),
            )
        }

    private fun applyActiveEffects(
        puck: MviPuck,
        state: MviGameState,
        active: List<MviActivePowerUp>,
        deltaSeconds: Double,
    ): MviPuck =
        if (active.has(PowerUpType.MAGNET_BALL)) {
            PuckPhysics.applyMagnet(puck, state, deltaSeconds)
        } else {
            puck
        }

    private fun finalize(
        state: MviGameState,
        score: MviScore,
        result: PowerUpResult,
        deltaSeconds: Double,
    ): MviGameState =
        state.copy(
            puck = result.frame.puck,
            score = score,
            paddle1Velocity = 0.0,
            paddle2Velocity = 0.0,
            elapsedSeconds = state.elapsedSeconds + deltaSeconds,
            powerUps = result.field,
            activePowerUps = result.active,
            speedMultiplier = if (result.active.has(PowerUpType.SPEED_BOOST)) SPEED_BOOST_MULTIPLIER else 1.0,
            ghostMode = result.active.has(PowerUpType.GHOST_MODE),
            paddleShield = result.active.has(PowerUpType.PADDLE_SHIELD),
            touchLedger = result.frame.touchLedger,
        )

    private fun MviPowerUp.intersects(puck: MviPuck): Boolean = hypot(puck.x - x, puck.y - y) < radius + puck.radius

    private fun MviPowerUp.activate(elapsedNs: Long): MviActivePowerUp =
        MviActivePowerUp(
            type = type,
            activatedNs = elapsedNs,
            durationNs = PowerUpType.getDuration(type),
        )

    private fun List<MviActivePowerUp>.has(type: PowerUpType): Boolean = any { it.type == type }

    private data class ScoringResult(
        val frame: TickFrame,
        val score: MviScore,
    )

    private data class PowerUpResult(
        val frame: TickFrame,
        val field: List<MviPowerUp>,
        val active: List<MviActivePowerUp>,
    )

    private const val SECONDS_PER_MINUTE = 60.0
    private const val SPEED_BOOST_MULTIPLIER = 1.5
}
