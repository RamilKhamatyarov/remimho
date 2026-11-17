package ru.rkhamatyarov.determinism

import ru.rkhamatyarov.proto.ReplayFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Command-line entry point of the determinism tools (see docs/determinism/README.md).
 *
 * Exit status: 0 agreement or success, 1 divergence or failed check, 2 refused or bad usage.
 */
object DeterminismCli {
    const val EXIT_OK = 0
    const val EXIT_DIVERGED = 1
    const val EXIT_REFUSED = 2

    @JvmStatic
    fun main(args: Array<String>) {
        val command = System.getProperty("determinism.command")
        exitProcess(run(listOfNotNull(command) + args, ::println))
    }

    fun run(
        args: List<String>,
        out: (String) -> Unit,
    ): Int =
        try {
            when (args.firstOrNull()) {
                "record" -> record(args, out)
                "dump" -> dump(args, out)
                "diff" -> diff(args, out)
                "audit" -> Audit.audit(args, out)
                "parity" -> Audit.parity(args, out)
                "gate" -> PhysicsGate.check(args, out)
                "update-references" -> Audit.updateReferences(args, out)
                else -> usage(out)
            }
        } catch (error: IllegalArgumentException) {
            out("error: ${error.message}")
            EXIT_REFUSED
        }

    private fun record(
        args: List<String>,
        out: (String) -> Unit,
    ): Int {
        if (args.size != RECORD_ARGS) return usage(out)
        val trace = RunLoader.load(Path.of(args[1])).record()
        trace.write(Path.of(args[2]))
        out("recorded ${trace.entries.size} steps to ${args[2]}")
        return EXIT_OK
    }

    private fun dump(
        args: List<String>,
        out: (String) -> Unit,
    ): Int {
        if (args.size != DUMP_ARGS) return usage(out)
        val step = args[2].toLongOrNull() ?: return usage(out)
        val dump =
            RunLoader.load(Path.of(args[1])).dumpAt(step)
                ?: throw IllegalArgumentException("${args[1]} never reaches step $step")
        dump.write(Path.of(args[3]))
        out("dumped ${dump.fields.size} fields at step $step to ${args[3]}")
        return EXIT_OK
    }

    private fun diff(
        args: List<String>,
        out: (String) -> Unit,
    ): Int {
        val dumpFlag = args.indexOf("--dump")
        val positional = if (dumpFlag >= 0) args.subList(1, dumpFlag) else args.drop(1)
        if (positional.size != 2 || (dumpFlag >= 0 && dumpFlag + 1 >= args.size)) return usage(out)
        val foreignDump = if (dumpFlag >= 0) StateDump.read(Path.of(args[dumpFlag + 1])) else null

        val left = Side.of(Path.of(positional[0]))
        val right = Side.of(Path.of(positional[1]))
        val comparison = TraceDiff.compare(left.trace, right.trace)
        out(TraceDiff.describe(comparison))
        return when (comparison) {
            is TraceComparison.Agreement -> EXIT_OK
            is TraceComparison.Refused -> EXIT_REFUSED
            is TraceComparison.Divergence -> {
                reportFields(comparison.step, left, right, foreignDump, out)
                EXIT_DIVERGED
            }
        }
    }

    private fun reportFields(
        step: Long,
        left: Side,
        right: Side,
        foreignDump: StateDump?,
        out: (String) -> Unit,
    ) {
        val foreign = foreignDump?.takeIf { it.step == step }?.fields
        if (foreignDump != null && foreign == null) {
            out("the supplied dump is for step ${foreignDump.step}, not the divergent step $step")
        }
        val leftFields = left.run?.dumpAt(step)?.fields ?: foreign
        val rightFields = right.run?.dumpAt(step)?.fields ?: foreign
        if (leftFields == null || rightFields == null || (left.run == null && right.run == null)) {
            out("state fields unavailable: on the machine that produced the trace run")
            out("  gradlew traceDump --args=\"<input> $step <out.dump>\"")
            out("then repeat this diff with --dump <out.dump>")
            return
        }
        val differences = TraceDiff.fields(leftFields, rightFields)
        out("${differences.size} differing field(s) at step $step:")
        differences.forEach { out("  ${it.describe()}") }
    }

    private fun usage(out: (String) -> Unit): Int {
        out(
            """
            usage:
              record <input> <out.trace>            hash every step of a replay or tape
              dump <input> <step> <out.dump>        write every state field after one step
              diff <left> <right> [--dump <file>]   first divergent step and differing fields
              audit <projectDir> <outDir> [--compare <dir>]
              parity <projectDir> <blocking|2d> <outDir>
              gate <projectDir> [gate.yaml]
              update-references <projectDir>
            inputs are .replay (2D), .tape (3D tunnel) or .trace files
            """.trimIndent(),
        )
        return EXIT_REFUSED
    }

    /** One operand of a diff: a stored trace, or something that can be re-simulated here. */
    private class Side(
        val trace: Trace,
        val run: TraceableRun?,
    ) {
        companion object {
            fun of(path: Path): Side =
                if (path.toString().endsWith(".trace")) {
                    Side(Trace.read(path), null)
                } else {
                    val run = RunLoader.load(path)
                    Side(run.record(), run)
                }
        }
    }

    private const val RECORD_ARGS = 3
    private const val DUMP_ARGS = 4
}

/** Turns an input file into something that can be simulated, by file extension. */
object RunLoader {
    private val loaders = LinkedHashMap<String, (Path) -> TraceableRun>()

    init {
        register(".replay") { path ->
            val bytes = Files.readAllBytes(path)
            ReplayRun(ReplayFile.parseFrom(bytes), sha256(bytes))
        }
    }

    fun register(
        extension: String,
        loader: (Path) -> TraceableRun,
    ) {
        loaders[extension] = loader
    }

    fun load(path: Path): TraceableRun {
        require(Files.isRegularFile(path)) { "no such file: $path" }
        val loader =
            loaders.entries.firstOrNull { path.toString().endsWith(it.key) }?.value
                ?: throw IllegalArgumentException("unsupported input '$path' (expected ${loaders.keys.joinToString()})")
        return loader(path)
    }
}
