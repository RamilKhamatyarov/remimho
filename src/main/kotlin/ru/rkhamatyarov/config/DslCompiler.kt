package ru.rkhamatyarov.config

import jakarta.enterprise.context.ApplicationScoped
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@ApplicationScoped
class DslCompiler {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun compile(
        source: String,
        format: String,
    ): DslResult {
        if (source.isBlank()) return DslResult.Failure(listOf("source must not be blank"))
        val config =
            try {
                when (format.lowercase()) {
                    "json" -> json.decodeFromString<RuleConfig>(source)
                    "yaml", "yml" -> parseFlatConfig(parseYaml(source))
                    "toml" -> parseFlatConfig(parseToml(source))
                    else -> return DslResult.Failure(listOf("format must be json, yaml, yml, or toml"))
                }
            } catch (e: Exception) {
                return DslResult.Failure(listOf(e.message ?: "failed to parse config"))
            }

        val errors = validateDependencies(config)
        return if (errors.isEmpty()) DslResult.Success(config) else DslResult.Failure(errors)
    }

    fun encode(config: RuleConfig): ByteArray = json.encodeToString(config).toByteArray(Charsets.UTF_8)

    fun checksum(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    fun validateDependencies(config: RuleConfig): List<String> {
        val errors = mutableListOf<String>()
        config.lines.forEachIndexed { index, line ->
            if (line.x1 == line.x2 && line.y1 == line.y2) errors += "line[$index] must not be zero-length"
            if (line.x1 !in 0.0..800.0 || line.x2 !in 0.0..800.0) errors += "line[$index] x coordinates must be within canvas"
            if (line.y1 !in 0.0..600.0 || line.y2 !in 0.0..600.0) errors += "line[$index] y coordinates must be within canvas"
        }
        val teleportIds = config.teleports.map { it.id }.toSet()
        config.teleports.forEach { teleport ->
            if (teleport.pair !in teleportIds) errors += "teleport '${teleport.id}' pair '${teleport.pair}' is missing"
            if (teleport.id == teleport.pair) errors += "teleport '${teleport.id}' cannot pair with itself"
        }
        return errors
    }

    private fun parseYaml(source: String): Map<String, String> {
        val values = mutableMapOf<String, String>()
        val sectionStack = ArrayDeque<Pair<Int, String>>()
        source.lineSequence().forEach { raw ->
            val withoutComment = raw.substringBefore("#")
            if (withoutComment.isBlank()) return@forEach
            val indent = withoutComment.takeWhile { it == ' ' }.length
            val line = withoutComment.trim()
            val key = line.substringBefore(":").trim()
            val value = line.substringAfter(":", "").trim().trim('"')
            while (sectionStack.isNotEmpty() && sectionStack.last().first >= indent) sectionStack.removeLast()
            if (value.isBlank()) {
                sectionStack.addLast(indent to key)
            } else {
                val path = (sectionStack.map { it.second } + key).joinToString(".")
                values[path] = value
            }
        }
        return values
    }

    private fun parseToml(source: String): Map<String, String> {
        val values = mutableMapOf<String, String>()
        var section = ""
        source.lineSequence().forEach { raw ->
            val line = raw.substringBefore("#").trim()
            if (line.isBlank()) return@forEach
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.removePrefix("[").removeSuffix("]").trim()
                return@forEach
            }
            val key = line.substringBefore("=").trim()
            val value = line.substringAfter("=", "").trim().trim('"')
            values[listOf(section, key).filter { it.isNotBlank() }.joinToString(".")] = value
        }
        return values
    }

    private fun parseFlatConfig(values: Map<String, String>): RuleConfig =
        RuleConfig(
            name = values["name"] ?: "Untitled",
            version = values["version"]?.toIntOrNull() ?: 1,
            speed =
                RuleSpeedConfig(
                    baseMultiplier = values["speed.baseMultiplier"]?.toDoubleOrNull() ?: 1.0,
                    timeAccelerationRate = values["speed.timeAccelerationRate"]?.toDoubleOrNull() ?: 0.05,
                    levelAccelerationPerLine = values["speed.levelAccelerationPerLine"]?.toDoubleOrNull() ?: 0.02,
                    maxMultiplier = values["speed.maxMultiplier"]?.toDoubleOrNull() ?: 3.0,
                ),
            ai =
                RuleAiConfig(
                    enabled = values["ai.enabled"]?.toBooleanStrictOrNull() ?: true,
                    reactionDelayMs = values["ai.reactionDelayMs"]?.toLongOrNull() ?: 180,
                    maxSpeed = values["ai.maxSpeed"]?.toDoubleOrNull() ?: 180.0,
                    trackingError = values["ai.trackingError"]?.toDoubleOrNull() ?: 10.0,
                    reactZoneRatio = values["ai.reactZoneRatio"]?.toDoubleOrNull() ?: 0.7,
                ),
        )
}

sealed interface DslResult {
    data class Success(
        val config: RuleConfig,
    ) : DslResult

    data class Failure(
        val errors: List<String>,
    ) : DslResult
}
