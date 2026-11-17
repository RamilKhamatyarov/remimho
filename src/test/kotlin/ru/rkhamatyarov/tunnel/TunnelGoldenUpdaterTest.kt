package ru.rkhamatyarov.tunnel

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TunnelGoldenUpdaterTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `update refuses without explicit permission evidence amd64 or clean tree`() {
        val output = mutableListOf<String>()
        val exit =
            TunnelGoldenUpdater.run(
                args = listOf("--update"),
                projectDir = temporaryDirectory,
                out = output::add,
                arch = "amd64",
                commitSha = SHA,
                worktreeClean = false,
            )

        assertEquals(TunnelGoldenUpdater.EXIT_FAILED, exit)
        assertTrue(output.any { it.contains("--allow-golden-write") })
        assertTrue(output.any { it.contains("native aarch64") })
        assertTrue(output.any { it.contains("clean") })
        assertTrue(output.any { it.contains("evidence manifest") })
        assertFalse(Files.exists(temporaryDirectory.resolve("src/test/resources/tunnel/golden")))
    }

    @Test
    fun `manifest must attest matching arm64 commit and passing checks`() {
        val blockers =
            GoldenWriteGuard.blockers(
                allowGoldenWrite = true,
                manifest =
                    GoldenEvidence(
                        arch = "aarch64",
                        runnerId = "github-runner-42",
                        commitSha = "b".repeat(40),
                        hashVectors = "fail",
                        traceParity = "missing",
                    ),
                arch = "aarch64",
                commitSha = SHA,
                worktreeClean = true,
            )

        assertEquals(3, blockers.size)
        assertTrue(blockers.any { it.contains("does not match") })
        assertTrue(blockers.any { it.contains("hashVectors") })
        assertTrue(blockers.any { it.contains("traceParity") })
    }

    @Test
    fun `authorized update only offers whitelisted paths to writer`() {
        val candidateDirectory = temporaryDirectory.resolve("build/tunnel-golden-candidates")
        Files.createDirectories(candidateDirectory)
        SCENARIOS.forEach { scenario ->
            listOf("tape", "trace").forEach { extension ->
                Files.writeString(candidateDirectory.resolve("$scenario.$extension"), "candidate:$scenario:$extension")
            }
        }
        val evidence = temporaryDirectory.resolve("arm64-evidence.properties")
        Files.writeString(
            evidence,
            """
                |arch=aarch64
                |runnerId=github-runner-42
                |commitSha=$SHA
                |hashVectors=pass
                |traceParity=pass
                |
            """.trimMargin(),
        )
        val writtenPaths = mutableListOf<Path>()

        val exit =
            TunnelGoldenUpdater.run(
                args = listOf("--update", "--allow-golden-write", "--evidence", evidence.toString()),
                projectDir = temporaryDirectory,
                out = {},
                arch = "aarch64",
                commitSha = SHA,
                worktreeClean = true,
                writeFile = { path, _ -> writtenPaths.add(path) },
            )

        val goldenDirectory = temporaryDirectory.resolve("src/test/resources/tunnel/golden").toAbsolutePath()
        assertEquals(TunnelGoldenUpdater.EXIT_OK, exit)
        assertEquals(SCENARIOS.size * 2, writtenPaths.size)
        writtenPaths.forEach { GoldenWriteGuard.requireWhitelisted(goldenDirectory, it) }
        assertFalse(Files.exists(goldenDirectory))
    }

    @Test
    fun `path guard rejects traversal and unrelated files`() {
        val root = temporaryDirectory.resolve("golden")

        assertTrue(
            runCatching {
                GoldenWriteGuard.requireWhitelisted(root, root.resolve("../outside.trace"))
            }.isFailure,
        )
        assertTrue(
            runCatching {
                GoldenWriteGuard.requireWhitelisted(root, root.resolve("extra.tape"))
            }.isFailure,
        )
    }

    @Test
    fun `default verification never calls writer`() {
        val candidates = temporaryDirectory.resolve("build/tunnel-golden-candidates")
        val goldens = temporaryDirectory.resolve("src/test/resources/tunnel/golden")
        Files.createDirectories(candidates)
        SCENARIOS.forEach { name ->
            listOf("tape", "trace").forEach { extension ->
                val file = "$name.$extension"
                val bytes = "same:$file".encodeToByteArray()
                Files.write(candidates.resolve(file), bytes)
            }
        }

        val exit =
            TunnelGoldenUpdater.run(
                args = emptyList(),
                projectDir = temporaryDirectory,
                out = {},
                writeFile = { _, _ -> error("verify mode must never write") },
            )

        assertEquals(TunnelGoldenUpdater.EXIT_FAILED, exit)
        assertFalse(Files.exists(goldens))
    }

    companion object {
        private val SHA = "a".repeat(40)
        private val SCENARIOS = listOf("zero-spin-wall-bounce", "constant-spin-curve", "known-sweep-paddle-contact")
    }
}
