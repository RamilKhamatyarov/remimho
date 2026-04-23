package ru.rkhamatyarov.api.v1

import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import ru.rkhamatyarov.api.v1.request.PowerUpSpawnRequest
import ru.rkhamatyarov.api.v1.request.SpeedRequest
import ru.rkhamatyarov.api.v1.request.TimeTravelRequest
import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.proto.GameStateDelta
import ru.rkhamatyarov.service.GameEngine
import ru.rkhamatyarov.service.StateHistory

@Path("/api/v1/game")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class GameResource {
    @Inject lateinit var engine: GameEngine

    @Inject lateinit var history: StateHistory

    @GET
    @Path("/state")
    fun getGameState() =
        Response
            .ok(
                mapOf(
                    "puckX" to engine.puck.x,
                    "puckY" to engine.puck.y,
                    "paddle1Y" to engine.paddle1Y,
                    "paddle2Y" to engine.paddle2Y,
                    "paddleHeight" to engine.paddleHeight,
                    "canvasWidth" to engine.canvasWidth,
                    "canvasHeight" to engine.canvasHeight,
                    "paused" to engine.paused,
                    "linesCount" to engine.lines.size,
                    "scoreA" to engine.score.playerA,
                    "scoreB" to engine.score.playerB,
                ),
            ).build()

    @POST
    @Path("/reset")
    fun resetGame(): Response {
        engine.resetPuck()
        engine.clearLines()
        engine.paused = false
        return Response.ok(mapOf("message" to "Game reset successfully")).build()
    }

    @POST
    @Path("/pause")
    fun togglePause(): Response {
        engine.paused = !engine.paused
        return Response.ok(mapOf("paused" to engine.paused)).build()
    }

    @POST
    @Path("/speed")
    fun setSpeed(request: SpeedRequest): Response {
        if (request.speed !in 0.1..10.0) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "Speed must be between 0.1 and 10.0"))
                .build()
        }
        return Response.ok(mapOf("message" to "Speed updated", "speed" to request.speed)).build()
    }

    @DELETE
    @Path("/lines")
    fun clearLines(): Response {
        val count = engine.lines.size
        engine.clearLines()
        return Response.ok(mapOf("cleared" to count)).build()
    }

    @GET
    @Path("/statistics")
    fun getStatistics() =
        Response
            .ok(
                mapOf(
                    "drawnLines" to engine.lines.size,
                    "isPaused" to engine.paused,
                    "scoreA" to engine.score.playerA,
                    "scoreB" to engine.score.playerB,
                ),
            ).build()

    @POST
    @Path("/time-travel")
    fun commitTimeTravel(request: TimeTravelRequest): Response {
        if (request.offset < 0.0 || request.offset > StateHistory.MAX_RETENTION_SECONDS) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "Offset must be between 0 and ${StateHistory.MAX_RETENTION_SECONDS} seconds"))
                .build()
        }

        val snapshot =
            history.getByOffsetSeconds(request.offset)
                ?: return Response
                    .status(Response.Status.NOT_FOUND)
                    .entity(mapOf("error" to "No snapshot available for offset ${request.offset}s"))
                    .build()

        engine.restoreFromDelta(GameStateDelta.parseFrom(snapshot))
        history.clear()
        return Response
            .ok(
                mapOf(
                    "message" to "Timeline restored",
                    "offset" to request.offset,
                    "puckX" to engine.puck.x,
                    "puckY" to engine.puck.y,
                ),
            ).build()
    }

    @POST
    @Path("/powerup/spawn")
    fun spawnPowerUp(request: PowerUpSpawnRequest): Response =
        try {
            val type = PowerUpType.valueOf(request.type)
            Response.ok(mapOf("type" to type.name, "message" to "Power-up spawned")).build()
        } catch (_: IllegalArgumentException) {
            Response
                .status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "Invalid power-up type: ${request.type}"))
                .build()
        }
}
