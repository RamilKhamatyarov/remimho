package ru.rkhamatyarov.tunnel

import ru.rkhamatyarov.determinism.Trace
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Replays one scenario tape with the real physics and prints the trace; used by fresh-process tests. */
object TunnelReplayCli {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) { "usage: TunnelReplayCli <scenario-name>" }
        val scenario = TunnelGoldenScenarios.byName(args[0])
        print(TunnelGoldenFixtures.traceFor(scenario).render())
    }

    /** Runs the CLI in a fresh JVM and returns its stdout, or fails with the captured stderr. */
    fun inFreshProcess(
        scenarioName: String,
        javaExecutable: Path = Path.of(System.getProperty("java.home"), "bin", javaExecutableName()),
        classpath: String = System.getProperty("java.class.path"),
        workingDirectory: File = File(System.getProperty("user.dir")),
    ): String {
        val argumentFile = Files.createTempFile("tunnel-replay-", ".args")
        try {
            val portableClasspath = classpath.replace('\\', '/')
            Files.writeString(
                argumentFile,
                "-cp\n\"$portableClasspath\"\n${TunnelReplayCli::class.java.name}\n$scenarioName\n",
            )
            val process =
                ProcessBuilder(javaExecutable.toString(), "@$argumentFile").directory(workingDirectory)
                    .redirectErrorStream(false)
                    .start()
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            check(process.waitFor() == 0) { "fresh replay failed for $scenarioName: $stderr" }
            return stdout
        } finally {
            Files.deleteIfExists(argumentFile)
        }
    }

    /** Fails when the committed trace is missing; the golden updater writes it first. */
    fun committedTrace(
        projectDir: Path,
        scenarioName: String,
    ): Trace = Trace.read(projectDir.resolve(TunnelGoldenFixtures.GOLDEN_DIRECTORY).resolve("$scenarioName.trace"))

    private fun javaExecutableName(): String =
        if (System.getProperty("os.name").lowercase().contains("windows")) "java.exe" else "java"
}
