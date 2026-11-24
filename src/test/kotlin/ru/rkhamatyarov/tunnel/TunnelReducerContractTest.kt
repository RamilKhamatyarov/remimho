package ru.rkhamatyarov.tunnel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TunnelReducerContractTest {
    private val rules = TunnelRules()

    @Test
    fun `reducer accepts immutable state ordered inputs and explicit rules`() {
        val start = TunnelState(rules = rules)
        val first = TunnelVector3(TunnelMath.fromInt(10L), TunnelScalar.ZERO, TunnelScalar.ZERO)
        val second = TunnelVector3(TunnelMath.fromInt(20L), TunnelScalar.ZERO, TunnelScalar.ZERO)

        val reduced = reduceTunnel(start, listOf(TunnelInput.Serve(first), TunnelInput.Serve(second)), rules)

        assertEquals(second, reduced.puck.velocity)
        assertEquals(start, TunnelState(rules = rules))
        assertNotEquals(reduced, start)
    }

    @Test
    fun `reducer has no random or wall clock dependencies`() {
        val start = TunnelState(rules = rules)
        val inputs =
            listOf(
                TunnelInput.Serve(
                    TunnelVector3(TunnelMath.fromInt(300L), TunnelMath.fromInt(120L), TunnelMath.fromInt(90L)),
                ),
            )

        val first =
            (1..50).fold(
                start,
            ) { state, _ -> reduceTunnel(state, inputs.takeIf { state.step == 0L }.orEmpty(), rules) }
        val second =
            (1..50).fold(start) { state, _ ->
                reduceTunnel(
                    state,
                    inputs.takeIf {
                        state.step == 0L
                    }.orEmpty(),
                    rules,
                )
            }

        assertEquals(first, second)
    }

    @Test
    fun `fixed tape vectors produce the same per-step state hashes`() {
        val scenario = TunnelGoldenScenarios.all.first()

        val first = TunnelGoldenFixtures.traceFor(scenario, rules)
        val second = TunnelGoldenFixtures.traceFor(scenario, rules)

        assertEquals(first.entries, second.entries)
    }
}
