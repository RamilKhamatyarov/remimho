package ru.rkhamatyarov.tunnel

import ru.rkhamatyarov.determinism.Trace
import ru.rkhamatyarov.determinism.TraceComparison
import ru.rkhamatyarov.determinism.TraceDiff
import ru.rkhamatyarov.proto.TapeRecord
import java.io.InputStream

/** Replays a tape and compares every emitted step hash with its committed reference trace. */
object TunnelGoldenHarness {
    fun <S> compare(
        tapeInput: InputStream,
        expected: Trace,
        supportedPhysicsVersions: Set<String>,
        supportedSubstrates: Set<String>,
        initial: S,
        reduceStep: (S, Long, List<TapeRecord>) -> S,
        hash: (S) -> Long,
    ): GoldenComparison {
        val tape = TunnelTapeReader.read(tapeInput, supportedPhysicsVersions, supportedSubstrates)
        require(expected.header[Trace.KEY_PHYSICS] == tape.header.physicsVersion) {
            "golden physics version '${expected.header[Trace.KEY_PHYSICS]}' does not match tape '${tape.header.physicsVersion}'"
        }
        val actual = tape.replay(initial, reduceStep, hash)
        val comparison = TraceDiff.compare(expected, actual)
        return GoldenComparison(comparison, TraceDiff.describe(comparison))
    }
}

data class GoldenComparison(
    val comparison: TraceComparison,
    val report: String,
) {
    val matches: Boolean
        get() = comparison is TraceComparison.Agreement && comparison.lengthDifference == 0
}

/** Stable stand-in hash used only while the tunnel physics implementation is gated. */
fun standInHash(value: Long): Long {
    var hash = FNV_OFFSET_BASIS
    for (shift in 0..56 step 8) {
        hash = (hash xor ((value ushr shift) and 0xffL)) * FNV_PRIME
    }
    return hash
}

private const val FNV_OFFSET_BASIS = -3750763034362895579L
private const val FNV_PRIME = 1099511628211L
