package ru.rkhamatyarov.determinism

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviPuck
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TraceDiffTest {
    private val header =
        mapOf(Trace.KEY_SOURCE to "abc", Trace.KEY_KIND to "test", Trace.KEY_PHYSICS to "1", Trace.KEY_SUBSTRATE to "s")

    private fun trace(
        steps: Int,
        differentFrom: Int? = null,
        header: Map<String, String> = this.header,
    ): Trace =
        Trace(
            header,
            (1..steps).map { step ->
                TraceEntry(
                    step.toLong(),
                    if (differentFrom != null && step >= differentFrom) -step.toLong() else step * 31L,
                )
            },
        )

    @Test
    fun `a trace survives writing and reading`(
        @TempDir dir: Path,
    ) {
        val original =
            Trace(
                header + Trace.environment(),
                listOf(TraceEntry(1, Long.MIN_VALUE), TraceEntry(2, -1L), TraceEntry(3, 7L)),
            )
        val file = dir.resolve("nested/a.trace")
        original.write(file)
        assertEquals(original, Trace.read(file))
    }

    @Test
    fun `divergence in the middle reports the first divergent step`() {
        val comparison = TraceDiff.compare(trace(3000), trace(3000, differentFrom = 1500))
        assertEquals(1500L, assertIs<TraceComparison.Divergence>(comparison).step)
        assertTrue(TraceDiff.describe(comparison).contains("first divergent step 1500"))
    }

    @Test
    fun `identical traces agree and report the steps compared`() {
        assertEquals(
            TraceComparison.Agreement(stepsCompared = 3000, lengthDifference = 0),
            TraceDiff.compare(trace(3000), trace(3000)),
        )
    }

    @Test
    fun `a shorter trace agrees on the shared steps and states the length difference`() {
        val comparison = TraceDiff.compare(trace(3000), trace(2500))
        assertEquals(TraceComparison.Agreement(stepsCompared = 2500, lengthDifference = 500), comparison)
        assertTrue(TraceDiff.describe(comparison).contains("left has 500 more steps"))
    }

    @Test
    fun `traces of different simulations are refused with the differing header values`() {
        val other = trace(10, header = header + (Trace.KEY_SOURCE to "xyz") + (Trace.KEY_PHYSICS to "2"))
        val refused = assertIs<TraceComparison.Refused>(TraceDiff.compare(trace(10), other))
        assertEquals(listOf("source: abc vs xyz", "physics: 1 vs 2"), refused.differences)
    }

    @Test
    fun `where the run happened does not prevent comparison`() {
        val elsewhere = trace(10, header = header + (Trace.KEY_ARCH to "aarch64") + (Trace.KEY_JVM to "other"))
        assertIs<TraceComparison.Agreement>(
            TraceDiff.compare(trace(10, header = header + Trace.environment()), elsewhere),
        )
    }

    @Test
    fun `a last-bit difference is listed alone with distinguishable values`() {
        val left = MviGameState(puck = MviPuck(vx = 300.0))
        val right = MviGameState(puck = MviPuck(vx = Math.nextUp(300.0)))
        val differences = TraceDiff.fields(MviGameStateEncoder.dumpOf(left), MviGameStateEncoder.dumpOf(right))
        assertEquals(listOf("puck.vx"), differences.map { it.path })
        val only = differences.single()
        assertTrue(only.left!!.exact != only.right!!.exact)
        assertTrue(
            only.describe().contains("d:4072c00000000000") && only.describe().contains("d:4072c00000000001"),
            only.describe(),
        )
    }

    @Test
    fun `exactly the differing fields are listed`() {
        val left = MviGameState()
        val right = MviGameState(puck = MviPuck(y = 301.0), paddle2Y = 251.0)
        val differences = TraceDiff.fields(MviGameStateEncoder.dumpOf(left), MviGameStateEncoder.dumpOf(right))
        assertEquals(listOf("puck.y", "paddle2Y"), differences.map { it.path })
    }

    @Test
    fun `a state dump survives writing and reading`(
        @TempDir dir: Path,
    ) {
        val dump = StateDump(header, 12, MviGameStateEncoder.dumpOf(HashVectors.states().getValue("populated")))
        val file = dir.resolve("s.dump")
        dump.write(file)
        assertEquals(dump, StateDump.read(file))
    }

    @Test
    fun `the tool exits with distinct statuses and lists fields against a foreign dump`(
        @TempDir dir: Path,
    ) {
        val recordings = AuditCorpus.recordings()
        val replay =
            dir.resolve("a.replay").also {
                Files.write(it, recordings.first { it.name == "edge-hit-swipe" }.replay.toByteArray())
            }
        val trace = dir.resolve("a.trace")
        val output = ArrayList<String>()
        assertEquals(DeterminismCli.EXIT_OK, DeterminismCli.run(listOf("record", "$replay", "$trace"), output::add))
        assertEquals(DeterminismCli.EXIT_OK, DeterminismCli.run(listOf("diff", "$replay", "$trace"), output::add))
        assertTrue(output.last().startsWith("AGREE: 600 steps"), output.last())

        // A trace from "another machine" that disagrees from step 40, with that machine's state.
        val recorded = Trace.read(trace)
        val foreign =
            recorded.copy(entries = recorded.entries.map { if (it.step >= 40) it.copy(hash = it.hash xor 1L) else it })
        val foreignTrace = dir.resolve("foreign.trace").also(foreign::write)
        val local = RunLoader.load(replay).dumpAt(40)!!
        val nudged =
            local.fields.map {
                if (it.path == "puck.vy") {
                    it.copy(
                        exact = it.exact + "ff",
                        readable = "other",
                    )
                } else {
                    it
                }
            }
        val foreignDump = dir.resolve("foreign.dump").also(local.copy(fields = nudged)::write)

        output.clear()
        assertEquals(
            DeterminismCli.EXIT_DIVERGED,
            DeterminismCli.run(listOf("diff", "$replay", "$foreignTrace"), output::add),
        )
        assertTrue(output.first().contains("first divergent step 40"), output.first())
        assertTrue(output.any { it.contains("traceDump") })

        output.clear()
        val withDump = listOf("diff", "$replay", "$foreignTrace", "--dump", "$foreignDump")
        assertEquals(DeterminismCli.EXIT_DIVERGED, DeterminismCli.run(withDump, output::add))
        assertTrue(output.contains("1 differing field(s) at step 40:"), output.joinToString("\n"))
        assertTrue(output.last().trim().startsWith("puck.vy:"), output.last())

        // Traces of two different recordings must not be compared.
        val otherReplay =
            dir.resolve("b.replay").also {
                Files.write(it, recordings.first { it.name == "edge-hit-retreat" }.replay.toByteArray())
            }
        output.clear()
        assertEquals(
            DeterminismCli.EXIT_REFUSED,
            DeterminismCli.run(listOf("diff", "$replay", "$otherReplay"), output::add),
        )
        assertTrue(output.joinToString("\n").contains("source:"))
    }
}
