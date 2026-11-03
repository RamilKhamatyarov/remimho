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
        legacyGhostMode: Boolean = false,
    ): MviGameState {
        check(deltaSeconds.isFinite()) { "Tick delta must be finite" }
        if (state.paused || deltaSeconds <= 0.0) return state

        val effectiveSpeed = effectiveSpeed(state, turboSpeedMultiplier)
        var frame =
            BoundaryPhysics.resolve(
                state = state,
                advanced = PuckPhysics.advance(state.puck, deltaSeconds, effectiveSpeed),
                effectiveSpeed = effectiveSpeed,
                elapsedNs = elapsedNs,
                legacyGhostPassThrough = legacyGhostMode && state.ghostMode,
            )
        frame = resolveLines(frame, state, elapsedNs, effectiveSpeed)

        val scoring = resolveScore(frame, state, elapsedNs, legacyGhostMode)
        val powerUps = resolvePowerUps(scoring.frame, state, elapsedNs, deltaSeconds, effectiveSpeed)
        return finalize(state, scoring.score, powerUps, deltaSeconds, legacyGhostMode)
    }

    private fun effectiveSpeed(
        state: MviGameState,
        turboSpeedMultiplier: Double,
    ): Double = SpeedModel.effectiveMultiplier(state, turboSpeedMultiplier)

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
        elapsedNs: Long,
        legacyGhostMode: Boolean,
    ): ScoringResult {
        val side =
            when {
                frame.puck.x - frame.puck.radius <= 0.0 -> PaddleSide.B
                frame.puck.x + frame.puck.radius >= state.canvasWidth -> PaddleSide.A
                else -> return ScoringResult(frame, state.score)
            }
        if (frame.touchLedger.defendedThisTick(side.opponent(), elapsedNs, includeShield = !legacyGhostMode)) {
            return ScoringResult(frame, state.score)
        }
        val chainLength = ComboMechanics.superGoalChainLength(frame.touchLedger, side, elapsedNs, state.combo)
        val points = if (chainLength > 0) 2 else 1
        if (chainLength > 0) MviDomainEvents.record(MviDomainEvent.SuperGoalScored(side, chainLength))
        val score =
            when (side) {
                PaddleSide.A -> state.score.copy(playerA = state.score.playerA + points)
                PaddleSide.B -> state.score.copy(playerB = state.score.playerB + points)
            }

        return ScoringResult(
            frame =
                TickFrame(
                    puck = frame.puck.resetForServe(state.canvasWidth, state.canvasHeight, state.serveSide.opponent()),
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
        val active =
            state.activePowerUps.filterNot {
                it.isExpired(elapsedNs) ||
                    (it.type == PowerUpType.RABBIT_MODE && elapsedNs - it.activatedNs >= it.durationNs)
            }
        val field = state.powerUps.filter { elapsedNs - it.createdNs <= it.lifetimeNs }
        val (remaining, collected) = field.partition { !it.intersects(frame.puck) }
        val nextActive = RabbitPhysics.refresh(active, collected, elapsedNs)
        val ledger = appendPowerUpTouches(frame, collected, elapsedNs, effectiveSpeed)
        val puck = applyActiveEffects(frame.puck, state, nextActive, deltaSeconds)
        val rabbit = RabbitPhysics.apply(puck, nextActive, elapsedNs)
        return PowerUpResult(
            frame = TickFrame(rabbit.puck, ledger),
            field = remaining,
            active = rabbit.active,
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
        legacyGhostMode: Boolean,
    ): MviGameState =
        state.copy(
            puck = result.frame.puck,
            score = score,
            serveSide = if (score != state.score) state.serveSide.opponent() else state.serveSide,
            paddle1Velocity = 0.0,
            paddle2Velocity = 0.0,
            elapsedSeconds = state.elapsedSeconds + deltaSeconds,
            powerUps = result.field,
            activePowerUps = result.active,
            speedMultiplier = if (result.active.has(PowerUpType.SPEED_BOOST)) SPEED_BOOST_MULTIPLIER else 1.0,
            ghostMode = getLegacyGhostMode(legacyGhostMode, result),
            paddleShield = result.active.has(PowerUpType.PADDLE_SHIELD),
            touchLedger =
                if (state.combo.enabled && score != state.score) TouchLedger() else result.frame.touchLedger,
        )

    private fun getLegacyGhostMode(
        legacyGhostMode: Boolean,
        result: PowerUpResult,
    ): Boolean = legacyGhostMode && result.active.has(PowerUpType.GHOST_MODE)

    /** A paddle or current-rules shield save must not also award a goal at the boundary. */
    private fun TouchLedger.defendedThisTick(
        side: PaddleSide,
        elapsedNs: Long,
        includeShield: Boolean,
    ): Boolean =
        entries.any {
            it.ownerSide == side && it.elapsedNs == elapsedNs &&
                (it.source == TouchSource.PADDLE || (includeShield && it.source == TouchSource.WALL))
        }

    private fun MviPowerUp.intersects(puck: MviPuck): Boolean = hypot(puck.x - x, puck.y - y) < radius + puck.radius

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

    private const val SPEED_BOOST_MULTIPLIER = 1.5
}
