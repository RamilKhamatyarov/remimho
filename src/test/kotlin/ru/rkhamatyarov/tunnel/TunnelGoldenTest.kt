package ru.rkhamatyarov.tunnel

import ru.rkhamatyarov.determinism.Trace
import ru.rkhamatyarov.determinism.TraceComparison
import ru.rkhamatyarov.determinism.TraceDiff
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class TunnelGoldenTest {
    private val projectDir: Path = Path.of(System.getProperty("user.dir"))
    private val rules = TunnelRules()

    @Test
    fun `committed goldens replay exactly with the current physics`() {
        requireGoldens()
        val failures = TunnelGoldenFixtures.mismatches(projectDir)

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `every physics constant is pinned by at least one golden`() {
        requireGoldens()
        mutations().forEach { (name, mutated) ->
            val detected =
                TunnelGoldenScenarios.all.any { scenario ->
                    val divergence = runCatching { compareWithCommitted(scenario, mutated) }
                    divergence.isFailure || divergence.getOrNull() == false
                }
            assertTrue(detected, "mutating $name did not change any golden trace")
        }
    }

    private fun mutations(): List<Pair<String, TunnelRules>> =
        listOf(
            // Geometry and timing are pinned by the tape header check: any change makes replay refuse.
            "width" to rules.copy(width = TunnelScalar(rules.width.raw + 1L)),
            "height" to rules.copy(height = TunnelScalar(rules.height.raw + 1L)),
            "depth" to rules.copy(depth = TunnelScalar(rules.depth.raw + 1L)),
            "stepDurationNs" to rules.copy(stepDurationNs = rules.stepDurationNs + 1L),
            "physicsVersion" to rules.copy(physicsVersion = "tunnel-v1-mutated"),
            // Physics constants are pinned by the traces: each change must move a golden hash.
            "paddleTravelPerStep" to rules.copy(paddleTravelPerStep = TunnelScalar.ZERO),
            "paddleHalfWidth" to rules.copy(paddleHalfWidth = TunnelMath.fromInt(10L)),
            "paddleHalfHeight" to rules.copy(paddleHalfHeight = TunnelMath.fromInt(10L)),
            "spinCurveAcceleration" to rules.copy(spinCurveAcceleration = TunnelMath.fromRatio(1L, 10L)),
            "spinDecayPerStep" to rules.copy(spinDecayPerStep = TunnelMath.fromRatio(1L, 2L)),
            "paddleSpinGain" to rules.copy(paddleSpinGain = TunnelMath.fromRatio(3L, 20L)),
            "maxSpin" to rules.copy(maxSpin = TunnelMath.fromRatio(1L, 10L)),
        )

    private fun compareWithCommitted(
        scenario: TunnelGoldenScenarios.Scenario,
        mutated: TunnelRules,
    ): Boolean {
        val committed =
            Trace.read(
                projectDir.resolve(TunnelGoldenFixtures.GOLDEN_DIRECTORY).resolve("${scenario.name}.trace"),
            )
        val actual = TunnelGoldenFixtures.traceFor(scenario, mutated)
        val comparison = TraceDiff.compare(committed, actual)
        return comparison is TraceComparison.Agreement && comparison.lengthDifference == 0
    }

    private fun requireGoldens() {
        assertTrue(
            Files.isDirectory(projectDir.resolve(TunnelGoldenFixtures.GOLDEN_DIRECTORY)),
            "missing ${TunnelGoldenFixtures.GOLDEN_DIRECTORY}; run updateTunnelGoldens first",
        )
    }
}
