package ru.rkhamatyarov.api.v1

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import org.junit.jupiter.api.Test

@QuarkusTest
class GameResourceTest {
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
                        .get("/api/v1/game/stats")
                        .then()
                        .statusCode(200)
                }
            }

        requests.forEach { it.start() }
        requests.forEach { it.join() }
    }
}
