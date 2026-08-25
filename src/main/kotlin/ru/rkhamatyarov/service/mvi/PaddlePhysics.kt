package ru.rkhamatyarov.service.mvi

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal object PaddlePhysics {
    fun resolve(
        frame: TickFrame,
        state: MviGameState,
        effectiveSpeed: Double,
        elapsedNs: Long,
    ): TickFrame {
        if (state.ghostMode) return frame

        var resolved = resolveLeft(frame, state, effectiveSpeed, elapsedNs)
        resolved = resolveRight(resolved, state, effectiveSpeed, elapsedNs)
        return resolveShield(resolved, state, effectiveSpeed, elapsedNs)
    }

    private fun resolveLeft(
        frame: TickFrame,
        state: MviGameState,
        effectiveSpeed: Double,
        elapsedNs: Long,
    ): TickFrame {
        val puck = frame.puck
        if (!puck.hitsLeftPaddle(state)) return frame
        return resolveContact(
            frame = frame,
            side = PaddleSide.A,
            paddleY = state.paddle1Y,
            paddleHeight = state.paddleHeight,
            paddleVelocity = state.paddle1Velocity,
            horizontalDirection = 1.0,
            x = PADDLE_WIDTH + puck.radius,
            effectiveSpeed = effectiveSpeed,
            elapsedNs = elapsedNs,
            config = state.oneTimerConfig,
        )
    }

    private fun resolveRight(
        frame: TickFrame,
        state: MviGameState,
        effectiveSpeed: Double,
        elapsedNs: Long,
    ): TickFrame {
        val puck = frame.puck
        if (!puck.hitsRightPaddle(state)) return frame
        return resolveContact(
            frame = frame,
            side = PaddleSide.B,
            paddleY = state.paddle2Y,
            paddleHeight = state.paddleHeight,
            paddleVelocity = state.paddle2Velocity,
            horizontalDirection = -1.0,
            x = state.canvasWidth - PADDLE_WIDTH - puck.radius,
            effectiveSpeed = effectiveSpeed,
            elapsedNs = elapsedNs,
            config = state.oneTimerConfig,
        )
    }

    private fun resolveContact(
        frame: TickFrame,
        side: PaddleSide,
        paddleY: Double,
        paddleHeight: Double,
        paddleVelocity: Double,
        horizontalDirection: Double,
        x: Double,
        effectiveSpeed: Double,
        elapsedNs: Long,
        config: OneTimerConfig,
    ): TickFrame {
        val incomingSpeed = frame.puck.speed(effectiveSpeed)
        val multiplier = oneTimerMultiplier(frame.touchLedger, side, incomingSpeed, elapsedNs, config)
        var outgoing =
            redirect(
                puck = frame.puck,
                paddleY = paddleY,
                paddleHeight = paddleHeight,
                paddleVelocity = paddleVelocity,
                horizontalDirection = horizontalDirection,
                x = x,
            )
        outgoing = applyOneTimer(outgoing, side, incomingSpeed, multiplier, elapsedNs, config)
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
        val speed = hypot(puck.vx, puck.vy).coerceAtLeast(MIN_PUCK_SPEED)
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

    private fun MviPuck.hitsLeftPaddle(state: MviGameState): Boolean =
        vx < 0 &&
            x - radius <= PADDLE_WIDTH &&
            x + radius >= 0.0 &&
            overlapsY(state.paddle1Y, state.paddleHeight)

    private fun MviPuck.hitsRightPaddle(state: MviGameState): Boolean {
        val paddleLeft = state.canvasWidth - PADDLE_WIDTH
        return vx > 0 &&
            x + radius >= paddleLeft &&
            x - radius <= state.canvasWidth &&
            overlapsY(state.paddle2Y, state.paddleHeight)
    }

    private fun MviPuck.overlapsY(
        paddleY: Double,
        paddleHeight: Double,
    ): Boolean = y + radius >= paddleY && y - radius <= paddleY + paddleHeight

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

    private const val PADDLE_WIDTH = 20.0
    private const val SHIELD_A_ID = "shield:A"
    private const val MAX_BOUNCE_ANGLE = PI * 0.36
    private const val MOVEMENT_ANGLE_INFLUENCE = PI * 0.08
    private const val PADDLE_MOVEMENT_NORMALIZER = 80.0
    private const val PADDLE_SPIN_NORMALIZER = 90.0
    private const val SPIN_DURATION_NS = 750_000_000L
    private const val WALL_SPIN_RETENTION = 0.75
    private const val MIN_PUCK_SPEED = 1.0
    private const val MAX_SPIN = 1.0
    private const val MIN_SPIN = 0.05
}
