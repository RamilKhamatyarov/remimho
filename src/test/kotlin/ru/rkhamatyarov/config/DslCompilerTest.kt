package ru.rkhamatyarov.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DslCompilerTest {
    private val compiler = DslCompiler()

    @Test
    fun `compile YAML returns rule config`() {
        val source =
            """
            name: Solo Rules
            speed:
              baseMultiplier: 1.4
            ai:
              reactionDelayMs: 220
            """.trimIndent()

        val result = compiler.compile(source, "yaml") as DslResult.Success

        assertEquals("Solo Rules", result.config.name)
        assertEquals(1.4, result.config.speed.baseMultiplier, 0.0001)
        assertEquals(220, result.config.ai.reactionDelayMs)
    }

    @Test
    fun `compile TOML returns rule config`() {
        val source =
            """
            name = "Toml Rules"
            [speed]
            maxMultiplier = 4.0
            """.trimIndent()

        val result = compiler.compile(source, "toml") as DslResult.Success

        assertEquals("Toml Rules", result.config.name)
        assertEquals(4.0, result.config.speed.maxMultiplier, 0.0001)
    }

    @Test
    fun `validate dependencies rejects missing teleport pair`() {
        val config = RuleConfig(teleports = listOf(RuleTeleportConfig(id = "a", pair = "b")))

        val errors = compiler.validateDependencies(config)

        assertTrue(errors.any { it.contains("pair 'b' is missing") })
    }
}
