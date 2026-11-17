package ru.rkhamatyarov.determinism

import ru.rkhamatyarov.proto.ReplayFile
import ru.rkhamatyarov.replay.HeadlessReplayImporter
import ru.rkhamatyarov.service.mvi.GameAction
import java.security.MessageDigest

/** Something that can be simulated from recorded input, exposing its state after every step. */
interface TraceableRun {
    /** What is being simulated; becomes the identity part of the trace header. */
    val identity: Map<String, String>

    fun run(visitor: StepVisitor)
}

fun interface StepVisitor {
    /** [write] feeds the state after [step] into a sink; call it only when the state is needed. */
    fun visit(
        step: Long,
        write: (FieldSink) -> Unit,
    )
}

fun TraceableRun.record(): Trace {
    val entries = ArrayList<TraceEntry>()
    run { step, write -> entries += TraceEntry(step, HashSink().also(write).value) }
    return Trace(identity + Trace.environment(), entries)
}

/** The full field listing after [step], or null when the run never reaches it. */
fun TraceableRun.dumpAt(step: Long): StateDump? {
    var fields: List<DumpedField>? = null
    run { current, write -> if (current == step) fields = DumpSink().also(write).fields }
    return fields?.let { StateDump(identity + Trace.environment(), step, it) }
}

/** A 2D replay file; one step per reduced tick, matching how the live room advances. */
class ReplayRun(
    private val replayFile: ReplayFile,
    sourceId: String = sha256(replayFile.toByteArray()),
) : TraceableRun {
    override val identity: Map<String, String> =
        linkedMapOf(
            Trace.KEY_SOURCE to sourceId,
            Trace.KEY_KIND to KIND,
            Trace.KEY_PHYSICS to replayFile.version.ifEmpty { "1" },
            Trace.KEY_SUBSTRATE to SUBSTRATE,
        )

    override fun run(visitor: StepVisitor) {
        var tick = 0L
        HeadlessReplayImporter().importObserved(replayFile) { _, action, state ->
            if (action is GameAction.Tick) {
                tick++
                visitor.visit(tick) { sink -> MviGameStateEncoder.encode(state, sink) }
            }
        }
    }

    companion object {
        const val KIND = "2d-replay"
        const val SUBSTRATE = "jvm-double"
    }
}

internal fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
