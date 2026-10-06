package ru.rkhamatyarov.websocket

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import ru.rkhamatyarov.proto.GameStateDelta
import ru.rkhamatyarov.service.RoomRegistry
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.PaddleSide
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

    @Inject
    lateinit var registry: RoomRegistry

    @Test
    fun `combination is owned by sender recorded and cleared for both clients`() =
        runBlocking {
            val roomId = "combination-${UUID.randomUUID()}"
            val a = GameTestClient(wsUri(roomId, "A"))
            val b = GameTestClient(wsUri(roomId, "B"))
            try {
                a.send(combinationCommand())
                awaitMessageType(a, "COMBINATION_ACCEPTED")
                val room = registry.get(roomId)
                val applied = withTimeout(5000) { room.reliableState.first { it.lines.size == 1 } }
                assertEquals(true, applied.paused)
                assertEquals(PaddleSide.A, applied.lines.single().ownerSide)
                assertEquals("triangle", applied.lines.single().combinationId)
                assertEquals(160.0, applied.lines.single().points.first().x)
                assertEquals(1, room.getReplayLog().count { it.action is GameAction.ApplyCombination })
                b.awaitSnapshot { it.linesCount == 1 }

                a.send("""{"type":"APPLY_COMBINATION","data":{"lines":[]}}""")
                awaitMessageType(a, "COMBINATION_ACCEPTED")
                withTimeout(5000) { room.reliableState.first { it.lines.isEmpty() } }
                b.awaitSnapshot { it.fullState && it.linesCount == 0 }
            } finally {
                a.close()
                b.close()
            }
        }

    @Test
    fun `invalid combination never enters the replay log`() {
        val roomId = "invalid-combination-${UUID.randomUUID()}"
        val client = GameTestClient(wsUri(roomId))
        try {
            client.send(combinationCommand().replace("0.2", "-0.2"))
            awaitMessageType(client, "ERROR")
            assertEquals(0, registry.get(roomId).getReplayLog().count { it.action is GameAction.ApplyCombination })
        } finally {
            client.close()
        }
    }

    private fun combinationCommand(): String =
        """{"type":"APPLY_COMBINATION","data":{"id":"triangle","lines":[""" +
            """{"points":[{"x":0.2,"y":0.3},{"x":0.3,"y":0.4}],"width":5}]}}"""

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

    private fun wsUri(
        roomId: String = "test-${UUID.randomUUID()}",
        side: String = "B",
    ): URI = URI("ws", null, gameUri.host, gameUri.port, gameUri.path, "roomId=$roomId&side=$side", null)

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
    private val snapshots: BlockingQueue<GameStateDelta> = LinkedBlockingQueue()
    private val binary = java.io.ByteArrayOutputStream()
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
                        val bytes = ByteArray(data.remaining())
                        data.get(bytes)
                        binary.write(bytes)
                        if (last) {
                            snapshots.add(GameStateDelta.parseFrom(binary.toByteArray()))
                            binary.reset()
                        }
                        webSocket.request(1)
                        return null
                    }
                },
            ).get(5, TimeUnit.SECONDS)

    fun send(text: String) {
        socket.sendText(text, true).get(5, TimeUnit.SECONDS)
    }

    fun poll(timeoutSeconds: Long): String? = inbox.poll(timeoutSeconds, TimeUnit.SECONDS)

    fun awaitSnapshot(predicate: (GameStateDelta) -> Boolean): GameStateDelta {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            val snapshot = snapshots.poll(100, TimeUnit.MILLISECONDS) ?: continue
            if (predicate(snapshot)) return snapshot
        }
        fail("Expected matching game snapshot")
    }

    fun close() {
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "test-complete").get(5, TimeUnit.SECONDS)
    }
}
