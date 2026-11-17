package ru.rkhamatyarov.determinism

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class NumericInventoryTest {
    @Test
    fun `math call sites match the reviewed per-file allowlist`() {
        val sourceDirectory =
            Path.of(
                System.getProperty("user.dir"),
                "src/main/kotlin/ru/rkhamatyarov/service/mvi",
            )
        val actual = linkedMapOf<String, Map<String, Int>>()
        Files.list(sourceDirectory).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".kt") }
                .sorted()
                .forEach { path -> countCalls(path)?.let { actual[path.fileName.toString()] = it } }
        }

        assertEquals(EXPECTED_CALLS, actual, "Update the audit report and allowlist after reviewing new math calls")
    }

    private fun countCalls(path: Path): Map<String, Int>? {
        val counts = mutableMapOf<String, Int>()
        Files.readAllLines(path).forEach { rawLine ->
            val line = rawLine.substringBefore("//")
            MATH_CALL.findAll(line).forEach { match ->
                val function = match.groupValues[1].ifBlank { match.groupValues[2] }
                counts[function] = counts.getOrDefault(function, 0) + 1
            }
        }
        return counts.takeIf { it.isNotEmpty() }?.toSortedMap()
    }

    companion object {
        private val MATH_CALL =
            Regex("(?:Math|StrictMath)\\.(sin|cos|atan2|hypot|pow)\\s*\\(|(?<![\\w.])(sin|cos|atan2|hypot|pow)\\s*\\(")
        private val EXPECTED_CALLS =
            mapOf(
                "LineCollisionPhysics.kt" to mapOf("atan2" to 1, "cos" to 2, "hypot" to 1, "sin" to 2),
                "OneTimerMechanic.kt" to mapOf("hypot" to 1),
                "PaddlePhysics.kt" to mapOf("cos" to 1, "hypot" to 2, "sin" to 1),
                "PuckPhysics.kt" to mapOf("hypot" to 2, "pow" to 1),
                "RabbitPhysics.kt" to mapOf("cos" to 2, "sin" to 2),
                "SpeedModel.kt" to mapOf("hypot" to 1),
                "TickReducer.kt" to mapOf("hypot" to 1),
            )
    }
}
