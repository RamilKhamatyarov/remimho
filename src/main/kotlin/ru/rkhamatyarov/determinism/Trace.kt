package ru.rkhamatyarov.determinism

import java.nio.file.Files
import java.nio.file.Path

/** One state hash, taken after [step] was reduced. Steps start at 1. */
data class TraceEntry(
    val step: Long,
    val hash: Long,
)

/**
 * Per-step hash sequence of one run.
 *
 * [header] entries named in [IDENTITY_KEYS] say what was simulated and must match before two
 * traces may be compared; the rest (architecture, JVM) only describe where it ran.
 */
data class Trace(
    val header: Map<String, String>,
    val entries: List<TraceEntry>,
) {
    fun write(path: Path) {
        path.toAbsolutePath().parent?.let(Files::createDirectories)
        Files.newBufferedWriter(path).use { out ->
            out.write("$MAGIC\n")
            header.forEach { (key, value) -> out.write("# $key: $value\n") }
            entries.forEach { out.write("${it.step} ${hex(it.hash)}\n") }
        }
    }

    companion object {
        const val MAGIC = "# remimho-trace 1"
        const val KEY_SOURCE = "source"
        const val KEY_KIND = "kind"
        const val KEY_PHYSICS = "physics"
        const val KEY_SUBSTRATE = "substrate"
        const val KEY_ARCH = "arch"
        const val KEY_JVM = "jvm"
        val IDENTITY_KEYS = listOf(KEY_SOURCE, KEY_KIND, KEY_PHYSICS, KEY_SUBSTRATE)

        fun read(path: Path): Trace = parse(Files.readAllLines(path), path.toString())

        fun parse(
            lines: List<String>,
            origin: String = "trace",
        ): Trace {
            require(lines.firstOrNull()?.trim() == MAGIC) { "$origin is not a remimho trace" }
            val header = LinkedHashMap<String, String>()
            val entries = ArrayList<TraceEntry>()
            for ((index, raw) in lines.withIndex().drop(1)) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                if (line.startsWith("#")) {
                    val (key, value) =
                        headerLine(
                            line,
                        ) ?: throw IllegalArgumentException("$origin:${index + 1}: bad header")
                    header[key] = value
                    continue
                }
                val parts = line.split(' ')
                require(parts.size == 2) { "$origin:${index + 1}: expected 'step hash'" }
                entries += TraceEntry(parts[0].toLong(), java.lang.Long.parseUnsignedLong(parts[1], HEX_RADIX))
            }
            return Trace(header, entries)
        }

        /** Where this process runs; informational, never part of a trace's identity. */
        fun environment(): Map<String, String> =
            linkedMapOf(
                KEY_ARCH to System.getProperty("os.arch", "unknown"),
                KEY_JVM to System.getProperty("java.vm.name", "") + " " + System.getProperty("java.vm.version", ""),
            )

        internal fun headerLine(line: String): Pair<String, String>? {
            val body = line.removePrefix("#").trim()
            val colon = body.indexOf(':')
            if (colon <= 0) return null
            return body.substring(0, colon).trim() to body.substring(colon + 1).trim()
        }

        private const val HEX_RADIX = 16
    }
}

/** The complete field listing of one state, for comparing with a state from another machine. */
data class StateDump(
    val header: Map<String, String>,
    val step: Long,
    val fields: List<DumpedField>,
) {
    fun write(path: Path) {
        path.toAbsolutePath().parent?.let(Files::createDirectories)
        Files.newBufferedWriter(path).use { out ->
            out.write("$MAGIC\n")
            header.forEach { (key, value) -> out.write("# $key: $value\n") }
            out.write("# $KEY_STEP: $step\n")
            fields.forEach { out.write("${it.path}\t${it.exact}\t${it.readable}\n") }
        }
    }

    companion object {
        const val MAGIC = "# remimho-dump 1"
        const val KEY_STEP = "step"

        fun read(path: Path): StateDump {
            val lines = Files.readAllLines(path)
            require(lines.firstOrNull()?.trim() == MAGIC) { "$path is not a remimho state dump" }
            val header = LinkedHashMap<String, String>()
            val fields = ArrayList<DumpedField>()
            for (raw in lines.drop(1)) {
                if (raw.isBlank()) continue
                if (raw.startsWith("#")) {
                    Trace.headerLine(raw)?.let { (key, value) -> header[key] = value }
                    continue
                }
                val parts = raw.split('\t')
                require(parts.size >= 2) { "$path: bad field line '$raw'" }
                fields += DumpedField(parts[0], parts[1], parts.getOrElse(2) { "" })
            }
            val step = header.remove(KEY_STEP)?.toLong() ?: throw IllegalArgumentException("$path has no step")
            return StateDump(header, step, fields)
        }
    }
}
