package ru.rkhamatyarov.tunnel

/** Applies one fixed logical step of tunnel physics. This scaffold has no live integration. */
fun reduceTunnel(
    state: TunnelState,
    inputs: List<TunnelInput>,
    rules: TunnelRules,
): TunnelState {
    val startA = state.paddleA.position
    val startB = state.paddleB.position
    var next = state.copy(rules = rules)
    inputs.forEach { input -> next = next.apply(input, rules) }
    next =
        next.copy(
            paddleA = next.paddleA.move(rules),
            paddleB = next.paddleB.move(rules),
        )
    val deltaA = displacement(startA, next.paddleA.position)
    val deltaB = displacement(startB, next.paddleB.position)
    return TunnelPhysics.advance(next, rules, deltaA, deltaB).copy(
        step = TunnelMath.increment(next.step),
    )
}

private fun displacement(
    from: TunnelVector2,
    to: TunnelVector2,
): TunnelVector2 =
    TunnelVector2(
        TunnelMath.subtract(to.x, from.x),
        TunnelMath.subtract(to.y, from.y),
    )

private fun TunnelState.apply(
    input: TunnelInput,
    rules: TunnelRules,
): TunnelState =
    when (input) {
        is TunnelInput.PaddleTarget -> {
            val clamped = input.position.clamp(rules)
            when (input.side) {
                TunnelSide.A -> copy(paddleA = paddleA.copy(target = clamped))
                TunnelSide.B -> copy(paddleB = paddleB.copy(target = clamped))
            }
        }
        is TunnelInput.Serve -> copy(puck = puck.copy(velocity = input.velocity))
        TunnelInput.Reset -> TunnelState(rules = rules, step = step)
    }

private fun TunnelVector2.clamp(rules: TunnelRules): TunnelVector2 =
    TunnelVector2(
        TunnelMath.clamp(x, TunnelScalar.ZERO, rules.width),
        TunnelMath.clamp(y, TunnelScalar.ZERO, rules.height),
    )

private fun TunnelPaddle.move(rules: TunnelRules): TunnelPaddle =
    copy(
        position =
            TunnelVector2(
                TunnelMath.moveTowards(position.x, target.x, rules.paddleTravelPerStep),
                TunnelMath.moveTowards(position.y, target.y, rules.paddleTravelPerStep),
            ),
    )
