package ru.rkhamatyarov.tunnel

/** Applies one fixed logical step. This scaffold has no puck physics or live integration. */
fun reduceTunnel(
    state: TunnelState,
    inputs: List<TunnelInput>,
    rules: TunnelRules,
): TunnelState {
    var next = state.copy(rules = rules)
    inputs.forEach { input -> next = next.apply(input, rules) }
    return next.copy(
        paddleA = next.paddleA.move(rules),
        paddleB = next.paddleB.move(rules),
        step = TunnelMath.increment(next.step),
    )
}

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
