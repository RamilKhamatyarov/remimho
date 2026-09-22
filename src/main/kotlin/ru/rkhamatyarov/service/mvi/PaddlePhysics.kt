package ru.rkhamatyarov.service.mvi

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal object PaddlePhysics {
    fun resolve(
        frame: TickFrame,
        state: MviGameState,
        effectiveSpeed: Double,
        elapsedNs: Long,
    ): TickFrame {
        if (state.ghostMode) return frame

        var resolved = resolveSide(frame, state, PaddleSide.A, effectiveSpeed, elapsedNs)
        resolved = resolveSide(resolved, state, PaddleSide.B, effectiveSpeed, elapsedNs)
        return resolveShield(resolved, state, effectiveSpeed, elapsedNs)
    }

    private fun resolveSide(
        frame: TickFrame,
        state: MviGameState,
        side: PaddleSide,
        effectiveSpeed: Double,
        elapsedNs: Long,
    ): TickFrame {
        val impact = frame.puck.paddleImpact(state, side) ?: return frame
        return when (impact.axis) {
            ImpactAxis.FACE ->
                resolveFace(
                    frame = frame.copy(puck = impact.puck),
                    side = side,
                    state = state,
                    effectiveSpeed = effectiveSpeed,
                    elapsedNs = elapsedNs,
                )

            ImpactAxis.EDGE ->
                resolveEdge(
                    frame = frame,
                    impact = impact.puck,
                    side = side,
                    state = state,
                    effectiveSpeed = effectiveSpeed,
                    elapsedNs = elapsedNs,
                )
        }
    }

    private fun resolveFace(
        frame: TickFrame,
        side: PaddleSide,
        state: MviGameState,
        effectiveSpeed: Double,
        elapsedNs: Long,
    ): TickFrame {
        val incomingSpeed = frame.puck.speed(effectiveSpeed)
        val config = state.oneTimerConfig
        val multiplier = oneTimerMultiplier(frame.touchLedger, side, incomingSpeed, elapsedNs, config)
        var outgoing =
            redirect(
                puck = frame.puck,
                paddleY = state.paddleY(side),
                paddleHeight = state.paddleHeight,
                paddleVelocity = state.paddleVelocity(side),
                horizontalDirection = side.horizontalDirection(),
                x = side.faceX(state.canvasWidth, frame.puck.radius),
            )
        outgoing = applyOneTimer(outgoing, side, incomingSpeed, multiplier, elapsedNs, config)
        if (ComboMechanics.isGiveAndGo(frame.touchLedger, side, elapsedNs, state.combo)) {
            outgoing = OneTimerMechanic.apply(outgoing, state.combo.giveAndGoMultiplier, state.combo.maximumRawSpeed)
            MviDomainEvents.record(MviDomainEvent.GiveAndGoCompleted(side))
        }
        return TickFrame(outgoing, frame.touchLedger.append(paddleTouch(side, elapsedNs, incomingSpeed)))
    }

    /** A puck that meets the top or bottom of the paddle rebounds off that edge instead of entering the body. */
    private fun resolveEdge(
        frame: TickFrame,
        impact: MviPuck,
        side: PaddleSide,
        state: MviGameState,
        effectiveSpeed: Double,
        elapsedNs: Long,
    ): TickFrame {
        val incomingSpeed = frame.puck.speed(effectiveSpeed)
        val paddleY = state.paddleY(side)
        val fromAbove = impact.y <= paddleY + state.paddleHeight / 2.0
        val outgoing =
            impact.copy(
                y = if (fromAbove) paddleY - impact.radius else paddleY + state.paddleHeight + impact.radius,
                vy = if (fromAbove) -abs(impact.vy) else abs(impact.vy),
                spin = 0.0,
                spinRemainingNs = 0L,
            )
        MviDomainEvents.record(MviDomainEvent.PaddleHit(side))
        return TickFrame(outgoing, frame.touchLedger.append(paddleTouch(side, elapsedNs, incomingSpeed)))
    }

    private fun oneTimerMultiplier(
        ledger: TouchLedger,
        side: PaddleSide,
        incomingSpeed: Double,
        elapsedNs: Long,
        config: OneTimerConfig,
    ): Double? =
        OneTimerMechanic.multiplier(
            ledger = ledger,
            side = side,
            incomingSpeed = incomingSpeed,
            elapsedNs = elapsedNs,
            config = config,
        )

    private fun applyOneTimer(
        puck: MviPuck,
        side: PaddleSide,
        incomingSpeed: Double,
        multiplier: Double?,
        elapsedNs: Long,
        config: OneTimerConfig,
    ): MviPuck {
        MviDomainEvents.record(MviDomainEvent.PaddleHit(side))
        if (multiplier == null) return puck

        MviDomainEvents.record(
            MviDomainEvent.OneTimerFired(
                side = side,
                incomingSpeed = incomingSpeed,
                multiplier = multiplier,
                elapsedNs = elapsedNs,
            ),
        )
        return OneTimerMechanic.apply(puck, multiplier, config.maximumRawSpeed)
    }

    private fun resolveShield(
        frame: TickFrame,
        state: MviGameState,
        effectiveSpeed: Double,
        elapsedNs: Long,
    ): TickFrame {
        val puck = frame.puck
        if (!state.paddleShield || puck.vx >= 0 || puck.x - puck.radius > 0.0) return frame

        val touch =
            puck.touch(
                source = TouchSource.WALL,
                ownerSide = PaddleSide.A,
                identifier = SHIELD_A_ID,
                elapsedNs = elapsedNs,
                effectiveSpeed = effectiveSpeed,
            )
        return TickFrame(
            puck =
                puck.copy(
                    x = puck.radius,
                    vx = abs(puck.vx),
                    spin = puck.spin * WALL_SPIN_RETENTION,
                ),
            touchLedger = frame.touchLedger.append(touch),
        )
    }

    private fun redirect(
        puck: MviPuck,
        paddleY: Double,
        paddleHeight: Double,
        paddleVelocity: Double,
        horizontalDirection: Double,
        x: Double,
    ): MviPuck {
        val speed = ejectionSpeed(puck)
        val angle = bounceAngle(puck, paddleY, paddleHeight, paddleVelocity)
        val spin = (paddleVelocity / PADDLE_SPIN_NORMALIZER).coerceIn(-MAX_SPIN, MAX_SPIN)
        val hasSpin = abs(spin) >= MIN_SPIN
        return puck.copy(
            x = x,
            vx = horizontalDirection * cos(angle) * speed,
            vy = sin(angle) * speed,
            spin = if (hasSpin) spin else 0.0,
            spinRemainingNs = if (hasSpin) SPIN_DURATION_NS else 0L,
        )
    }

    /** A puck resting on the face carries no usable direction, so it leaves at a playable serve speed. */
    private fun ejectionSpeed(puck: MviPuck): Double {
        val incoming = hypot(puck.vx, puck.vy)
        return if (incoming < MIN_CONTACT_SPEED) MIN_EJECT_SPEED else incoming
    }

    private fun bounceAngle(
        puck: MviPuck,
        paddleY: Double,
        paddleHeight: Double,
        paddleVelocity: Double,
    ): Double {
        val center = paddleY + paddleHeight / 2.0
        val offset = ((puck.y - center) / (paddleHeight / 2.0)).coerceIn(-1.0, 1.0)
        val movement = (paddleVelocity / PADDLE_MOVEMENT_NORMALIZER).coerceIn(-1.0, 1.0)
        return (offset * MAX_BOUNCE_ANGLE + movement * MOVEMENT_ANGLE_INFLUENCE)
            .coerceIn(-MAX_BOUNCE_ANGLE, MAX_BOUNCE_ANGLE)
    }

    /**
     * Sweeps the puck against the whole paddle rectangle rather than only its face plane.
     *
     * The rectangle is expanded by the puck radius and the tick travel is clipped against it with
     * the slab method, so the puck cannot tunnel through the face, clip a corner, or finish a tick
     * inside the paddle body. The arrival height is mirrored back into the playfield first so a
     * puck that rebounds off a wall within the same tick is still tested at its true height.
     */
    private fun MviPuck.paddleImpact(
        state: MviGameState,
        side: PaddleSide,
    ): PaddleImpact? {
        if (separatingFrom(side)) return null

        val start = state.puck
        val paddleY = state.paddleY(side)
        val dx = x - start.x
        val dy = reflectIntoField(y, state.canvasHeight) - start.y

        var enter = 0.0
        var exit = 1.0
        var axis = ImpactAxis.FACE

        val horizontal =
            slab(
                origin = start.x,
                travel = dx,
                low = side.minX(state.canvasWidth) - radius,
                high = side.maxX(state.canvasWidth) + radius,
            ) ?: return null
        if (horizontal.near > enter) {
            enter = horizontal.near
            axis = ImpactAxis.FACE
        }
        exit = min(exit, horizontal.far)

        val vertical =
            slab(
                origin = start.y,
                travel = dy,
                low = paddleY - radius,
                high = paddleY + state.paddleHeight + radius,
            ) ?: return null
        if (vertical.near > enter) {
            enter = vertical.near
            axis = ImpactAxis.EDGE
        }
        exit = min(exit, vertical.far)

        if (enter > exit || enter > 1.0) return null

        return PaddleImpact(axis, copy(x = start.x + dx * enter, y = start.y + dy * enter))
    }

    /** Clips the travel against one axis of the expanded rectangle, or null when it never overlaps. */
    private fun slab(
        origin: Double,
        travel: Double,
        low: Double,
        high: Double,
    ): Slab? {
        if (abs(travel) < MIN_SWEEP_DISTANCE) {
            return if (origin < low || origin > high) null else Slab(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
        }

        val a = (low - origin) / travel
        val b = (high - origin) / travel
        return Slab(min(a, b), max(a, b))
    }

    private fun MviPuck.separatingFrom(side: PaddleSide): Boolean =
        when (side) {
            PaddleSide.A -> vx > 0.0
            PaddleSide.B -> vx < 0.0
        }

    private fun MviPuck.reflectIntoField(
        candidate: Double,
        canvasHeight: Double,
    ): Double {
        val low = radius
        val span = canvasHeight - radius - low
        if (span <= 0.0) return low

        val offset = (candidate - low).mod(2.0 * span)
        return low + if (offset <= span) offset else 2.0 * span - offset
    }

    private fun PaddleSide.minX(canvasWidth: Double): Double =
        when (this) {
            PaddleSide.A -> 0.0
            PaddleSide.B -> canvasWidth - PADDLE_WIDTH
        }

    private fun PaddleSide.maxX(canvasWidth: Double): Double =
        when (this) {
            PaddleSide.A -> PADDLE_WIDTH
            PaddleSide.B -> canvasWidth
        }

    private fun PaddleSide.faceX(
        canvasWidth: Double,
        radius: Double,
    ): Double =
        when (this) {
            PaddleSide.A -> PADDLE_WIDTH + radius
            PaddleSide.B -> canvasWidth - PADDLE_WIDTH - radius
        }

    private fun PaddleSide.horizontalDirection(): Double =
        when (this) {
            PaddleSide.A -> 1.0
            PaddleSide.B -> -1.0
        }

    private fun MviGameState.paddleY(side: PaddleSide): Double =
        when (side) {
            PaddleSide.A -> paddle1Y
            PaddleSide.B -> paddle2Y
        }

    private fun MviGameState.paddleVelocity(side: PaddleSide): Double =
        when (side) {
            PaddleSide.A -> paddle1Velocity
            PaddleSide.B -> paddle2Velocity
        }

    private fun paddleTouch(
        side: PaddleSide,
        elapsedNs: Long,
        incomingSpeed: Double,
    ): PuckTouch =
        PuckTouch(
            source = TouchSource.PADDLE,
            ownerSide = side,
            identifier = "paddle:${side.name}",
            elapsedNs = elapsedNs,
            speedAtContact = incomingSpeed,
        )

    private enum class ImpactAxis {
        FACE,
        EDGE,
    }

    private data class PaddleImpact(
        val axis: ImpactAxis,
        val puck: MviPuck,
    )

    private data class Slab(
        val near: Double,
        val far: Double,
    )

    private const val PADDLE_WIDTH = 20.0
    private const val SHIELD_A_ID = "shield:A"
    private const val MAX_BOUNCE_ANGLE = PI * 0.36
    private const val MOVEMENT_ANGLE_INFLUENCE = PI * 0.08
    private const val PADDLE_MOVEMENT_NORMALIZER = 80.0
    private const val PADDLE_SPIN_NORMALIZER = 90.0
    private const val SPIN_DURATION_NS = 750_000_000L
    private const val WALL_SPIN_RETENTION = 0.75
    private const val MIN_SWEEP_DISTANCE = 1e-9
    private const val MIN_CONTACT_SPEED = 1.0
    private const val MIN_EJECT_SPEED = 120.0
    private const val MAX_SPIN = 1.0
    private const val MIN_SPIN = 0.05
}
