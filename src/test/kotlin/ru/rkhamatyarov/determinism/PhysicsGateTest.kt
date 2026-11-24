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
    fun `committed gate prints the passing evidence and is open`() {
        val projectDirectory = Path.of(System.getProperty("user.dir"))
        val output = mutableListOf<String>()

        val status = PhysicsGate.check(listOf("gate", projectDirectory.toString()), output::add)

        assertEquals(PhysicsGate.EXIT_OPEN, status)
        assertTrue(output.any { it.contains("real-tunnel-reducer") && it.contains("PASS") })
        assertTrue(output.any { it.contains("golden-updater-through-real-reducer") && it.contains("PASS") })
        assertTrue(output.last().startsWith("GATE OPEN"))
    }

    @Test
    fun `substrate decision resolves to the canonical ADR`() {
        val projectDirectory = Path.of(System.getProperty("user.dir"))
        val report = PhysicsGate.inspect(projectDirectory.resolve(PhysicsGate.DEFAULT_FILE), projectDirectory)
        val decision = report.rows.single { it.id == "numerical-substrate-decision" }

        assertEquals("openspec/adr/0018-tunnel-numerical-substrate.md", decision.evidence)
        assertEquals(null, decision.blocker)
        assertTrue(Files.isRegularFile(projectDirectory.resolve(requireNotNull(decision.evidence))))
        assertTrue(report.open)
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
