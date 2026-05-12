package ru.rkhamatyarov.api.v1

import com.fasterxml.jackson.databind.JsonNode
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import ru.rkhamatyarov.model.AiOpponentConfig
import ru.rkhamatyarov.model.SpeedConfig
import ru.rkhamatyarov.service.GameEngine

enum class ContentType {
    LEVEL,
    SKIN,
    THEME,
    POWERUP_SET,
    GAME_MODE,
    SPEED_CONFIG,
    AI_OPPONENT_CONFIG,
}

data class WorkshopContentDTO(
    val type: ContentType,
    val data: JsonNode,
    val metadata: Map<String, String>,
)

@Path("/api/v1/workshop")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class WorkshopResource {
    @Inject
    lateinit var engine: GameEngine

    @POST
    @Path("/content")
    fun submitContent(dto: WorkshopContentDTO): Response {
        if (dto.metadata["name"].isNullOrBlank()) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "metadata.name is required"))
                .build()
        }

        return Response
            .status(Response.Status.CREATED)
            .entity(
                mapOf(
                    "type" to dto.type.name,
                    "accepted" to true,
                ),
            ).build()
    }

    @POST
    @Path("/speed-config")
    fun applySpeedConfig(config: SpeedConfig): Response {
        if (config.baseMultiplier !in 0.1..5.0) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "baseMultiplier must be between 0.1 and 5.0"))
                .build()
        }
        if (config.timeAccelerationRate !in 0.0..1.0) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "timeAccelerationRate must be between 0.0 and 1.0"))
                .build()
        }
        if (config.levelAccelerationPerLine !in 0.0..0.5) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "levelAccelerationPerLine must be between 0.0 and 0.5"))
                .build()
        }
        if (config.maxMultiplier !in 1.0..10.0) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "maxMultiplier must be between 1.0 and 10.0"))
                .build()
        }
        engine.speedConfig = config
        return Response
            .ok(
                mapOf(
                    "applied" to true,
                    "baseMultiplier" to config.baseMultiplier,
                    "timeAccelerationRate" to config.timeAccelerationRate,
                    "levelAccelerationPerLine" to config.levelAccelerationPerLine,
                    "maxMultiplier" to config.maxMultiplier,
                ),
            ).build()
    }

    @POST
    @Path("/ai-opponent-config")
    fun applyAiOpponentConfig(config: AiOpponentConfig): Response {
        validateAiOpponentConfig(config)?.let { return it }
        engine.aiOpponentConfig = config
        return aiOpponentConfigResponse(config)
    }

    companion object {
        fun validateAiOpponentConfig(config: AiOpponentConfig): Response? {
            if (config.reactionDelayMs !in 0..1500) {
                return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(mapOf("error" to "reactionDelayMs must be between 0 and 1500"))
                    .build()
            }
            if (config.maxSpeed !in 40.0..600.0) {
                return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(mapOf("error" to "maxSpeed must be between 40.0 and 600.0"))
                    .build()
            }
            if (config.trackingError !in -80.0..80.0) {
                return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(mapOf("error" to "trackingError must be between -80.0 and 80.0"))
                    .build()
            }
            if (config.reactZoneRatio !in 0.25..1.0) {
                return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(mapOf("error" to "reactZoneRatio must be between 0.25 and 1.0"))
                    .build()
            }
            return null
        }

        fun aiOpponentConfigResponse(config: AiOpponentConfig): Response =
            Response
                .ok(
                    mapOf(
                        "applied" to true,
                        "enabled" to config.enabled,
                        "reactionDelayMs" to config.reactionDelayMs,
                        "maxSpeed" to config.maxSpeed,
                        "trackingError" to config.trackingError,
                        "reactZoneRatio" to config.reactZoneRatio,
                    ),
                ).build()
    }
}
