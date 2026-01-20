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
import org.jboss.logging.Logger
import ru.rkhamatyarov.model.GameState
import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.service.GameEngine

/**
 * REST API v1 for game state management
 * Base path: /api/v1/game
 */
@Path("/api/v1/game")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class GameResource {
    private val log = Logger.getLogger(javaClass)

    @Inject
    lateinit var gameState: GameState

    @Inject
    lateinit var gameEngine: GameEngine

    /**
     * GET /api/v1/game/state
     * Returns current game state snapshot
     */
    @GET
    @Path("/state")
    fun getGameState(): Response {
        val state = createGameStateResponse()
        return Response.ok(state).build()
    }

    /**
     * POST /api/v1/game/reset
     * Resets the game to initial state
     */
    @POST
    @Path("/reset")
    fun resetGame(): Response {
        gameState.reset()
        log.info("Game reset via REST API")
        return Response.ok(mapOf("message" to "Game reset successfully")).build()
    }

    /**
     * POST /api/v1/game/pause
     * Toggles game pause state
     */
    @POST
    @Path("/pause")
    fun togglePause(): Response {
        gameState.togglePause()
        val status = if (gameState.paused) "paused" else "running"
        log.info("Game $status via REST API")
        return Response
            .ok(
                mapOf(
                    "paused" to gameState.paused,
                    "status" to status,
                ),
            ).build()
    }

    /**
     * POST /api/v1/game/speed
     * Sets game speed multiplier
     */
    @POST
    @Path("/speed")
    fun setSpeed(request: SpeedRequest): Response {
        if (request.speed < 0.1 || request.speed > 10.0) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "Speed must be between 0.1 and 10.0"))
                .build()
        }

        gameState.baseSpeedMultiplier = request.speed
        log.info("Game speed set to ${request.speed}x via REST API")

        return Response
            .ok(
                mapOf(
                    "speed" to gameState.baseSpeedMultiplier,
                    "message" to "Speed updated successfully",
                ),
            ).build()
    }

    /**
     * DELETE /api/v1/game/lines
     * Clears all drawn lines
     */
    @DELETE
    @Path("/lines")
    fun clearLines(): Response {
        val count = gameState.lines.size
        gameState.clearLines()
        log.info("Cleared $count lines via REST API")

        return Response
            .ok(
                mapOf(
                    "cleared" to count,
                    "message" to "Lines cleared successfully",
                ),
            ).build()
    }

    /**
     * POST /api/v1/game/powerup/spawn
     * Spawns a test power-up (development only)
     */
    @POST
    @Path("/powerup/spawn")
    fun spawnPowerUp(request: PowerUpSpawnRequest): Response {
        try {
            val type = PowerUpType.valueOf(request.type)
            gameEngine.spawnTestPowerUp(type)
            log.info("Spawned test power-up: $type via REST API")

            return Response
                .ok(
                    mapOf(
                        "type" to type.name,
                        "message" to "Power-up spawned successfully",
                    ),
                ).build()
        } catch (e: IllegalArgumentException) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "Invalid power-up type: ${request.type}"))
                .build()
        }
    }

    /**
     * GET /api/v1/game/statistics
     * Returns game statistics
     */
    @GET
    @Path("/statistics")
    fun getStatistics(): Response {
        val stats =
            mapOf(
                "activePowerUps" to gameState.activePowerUpEffects.size,
                "powerUpsOnField" to gameState.powerUps.size,
                "drawnLines" to gameState.lines.size,
                "additionalPucks" to gameState.additionalPucks.size,
                "lifeGridAliveCells" to gameState.lifeGrid.getAliveCells().size,
                "speedMultiplier" to gameState.speedMultiplier,
                "isPaused" to gameState.paused,
                "puckMovingTime" to gameState.puckMovingTime / 1_000_000_000.0, // seconds
            )

        return Response.ok(stats).build()
    }

    /**
     * GET /api/v1/game/powerups/types
     * Returns available power-up types
     */
    @GET
    @Path("/powerups/types")
    fun getPowerUpTypes(): Response {
        val types =
            PowerUpType.entries.map { type ->
                mapOf(
                    "name" to type.name,
                    "description" to type.description,
                    "emoji" to type.emoji,
                    "color" to PowerUpType.getColorCode(type),
                    "duration" to PowerUpType.getDuration(type) / 1_000_000_000.0, // seconds
                )
            }

        return Response.ok(types).build()
    }

    private fun createGameStateResponse(): GameStateResponse =
        GameStateResponse(
            puckX = gameState.puckX,
            puckY = gameState.puckY,
            puckVX = gameState.puckVX,
            puckVY = gameState.puckVY,
            paddle1Y = gameState.paddle1Y,
            paddle2Y = gameState.paddle2Y,
            paddleHeight = gameState.paddleHeight,
            canvasWidth = gameState.canvasWidth,
            canvasHeight = gameState.canvasHeight,
            paused = gameState.paused,
            speedMultiplier = gameState.speedMultiplier,
            linesCount = gameState.lines.size,
            powerUpsCount = gameState.powerUps.size,
            activePowerUpsCount = gameState.activePowerUpEffects.size,
        )
}

// Request/Response DTOs
data class SpeedRequest(
    val speed: Double,
)

data class PowerUpSpawnRequest(
    val type: String,
)

data class GameStateResponse(
    val puckX: Double,
    val puckY: Double,
    val puckVX: Double,
    val puckVY: Double,
    val paddle1Y: Double,
    val paddle2Y: Double,
    val paddleHeight: Double,
    val canvasWidth: Double,
    val canvasHeight: Double,
    val paused: Boolean,
    val speedMultiplier: Double,
    val linesCount: Int,
    val powerUpsCount: Int,
    val activePowerUpsCount: Int,
)
