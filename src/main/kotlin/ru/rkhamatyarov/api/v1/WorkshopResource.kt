package ru.rkhamatyarov.api.v1

import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import ru.rkhamatyarov.api.v1.dto.WorkshopContentDTO
import ru.rkhamatyarov.config.CompileRequest
import ru.rkhamatyarov.config.CompileResponse
import ru.rkhamatyarov.config.DslCompiler
import ru.rkhamatyarov.config.DslResult
import ru.rkhamatyarov.config.PreviewResponse
import ru.rkhamatyarov.config.RuleConfig
import ru.rkhamatyarov.model.AiOpponentConfig
import ru.rkhamatyarov.model.SpeedConfig
import ru.rkhamatyarov.service.RoomRegistry
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.GameIntent
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviLine
import ru.rkhamatyarov.service.mvi.MviPoint
import ru.rkhamatyarov.service.mvi.reduce
import ru.rkhamatyarov.workshop.CompiledConfigCache

@Path("/api/v1/workshop")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class WorkshopResource {
    @Inject
    lateinit var roomRegistry: RoomRegistry

    @Inject
    lateinit var dslCompiler: DslCompiler

    @Inject
    lateinit var compiledConfigCache: CompiledConfigCache

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
    @Path("/compile")
    fun compile(request: CompileRequest): Response =
        when (val result = dslCompiler.compile(request.source, request.format)) {
            is DslResult.Failure -> {
                Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(CompileResponse(ok = false, errors = result.errors))
                    .build()
            }

            is DslResult.Success -> {
                val bytes = dslCompiler.encode(result.config)
                val checksum = dslCompiler.checksum(bytes)
                compiledConfigCache.put(checksum, bytes)
                Response
                    .ok(
                        CompileResponse(
                            ok = true,
                            config = result.config,
                            version = result.config.version,
                            checksum = checksum,
                        ),
                    ).build()
            }
        }

    @GET
    @Path("/compiled/{checksum}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun compiledByChecksum(
        @PathParam("checksum") checksum: String,
    ): Response {
        val bytes =
            compiledConfigCache.get(checksum)
                ?: return Response.status(Response.Status.NOT_FOUND).entity(mapOf("error" to "compiled config not found")).build()
        return Response
            .ok(bytes)
            .header("X-Config-Checksum", checksum)
            .build()
    }

    @POST
    @Path("/preview")
    fun preview(config: RuleConfig): Response {
        val validationErrors = dslCompiler.validateDependencies(config)
        if (validationErrors.isNotEmpty()) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(PreviewResponse(ok = false, errors = validationErrors))
                .build()
        }

        var previewState =
            MviGameState(
                speedConfig =
                    SpeedConfig(
                        baseMultiplier = config.speed.baseMultiplier,
                        timeAccelerationRate = config.speed.timeAccelerationRate,
                        levelAccelerationPerLine = config.speed.levelAccelerationPerLine,
                        maxMultiplier = config.speed.maxMultiplier,
                    ),
                aiConfig =
                    AiOpponentConfig(
                        enabled = config.ai.enabled,
                        reactionDelayMs = config.ai.reactionDelayMs,
                        maxSpeed = config.ai.maxSpeed,
                        trackingError = config.ai.trackingError,
                        reactZoneRatio = config.ai.reactZoneRatio,
                    ),
                lines =
                    config.lines.mapIndexed { index, line ->
                        MviLine(
                            id = "preview-$index",
                            points = listOf(MviPoint(line.x1, line.y1), MviPoint(line.x2, line.y2)),
                        )
                    },
            )

        val beforeMemory = usedMemory()
        val startNs = System.nanoTime()
        var collisionCount = 0
        var previousVx = previewState.puck.vx
        var previousVy = previewState.puck.vy
        repeat(PREVIEW_TICKS) { tick ->
            previewState = reduce(previewState, GameAction.Tick(PREVIEW_DT, startNs + tick))
            if (previewState.puck.vx != previousVx || previewState.puck.vy != previousVy) collisionCount++
            previousVx = previewState.puck.vx
            previousVy = previewState.puck.vy
        }
        val frameTimeMs = (System.nanoTime() - startNs) / 1_000_000.0 / PREVIEW_TICKS
        val memoryBytes = (usedMemory() - beforeMemory).coerceAtLeast(0)
        val bytes = dslCompiler.encode(config)
        val checksum = dslCompiler.checksum(bytes)
        compiledConfigCache.put(checksum, bytes)

        return Response
            .ok(
                PreviewResponse(
                    ok = true,
                    checksum = checksum,
                    collisionCount = collisionCount,
                    frameTimeMs = frameTimeMs,
                    memoryBytes = memoryBytes,
                ),
            ).build()
    }

    @POST
    @Path("/speed-config")
    fun applySpeedConfig(config: SpeedConfig): Response {
        validateSpeedConfig(config)?.let { return it }
        defaultRoom().dispatch(GameIntent.Reliable(GameAction.ApplySpeedConfig(config)))
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
        defaultRoom().dispatch(GameIntent.Reliable(GameAction.ApplyAiConfig(config)))
        return aiOpponentConfigResponse(config)
    }

    companion object {
        private const val PREVIEW_TICKS = 200
        private const val PREVIEW_DT = 0.016

        private fun usedMemory(): Long {
            val runtime = Runtime.getRuntime()
            return runtime.totalMemory() - runtime.freeMemory()
        }

        fun validateAiOpponentConfig(config: AiOpponentConfig): Response? {
            if (config.reactionDelayMs !in 0..1500) {
                return badRequest("reactionDelayMs must be between 0 and 1500")
            }
            if (config.maxSpeed !in 40.0..600.0) {
                return badRequest("maxSpeed must be between 40.0 and 600.0")
            }
            if (config.trackingError !in -80.0..80.0) {
                return badRequest("trackingError must be between -80.0 and 80.0")
            }
            if (config.reactZoneRatio !in 0.25..1.0) {
                return badRequest("reactZoneRatio must be between 0.25 and 1.0")
            }
            return null
        }

        fun validateSpeedConfig(config: SpeedConfig): Response? {
            if (config.baseMultiplier !in 0.1..5.0) {
                return badRequest("baseMultiplier must be between 0.1 and 5.0")
            }
            if (config.timeAccelerationRate !in 0.0..1.0) {
                return badRequest("timeAccelerationRate must be between 0.0 and 1.0")
            }
            if (config.levelAccelerationPerLine !in 0.0..0.5) {
                return badRequest("levelAccelerationPerLine must be between 0.0 and 0.5")
            }
            if (config.maxMultiplier !in 1.0..10.0) {
                return badRequest("maxMultiplier must be between 1.0 and 10.0")
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

        private fun badRequest(message: String): Response =
            Response
                .status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to message))
                .build()
    }

    private fun defaultRoom() = roomRegistry.get(RoomRegistry.DEFAULT_ROOM_ID)
}
