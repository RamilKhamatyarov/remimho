package ru.rkhamatyarov.service.mvi

import kotlin.math.hypot

internal object OneTimerMechanic {
    /** Requires a fresh direct return of a fast opponent paddle touch. */
    fun multiplier(
        ledger: TouchLedger,
        side: PaddleSide,
        incomingSpeed: Double,
        elapsedNs: Long,
        config: OneTimerConfig,
    ): Double? {
        val previous = ledger.entries.lastOrNull() ?: return null
        if (!isEligible(previous, side, incomingSpeed, elapsedNs, config)) return null

        val strengthRange = config.fullStrengthIncomingSpeed - config.minimumIncomingSpeed
        val strength = strength(incomingSpeed, config.minimumIncomingSpeed, strengthRange)
        return config.minimumMultiplier + strength * (config.maximumMultiplier - config.minimumMultiplier)
    }

    fun apply(
        puck: MviPuck,
        multiplier: Double,
        maximumRawSpeed: Double,
    ): MviPuck {
        val rawSpeed = hypot(puck.vx, puck.vy)
        if (rawSpeed <= 0.0 || !rawSpeed.isFinite()) return puck

        val targetSpeed = (rawSpeed * multiplier).coerceAtMost(maximumRawSpeed)
        val scale = targetSpeed / rawSpeed
        return puck.copy(
            vx = puck.vx * scale,
            vy = puck.vy * scale,
        )
    }

    private fun isEligible(
        touch: PuckTouch,
        side: PaddleSide,
        incomingSpeed: Double,
        elapsedNs: Long,
        config: OneTimerConfig,
    ): Boolean {
        val ageNs = elapsedNs - touch.elapsedNs
        return incomingSpeed.isFinite() &&
            touch.source == TouchSource.PADDLE &&
            touch.ownerSide != null &&
            touch.ownerSide != side &&
            ageNs in 0L..config.freshnessWindowNs &&
            incomingSpeed >= config.minimumIncomingSpeed
    }

    private fun strength(
        incomingSpeed: Double,
        minimumIncomingSpeed: Double,
        strengthRange: Double,
    ): Double =
        if (strengthRange <= 0.0) {
            1.0
        } else {
            ((incomingSpeed - minimumIncomingSpeed) / strengthRange).coerceIn(0.0, 1.0)
        }
}
