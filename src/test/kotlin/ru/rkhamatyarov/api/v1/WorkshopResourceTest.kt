package ru.rkhamatyarov.api.v1

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

@QuarkusTest
class WorkshopResourceTest {
    @Test
    fun `POST content with LEVEL type returns 201 and type echo`() {
        given()
            .contentType(ContentType.JSON)
            .body(
                """
                {
                  "type": "LEVEL",
                  "data": { "tiles": [], "spawnX": 400, "spawnY": 300 },
                  "metadata": { "name": "My First Level", "author": "alice" }
                }
                """.trimIndent(),
            ).`when`()
            .post("/api/v1/workshop/content")
            .then()
            .statusCode(201)
            .body("type", equalTo("LEVEL"))
            .body("accepted", equalTo(true))
    }

    @Test
    fun `POST content with POWERUP_SET type returns 201`() {
        given()
            .contentType(ContentType.JSON)
            .body(
                """
                {
                  "type": "POWERUP_SET",
                  "data": { "powerUps": ["SPEED_BOOST", "MAGNET_BALL"] },
                  "metadata": { "name": "Speed Pack" }
                }
                """.trimIndent(),
            ).`when`()
            .post("/api/v1/workshop/content")
            .then()
            .statusCode(201)
            .body("type", equalTo("POWERUP_SET"))
    }

    @Test
    fun `POST content without metadata name returns 400`() {
        given()
            .contentType(ContentType.JSON)
            .body(
                """
                {
                  "type": "SKIN",
                  "data": {},
                  "metadata": {}
                }
                """.trimIndent(),
            ).`when`()
            .post("/api/v1/workshop/content")
            .then()
            .statusCode(400)
            .body("error", equalTo("metadata.name is required"))
    }

    @Test
    fun `POST content with unknown type returns 400`() {
        given()
            .contentType(ContentType.JSON)
            .body(
                """
                {
                  "type": "DOES_NOT_EXIST",
                  "data": {},
                  "metadata": { "name": "bad" }
                }
                """.trimIndent(),
            ).`when`()
            .post("/api/v1/workshop/content")
            .then()
            .statusCode(400)
    }

    @Test
    fun `POST content with empty body returns 400`() {
        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .`when`()
            .post("/api/v1/workshop/content")
            .then()
            .statusCode(400)
    }
}
