package ru.rkhamatyarov.api.v1

import com.fasterxml.jackson.databind.JsonNode
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

enum class ContentType {
    LEVEL,
    SKIN,
    THEME,
    POWERUP_SET,
    GAME_MODE,
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
}
