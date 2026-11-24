package ru.rkhamatyarov.tunnel

import ru.rkhamatyarov.determinism.Trace
import ru.rkhamatyarov.determinism.TraceComparison
import ru.rkhamatyarov.proto.PaddleTarget
import ru.rkhamatyarov.proto.TapeHeader
import ru.rkhamatyarov.proto.TapeRecord
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TunnelGoldenHarnessTest {
    @Test
    fun `golden scenario scripts produce valid tapes and declare qualitative invariants`() {
        val scenarios = TunnelGoldenScenarios.all.associateBy { it.name }

        assertEquals(
            setOf("zero-spin-wall-bounce", "constant-spin-curve", "known-sweep-paddle-contact"),
            scenarios.keys,
        )
        scenarios.values.forEach { scenario ->
            val tape = TunnelGoldenScenarios.parse(scenario)
            assertEquals(TunnelRules.PHYSICS_VERSION, tape.header.physicsVersion)
            assertTrue(tape.records.any { it.kind == TapeRecord.Kind.MARK })
            assertTrue(scenario.qualitativeInvariant.isNotBlank())
        }
        assertTrue(scenarios.getValue("zero-spin-wall-bounce").initialState.contains("spin=0"))
        assertTrue(scenarios.getValue("constant-spin-curve").initialState.contains("spin-x=0.5"))
        assertEquals(
            TunnelMath.fromInt(-20L).raw,
            TunnelGoldenScenarios.parse(scenarios.getValue("known-sweep-paddle-contact"))
                .records.first { it.kind == TapeRecord.Kind.SERVE }.serve.vzRaw,
        )
        assertTrue(
            scenarios.getValue("known-sweep-paddle-contact").records
                .any { it.kind == TapeRecord.Kind.PADDLE_TARGET },
        )
    }

    @Test
    fun `harness agrees with matching trace and reports first mutation divergence`() {
        val tapeBytes = tapeBytes()
        val tape = TunnelTapeReader.read(ByteArrayInputStream(tapeBytes), PHYSICS, SUBSTRATES)
        val expected = tape.replay(0L, ::reduce, ::standInHash)
        val unchanged = compare(tapeBytes, expected, multiplier = 1L)
        val mutated = compare(tapeBytes, expected, multiplier = 2L)

        assertTrue(unchanged.matches)
        assertFalse(mutated.matches)
        assertIs<TraceComparison.Divergence>(mutated.comparison).also {
            assertEquals(1L, it.step)
        }
        assertTrue(mutated.report.contains("first divergent step 1"))
    }

    @Test
    fun `harness rejects trace physics version mismatch`() {
        val tapeBytes = tapeBytes()
        val wrongVersion =
            Trace(
                mapOf(
                    Trace.KEY_SOURCE to "source",
                    Trace.KEY_KIND to "tunnel-tape",
                    Trace.KEY_PHYSICS to "future-version",
                    Trace.KEY_SUBSTRATE to "q32.32-provisional",
                ),
                emptyList(),
            )

        val error =
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                compare(tapeBytes, wrongVersion, 1L)
            }
        assertTrue(error.message.orEmpty().contains("future-version"))
        assertTrue(error.message.orEmpty().contains("tunnel-v1"))
    }

    @Test
    fun `harness reports corrupted trace as divergent`() {
        val tapeBytes = tapeBytes()
        val valid =
            TunnelTapeReader.read(ByteArrayInputStream(tapeBytes), PHYSICS, SUBSTRATES)
                .replay(0L, ::reduce, ::standInHash)
        val corrupted =
            valid.copy(
                entries =
                    valid.entries.mapIndexed { index, entry ->
                        if (index == 1) entry.copy(hash = entry.hash xor 1L) else entry
                    },
            )

        val result = compare(tapeBytes, corrupted, 1L)

        assertEquals(2L, assertIs<TraceComparison.Divergence>(result.comparison).step)
    }

    private fun compare(
        tape: ByteArray,
        expected: Trace,
        multiplier: Long,
    ): GoldenComparison =
        TunnelGoldenHarness.compare(
            tapeInput = ByteArrayInputStream(tape),
            expected = expected,
            supportedPhysicsVersions = PHYSICS,
            supportedSubstrates = SUBSTRATES,
            initial = 0L,
            reduceStep = { state, step, inputs -> reduce(state, step, inputs) * multiplier },
            hash = ::standInHash,
        )

    private fun tapeBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        TunnelTapeWriter(output, header(), markIntervalSteps = 2L).use { writer ->
            writer.append(record(step = 1L, target = 3L))
            writer.append(record(step = 2L, target = 5L))
            writer.append(record(step = 3L, target = 7L))
        }
        return output.toByteArray()
    }

    private fun reduce(
        state: Long,
        step: Long,
        inputs: List<TapeRecord>,
    ): Long = state + step + inputs.sumOf { it.paddleTarget.xRaw / TunnelScalar.SCALE }

    private fun header(): TapeHeader =
        TapeHeader.newBuilder()
            .setFormatVersion(TunnelTapeWriter.FORMAT_VERSION)
            .setPhysicsVersion("tunnel-v1")
            .setSubstrateId("q32.32-provisional")
            .setStepDurationNs(16_666_667L)
            .setWidthRaw(TunnelMath.fromInt(800L).raw)
            .setHeightRaw(TunnelMath.fromInt(600L).raw)
            .setDepthRaw(TunnelMath.fromInt(300L).raw)
            .setInitialState(com.google.protobuf.ByteString.copyFrom(byteArrayOf(1)))
            .build()

    private fun record(
        step: Long,
        target: Long,
    ): TapeRecord =
        TapeRecord.newBuilder()
            .setKind(TapeRecord.Kind.PADDLE_TARGET)
            .setStep(step)
            .setPaddleTarget(
                PaddleTarget.newBuilder()
                    .setSide(PaddleTarget.Side.A)
                    .setXRaw(TunnelMath.fromInt(target).raw)
                    .setYRaw(TunnelScalar.ZERO.raw)
                    .build(),
            )
            .build()

    companion object {
        private val PHYSICS = setOf("tunnel-v1")
        private val SUBSTRATES = setOf("q32.32-provisional")
    }
}
