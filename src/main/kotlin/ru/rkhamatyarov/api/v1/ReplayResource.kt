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
import org.jboss.logging.Logger
import ru.rkhamatyarov.proto.ReplayFile
import ru.rkhamatyarov.replay.HeadlessReplayImporter
import ru.rkhamatyarov.replay.ReplayConverter
import ru.rkhamatyarov.service.RoomRegistry
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.GameIntent
import java.util.UUID

@Path("/api/v1/replay")
@Produces(MediaType.APPLICATION_JSON)
class ReplayResource {
    private val log = Logger.getLogger(javaClass)

    @Inject
    lateinit var roomRegistry: RoomRegistry

    @Inject
    lateinit var importer: HeadlessReplayImporter

    @GET
    @Path("/export/{roomId}")
    @Produces("application/octet-stream")
    fun export(
        @PathParam("roomId") roomId: String,
    ): Response {
        val replayLog = roomRegistry.get(roomId).getReplayLog()

        val (startingState, intents) =
            when (val first = replayLog.firstOrNull()?.action) {
                is GameAction.RestoreSnapshot -> first.state to replayLog.drop(1)
                else -> null to replayLog
            }

        val replayIntents = intents.mapNotNull { ReplayConverter.toProto(it) }

        val builder =
            ReplayFile
                .newBuilder()
                .setVersion("1")
                .setRoomId(roomId)
                .setStartWallTimeMs(System.currentTimeMillis())
                .setFrameCount(replayIntents.size)
                .addAllIntents(replayIntents)

        if (startingState != null) {
            builder.startingState = ReplayConverter.stateToSnapshot(startingState)
        }

        val bytes = builder.build().toByteArray()
        return Response
            .ok(bytes)
            .header("Content-Disposition", "attachment; filename=\"replay-$roomId.replay\"")
            .header("X-Replay-Frame-Count", replayIntents.size)
            .build()
    }

    @POST
    @Path("/import")
    @Consumes("application/octet-stream")
    fun import(body: ByteArray): Response {
        if (body.isEmpty()) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "Empty replay file"))
                .build()
        }

        return try {
            val replayFile = ReplayFile.parseFrom(body)
            val result = importer.import(replayFile)

            val newRoomId = "${replayFile.roomId.ifBlank { "unknown" }}_replay_${UUID.randomUUID()}"
            val room = roomRegistry.get(newRoomId)

            room.dispatch(GameIntent.Reliable(GameAction.RestoreSnapshot(result.finalState)))
            room.history.importRange(result.snapshots)

            log.infof(
                "Replay imported: roomId=%s frames=%d snapshots=%d elapsedSeconds=%.2f",
                newRoomId,
                result.frameCount,
                result.snapshots.size,
                result.finalState.elapsedSeconds,
            )

            Response
                .status(Response.Status.CREATED)
                .entity(
                    mapOf(
                        "roomId" to newRoomId,
                        "frameCount" to result.frameCount,
                        "snapshotCount" to result.snapshots.size,
                        "elapsedSeconds" to result.finalState.elapsedSeconds,
                    ),
                ).build()
        } catch (e: Exception) {
            log.error("Failed to import replay", e)
            Response
                .status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "Invalid replay file: ${e.message}"))
                .build()
        }
    }
}
