package ru.rkhamatyarov.determinism

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.rkhamatyarov.proto.ReplayFile
import ru.rkhamatyarov.replay.HeadlessReplayImporter
import ru.rkhamatyarov.service.mvi.GameAction
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuditCorpusTest {
    private val projectDir: Path = Path.of("").toAbsolutePath()

    @Test
    fun `audit rejects an otherwise matching truncated reference`(
        @TempDir directory: Path,
    ) {
        val reference = directory.resolve("reference")
        Audit.runs(projectDir).forEach { (name, run) ->
            val trace = run.record()
            trace.copy(entries = trace.entries.dropLast(1)).write(reference.resolve("$name.trace"))
        }
        val result =
            Audit.audit(
                listOf("audit", "$projectDir", "${directory.resolve("actual")}", "--compare", "$reference"),
            ) {}
        assertEquals(DeterminismCli.EXIT_DIVERGED, result)
    }

    @Test
    fun `every generator produces a byte-identical recording on two runs`() {
        val first = AuditCorpus.recordings()
        val second = AuditCorpus.recordings()
        assertEquals(first.map { it.name }, second.map { it.name })
        first.zip(second).forEach {
                (a, b) ->
            assertContentEquals(a.replay.toByteArray(), b.replay.toByteArray(), a.name)
        }
        assertEquals(first.size, first.map { it.name }.toSet().size)
    }

    @Test
    fun `a 2D replay yields exactly one hash per reduced tick in order`() {
        AuditCorpus.recordings().forEach { recording ->
            val ticks = recording.replay.intentsList.count { it.hasTick() }
            val trace = ReplayRun(recording.replay).record()
            assertEquals(ticks, trace.entries.size, recording.name)
            assertEquals((1..ticks.toLong()).toList(), trace.entries.map { it.step }, recording.name)
        }
    }

    @Test
    fun `observing an import does not change its result`() {
        val demo = ReplayFile.parseFrom(Files.readAllBytes(projectDir.resolve("src/test/resources/demo.replay")))
        (AuditCorpus.recordings().map { it.replay } + demo).forEach { replay ->
            var observed = 0
            val plain = HeadlessReplayImporter().import(replay)
            val watched =
                HeadlessReplayImporter().importObserved(replay) { _, action, state ->
                    observed++
                    if (action is GameAction.Tick) MviGameStateEncoder.hashOf(state)
                }
            assertEquals(plain.finalState, watched.finalState)
            assertEquals(plain.frameCount, watched.frameCount)
            assertEquals(replay.intentsCount, observed)
        }
    }

    @Test
    fun `the corpus exercises goals, contacts, spin and power-ups`() {
        val finals =
            AuditCorpus.recordings().associate {
                it.name to
                    HeadlessReplayImporter().import(
                        it.replay,
                    ).finalState
            }
        assertTrue(finals.values.any { it.score.playerA + it.score.playerB > 0 }, "no goals anywhere in the corpus")
        assertTrue(
            finals.values.any { it.touchLedger.entries.isNotEmpty() },
            "no recorded touches anywhere in the corpus",
        )
        val visited = HashSet<String>()
        AuditCorpus.recordings().forEach { recording ->
            HeadlessReplayImporter().importObserved(recording.replay) { _, _, state ->
                if (state.puck.spin != 0.0) visited += "spin"
                state.activePowerUps.forEach { visited += it.type.name }
                state.touchLedger.entries.forEach { visited += it.source.name }
            }
        }
        val wanted =
            setOf("spin", "RABBIT_MODE", "PADDLE_SHIELD", "SPEED_BOOST", "GHOST_MODE", "PADDLE", "WALL", "DRAWN_LINE")
        assertTrue(visited.containsAll(wanted), "corpus never reached: ${wanted - visited}")
    }

    @Test
    fun `2D traces match the committed x86-64 references`() {
        assumeTrue(System.getProperty("os.arch") in setOf("amd64", "x86_64"), "reference traces come from x86-64")
        Audit.runs(projectDir).forEach { (name, run) ->
            val reference = Trace.read(projectDir.resolve(Audit.REFERENCE_DIR).resolve("$name.trace"))
            val comparison = TraceDiff.compare(run.record(), reference)
            assertIs<TraceComparison.Agreement>(comparison, "$name: ${TraceDiff.describe(comparison)}")
            assertEquals(0, comparison.lengthDifference, name)
        }
    }

    @Test
    fun `blocking parity checks pass here`() {
        val output = ArrayList<String>()
        val status =
            DeterminismCli.run(
                listOf("parity", "$projectDir", "blocking", "build/determinism/test-parity"),
                output::add,
            )
        assertEquals(DeterminismCli.EXIT_OK, status, output.joinToString("\n"))
    }
}
