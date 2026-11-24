package ru.rkhamatyarov.tunnel

/**
 * Swept fixed-step tunnel physics.
 *
 * The puck is a sphere of `puck.radius` travelling through a box of `width` x `height` x `depth`.
 * Paddle A guards the `z = 0` plane and paddle B the `z = depth` plane; both are rectangles in the
 * cross-section. Every operation is integer-only Q32.32 arithmetic from [TunnelMath], so the same
 * recorded step produces the same state on every architecture. No clock and no randomness is read.
 */
internal object TunnelPhysics {
    private val INFINITE = TunnelScalar(Long.MAX_VALUE / 2)
    private val ONE = TunnelScalar.ONE
    private val ZERO = TunnelScalar.ZERO

    /**
     * Advances the puck by one step. [deltaA] and [deltaB] are the paddle displacements already
     * applied this step; they widen the contact rectangle (the swept paddle) and drive spin.
     */
    fun advance(
        state: TunnelState,
        rules: TunnelRules,
        deltaA: TunnelVector2,
        deltaB: TunnelVector2,
    ): TunnelState {
        var puck = state.puck
        var scoreA = state.scoreA
        var scoreB = state.scoreB
        var remaining = ONE
        var reflections = 0

        puck =
            puck.copy(
                velocity =
                    TunnelVector3(
                        TunnelMath.add(puck.velocity.x, TunnelMath.multiply(puck.spin.x, rules.spinCurveAcceleration)),
                        TunnelMath.add(puck.velocity.y, TunnelMath.multiply(puck.spin.y, rules.spinCurveAcceleration)),
                        puck.velocity.z,
                    ),
            )

        while (remaining > ZERO && reflections <= rules.maxReflectionsPerStep) {
            val tX =
                boundaryTime(
                    puck.position.x,
                    puck.velocity.x,
                    puck.radius,
                    TunnelMath.subtract(rules.width, puck.radius),
                )
            val tY =
                boundaryTime(
                    puck.position.y,
                    puck.velocity.y,
                    puck.radius,
                    TunnelMath.subtract(rules.height, puck.radius),
                )
            val tA = paddlePlaneTime(puck, TunnelSide.A, rules)
            val tB = paddlePlaneTime(puck, TunnelSide.B, rules)
            val tPaddle = TunnelMath.min(tA, tB)
            val tWall = TunnelMath.min(tX, tY)
            val t = TunnelMath.min(TunnelMath.min(tWall, tPaddle), remaining)

            puck = move(puck, t)
            val paddleSide = if (tA <= tB) TunnelSide.A else TunnelSide.B
            val reachesPaddle = tPaddle <= tWall && tPaddle <= remaining

            if (reachesPaddle) {
                val paddle = if (paddleSide == TunnelSide.A) state.paddleA else state.paddleB
                val delta = if (paddleSide == TunnelSide.A) deltaA else deltaB
                if (insidePaddle(puck, paddle.position, delta, rules)) {
                    puck = returnFromPaddle(puck, paddleSide, delta, rules)
                    remaining = TunnelMath.subtract(remaining, t)
                    if (remaining <= ZERO) break
                    continue
                }
                if (paddleSide == TunnelSide.A) scoreB++ else scoreA++
                puck =
                    puck.copy(
                        velocity = TunnelVector3(ZERO, ZERO, ZERO),
                        spin = TunnelVector2(ZERO, ZERO),
                        position = puck.position.copy(z = if (paddleSide == TunnelSide.A) ZERO else rules.depth),
                    )
                break
            }

            if (t >= remaining) {
                break
            }

            reflections++
            puck = reflectWall(puck, reflectX = tX <= tY, rules = rules)
            remaining = TunnelMath.subtract(remaining, t)
        }

        if (reflections > rules.maxReflectionsPerStep) {
            puck =
                puck.copy(
                    velocity = TunnelVector3(ZERO, ZERO, ZERO),
                    spin = TunnelVector2(ZERO, ZERO),
                    position = clampToTunnel(puck.position, puck.radius, rules),
                )
        }

        return state.copy(
            puck =
                puck.copy(
                    spin = decay(puck.spin, rules),
                    position = clampToTunnel(puck.position, puck.radius, rules),
                ),
            scoreA = scoreA,
            scoreB = scoreB,
        )
    }

    private fun move(
        puck: TunnelPuck,
        time: TunnelScalar,
    ): TunnelPuck {
        if (time <= ZERO) return puck
        return puck.copy(
            position =
                TunnelVector3(
                    TunnelMath.add(puck.position.x, TunnelMath.multiply(puck.velocity.x, time)),
                    TunnelMath.add(puck.position.y, TunnelMath.multiply(puck.velocity.y, time)),
                    TunnelMath.add(puck.position.z, TunnelMath.multiply(puck.velocity.z, time)),
                ),
        )
    }

    private fun boundaryTime(
        position: TunnelScalar,
        velocity: TunnelScalar,
        minimum: TunnelScalar,
        maximum: TunnelScalar,
    ): TunnelScalar =
        when {
            velocity > ZERO -> {
                if (position >= maximum) ZERO else timeWithinStep(TunnelMath.subtract(maximum, position), velocity)
            }
            velocity < ZERO -> {
                if (position <= minimum) {
                    ZERO
                } else {
                    timeWithinStep(
                        TunnelMath.subtract(position, minimum),
                        TunnelMath.negate(velocity),
                    )
                }
            }
            else -> INFINITE
        }

    /** Time in step fractions, or [INFINITE] when the crossing is beyond this step. */
    private fun timeWithinStep(
        distance: TunnelScalar,
        speed: TunnelScalar,
    ): TunnelScalar {
        if (distance <= ZERO || speed <= ZERO) return INFINITE
        if (distance.raw > speed.raw) return INFINITE
        return TunnelMath.divide(distance, speed)
    }

    private fun paddlePlaneTime(
        puck: TunnelPuck,
        side: TunnelSide,
        rules: TunnelRules,
    ): TunnelScalar {
        val plane = if (side == TunnelSide.A) puck.radius else TunnelMath.subtract(rules.depth, puck.radius)
        return if (side == TunnelSide.A) {
            if (puck.velocity.z >= ZERO) {
                INFINITE
            } else if (puck.position.z <= plane) {
                ZERO
            } else {
                timeWithinStep(TunnelMath.subtract(puck.position.z, plane), TunnelMath.negate(puck.velocity.z))
            }
        } else {
            if (puck.velocity.z <= ZERO) {
                INFINITE
            } else if (puck.position.z >= plane) {
                ZERO
            } else {
                timeWithinStep(TunnelMath.subtract(plane, puck.position.z), puck.velocity.z)
            }
        }
    }

    private fun insidePaddle(
        puck: TunnelPuck,
        paddlePosition: TunnelVector2,
        delta: TunnelVector2,
        rules: TunnelRules,
    ): Boolean {
        val reachX =
            TunnelMath.add(
                TunnelMath.add(rules.paddleHalfWidth, puck.radius),
                TunnelMath.add(rules.paddleTravelPerStep, TunnelMath.absolute(delta.x)),
            )
        val reachY =
            TunnelMath.add(
                TunnelMath.add(rules.paddleHalfHeight, puck.radius),
                TunnelMath.add(rules.paddleTravelPerStep, TunnelMath.absolute(delta.y)),
            )
        val withinX = TunnelMath.absolute(TunnelMath.subtract(puck.position.x, paddlePosition.x)) <= reachX
        val withinY = TunnelMath.absolute(TunnelMath.subtract(puck.position.y, paddlePosition.y)) <= reachY
        return withinX && withinY
    }

    private fun returnFromPaddle(
        puck: TunnelPuck,
        side: TunnelSide,
        delta: TunnelVector2,
        rules: TunnelRules,
    ): TunnelPuck {
        val plane = if (side == TunnelSide.A) puck.radius else TunnelMath.subtract(rules.depth, puck.radius)
        val imparted =
            TunnelVector2(
                TunnelMath.multiply(delta.x, rules.paddleSpinGain),
                TunnelMath.multiply(delta.y, rules.paddleSpinGain),
            )
        return puck.copy(
            position = puck.position.copy(z = plane),
            velocity = puck.velocity.copy(z = TunnelMath.negate(puck.velocity.z)),
            spin =
                TunnelVector2(
                    clampSpin(TunnelMath.add(puck.spin.x, imparted.x), rules.maxSpin),
                    clampSpin(TunnelMath.add(puck.spin.y, imparted.y), rules.maxSpin),
                ),
        )
    }

    private fun clampSpin(
        value: TunnelScalar,
        maximum: TunnelScalar,
    ): TunnelScalar = TunnelMath.clamp(value, TunnelMath.negate(maximum), maximum)

    private fun reflectWall(
        puck: TunnelPuck,
        reflectX: Boolean,
        rules: TunnelRules,
    ): TunnelPuck {
        val position =
            TunnelVector3(
                if (reflectX) {
                    clampAxis(puck.position.x, puck.radius, TunnelMath.subtract(rules.width, puck.radius))
                } else {
                    puck.position.x
                },
                if (reflectX) {
                    puck.position.y
                } else {
                    clampAxis(puck.position.y, puck.radius, TunnelMath.subtract(rules.height, puck.radius))
                },
                puck.position.z,
            )
        val velocity =
            TunnelVector3(
                if (reflectX) TunnelMath.negate(puck.velocity.x) else puck.velocity.x,
                if (reflectX) puck.velocity.y else TunnelMath.negate(puck.velocity.y),
                puck.velocity.z,
            )
        return puck.copy(position = position, velocity = velocity)
    }

    private fun clampAxis(
        value: TunnelScalar,
        minimum: TunnelScalar,
        maximum: TunnelScalar,
    ): TunnelScalar =
        if (minimum > maximum) {
            TunnelMath.divide(TunnelMath.add(minimum, maximum), TunnelScalar(2L * TunnelScalar.SCALE))
        } else {
            TunnelMath.clamp(value, minimum, maximum)
        }

    private fun clampToTunnel(
        position: TunnelVector3,
        radius: TunnelScalar,
        rules: TunnelRules,
    ): TunnelVector3 =
        position.copy(
            x = clampAxis(position.x, radius, TunnelMath.subtract(rules.width, radius)),
            y = clampAxis(position.y, radius, TunnelMath.subtract(rules.height, radius)),
            z = TunnelMath.clamp(position.z, ZERO, rules.depth),
        )

    private fun decay(
        spin: TunnelVector2,
        rules: TunnelRules,
    ): TunnelVector2 =
        TunnelVector2(
            TunnelMath.multiply(spin.x, rules.spinDecayPerStep),
            TunnelMath.multiply(spin.y, rules.spinDecayPerStep),
        )
}
