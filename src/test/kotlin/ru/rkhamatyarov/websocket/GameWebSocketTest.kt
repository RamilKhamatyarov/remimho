package ru.rkhamatyarov.websocket

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.fail

@QuarkusTest
class GameWebSocketTest {
    @TestHTTPResource("/game")
    lateinit var gameUri: URI

    private val mapper = jacksonObjectMapper()

    @Test
    fun `P2P telemetry with invalid status returns error`() {
        // g
        val client = GameTestClient(wsUri())

        // w
        client.send("""{"type":"P2P_TELEMETRY","data":{"status":"bogus","peerId":"peer-1"}}""")

        // t
        val error = awaitErrorText(client)
        assertEquals("ERROR", error["type"])
        assertEquals(true, (error["message"] as String).contains("status"))

        client.close()
    }

    @Test
    fun `P2P telemetry with valid status is not rejected`() {
        // g
        val client = GameTestClient(wsUri())

        // w
        client.send("""{"type":"P2P_TELEMETRY","data":{"status":"success","peerId":"peer-1"}}""")

        // t
        val deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(1L)
        while (System.nanoTime() < deadlineNs) {
            val message = client.poll(1L) ?: break
            if (parse(message)["type"] == "ERROR") fail("Valid P2P telemetry was rejected: $message")
        }

        client.close()
    }

    private fun wsUri(): URI = URI("ws", null, gameUri.host, gameUri.port, gameUri.path, null, null)

    private fun parse(json: String): Map<String, Any?> = mapper.readValue(json)

    private fun awaitErrorText(client: GameTestClient): Map<String, Any?> {
        val deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L)
        while (System.nanoTime() < deadlineNs) {
            val message = client.poll(5L) ?: break
            val parsed = parse(message)
            if (parsed["type"] == "ERROR") return parsed
        }
        fail("Expected an ERROR frame from the game socket")
    }
}

private class GameTestClient(
    uri: URI,
) {
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
                        data: ByteBuffer,
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
