package ru.rkhamatyarov.service.mvi

import kotlin.math.abs
import kotlin.math.hypot

internal data class TickFrame(
    val puck: MviPuck,
    val touchLedger: TouchLedger,
)

internal object PuckPhysics {
    fun advance(
        puck: MviPuck,
        deltaSeconds: Double,
        speedMultiplier: Double,
    ): MviPuck {
        val curved = applySpinCurve(puck, deltaSeconds)
        return curved.copy(
            x = curved.x + curved.vx * speedMultiplier * deltaSeconds,
            y = curved.y + curved.vy * speedMultiplier * deltaSeconds,
        )
    }

    fun resolveWalls(
        frame: TickFrame,
        canvasHeight: Double,
        elapsedNs: Long,
        effectiveSpeed: Double,
    ): TickFrame =
        when {
            frame.puck.y - frame.puck.radius <= 0.0 -> {
                frame.wallBounce(
                    y = frame.puck.radius,
                    vy = abs(frame.puck.vy),
                    identifier = WALL_TOP_ID,
                    elapsedNs = elapsedNs,
                    effectiveSpeed = effectiveSpeed,
                )
            }

            frame.puck.y + frame.puck.radius >= canvasHeight -> {
                frame.wallBounce(
                    y = canvasHeight - frame.puck.radius,
                    vy = -abs(frame.puck.vy),
                    identifier = WALL_BOTTOM_ID,
                    elapsedNs = elapsedNs,
                    effectiveSpeed = effectiveSpeed,
                )
            }

            else -> {
                frame
            }
        }

    fun applyMagnet(
        puck: MviPuck,
        state: MviGameState,
        deltaSeconds: Double,
    ): MviPuck {
        val centerX = state.canvasWidth - puck.radius
        val centerY = state.paddle2Y + state.paddleHeight / 2
        val distance = hypot(puck.x - centerX, puck.y - centerY)
        if (distance !in MIN_MAGNET_DISTANCE..MAGNET_RANGE) return puck

        val acceleration = MAGNET_STRENGTH * deltaSeconds * TICKS_PER_SECOND
        return puck.copy(
            vx = puck.vx + (centerX - puck.x) / distance * acceleration,
            vy = puck.vy + (centerY - puck.y) / distance * acceleration,
        )
    }

    private fun TickFrame.wallBounce(
        y: Double,
        vy: Double,
        identifier: String,
        elapsedNs: Long,
        effectiveSpeed: Double,
    ): TickFrame {
        val touch =
            puck.touch(
                source = TouchSource.WALL,
                ownerSide = null,
                identifier = identifier,
                elapsedNs = elapsedNs,
                effectiveSpeed = effectiveSpeed,
            )
        return TickFrame(
            puck = puck.copy(y = y, vy = vy, spin = puck.spin * WALL_SPIN_RETENTION),
            touchLedger = touchLedger.append(touch),
        )
    }

    private fun applySpinCurve(
        puck: MviPuck,
        deltaSeconds: Double,
    ): MviPuck {
        if (puck.spinRemainingNs <= 0L || !puck.spin.isFinite() || abs(puck.spin) < MIN_SPIN) {
            return puck.copy(spin = 0.0, spinRemainingNs = 0L)
        }

        val elapsedNs = (deltaSeconds * NANOS_PER_SECOND).toLong().coerceAtLeast(0L)
        val remainingNs = (puck.spinRemainingNs - elapsedNs).coerceAtLeast(0L)
        if (remainingNs == 0L) return puck.copy(spin = 0.0, spinRemainingNs = 0L)

        val nextSpin = puck.spin * SPIN_DECAY_PER_TICK
        return puck.copy(
            vy = puck.vy + nextSpin * SPIN_CURVE_ACCELERATION * deltaSeconds,
            spin = nextSpin,
            spinRemainingNs = remainingNs,
        )
    }

    private const val WALL_TOP_ID = "wall:top"
    private const val WALL_BOTTOM_ID = "wall:bottom"
    private const val MAGNET_RANGE = 150.0
    private const val MIN_MAGNET_DISTANCE = 1e-9
    private const val MAGNET_STRENGTH = 0.3
    private const val TICKS_PER_SECOND = 60.0
    private const val SPIN_CURVE_ACCELERATION = 260.0
    private const val SPIN_DECAY_PER_TICK = 0.96
    private const val WALL_SPIN_RETENTION = 0.75
    private const val MIN_SPIN = 0.05
    private const val NANOS_PER_SECOND = 1_000_000_000.0
}

internal fun MviPuck.touch(
    source: TouchSource,
    ownerSide: PaddleSide?,
    identifier: String,
    elapsedNs: Long,
    effectiveSpeed: Double,
): PuckTouch =
    PuckTouch(
        source = source,
        ownerSide = ownerSide,
        identifier = identifier,
        elapsedNs = elapsedNs,
        speedAtContact = speed(effectiveSpeed),
    )

internal fun MviPuck.speed(multiplier: Double): Double = hypot(vx, vy) * multiplier
