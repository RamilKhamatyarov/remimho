package ru.rkhamatyarov.determinism

import ru.rkhamatyarov.proto.ReplayFile
import java.nio.file.Files
import java.nio.file.Path

/** The 2D determinism audit and the cross-architecture parity checks built on it. */
object Audit {
    const val REFERENCE_DIR = "src/test/resources/determinism/2d"
    private const val DEMO_REPLAY = "src/test/resources/demo.replay"

    /** Checks that must hold on every architecture; other features append their own. */
    private val blockingChecks = ArrayList<Pair<String, (Path) -> List<String>>>()

    init {
        registerBlockingCheck("state hash vectors", HashVectors::mismatches)
        registerBlockingCheck("tunnel Q32.32 vectors", ru.rkhamatyarov.tunnel.TunnelMathVectors::mismatches)
        registerBlockingCheck("tunnel golden traces", ru.rkhamatyarov.tunnel.TunnelGoldenCheck::mismatches)
    }

    /** [check] returns a description of every failure, or an empty list when it passes. */
    fun registerBlockingCheck(
        name: String,
        check: (Path) -> List<String>,
    ) {
        blockingChecks += name to check
    }

    /** Every audited 2D recording by name: the scripted corpus plus the committed demo replay. */
    fun runs(projectDir: Path): Map<String, TraceableRun> {
        val runs = LinkedHashMap<String, TraceableRun>()
        AuditCorpus.recordings().forEach { runs[it.name] = ReplayRun(it.replay) }
        val demo = projectDir.resolve(DEMO_REPLAY)
        if (Files.isRegularFile(demo)) {
            val bytes = Files.readAllBytes(demo)
            runs["demo"] = ReplayRun(ReplayFile.parseFrom(bytes), sha256(bytes))
        }
        return runs
    }

    fun audit(
        args: List<String>,
        out: (String) -> Unit,
    ): Int {
        val compareFlag = args.indexOf("--compare")
        val positional = if (compareFlag >= 0) args.subList(1, compareFlag) else args.drop(1)
        require(positional.size == 2) { "audit needs <projectDir> <outDir>" }
        val outDir = Path.of(positional[1])
        val compareDir = if (compareFlag >= 0) Path.of(args[compareFlag + 1]) else null

        val traces = runs(Path.of(positional[0])).mapValues { (_, run) -> run.record() }
        traces.forEach { (name, trace) -> trace.write(outDir.resolve("$name.trace")) }
        out("wrote ${traces.size} traces to $outDir")
        if (compareDir == null) return DeterminismCli.EXIT_OK

        val results =
            traces.map { (name, trace) ->
                name to
                    compareWithStored(
                        trace,
                        compareDir.resolve("$name.trace"),
                    )
            }
        out(table(results, traces))
        return comparisonStatus(results)
    }

    fun parity(
        args: List<String>,
        out: (String) -> Unit,
    ): Int {
        require(args.size == PARITY_ARGS) { "parity needs <projectDir> <blocking|2d> <outDir>" }
        val projectDir = Path.of(args[1])
        val outDir = Path.of(args[3])
        return when (args[2]) {
            "blocking" -> blocking(projectDir, out)
            "2d" -> parity2d(projectDir, outDir, out)
            else -> throw IllegalArgumentException("unknown parity mode '${args[2]}'")
        }
    }

    fun updateReferences(
        args: List<String>,
        out: (String) -> Unit,
    ): Int {
        require(args.size == 2) { "update-references needs <projectDir>" }
        val projectDir = Path.of(args[1])
        HashVectors.write(projectDir)
        val referenceDir = projectDir.resolve(REFERENCE_DIR)
        runs(projectDir).forEach { (name, run) -> run.record().write(referenceDir.resolve("$name.trace")) }
        out("rewrote hash vectors and 2D reference traces under $referenceDir")
        return DeterminismCli.EXIT_OK
    }

    private fun blocking(
        projectDir: Path,
        out: (String) -> Unit,
    ): Int {
        var failed = false
        for ((name, check) in blockingChecks) {
            val failures = check(projectDir)
            out(if (failures.isEmpty()) "PASS $name" else "FAIL $name")
            failures.forEach { out("  $it") }
            failed = failed || failures.isNotEmpty()
        }
        return if (failed) DeterminismCli.EXIT_DIVERGED else DeterminismCli.EXIT_OK
    }

    /**
     * Compares this machine's 2D traces with the committed reference traces. On divergence the
     * local trace and the local state at the divergent step are kept in [outDir], which is what
     * the reference machine needs to list the differing fields.
     */
    private fun parity2d(
        projectDir: Path,
        outDir: Path,
        out: (String) -> Unit,
    ): Int {
        val referenceDir = projectDir.resolve(REFERENCE_DIR)
        val traces = LinkedHashMap<String, Trace>()
        val results = ArrayList<Pair<String, TraceComparison>>()
        for ((name, run) in runs(projectDir)) {
            val trace = run.record()
            traces[name] = trace
            trace.write(outDir.resolve("$name.trace"))
            val comparison = compareWithStored(trace, referenceDir.resolve("$name.trace"))
            if (comparison is TraceComparison.Divergence) {
                run.dumpAt(comparison.step)?.write(outDir.resolve("$name.step-${comparison.step}.dump"))
            }
            results += name to comparison
        }
        val report = table(results, traces)
        Files.writeString(outDir.resolve("parity-2d.md"), report + "\n")
        out(report)
        return comparisonStatus(results)
    }

    private fun comparisonStatus(results: List<Pair<String, TraceComparison>>): Int {
        val identical =
            results.all { (_, result) ->
                result is TraceComparison.Agreement && result.lengthDifference == 0
            }
        return if (identical) DeterminismCli.EXIT_OK else DeterminismCli.EXIT_DIVERGED
    }

    private fun compareWithStored(
        trace: Trace,
        stored: Path,
    ): TraceComparison =
        if (Files.isRegularFile(stored)) {
            TraceDiff.compare(trace, Trace.read(stored))
        } else {
            TraceComparison.Refused(listOf("no stored trace at $stored"))
        }

    private fun table(
        results: List<Pair<String, TraceComparison>>,
        traces: Map<String, Trace>,
    ): String {
        val environment = Trace.environment()
        val rows =
            results.map { (name, comparison) ->
                val trace = traces.getValue(name)
                val last = trace.entries.lastOrNull()?.hash?.let(::hex) ?: "-"
                val outcome =
                    when (comparison) {
                        is TraceComparison.Agreement ->
                            if (comparison.lengthDifference == 0) "identical" else "length differs"
                        is TraceComparison.Divergence -> "DIVERGES at step ${comparison.step}"
                        is TraceComparison.Refused -> "not comparable: ${comparison.differences.joinToString("; ")}"
                    }
                "| $name | ${trace.entries.size} | `$last` | $outcome |"
            }
        return (
            listOf(
                "Machine: ${environment[Trace.KEY_ARCH]}, ${environment[Trace.KEY_JVM]}",
                "",
                "| Recording | Steps | Final hash | Result |",
                "| --- | --- | --- | --- |",
            ) + rows
        ).joinToString("\n")
    }

    private const val PARITY_ARGS = 4
}
