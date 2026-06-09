package ru.rkhamatyarov.api.v1

import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.config.inject.ConfigProperty
import ru.rkhamatyarov.api.v1.request.PowerUpSpawnRequest
import ru.rkhamatyarov.api.v1.request.SpeedRequest
import ru.rkhamatyarov.api.v1.request.TimeTravelRequest
import ru.rkhamatyarov.model.AiOpponentConfig
import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.proto.GameStateDelta
import ru.rkhamatyarov.service.GameRoom
import ru.rkhamatyarov.service.RoomRegistry
import ru.rkhamatyarov.service.StateHistory
import ru.rkhamatyarov.service.createRandomPowerUp
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.GameIntent
import ru.rkhamatyarov.service.mvi.mviStateFromDelta
import java.util.Base64

@Path("/api/v1/game")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class GameResource {
    @Inject lateinit var history: StateHistory

    @Inject lateinit var roomRegistry: RoomRegistry

    @ConfigProperty(name = "remimho.client.interpolation-enabled", defaultValue = "false")
    var clientInterpolationEnabled: Boolean = false

    @ConfigProperty(name = "remimho.rooms.enabled", defaultValue = "false")
    var roomsEnabled: Boolean = false

    @GET
    @Path("/state")
    fun getGameState(): Response {
        val state = defaultRoom().reliableState.value
        return Response
            .ok(
                mapOf(
                    "puckX" to state.puck.x,
                    "puckY" to state.puck.y,
                    "paddle1Y" to state.paddle1Y,
                    "paddle2Y" to state.paddle2Y,
                    "paddleHeight" to state.paddleHeight,
                    "canvasWidth" to state.canvasWidth,
                    "canvasHeight" to state.canvasHeight,
                    "paused" to state.paused,
                    "linesCount" to state.lines.size,
                    "scoreA" to state.score.playerA,
                    "scoreB" to state.score.playerB,
                    "aiOpponent" to state.aiConfig,
                ),
            ).build()
    }

    @GET
    @Path("/client-config")
    fun getClientConfig(): Response =
        Response
            .ok(
                mapOf(
                    "clientInterpolation" to clientInterpolationEnabled,
                    "roomsEnabled" to roomsEnabled,
                ),
            ).build()

    @POST
    @Path("/reset")
    fun resetGame(): Response {
        defaultRoom().dispatch(GameIntent.Reliable(GameAction.Reset))
        return Response.ok(mapOf("message" to "Game reset successfully")).build()
    }

    @POST
    @Path("/pause")
    fun togglePause(): Response {
        val room = defaultRoom()
        val paused = !room.reliableState.value.paused
        room.dispatch(GameIntent.Reliable(GameAction.TogglePause))
        return Response.ok(mapOf("paused" to paused)).build()
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
        val room = defaultRoom()
        val count = room.reliableState.value.lines.size
        room.dispatch(GameIntent.Reliable(GameAction.ClearLines))
        return Response.ok(mapOf("cleared" to count)).build()
    }

    @GET
    @Path("/statistics")
    fun getStatistics(): Response {
        val state = defaultRoom().reliableState.value
        return Response
            .ok(
                mapOf(
                    "drawnLines" to state.lines.size,
                    "isPaused" to state.paused,
                    "scoreA" to state.score.playerA,
                    "scoreB" to state.score.playerB,
                ),
            ).build()
    }

    @GET
    @Path("/ai-opponent")
    fun getAiOpponentConfig(): Response = Response.ok(defaultRoom().reliableState.value.aiConfig).build()

    @POST
    @Path("/ai-opponent")
    fun setAiOpponentConfig(config: AiOpponentConfig): Response {
        WorkshopResource.validateAiOpponentConfig(config)?.let { return it }
        defaultRoom().dispatch(GameIntent.Reliable(GameAction.ApplyAiConfig(config)))
        return WorkshopResource.aiOpponentConfigResponse(config)
    }

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

        val restoredState = mviStateFromDelta(GameStateDelta.parseFrom(snapshot))
        defaultRoom().dispatch(GameIntent.Reliable(GameAction.RestoreSnapshot(restoredState)))
        history.clear()
        return Response
            .ok(
                mapOf(
                    "message" to "Timeline restored",
                    "offset" to request.offset,
                    "puckX" to restoredState.puck.x,
                    "puckY" to restoredState.puck.y,
                ),
            ).build()
    }

    @POST
    @Path("/powerup/spawn")
    fun spawnPowerUp(
        request: PowerUpSpawnRequest,
        @QueryParam("roomId") roomId: String?,
    ): Response =
        try {
            val type = PowerUpType.valueOf(request.type)
            val room = roomRegistry.get(roomId ?: RoomRegistry.DEFAULT_ROOM_ID)
            val powerUp = createRandomPowerUp(type, room.reliableState.value)
            val accepted = room.dispatch(GameIntent.Reliable(GameAction.SpawnPowerUp(powerUp)))

            if (!accepted) {
                Response
                    .status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(mapOf("error" to "Room is not accepting power-up spawns"))
                    .build()
            } else {
                Response
                    .ok(
                        mapOf(
                            "type" to type.name,
                            "roomId" to room.id,
                            "powerUpId" to powerUp.id,
                            "x" to powerUp.x,
                            "y" to powerUp.y,
                            "message" to "Power-up spawned",
                        ),
                    ).build()
            }
        } catch (_: IllegalArgumentException) {
            Response
                .status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "Invalid power-up type: ${request.type}"))
                .build()
        }

    @GET
    @Path("/ghost/{roomId}")
    fun exportGhost(
        @PathParam("roomId") roomId: String,
        @QueryParam("start") startOffset: Double?,
        @QueryParam("end") endOffset: Double?,
    ): Response {
        if (!roomsEnabled) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "rooms must be enabled"))
                .build()
        }

        val start = startOffset ?: 0.0
        val end = endOffset ?: StateHistory.MAX_RETENTION_SECONDS.toDouble()
        if (start < 0.0 || end < start || end > StateHistory.MAX_RETENTION_SECONDS) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "Range must satisfy 0 <= start <= end <= ${StateHistory.MAX_RETENTION_SECONDS}"))
                .build()
        }

        val frames =
            roomRegistry
                .get(roomId)
                .history
                .exportRange(start, end)
                .map { (timestampNs, bytes) ->
                    mapOf(
                        "timestampNs" to timestampNs,
                        "delta" to Base64.getEncoder().encodeToString(bytes),
                    )
                }

        return Response
            .ok(
                mapOf(
                    "roomId" to roomId,
                    "startOffset" to start,
                    "endOffset" to end,
                    "frames" to frames,
                ),
            ).build()
    }

    private fun defaultRoom(): GameRoom = roomRegistry.get(RoomRegistry.DEFAULT_ROOM_ID)
}
