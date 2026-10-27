package ru.rkhamatyarov.api.v1

import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import ru.rkhamatyarov.service.RoomRegistry
import ru.rkhamatyarov.telemetry.CaptureAccess
import ru.rkhamatyarov.telemetry.CaptureDossier
import ru.rkhamatyarov.telemetry.CapturePermission

@Path("/api/v1/capture")
@Produces(MediaType.APPLICATION_JSON)
class CaptureResource {
    @Inject
    lateinit var roomRegistry: RoomRegistry

    @Inject
    lateinit var access: CaptureAccess

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "unknown")
    lateinit var applicationVersion: String

    /** Downloads existing room evidence without dispatching gameplay actions or creating rooms. */
    @GET
    @Path("/{roomId}")
    fun capture(
        @PathParam("roomId") roomId: String,
        @HeaderParam("X-Capture-Token") token: String?,
    ): Response {
        val status =
            when (access.acquire(roomId, token)) {
                CapturePermission.DISABLED -> 404
                CapturePermission.FORBIDDEN -> 403
                CapturePermission.COOLDOWN -> 429
                CapturePermission.ALLOWED -> null
            }
        if (status != null) {
            return Response.status(status).header("Cache-Control", "no-store")
                .header("Retry-After", "10").build()
        }
        val room = roomRegistry.find(roomId) ?: throw NotFoundException()
        return try {
            val evidence = CaptureDossier.build(roomId, applicationVersion, room.captureRecorder.capture())
            Response.ok(evidence).header("Cache-Control", "no-store").build()
        } catch (exception: Exception) {
            Logger.getLogger(javaClass).error("Failed to capture room diagnostics", exception)
            Response.serverError().header("Cache-Control", "no-store").build()
        }
    }
}
