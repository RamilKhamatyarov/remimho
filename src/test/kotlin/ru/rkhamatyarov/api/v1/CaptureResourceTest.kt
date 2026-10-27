package ru.rkhamatyarov.api.v1

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import ru.rkhamatyarov.service.RoomRegistry
import ru.rkhamatyarov.telemetry.CaptureAccess
import ru.rkhamatyarov.telemetry.CapturePermission
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@QuarkusTest
class CaptureResourceTest {
    @Inject
    lateinit var registry: RoomRegistry

    @Inject
    lateinit var access: CaptureAccess

    @TestHTTPResource("/game")
    lateinit var gameUri: URI

    @Test
    fun `unknown room is not created by unauthorized diagnostics`() {
        val id = UUID.randomUUID().toString()
        given().get("/api/v1/capture/$id").then().statusCode(403)
        assertNull(registry.find(id))
    }

    @Test
    fun `websocket participant token grants only its room and downloads are throttled`() {
        val roomId = UUID.randomUUID().toString()
        val messages = LinkedBlockingQueue<String>()
        val listener =
            object : WebSocket.Listener {
                private val text = StringBuilder()

                override fun onOpen(webSocket: WebSocket) {
                    webSocket.request(1)
                }

                override fun onText(
                    webSocket: WebSocket,
                    data: CharSequence,
                    last: Boolean,
                ): java.util.concurrent.CompletionStage<*>? {
                    text.append(data)
                    if (last) {
                        messages.add(text.toString())
                        text.setLength(0)
                    }
                    webSocket.request(1)
                    return null
                }

                override fun onBinary(
                    webSocket: WebSocket,
                    data: java.nio.ByteBuffer,
                    last: Boolean,
                ): java.util.concurrent.CompletionStage<*>? {
                    webSocket.request(1)
                    return null
                }
            }
        val uri = URI(gameUri.toString().replaceFirst("http", "ws") + "?roomId=$roomId&side=B")
        val socket = HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(uri, listener).join()
        try {
            val mapper = jacksonObjectMapper()
            var token: String? = null
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (token == null && System.nanoTime() < deadline) {
                val message = messages.poll(100, TimeUnit.MILLISECONDS) ?: continue
                val json = mapper.readTree(message)
                if (json["type"]?.asText() == "CAPTURE_ACCESS") token = json["token"].asText()
            }
            assertNotNull(token)
            given().header("X-Capture-Token", token).get("/api/v1/capture/other-room").then().statusCode(403)
            given().header("X-Capture-Token", "wrong").get("/api/v1/capture/$roomId").then().statusCode(403)
            given().header("X-Capture-Token", token).get("/api/v1/capture/$roomId").then()
                .statusCode(200)
                .header("Cache-Control", "no-store")
                .body("schemaVersion", equalTo(2))
                .body("roomId", equalTo(roomId))
            given().header("X-Capture-Token", token).get("/api/v1/capture/$roomId").then()
                .statusCode(429).header("Retry-After", "10")
            given().get("/api/v1/game/client-config").then().body("captureEnabled", equalTo(true))
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join()
            val closeDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (access.acquire(roomId, token) != CapturePermission.FORBIDDEN && System.nanoTime() < closeDeadline) {
                Thread.sleep(10)
            }
            assertEquals(CapturePermission.FORBIDDEN, access.acquire(roomId, token))
        } finally {
            socket.abort()
        }
    }
}
