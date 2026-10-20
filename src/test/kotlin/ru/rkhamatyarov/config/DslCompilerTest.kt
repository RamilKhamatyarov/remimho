package ru.rkhamatyarov.config

import org.junit.jupiter.api.Test
import ru.rkhamatyarov.model.SpeedConfig
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

    @Test
    fun `compile TOML reads rally speed settings`() {
        val source =
            """
            name = "Pro Rally"
            [speed]
            rallyGrowthPerHit = 1.1
            smashBoost = 0.2
            maxRallySpeed = 2400
            maxEffectiveSpeed = 3600
            """.trimIndent()

        val speed = (compiler.compile(source, "toml") as DslResult.Success).config.speed

        assertEquals(1.1, speed.rallyGrowthPerHit, 0.0001)
        assertEquals(0.2, speed.smashBoost, 0.0001)
        assertEquals(2_400.0, speed.maxRallySpeed, 0.0001)
        assertEquals(3_600.0, speed.maxEffectiveSpeed, 0.0001)
    }

    @Test
    fun `rule speed defaults match the domain and map every field`() {
        val rule = RuleSpeedConfig(baseMultiplier = 1.2, smashBoost = 0.0, maxRallySpeed = 2_000.0)

        assertEquals(SpeedConfig(), RuleSpeedConfig().toSpeedConfig())
        assertEquals(
            SpeedConfig(baseMultiplier = 1.2, smashBoost = 0.0, maxRallySpeed = 2_000.0),
            rule.toSpeedConfig(),
        )
    }
}
