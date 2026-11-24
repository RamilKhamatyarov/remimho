package ru.rkhamatyarov.tunnel

import ru.rkhamatyarov.determinism.Trace
import ru.rkhamatyarov.determinism.TraceComparison
import ru.rkhamatyarov.determinism.TraceDiff
import java.nio.file.Files
import java.nio.file.Path

/** Committed tunnel goldens and the candidate files produced from the scenario scripts. */
object TunnelGoldenFixtures {
    const val GOLDEN_DIRECTORY = "src/test/resources/tunnel/golden"
    const val CANDIDATE_DIRECTORY = "build/tunnel-golden-candidates"

    fun tapeBytes(scenario: TunnelGoldenScenarios.Scenario): ByteArray = TunnelGoldenScenarios.serialize(scenario)

    fun traceFor(
        scenario: TunnelGoldenScenarios.Scenario,
        rules: TunnelRules = TunnelRules(),
    ): Trace =
        TunnelGoldenScenarios.parse(scenario).replayTunnel(
            TunnelGoldenScenarios.initialState(scenario).copy(rules = rules),
            rules,
        )

    /** Every candidate file name and its exact bytes, ready to be compared or written. */
    fun candidates(): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        TunnelGoldenScenarios.all.forEach { scenario ->
            result["${scenario.name}.tape"] = tapeBytes(scenario)
            result["${scenario.name}.trace"] = traceFor(scenario).render().encodeToByteArray()
        }
        return result
    }

    /** Writes the current candidates into [directory], replacing any previous candidate files. */
    fun writeCandidates(directory: Path) {
        Files.createDirectories(directory)
        Files.list(directory).use { entries ->
            entries.filter { it.fileName.toString().endsWith(".tape") || it.fileName.toString().endsWith(".trace") }
                .forEach(Files::deleteIfExists)
        }
        candidates().forEach { (name, bytes) -> Files.write(directory.resolve(name), bytes) }
    }

    /**
     * Compares this build's replay of every committed scenario tape with its committed trace.
     * Returns one human-readable line per divergence; an empty list means the goldens agree.
     */
    fun mismatches(projectDir: Path): List<String> {
        val directory = projectDir.resolve(GOLDEN_DIRECTORY)
        val failures = ArrayList<String>()
        TunnelGoldenScenarios.all.forEach { scenario ->
            val traceFile = directory.resolve("${scenario.name}.trace")
            if (!Files.isRegularFile(traceFile)) {
                failures += "${scenario.name}: missing committed trace $traceFile"
                return@forEach
            }
            val tapeFile = directory.resolve("${scenario.name}.tape")
            if (!Files.isRegularFile(tapeFile)) {
                failures += "${scenario.name}: missing committed tape $tapeFile"
                return@forEach
            }
            val committedTape = Files.readAllBytes(tapeFile)
            if (!committedTape.contentEquals(tapeBytes(scenario))) {
                failures += "${scenario.name}: committed tape does not match the scenario script"
                return@forEach
            }
            val expected = Trace.read(traceFile)
            val actual = traceFor(scenario)
            if (expected.header[Trace.KEY_PHYSICS] != actual.header[Trace.KEY_PHYSICS]) {
                failures +=
                    "${scenario.name}: golden physics version '${expected.header[Trace.KEY_PHYSICS]}' " +
                    "does not match '${actual.header[Trace.KEY_PHYSICS]}'"
                return@forEach
            }
            val comparison = TraceDiff.compare(expected, actual)
            val agrees = comparison is TraceComparison.Agreement && comparison.lengthDifference == 0
            if (!agrees) failures += "${scenario.name}: ${TraceDiff.describe(comparison)}"
        }
        return failures
    }
}
