package ru.rkhamatyarov.api.v1

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.rkhamatyarov.model.AiOpponentConfig
import ru.rkhamatyarov.model.SpeedConfig
import ru.rkhamatyarov.service.GameEngine
import kotlin.test.assertEquals

@QuarkusTest
class WorkshopResourceTest {
    @Inject
    lateinit var engine: GameEngine

    @BeforeEach
    fun resetEngine() {
        engine.speedConfig = SpeedConfig()
        engine.aiOpponentConfig = AiOpponentConfig()
    }

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

    @Test
    fun `POST speed-config with valid config returns 200 and applied true`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"baseMultiplier":1.5,"timeAccelerationRate":0.1,"levelAccelerationPerLine":0.03,"maxMultiplier":4.0}""")
            .`when`()
            .post("/api/v1/workshop/speed-config")
            .then()
            .statusCode(200)
            .body("applied", equalTo(true))
            .body("baseMultiplier", equalTo(1.5f))
            .body("timeAccelerationRate", equalTo(0.1f))
            .body("levelAccelerationPerLine", equalTo(0.03f))
            .body("maxMultiplier", equalTo(4.0f))
    }

    @Test
    fun `POST speed-config applies config to engine`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"baseMultiplier":2.0,"timeAccelerationRate":0.08,"levelAccelerationPerLine":0.01,"maxMultiplier":5.0}""")
            .`when`()
            .post("/api/v1/workshop/speed-config")
            .then()
            .statusCode(200)

        assertEquals(2.0, engine.speedConfig.baseMultiplier, 0.001)
        assertEquals(0.08, engine.speedConfig.timeAccelerationRate, 0.001)
        assertEquals(0.01, engine.speedConfig.levelAccelerationPerLine, 0.001)
        assertEquals(5.0, engine.speedConfig.maxMultiplier, 0.001)
    }

    @Test
    fun `POST speed-config with out-of-range baseMultiplier returns 400`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"baseMultiplier":0.0,"timeAccelerationRate":0.05,"levelAccelerationPerLine":0.02,"maxMultiplier":3.0}""")
            .`when`()
            .post("/api/v1/workshop/speed-config")
            .then()
            .statusCode(400)
            .body("error", equalTo("baseMultiplier must be between 0.1 and 5.0"))
    }

    @Test
    fun `POST speed-config with out-of-range timeAccelerationRate returns 400`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"baseMultiplier":1.0,"timeAccelerationRate":2.0,"levelAccelerationPerLine":0.02,"maxMultiplier":3.0}""")
            .`when`()
            .post("/api/v1/workshop/speed-config")
            .then()
            .statusCode(400)
            .body("error", equalTo("timeAccelerationRate must be between 0.0 and 1.0"))
    }

    @Test
    fun `POST speed-config with out-of-range maxMultiplier returns 400`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"baseMultiplier":1.0,"timeAccelerationRate":0.05,"levelAccelerationPerLine":0.02,"maxMultiplier":0.5}""")
            .`when`()
            .post("/api/v1/workshop/speed-config")
            .then()
            .statusCode(400)
            .body("error", equalTo("maxMultiplier must be between 1.0 and 10.0"))
    }

    @Test
    fun `POST content with SPEED_CONFIG type returns 201`() {
        given()
            .contentType(ContentType.JSON)
            .body(
                """
                {
                  "type": "SPEED_CONFIG",
                  "data": {"baseMultiplier": 1.5},
                  "metadata": { "name": "Fast Mode" }
                }
                """.trimIndent(),
            ).`when`()
            .post("/api/v1/workshop/content")
            .then()
            .statusCode(201)
            .body("type", equalTo("SPEED_CONFIG"))
    }

    @Test
    fun `POST ai-opponent-config with valid config applies to engine`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"enabled":true,"reactionDelayMs":240,"maxSpeed":210.0,"trackingError":8.0,"reactZoneRatio":0.75}""")
            .`when`()
            .post("/api/v1/workshop/ai-opponent-config")
            .then()
            .statusCode(200)
            .body("applied", equalTo(true))
            .body("enabled", equalTo(true))
            .body("reactionDelayMs", equalTo(240))
            .body("maxSpeed", equalTo(210.0f))
            .body("trackingError", equalTo(8.0f))
            .body("reactZoneRatio", equalTo(0.75f))

        assertEquals(240, engine.aiOpponentConfig.reactionDelayMs)
        assertEquals(210.0, engine.aiOpponentConfig.maxSpeed, 0.001)
        assertEquals(8.0, engine.aiOpponentConfig.trackingError, 0.001)
        assertEquals(0.75, engine.aiOpponentConfig.reactZoneRatio, 0.001)
    }

    @Test
    fun `POST ai-opponent-config with out-of-range delay returns 400`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"enabled":true,"reactionDelayMs":2000,"maxSpeed":210.0,"trackingError":8.0,"reactZoneRatio":0.75}""")
            .`when`()
            .post("/api/v1/workshop/ai-opponent-config")
            .then()
            .statusCode(400)
            .body("error", equalTo("reactionDelayMs must be between 0 and 1500"))
    }

    @Test
    fun `POST content with AI_OPPONENT_CONFIG type returns 201`() {
        given()
            .contentType(ContentType.JSON)
            .body(
                """
                {
                  "type": "AI_OPPONENT_CONFIG",
                  "data": {"reactionDelayMs": 180},
                  "metadata": { "name": "Solo Bot" }
                }
                """.trimIndent(),
            ).`when`()
            .post("/api/v1/workshop/content")
            .then()
            .statusCode(201)
            .body("type", equalTo("AI_OPPONENT_CONFIG"))
    }

    @Test
    fun `POST compile with YAML returns checksum`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"format":"yaml","source":"name: Solo Rules\nspeed:\n  baseMultiplier: 1.2"}""")
            .`when`()
            .post("/api/v1/workshop/compile")
            .then()
            .statusCode(200)
            .body("ok", equalTo(true))
            .body("config.name", equalTo("Solo Rules"))
    }

    @Test
    fun `POST preview dry run does not mutate live engine`() {
        val beforeX = engine.puck.x

        given()
            .contentType(ContentType.JSON)
            .body("""{"name":"Preview","version":1,"speed":{"baseMultiplier":1.0},"ai":{"enabled":true}}""")
            .`when`()
            .post("/api/v1/workshop/preview")
            .then()
            .statusCode(200)
            .body("ok", equalTo(true))

        assertEquals(beforeX, engine.puck.x, 0.001)
    }
}
