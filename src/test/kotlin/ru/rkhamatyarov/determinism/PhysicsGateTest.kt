package ru.rkhamatyarov.determinism

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhysicsGateTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `committed gate prints blockers and remains closed`() {
        val projectDirectory = Path.of(System.getProperty("user.dir"))
        val output = mutableListOf<String>()

        val status = PhysicsGate.check(listOf("gate", projectDirectory.toString()), output::add)

        assertEquals(PhysicsGate.EXIT_CLOSED, status)
        assertTrue(output.any { it.contains("arm64-hash-vectors") && it.contains("BLOCKED") })
        assertTrue(output.last().startsWith("GATE CLOSED"))
    }

    @Test
    fun `gate opens only when every prerequisite passes with resolving evidence`() {
        Files.writeString(temporaryDirectory.resolve("proof.md"), "verified")
        val gate = temporaryDirectory.resolve("gate.yaml")
        Files.writeString(
            gate,
            """
            {
              "version": 1,
              "prerequisites": [
                {"id": "one", "status": "pass", "evidence": "proof.md"},
                {"id": "two", "status": "pass", "evidence": "proof.md#run"}
              ]
            }
            """.trimIndent(),
        )

        val report = PhysicsGate.inspect(gate, temporaryDirectory)

        assertTrue(report.open)
        assertEquals(2, report.rows.size)
    }

    @Test
    fun `passing status without evidence and escaping evidence stay blocked`() {
        val gate = temporaryDirectory.resolve("gate.yaml")
        Files.writeString(
            gate,
            """
            {
              "version": 1,
              "prerequisites": [
                {"id": "no-evidence", "status": "pass", "evidence": null},
                {"id": "escape", "status": "pass", "evidence": "../outside.md"}
              ]
            }
            """.trimIndent(),
        )

        val report = PhysicsGate.inspect(gate, temporaryDirectory)

        assertFalse(report.open)
        assertEquals(2, report.rows.count { it.blocker != null })
    }

    @Test
    fun `provisional substrate evidence does not open the gate`() {
        Files.writeString(temporaryDirectory.resolve("provisional.md"), "x86-only")
        val gate = temporaryDirectory.resolve("provisional.yaml")
        Files.writeString(
            gate,
            """
            {
              "version": 1,
              "prerequisites": [
                {"id": "substrate", "status": "provisional", "evidence": "provisional.md"}
              ]
            }
            """.trimIndent(),
        )

        val report = PhysicsGate.inspect(gate, temporaryDirectory)

        assertFalse(report.open)
        assertEquals("status is 'provisional'", report.rows.single().blocker)
    }
}
