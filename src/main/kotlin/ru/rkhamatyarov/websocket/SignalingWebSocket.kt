package ru.rkhamatyarov.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.quarkus.websockets.next.OnClose
import io.quarkus.websockets.next.OnOpen
import io.quarkus.websockets.next.OnTextMessage
import io.quarkus.websockets.next.WebSocket
import io.quarkus.websockets.next.WebSocketConnection
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import ru.rkhamatyarov.service.RoomRegistry
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * WebRTC signaling relay. Peers connect with the same `?roomId=` convention used by
 * [GameWebSocket] and exchange SDP offers/answers and ICE candidates directly, so that
 * P2P-capable clients can bypass the central state server. The endpoint keeps no game
 * state — it only routes signaling envelopes between peers sharing a room.
 */
@WebSocket(path = "/signaling")
@ApplicationScoped
class SignalingWebSocket {
    private val log = Logger.getLogger(javaClass)

    @Inject
    lateinit var mapper: ObjectMapper

    @Inject
    lateinit var roomRegistry: RoomRegistry

    @ConfigProperty(name = "remimho.rooms.enabled", defaultValue = "false")
    var roomsEnabled: Boolean = false

    private val sessions = ConcurrentHashMap<String, WebSocketConnection>()
    private val connectionRooms = ConcurrentHashMap<String, String>()

    @OnOpen
    fun onOpen(connection: WebSocketConnection) {
        val roomId = roomId(connection)
        if (roomsEnabled) roomRegistry.get(roomId)
        connectionRooms[connection.id()] = roomId
        sessions[connection.id()] = connection

        val peers = peersInRoom(roomId, except = connection.id())
        log.info("Signaling peer connected: ${connection.id()} room=$roomId (peers: ${peers.size})")

        connection.sendTextAndAwait(
            mapper.writeValueAsString(
                mapOf(
                    "type" to "WELCOME",
                    "peerId" to connection.id(),
                    "roomId" to roomId,
                    "peers" to peers,
                ),
            ),
        )
        broadcastToRoom(
            roomId,
            mapper.writeValueAsString(mapOf("type" to "PEER_JOINED", "peerId" to connection.id())),
            except = connection.id(),
        )
    }

    @OnClose
    fun onClose(connection: WebSocketConnection) {
        sessions.remove(connection.id())
        val roomId = connectionRooms.remove(connection.id())
        log.info("Signaling peer disconnected: ${connection.id()} room=$roomId")
        if (roomId != null) {
            broadcastToRoom(
                roomId,
                mapper.writeValueAsString(mapOf("type" to "PEER_LEFT", "peerId" to connection.id())),
                except = connection.id(),
            )
        }
    }

    @OnTextMessage
    fun onMessage(
        message: String,
        connection: WebSocketConnection,
    ) {
        try {
            if (message.isBlank()) {
                sendError(connection, "Empty message")
                return
            }
            handleSignal(mapper.readValue<Map<String, Any>>(message), connection)
        } catch (e: Exception) {
            log.error("Failed to handle signaling message: $message", e)
            sendError(connection, "Invalid signaling message format")
        }
    }

    private fun handleSignal(
        cmd: Map<String, Any>,
        connection: WebSocketConnection,
    ) {
        val type =
            cmd["type"]?.toString()?.uppercase()
                ?: return sendError(connection, "Missing 'type'")
        when (type) {
            "WEBRTC_OFFER", "WEBRTC_ANSWER", "ICE_CANDIDATE" -> {
                relay(type, cmd, connection)
            }

            else -> {
                log.warn("Unknown signaling type: $type")
                sendError(connection, "Unknown signaling type: $type")
            }
        }
    }

    private fun relay(
        type: String,
        cmd: Map<String, Any>,
        connection: WebSocketConnection,
    ) {
        val roomId = connectionRooms[connection.id()] ?: roomId(connection)
        val envelope =
            mapper.writeValueAsString(
                mapOf(
                    "type" to type,
                    "from" to connection.id(),
                    "data" to (cmd["data"] ?: emptyMap<String, Any>()),
                ),
            )
        val target = cmd["to"]?.toString()?.takeIf { it.isNotBlank() }
        if (target != null) {
            relayToPeer(target, roomId, envelope, connection)
        } else {
            relayToRoom(roomId, envelope, connection)
        }
    }

    private fun relayToPeer(
        target: String,
        roomId: String,
        envelope: String,
        sender: WebSocketConnection,
    ) {
        val targetConnection = sessions[target]
        if (targetConnection == null || connectionRooms[target] != roomId) {
            sendError(sender, "Peer '$target' is not connected in room '$roomId'")
            return
        }
        sendTo(targetConnection, envelope)
    }

    private fun relayToRoom(
        roomId: String,
        envelope: String,
        sender: WebSocketConnection,
    ) {
        if (peersInRoom(roomId, except = sender.id()).isEmpty()) {
            sendError(sender, "No peers available in room '$roomId'")
            return
        }
        broadcastToRoom(roomId, envelope, except = sender.id())
    }

    private fun broadcastToRoom(
        roomId: String,
        message: String,
        except: String,
    ) {
        sessions.forEach { (id, conn) ->
            if (id == except || connectionRooms[id] != roomId) return@forEach
            sendTo(conn, message)
        }
    }

    private fun sendTo(
        connection: WebSocketConnection,
        message: String,
    ) {
        connection.sendText(message).subscribe().with(
            {},
            { t -> log.warn("Failed to relay signaling message to ${connection.id()}: ${t.message}") },
        )
    }

    private fun peersInRoom(
        roomId: String,
        except: String,
    ): List<String> = sessions.keys.filter { it != except && connectionRooms[it] == roomId }

    private fun roomId(connection: WebSocketConnection): String {
        val query = connection.handshakeRequest().query() ?: return RoomRegistry.DEFAULT_ROOM_ID
        return query
            .split("&")
            .firstOrNull { it.substringBefore("=") == "roomId" }
            ?.substringAfter("=", "")
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
            ?.takeIf { it.isNotBlank() }
            ?: RoomRegistry.DEFAULT_ROOM_ID
    }

    private fun sendError(
        connection: WebSocketConnection,
        message: String,
    ) {
        try {
            connection.sendTextAndAwait(
                mapper.writeValueAsString(mapOf("type" to "ERROR", "message" to message)),
            )
        } catch (e: Exception) {
            log.error("Failed to send error to ${connection.id()}", e)
        }
    }
}
