package ru.rkhamatyarov.api.v1

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import jakarta.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import ru.rkhamatyarov.model.AiOpponentConfig
import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.service.RoomRegistry
import ru.rkhamatyarov.service.StateHistory
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.GameIntent
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviPuck
import ru.rkhamatyarov.service.mvi.MviScore
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@QuarkusTest
class GameResourceTest {
    @Inject
    lateinit var history: StateHistory

    @Inject
    lateinit var roomRegistry: RoomRegistry

    @Test
    fun `test invalid endpoint returns 404`() {
        RestAssured
            .given()
            .`when`()
            .get("/api/v1/invalid/endpoint")
            .then()
            .statusCode(404)
    }

    @Test
    fun `test invalid websocket path returns error`() {
        RestAssured
            .given()
            .`when`()
            .get("/api/v1/invalid/ws")
            .then()
            .statusCode(404)
    }

    @Test
    fun `test multiple concurrent stats requests`() {
        val requests =
            (1..10).map {
                Thread {
                    RestAssured
                        .given()
                        .`when`()
                        .get("/api/v1/game/statistics")
                        .then()
                        .statusCode(200)
                }
            }

        requests.forEach { it.start() }
        requests.forEach { it.join() }
    }

    @Test
    fun `test time travel endpoint restores saved snapshot and clears future`() {
        val restored =
            MviGameState(
                puck = MviPuck(x = 111.0, y = 222.0),
                score = MviScore(playerA = 3, playerB = 4),
                paused = true,
            )
        history.clear()
        history.add(restored.toDelta().toByteArray())
        defaultRoom().dispatch(
            GameIntent.Reliable(
                GameAction.RestoreSnapshot(
                    MviGameState(
                        puck = MviPuck(x = 700.0, y = 500.0),
                        score = MviScore(playerA = 99),
                    ),
                ),
            ),
        )

        RestAssured
            .given()
            .contentType("application/json")
            .body("""{"offset":0.0}""")
            .`when`()
            .post("/api/v1/game/time-travel")
            .then()
            .statusCode(200)
            .body("message", equalTo("Timeline restored"))
            .body("puckX", equalTo(111.0f))
            .body("puckY", equalTo(222.0f))

        val state = awaitDefaultState { it.puck.x == 111.0 && it.puck.y == 222.0 }
        assertEquals(111.0, state.puck.x, 0.0001)
        assertEquals(222.0, state.puck.y, 0.0001)
        assertEquals(3, state.score.playerA)
        assertEquals(0, history.size())
    }

    @Test
    fun `test game ai-opponent endpoint updates bot config`() {
        defaultRoom().dispatch(GameIntent.Reliable(GameAction.ApplyAiConfig(AiOpponentConfig())))

        RestAssured
            .given()
            .contentType("application/json")
            .body("""{"enabled":true,"reactionDelayMs":120,"maxSpeed":240.0,"trackingError":4.0,"reactZoneRatio":0.8}""")
            .`when`()
            .post("/api/v1/game/ai-opponent")
            .then()
            .statusCode(200)
            .body("applied", equalTo(true))
            .body("reactionDelayMs", equalTo(120))
            .body("maxSpeed", equalTo(240.0f))

        val state = awaitDefaultState { it.aiConfig.reactionDelayMs == 120L }
        assertEquals(120L, state.aiConfig.reactionDelayMs)
        assertEquals(240.0, state.aiConfig.maxSpeed, 0.0001)
    }

    @Test
    fun `test powerup spawn endpoint dispatches reliable room intent`() {
        val roomId = "rest-powerup-${System.nanoTime()}"

        RestAssured
            .given()
            .contentType("application/json")
            .body("""{"type":"MAGNET_BALL"}""")
            .`when`()
            .post("/api/v1/game/powerup/spawn?roomId=$roomId")
            .then()
            .statusCode(200)
            .body("type", equalTo("MAGNET_BALL"))
            .body("roomId", equalTo(roomId))

        runBlocking {
            withTimeout(1.seconds) {
                roomRegistry.get(roomId).reliableState.first { state ->
                    state.powerUps.any { it.type == PowerUpType.MAGNET_BALL }
                }
            }
        }
    }

    @Test
    fun `test invalid powerup spawn type returns bad request`() {
        RestAssured
            .given()
            .contentType("application/json")
            .body("""{"type":"NOT_A_POWERUP"}""")
            .`when`()
            .post("/api/v1/game/powerup/spawn")
            .then()
            .statusCode(400)
            .body("error", equalTo("Invalid power-up type: NOT_A_POWERUP"))
    }

    private fun defaultRoom() = roomRegistry.get(RoomRegistry.DEFAULT_ROOM_ID)

    private fun awaitDefaultState(predicate: (MviGameState) -> Boolean): MviGameState =
        runBlocking {
            withTimeout(1.seconds) {
                defaultRoom().reliableState.first(predicate)
            }
        }
}
