package ru.rkhamatyarov.determinism

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TunnelSourceGuardTest {
    @Test
    fun `tunnel production sources avoid platform math randomness and clocks`() {
        val root = Path.of(System.getProperty("user.dir"), SOURCE_DIRECTORY)
        assertEquals(emptyList(), TunnelSourceGuard.scan(root))
    }

    @Test
    fun `guard reports forbidden imports and clock calls in an injected source`() {
        val violations =
            TunnelSourceGuard.inspect(
                mapOf(
                    "Temporary.kt" to
                        """
                        import kotlin.math.sin
                        import java.lang.Math
                        import java.util.Random
                        import java.time.Clock
                        fun bad() = System.nanoTime() + sin(Math.sin(Random().nextDouble())) + random()
                        fun clock() = Clock.systemUTC()
                        """.trimIndent(),
                ),
            )

        assertTrue(violations.any { it.contains("kotlin.math") })
        assertTrue(violations.any { it.contains("java.lang.Math") })
        assertTrue(violations.any { it.contains("Random") })
        assertTrue(violations.any { it.contains("nanoTime") })
        assertTrue(violations.any { it.contains("clock access") })
    }

    private object TunnelSourceGuard {
        private val forbidden =
            listOf(
                "kotlin.math" to Regex("\\bkotlin\\s*\\.\\s*math\\b"),
                "java.lang.Math" to
                    Regex("\\bjava\\s*\\.\\s*lang\\s*\\.\\s*Math\\b|\\bMath\\s*\\."),
                "Random" to Regex("\\b(?:kotlin\\.random\\.|java\\.util\\.)?Random\\b|\\brandom\\s*\\("),
                "java.time.Clock" to Regex("\\b(?:java\\.time\\.)?Clock\\b"),
                "clock access" to
                    Regex(
                        "\\b(?:java\\.time\\.Clock|Clock\\s*\\.system[A-Za-z]*|" +
                            "System\\s*\\.\\s*(?:nanoTime|currentTimeMillis)|" +
                            "kotlin\\.time\\.Clock\\s*\\.\\s*System\\s*\\.\\s*now|" +
                            "(?:Instant|LocalDate|LocalDateTime|OffsetDateTime|ZonedDateTime)" +
                            "\\s*\\.\\s*now|Calendar\\s*\\.\\s*getInstance|Date)\\s*\\(",
                    ),
            )

        fun scan(root: Path): List<String> {
            require(Files.isDirectory(root)) { "tunnel source directory missing: $root" }
            val sources =
                Files.walk(root).use { paths ->
                    paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }
                        .toList()
                        .associate { it.fileName.toString() to Files.readString(it) }
                }
            return inspect(sources)
        }

        fun inspect(sources: Map<String, String>): List<String> =
            sources.toSortedMap().flatMap { (name, source) ->
                forbidden.flatMap { (label, pattern) ->
                    pattern.findAll(source).map { match -> "$name: forbidden $label '${match.value}'" }.toList()
                }
            }
    }

    private companion object {
        const val SOURCE_DIRECTORY = "src/main/kotlin/ru/rkhamatyarov/tunnel"
    }
}
