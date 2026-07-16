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
import java.util.UUID
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
        val error = awaitMessageType(client, "ERROR")
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

    @Test
    fun `cursor move is broadcast to other room clients without local echo`() {
        // g
        val roomId = "cursor-${UUID.randomUUID()}"
        val sender = GameTestClient(wsUri(roomId))
        val receiver = GameTestClient(wsUri(roomId))

        // w
        repeat(5) {
            sender.send("""{"type":"CURSOR_MOVE","data":{"x":123.0,"y":234.0}}""")
            val cursor = receiver.pollTyped("CURSOR_MOVE", 1L)
            if (cursor != null) {
                assertEquals(123.0, cursor["x"] as Double, 0.001)
                assertEquals(234.0, cursor["y"] as Double, 0.001)
                assertEquals(true, (cursor["playerId"] as String).isNotBlank())
                assertNoMessageType(sender, "CURSOR_MOVE")
                sender.close()
                receiver.close()
                return
            }
        }

        sender.close()
        receiver.close()
        fail("Expected receiver to get a CURSOR_MOVE frame")
    }

    private fun wsUri(roomId: String = "test-${UUID.randomUUID()}"): URI =
        URI("ws", null, gameUri.host, gameUri.port, gameUri.path, "roomId=$roomId", null)

    private fun parse(json: String): Map<String, Any?> = mapper.readValue(json)

    private fun awaitMessageType(
        client: GameTestClient,
        type: String,
    ): Map<String, Any?> =
        client.pollTyped(type, 5L)
            ?: fail("Expected a $type frame from the game socket")

    private fun assertNoMessageType(
        client: GameTestClient,
        type: String,
    ) {
        val deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(350L)
        while (System.nanoTime() < deadlineNs) {
            val message = client.poll(1L) ?: return
            if (parse(message)["type"] == type) fail("Unexpected $type frame: $message")
        }
    }

    private fun GameTestClient.pollTyped(
        type: String,
        timeoutSeconds: Long,
    ): Map<String, Any?>? {
        val deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (System.nanoTime() < deadlineNs) {
            val message = poll(1L) ?: continue
            val parsed = parse(message)
            if (parsed["type"] == type) return parsed
        }
        return null
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
