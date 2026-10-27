package ru.rkhamatyarov.api.v1

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

class CaptureDisabledProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> = mapOf("remimho.capture.enabled" to "false")
}

@QuarkusTest
@TestProfile(CaptureDisabledProfile::class)
class CaptureDisabledResourceTest {
    @Test
    fun `explicit opt out disables capture on both client and server`() {
        given().get("/api/v1/game/client-config").then().body("captureEnabled", equalTo(false))
        given().get("/api/v1/capture/default").then().statusCode(404)
    }
}
