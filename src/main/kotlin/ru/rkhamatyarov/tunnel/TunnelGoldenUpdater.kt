package ru.rkhamatyarov.tunnel

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties
import kotlin.system.exitProcess

/** Compare-only by default; updating requires ARM64 evidence and an explicit write flag. */
object TunnelGoldenUpdater {
    const val EXIT_OK = 0
    const val EXIT_FAILED = 1
    const val EXIT_USAGE = 2
    private const val GOLDEN_DIRECTORY = "src/test/resources/tunnel/golden"
    private val scenarioNames =
        setOf("zero-spin-wall-bounce", "constant-spin-curve", "known-sweep-paddle-contact")

    @JvmStatic
    fun main(args: Array<String>) {
        exitProcess(run(args.toList(), Path.of("."), ::println))
    }

    fun run(
        args: List<String>,
        projectDir: Path,
        out: (String) -> Unit,
        arch: String = System.getProperty("os.arch", "unknown"),
        commitSha: String = "",
        worktreeClean: Boolean? = null,
        writeFile: (Path, ByteArray) -> Unit = ::atomicWrite,
    ): Int =
        try {
            val options = Options.parse(args)
            val projectRoot = projectDir.toAbsolutePath().normalize()
            val targetDirectory = projectRoot.resolve(GOLDEN_DIRECTORY).normalize()
            if (!targetDirectory.startsWith(projectRoot)) {
                throw IllegalArgumentException("golden directory escapes project root")
            }
            val candidateDirectory = options.candidateDirectory(projectDir).toAbsolutePath().normalize()
            require(!candidateDirectory.startsWith(targetDirectory.toAbsolutePath().normalize())) {
                "candidate directory must not be inside the golden output directory"
            }
            if (!options.update) {
                val candidates = readCandidates(candidateDirectory)
                return verify(candidates, targetDirectory, out)
            }

            val manifest = options.evidenceFile?.let(::readManifest)
            val blockers =
                GoldenWriteGuard.blockers(
                    allowGoldenWrite = options.allowGoldenWrite,
                    manifest = manifest,
                    arch = arch,
                    commitSha = commitSha.ifBlank { git(projectDir, "rev-parse", "HEAD").trim() },
                    worktreeClean = worktreeClean ?: git(projectDir, "status", "--porcelain").isBlank(),
                )
            if (blockers.isNotEmpty()) {
                blockers.forEach { out("BLOCKED $it") }
                return EXIT_FAILED
            }

            val candidates = readCandidates(candidateDirectory)
            candidates.forEach { candidate ->
                val target = targetDirectory.resolve(candidate.fileName).normalize()
                GoldenWriteGuard.requireWhitelisted(targetDirectory, target)
                writeFile(target, candidate.bytes)
                out("UPDATED ${candidate.fileName}")
            }
            EXIT_OK
        } catch (error: IllegalArgumentException) {
            out("REFUSED ${error.message}")
            EXIT_USAGE
        } catch (error: Exception) {
            out("FAILED ${error.message}")
            EXIT_FAILED
        }

    private fun verify(
        candidates: List<GoldenCandidate>,
        targetDirectory: Path,
        out: (String) -> Unit,
    ): Int {
        var matches = true
        candidates.forEach { candidate ->
            val target = targetDirectory.resolve(candidate.fileName).normalize()
            GoldenWriteGuard.requireWhitelisted(targetDirectory, target)
            val equal = Files.isRegularFile(target) && Files.readAllBytes(target).contentEquals(candidate.bytes)
            out("${if (equal) "MATCH" else "DIVERGE"} ${candidate.fileName}")
            matches = matches && equal
        }
        return if (matches) EXIT_OK else EXIT_FAILED
    }

    private fun readCandidates(directory: Path): List<GoldenCandidate> {
        require(Files.isDirectory(directory)) { "candidate directory is missing: $directory" }
        require(!Files.isSymbolicLink(directory)) { "candidate directory must not be a symbolic link" }
        val paths = Files.list(directory).use { it.toList() }
        require(paths.none(Files::isSymbolicLink)) { "candidate directory must not contain symbolic links" }
        val candidates =
            paths.map { path ->
                val name = path.fileName.toString()
                require(Files.isRegularFile(path) && allowedName(name)) { "not an allowed candidate file: $name" }
                val bytes = Files.readAllBytes(path)
                require(bytes.isNotEmpty()) { "candidate is empty: $name" }
                GoldenCandidate(name, bytes)
            }.sortedBy(GoldenCandidate::fileName)
        val expected = scenarioNames.flatMap { listOf("$it.tape", "$it.trace") }.toSet()
        require(candidates.map(GoldenCandidate::fileName).toSet() == expected) {
            "candidate set must contain exactly the three scenario .tape/.trace pairs"
        }
        return candidates
    }

    private fun allowedName(name: String): Boolean = scenarioNames.any { name == "$it.tape" || name == "$it.trace" }

    private fun readManifest(path: Path): GoldenEvidence {
        val properties = Properties()
        Files.newInputStream(path).use(properties::load)
        return GoldenEvidence(
            arch = properties.getProperty("arch", ""),
            runnerId = properties.getProperty("runnerId", ""),
            commitSha = properties.getProperty("commitSha", ""),
            hashVectors = properties.getProperty("hashVectors", ""),
            traceParity = properties.getProperty("traceParity", ""),
        )
    }

    private fun git(
        directory: Path,
        vararg arguments: String,
    ): String =
        try {
            val process = ProcessBuilder("git", *arguments).directory(directory.toFile()).start()
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            check(process.waitFor() == 0) { stderr.ifBlank { "git command failed" } }
            stdout
        } catch (error: Exception) {
            throw IllegalArgumentException("cannot establish Git provenance: ${error.message}")
        }

    private fun atomicWrite(
        target: Path,
        bytes: ByteArray,
    ) {
        requireNoSymbolicLink(target)
        Files.createDirectories(target.parent)
        requireNoSymbolicLink(target)
        require(!Files.isSymbolicLink(target)) {
            "golden output path must not be a symbolic link"
        }
        val temporary = Files.createTempFile(target.parent, "golden-", ".tmp")
        try {
            Files.write(temporary, bytes)
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun requireNoSymbolicLink(path: Path) {
        var current = path.root
        path.toAbsolutePath().normalize().forEach { part ->
            current = current.resolve(part)
            require(!Files.isSymbolicLink(current)) { "golden output path contains a symbolic link: $current" }
        }
    }

    private data class Options(
        val update: Boolean,
        val allowGoldenWrite: Boolean,
        val evidenceFile: Path?,
        val candidates: Path?,
    ) {
        fun candidateDirectory(projectDir: Path): Path =
            candidates?.let(projectDir::resolve) ?: projectDir.resolve("build/tunnel-golden-candidates")

        companion object {
            fun parse(args: List<String>): Options {
                var update = false
                var allowWrite = false
                var evidence: Path? = null
                var candidates: Path? = null
                var index = 0
                while (index < args.size) {
                    when (val option = args[index++]) {
                        "--verify" -> update = false
                        "--update" -> update = true
                        "--allow-golden-write" -> allowWrite = true
                        "--evidence" -> evidence = Path.of(args.getOrNull(index++) ?: usage(option))
                        "--candidate-dir" -> candidates = Path.of(args.getOrNull(index++) ?: usage(option))
                        else -> usage(option)
                    }
                }
                require(!allowWrite || update) { "--allow-golden-write is only valid with --update" }
                return Options(update, allowWrite, evidence, candidates)
            }

            private fun usage(option: String): Nothing =
                throw IllegalArgumentException("unknown option or missing value: $option")
        }
    }

    private data class GoldenCandidate(
        val fileName: String,
        val bytes: ByteArray,
    )
}

data class GoldenEvidence(
    val arch: String,
    val runnerId: String,
    val commitSha: String,
    val hashVectors: String,
    val traceParity: String,
)

/** Validates provenance before the updater is allowed to touch golden paths. */
object GoldenWriteGuard {
    fun blockers(
        allowGoldenWrite: Boolean,
        manifest: GoldenEvidence?,
        arch: String,
        commitSha: String,
        worktreeClean: Boolean,
    ): List<String> =
        buildList {
            if (!allowGoldenWrite) add("pass --allow-golden-write together with --update")
            if (arch != "aarch64") add("updates require a native aarch64 JVM; detected '$arch'")
            if (!worktreeClean) add("Git worktree must be clean before updating goldens")
            if (manifest == null) {
                add("an ARM64 evidence manifest is required")
            } else {
                if (manifest.arch != "aarch64") add("manifest arch must be aarch64")
                if (!manifest.runnerId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{2,127}")) ||
                    manifest.runnerId.equals("local", ignoreCase = true)
                ) {
                    add("manifest runnerId must identify a real CI runner")
                }
                if (!manifest.commitSha.matches(Regex("[0-9a-fA-F]{40}"))) {
                    add("manifest commitSha must be a full 40-character Git SHA")
                } else if (!manifest.commitSha.equals(commitSha, ignoreCase = true)) {
                    add("manifest commitSha does not match checked-out HEAD")
                }
                if (manifest.hashVectors != "pass") add("manifest hashVectors must be 'pass'")
                if (manifest.traceParity != "pass") add("manifest traceParity must be 'pass'")
            }
        }

    fun requireWhitelisted(
        goldenDirectory: Path,
        target: Path,
    ) {
        val root = goldenDirectory.toAbsolutePath().normalize()
        val candidate = target.toAbsolutePath().normalize()
        val allowed = Regex("(zero-spin-wall-bounce|constant-spin-curve|known-sweep-paddle-contact)\\.(tape|trace)")
        require(candidate.parent == root && allowed.matches(candidate.fileName.toString())) {
            "refusing non-whitelisted golden path: $candidate"
        }
    }
}
