package ru.rkhamatyarov.api.v1

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import jakarta.inject.Inject
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import ru.rkhamatyarov.service.GameEngine
import ru.rkhamatyarov.service.StateHistory

@QuarkusTest
class GameResourceTest {
    @Inject
    lateinit var engine: GameEngine

    @Inject
    lateinit var history: StateHistory

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
        engine.paused = true
        history.clear()
        engine.puck.x = 111.0
        engine.puck.y = 222.0
        engine.score.playerA = 3
        engine.score.playerB = 4
        history.add(engine.toGameStateDelta().toByteArray())

        engine.puck.x = 700.0
        engine.puck.y = 500.0
        engine.score.playerA = 99

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

        kotlin.test.assertEquals(111.0, engine.puck.x, 0.0001)
        kotlin.test.assertEquals(222.0, engine.puck.y, 0.0001)
        kotlin.test.assertEquals(3, engine.score.playerA)
        kotlin.test.assertEquals(0, history.size())
    }
}
