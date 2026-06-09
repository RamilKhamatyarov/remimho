package ru.rkhamatyarov.websocket

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.fail

@QuarkusTest
class SignalingWebSocketTest {
    @TestHTTPResource("/signaling")
    lateinit var signalingUri: URI

    private val mapper = jacksonObjectMapper()

    @Test
    fun `relay offer delivers to targeted peer in same room`() {
        // g
        val alice = connect("alpha")
        val bob = connect("alpha")

        // w
        bob.send(
            """{"type":"WEBRTC_OFFER","to":"${alice.peerId}","data":{"sdp":"offer-sdp"}}""",
        )

        // t
        val received = awaitType(alice, "WEBRTC_OFFER")
        assertEquals(bob.peerId, received["from"])
        @Suppress("UNCHECKED_CAST")
        assertEquals("offer-sdp", (received["data"] as Map<String, Any?>)["sdp"])

        alice.close()
        bob.close()
    }

    @Test
    fun `relay broadcast is isolated to room`() {
        // g
        val alice = connect("alpha")
        val bob = connect("alpha")
        val carol = connect("beta")

        // w
        alice.send("""{"type":"WEBRTC_ANSWER","data":{"sdp":"answer-sdp"}}""")

        // t
        val received = awaitType(bob, "WEBRTC_ANSWER")
        assertEquals(alice.peerId, received["from"])
        assertNoWebRtcAnswer(carol)

        alice.close()
        bob.close()
        carol.close()
    }

    @Test
    fun `relay to non-existent peer returns error`() {
        // g
        val alice = connect("alpha")

        // w
        alice.send("""{"type":"ICE_CANDIDATE","to":"ghost-peer","data":{"candidate":"c"}}""")

        // t
        val error = awaitType(alice, "ERROR")
        assertEquals(true, (error["message"] as String).contains("ghost-peer"))

        alice.close()
    }

    @Test
    fun `malformed JSON returns error`() {
        // g
        val alice = connect("alpha")

        // w
        alice.send("this-is-not-json")

        // t
        val error = awaitType(alice, "ERROR")
        assertEquals("Invalid signaling message format", error["message"])

        alice.close()
    }

    @Test
    fun `unknown signal type returns error`() {
        // g
        val alice = connect("alpha")

        // w
        alice.send("""{"type":"NOT_A_SIGNAL"}""")

        // t
        val error = awaitType(alice, "ERROR")
        assertEquals(true, (error["message"] as String).contains("NOT_A_SIGNAL"))

        alice.close()
    }

    private fun connect(roomId: String): TestClient {
        val client = TestClient(wsUri(roomId))
        val welcome = client.poll(AWAIT_SECONDS) ?: fail("No WELCOME received for room '$roomId'")
        val parsed = parse(welcome)
        assertEquals("WELCOME", parsed["type"])
        client.peerId = parsed["peerId"] as String
        return client
    }

    private fun wsUri(roomId: String): URI =
        URI("ws", null, signalingUri.host, signalingUri.port, signalingUri.path, "roomId=$roomId", null)

    private fun parse(json: String): Map<String, Any?> = mapper.readValue(json)

    private fun awaitType(
        client: TestClient,
        type: String,
    ): Map<String, Any?> {
        val deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS)
        while (System.nanoTime() < deadlineNs) {
            val message = client.poll(AWAIT_SECONDS) ?: break
            val parsed = parse(message)
            if (parsed["type"] == type) return parsed
        }
        fail("Expected signaling message of type '$type' was not received")
    }

    private fun assertNoWebRtcAnswer(client: TestClient) {
        val deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(QUIET_WINDOW_SECONDS)
        while (System.nanoTime() < deadlineNs) {
            val message = client.poll(QUIET_WINDOW_SECONDS) ?: return
            if (parse(message)["type"] == WEBRTC_ANSWER) fail("Unexpected message of type '$WEBRTC_ANSWER' leaked across rooms")
        }
    }

    private companion object {
        const val AWAIT_SECONDS = 5L
        const val QUIET_WINDOW_SECONDS = 1L
        const val WEBRTC_ANSWER = "WEBRTC_ANSWER"
    }
}

private class TestClient(
    uri: URI,
) {
    var peerId: String = ""
    private val inbox: BlockingQueue<String> = LinkedBlockingQueue()
    private val buffer = StringBuilder()

    private val socket: WebSocket =
        HttpClient
            .newHttpClient()
            .newWebSocketBuilder()
            .buildAsync(
                uri,
                object : WebSocket.Listener {
                    override fun onOpen(webSocket: WebSocket) {
                        webSocket.request(1)
                    }

                    override fun onText(
                        webSocket: WebSocket,
                        data: CharSequence,
                        last: Boolean,
                    ): CompletionStage<*>? {
                        buffer.append(data)
                        if (last) {
                            inbox.add(buffer.toString())
                            buffer.setLength(0)
                        }
                        webSocket.request(1)
                        return null
                    }

                    override fun onBinary(
                        webSocket: WebSocket,
                        data: java.nio.ByteBuffer,
                        last: Boolean,
                    ): CompletionStage<*>? {
                        webSocket.request(1)
                        return null
                    }
                },
            ).get(5, TimeUnit.SECONDS)

    fun send(text: String) {
        socket.sendText(text, true).get(5, TimeUnit.SECONDS)
    }

    fun poll(timeoutSeconds: Long): String? = inbox.poll(timeoutSeconds, TimeUnit.SECONDS)

    fun close() {
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "test-complete").get(5, TimeUnit.SECONDS)
    }
}
