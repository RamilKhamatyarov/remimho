package ru.rkhamatyarov.determinism

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/** Machine-checks the evidence rows required before tunnel physics may begin. */
object PhysicsGate {
    const val DEFAULT_FILE = "docs/determinism/physics-gate.yaml"
    const val EXIT_OPEN = 0
    const val EXIT_CLOSED = 1
    const val EXIT_INVALID = 2

    fun check(
        args: List<String>,
        out: (String) -> Unit,
    ): Int =
        try {
            require(args.size in 2..3) { "gate needs <projectDir> [optional gate file]" }
            val projectDir = Path.of(args[1])
            val gateFile = args.getOrNull(2)?.let(projectDir::resolve) ?: projectDir.resolve(DEFAULT_FILE)
            val report = inspect(gateFile, projectDir)
            out("| Prerequisite | Status | Evidence | Result |")
            out("| --- | --- | --- | --- |")
            report.rows.forEach { row ->
                val result = row.blocker?.let { "BLOCKED: $it" } ?: "PASS"
                out("| ${row.id} | ${row.status} | ${row.evidence ?: "-"} | $result |")
            }
            val closingMessage = "GATE CLOSED (${report.rows.count { it.blocker != null }} blocker(s))"
            out(if (report.open) "GATE OPEN" else closingMessage)
            if (report.open) EXIT_OPEN else EXIT_CLOSED
        } catch (error: Exception) {
            out("INVALID GATE: ${error.message}")
            EXIT_INVALID
        }

    fun inspect(
        gateFile: Path,
        projectDir: Path,
    ): GateReport {
        val root = JsonMapper.builder().build().readTree(Files.readString(gateFile))
        require(root.path("version").asInt(-1) == 1) { "unsupported gate schema version" }
        val requirements = root.path("prerequisites")
        require(requirements.isArray && !requirements.isEmpty) { "gate has no prerequisites" }
        val ids = HashSet<String>()
        val rows =
            requirements.values().map { requirement ->
                parseRequirement(requirement, projectDir, ids)
            }
        return GateReport(rows)
    }

    private fun parseRequirement(
        node: JsonNode,
        projectDir: Path,
        ids: MutableSet<String>,
    ): GateRow {
        val id = node.path("id").asText("")
        val status = node.path("status").asText("")
        val evidence = node.path("evidence").takeUnless(JsonNode::isNull)?.asText()
        require(id.matches(Regex("[a-z0-9-]+"))) { "invalid prerequisite id '$id'" }
        require(ids.add(id)) { "duplicate prerequisite id '$id'" }
        require(status in ALLOWED_STATUSES) { "invalid status '$status' for '$id'" }

        val blocker =
            when {
                status != STATUS_PASS -> "status is '$status'"
                evidence.isNullOrBlank() -> "passing row has no evidence pointer"
                !evidenceExists(evidence, projectDir) -> "evidence pointer does not resolve"
                else -> null
            }
        return GateRow(id, status, evidence, blocker)
    }

    private fun evidenceExists(
        evidence: String,
        projectDir: Path,
    ): Boolean =
        if (evidence.startsWith("https://")) {
            runCatching { URI(evidence).host != null }.getOrDefault(false)
        } else {
            val file = evidence.substringBefore('#')
            val root = projectDir.toAbsolutePath().normalize()
            val resolved = root.resolve(file).normalize()
            file.isNotBlank() && resolved.startsWith(root) && Files.exists(resolved)
        }

    private const val STATUS_PASS = "pass"
    private val ALLOWED_STATUSES = setOf(STATUS_PASS, "pending", "blocked", "provisional")
}

data class GateRow(
    val id: String,
    val status: String,
    val evidence: String?,
    val blocker: String?,
)

data class GateReport(
    val rows: List<GateRow>,
) {
    val open: Boolean
        get() = rows.isNotEmpty() && rows.all { it.blocker == null }
}
