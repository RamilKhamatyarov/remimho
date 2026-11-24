package ru.rkhamatyarov.tunnel

import ru.rkhamatyarov.proto.PaddleTarget
import ru.rkhamatyarov.proto.TapeHeader
import ru.rkhamatyarov.proto.TapeRecord
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TunnelTapeTest {
    @Test
    fun `same logical input produces byte-identical tape`() {
        val first = recordedTape()
        val second = recordedTape()

        assertTrue(first.contentEquals(second))
    }

    @Test
    fun `tape trace identity includes input records`() {
        val tape = read(recordedTape())
        val changedRecords =
            tape.records.mapIndexed { index, record ->
                if (index == 0) {
                    record.toBuilder()
                        .setPaddleTarget(record.paddleTarget.toBuilder().setXRaw(TunnelMath.fromInt(9L).raw))
                        .build()
                } else {
                    record
                }
            }
        val changedTape = tape.copy(records = changedRecords)

        val first = tape.replay(0, ::reduce, Int::toLong)
        val changed = changedTape.replay(0, ::reduce, Int::toLong)

        assertTrue(
            first.header[ru.rkhamatyarov.determinism.Trace.KEY_SOURCE] !=
                changed.header[ru.rkhamatyarov.determinism.Trace.KEY_SOURCE],
        )
    }

    @Test
    fun `writer inserts interval and final marks`() {
        val tape = TunnelTapeReader.read(ByteArrayInputStream(recordedTape()), PHYSICS, SUBSTRATES)

        assertEquals(listOf(2L, 3L), tape.records.filter { it.kind == TapeRecord.Kind.MARK }.map { it.step })
    }

    @Test
    fun `reader rejects a sequence gap with expected sequence`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                readRaw(record(seq = 2L))
            }

        assertTrue(exception.message.orEmpty().contains("expected seq 1"))

        val repeated =
            assertFailsWith<IllegalArgumentException> {
                readRaw(record(1L), record(1L, 2L))
            }
        assertTrue(repeated.message.orEmpty().contains("expected seq 2 but found 1"))
    }

    @Test
    fun `reader rejects decreasing step and identifies record`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                readRaw(record(1L, 2L), record(2L, 1L))
            }

        assertTrue(exception.message.orEmpty().contains("decreasing step at seq 2"))
    }

    @Test
    fun `reader rejects unknown kind and unsupported metadata`() {
        val unknown = TapeRecord.newBuilder().setSeq(1L).setStep(1L).setKindValue(99).build()
        val exception = assertFailsWith<IllegalArgumentException> { readRaw(unknown) }
        assertTrue(exception.message.orEmpty().contains("unknown record kind 99 at seq 1"))

        val missingPayload =
            TapeRecord.newBuilder()
                .setKind(TapeRecord.Kind.PADDLE_TARGET)
                .setSeq(1L)
                .setStep(1L)
                .build()
        assertTrue(
            assertFailsWith<IllegalArgumentException> { readRaw(missingPayload) }
                .message.orEmpty().contains("missing paddle target at seq 1"),
        )

        assertTrue(
            assertFailsWith<IllegalArgumentException> {
                TunnelTapeReader.read(
                    ByteArrayInputStream(header().toBuilder().setFormatVersion(2).build().toBytes()),
                    PHYSICS,
                    SUBSTRATES,
                )
            }.message.orEmpty().contains("unsupported format version 2"),
        )

        assertTrue(
            assertFailsWith<IllegalArgumentException> {
                TunnelTapeReader.read(ByteArrayInputStream(header(physics = "future").toBytes()), PHYSICS, SUBSTRATES)
            }.message.orEmpty().contains("unsupported physics version 'future'"),
        )
        assertTrue(
            assertFailsWith<IllegalArgumentException> {
                TunnelTapeReader.read(ByteArrayInputStream(header(substrate = "future").toBytes()), PHYSICS, SUBSTRATES)
            }.message.orEmpty().contains("unsupported substrate 'future'"),
        )
    }

    @Test
    fun `reader rejects invalid fixed point header fields`() {
        val invalidHeaders =
            listOf(
                header().toBuilder().setWidthRaw(0L).build() to "width raw value",
                header().toBuilder().setHeightRaw(-1L).build() to "height raw value",
                header().toBuilder().setDepthRaw(42_949_672_960_001L).build() to "depth raw value",
                header().toBuilder().setStepDurationNs(1_000_000_001L).build() to "step duration",
                header().toBuilder().setInitialState(com.google.protobuf.ByteString.EMPTY).build() to "initial state",
                header().toBuilder().setPhysicsVersion("").build() to "physics version",
                header().toBuilder().setSubstrateId("").build() to "substrate id",
            )

        invalidHeaders.forEach { (invalid, expectedMessage) ->
            val output = ByteArrayOutputStream()
            invalid.writeDelimitedTo(output)
            val error =
                assertFailsWith<IllegalArgumentException> {
                    TunnelTapeReader.read(ByteArrayInputStream(output.toByteArray()), PHYSICS, SUBSTRATES)
                }
            assertTrue(error.message.orEmpty().contains(expectedMessage))
        }
    }

    @Test
    fun `truncated complete-record tape replays matching hash prefix`() {
        val complete = recordedTape()
        val full = read(complete).replay(0, ::reduce, Int::toLong)
        for (maximumStep in 1L..3L) {
            val prefixBytes = tapePrefixThroughStep(complete, maximumStep)
            val prefix = read(prefixBytes).replay(0, ::reduce, Int::toLong)

            assertEquals(full.entries.take(maximumStep.toInt()), prefix.entries)
        }
    }

    @Test
    fun `tape replays through the input-only reducer with physics identity in trace`() {
        val tape = read(recordedTape())
        val rules = TunnelRules()

        val trace = tape.replayTunnel(TunnelState(rules = rules), rules)

        assertEquals(rules.physicsVersion, trace.header[ru.rkhamatyarov.determinism.Trace.KEY_PHYSICS])
        assertEquals(TunnelMath.SUBSTRATE_ID, trace.header[ru.rkhamatyarov.determinism.Trace.KEY_SUBSTRATE])
        assertEquals(3, trace.entries.size)
    }

    @Test
    fun `tunnel replay rejects physics version mismatch`() {
        val tape = read(recordedTape())
        val rules = TunnelRules(physicsVersion = "future")
        val error =
            assertFailsWith<IllegalArgumentException> {
                tape.replayTunnel(TunnelState(rules = rules), rules)
            }

        assertTrue(error.message.orEmpty().contains("future"))
        assertTrue(error.message.orEmpty().contains(TunnelRules.PHYSICS_VERSION))
    }

    @Test
    fun `replay preserves input order within each step`() {
        val tape =
            TunnelTape(
                header(),
                listOf(
                    record(1L, 1L, 4),
                    record(2L, 1L, 7),
                    record(3L, 2L, 2),
                ),
            )
        val seen = mutableListOf<Int>()

        tape.replay(0, { state, _, inputs ->
            inputs.forEach { seen += (it.paddleTarget.xRaw / TunnelScalar.SCALE).toInt() }
            state + inputs.sumOf { (it.paddleTarget.xRaw / TunnelScalar.SCALE).toInt() }
        }, Int::toLong)

        assertEquals(listOf(4, 7, 2), seen)
    }

    private fun recordedTape(): ByteArray {
        val output = ByteArrayOutputStream()
        TunnelTapeWriter(output, header(), markIntervalSteps = 2L).use { writer ->
            writer.append(record(1L, 1L, 4))
            writer.append(record(2L, 2L, 7))
            writer.append(record(3L, 3L, 2))
        }
        return output.toByteArray()
    }

    private fun read(bytes: ByteArray): TunnelTape =
        TunnelTapeReader.read(ByteArrayInputStream(bytes), PHYSICS, SUBSTRATES)

    private fun readRaw(vararg records: TapeRecord): TunnelTape {
        val output = ByteArrayOutputStream()
        header().writeDelimitedTo(output)
        records.forEach { it.writeDelimitedTo(output) }
        return read(output.toByteArray())
    }

    private fun tapePrefixThroughStep(
        bytes: ByteArray,
        maximumStep: Long,
    ): ByteArray {
        val input = ByteArrayInputStream(bytes)
        val output = ByteArrayOutputStream()
        TapeHeader.parseDelimitedFrom(input)!!.writeDelimitedTo(output)
        while (true) {
            val record = TapeRecord.parseDelimitedFrom(input) ?: break
            if (record.step > maximumStep) break
            record.writeDelimitedTo(output)
        }
        return output.toByteArray()
    }

    private fun header(
        physics: String = TunnelRules.PHYSICS_VERSION,
        substrate: String = TunnelMath.SUBSTRATE_ID,
    ): TapeHeader =
        TapeHeader.newBuilder()
            .setFormatVersion(TunnelTapeWriter.FORMAT_VERSION)
            .setPhysicsVersion(physics)
            .setSubstrateId(substrate)
            .setStepDurationNs(TunnelRules.DEFAULT_STEP_NS)
            .setWidthRaw(TunnelMath.fromInt(800L).raw)
            .setHeightRaw(TunnelMath.fromInt(600L).raw)
            .setDepthRaw(TunnelMath.fromInt(300L).raw)
            .setInitialState(com.google.protobuf.ByteString.copyFrom(byteArrayOf(1, 2, 3)))
            .build()

    private fun TapeHeader.toBytes(): ByteArray = ByteArrayOutputStream().also { writeDelimitedTo(it) }.toByteArray()

    private fun record(
        seq: Long,
        step: Long = 1L,
        targetX: Int = 1,
    ): TapeRecord =
        TapeRecord.newBuilder()
            .setKind(TapeRecord.Kind.PADDLE_TARGET)
            .setSeq(seq)
            .setStep(step)
            .setPaddleTarget(
                PaddleTarget.newBuilder()
                    .setSide(PaddleTarget.Side.A)
                    .setXRaw(TunnelMath.fromInt(targetX.toLong()).raw)
                    .setYRaw(TunnelMath.fromInt(25L).raw)
                    .build(),
            )
            .build()

    private fun reduce(
        state: Int,
        step: Long,
        inputs: List<TapeRecord>,
    ): Int = state + step.toInt() + inputs.size

    companion object {
        private val PHYSICS = setOf(TunnelRules.PHYSICS_VERSION)
        private val SUBSTRATES = setOf(TunnelMath.SUBSTRATE_ID)
    }
}
