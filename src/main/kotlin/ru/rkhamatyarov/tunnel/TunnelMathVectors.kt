package ru.rkhamatyarov.tunnel

import java.nio.file.Files
import java.nio.file.Path

/** Compares integer-only substrate results with the committed x86-64 reference vectors. */
object TunnelMathVectors {
    const val RESOURCE = "src/test/resources/determinism/tunnel/q32-32-v1.txt"

    fun mismatches(projectDir: Path): List<String> =
        Files.readAllLines(projectDir.resolve(RESOURCE))
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val tokens = line.trim().split(' ')
                val expected = tokens.last().toLong()
                val actual = evaluate(tokens.dropLast(1))
                if (actual == expected) null else "${tokens.first()}: expected $expected, got $actual"
            }

    private fun evaluate(tokens: List<String>): Long =
        when (tokens.first()) {
            "integer" -> TunnelMath.fromInt(tokens[1].toLong()).raw
            "add" -> TunnelMath.add(TunnelScalar(tokens[1].toLong()), TunnelScalar(tokens[2].toLong())).raw
            "subtract" -> TunnelMath.subtract(TunnelScalar(tokens[1].toLong()), TunnelScalar(tokens[2].toLong())).raw
            "multiply" -> TunnelMath.multiply(TunnelScalar(tokens[1].toLong()), TunnelScalar(tokens[2].toLong())).raw
            "divide" -> TunnelMath.divide(TunnelScalar(tokens[1].toLong()), TunnelScalar(tokens[2].toLong())).raw
            "ratio" -> TunnelMath.fromRatio(tokens[1].toLong(), tokens[2].toLong()).raw
            else -> error("unknown vector operation '${tokens.first()}'")
        }
}
