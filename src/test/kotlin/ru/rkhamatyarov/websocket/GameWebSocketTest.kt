package ru.example.game.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.mockito.InjectSpy
import io.quarkus.websockets.next.WebSocketConnection
import io.smallrye.mutiny.Uni
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import ru.example.game.service.GameEngine
import ru.rkhamatyarov.model.GameInnerState
import ru.rkhamatyarov.model.Puck
import ru.rkhamatyarov.model.Score

@QuarkusTest
class GameWebSocketTest {
    @Inject
    lateinit var gameWebSocket: GameWebSocket

    @InjectMock
    lateinit var gameEngine: GameEngine

    @InjectSpy
    lateinit var gameState: GameInnerState

    @Inject
    lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        gameState.reset()

        val puck = Puck(x = 400.0, y = 300.0, vx = 0.0, vy = 0.0, radius = 10.0)
        val score = Score(playerA = 0, playerB = 0)

        doReturn(puck).`when`(gameEngine).puck
        doReturn(score).`when`(gameEngine).score
        doReturn(800.0).`when`(gameEngine).canvasWidth
        doReturn(600.0).`when`(gameEngine).canvasHeight
        doReturn(100.0).`when`(gameEngine).paddleHeight
        doReturn(250.0).`when`(gameEngine).paddle1Y
        doReturn(250.0).`when`(gameEngine).paddle2Y
        doReturn(false).`when`(gameEngine).paused
    }

    @Test
    fun `test WebSocket connection and disconnection`() {
        val conn = mockConnection("test-conn-1")
        val before = gameWebSocket.getActiveSessionsCount()

        gameWebSocket.onOpen(conn)
        assertEquals(before + 1, gameWebSocket.getActiveSessionsCount())

        gameWebSocket.onClose(conn)
        assertEquals(before, gameWebSocket.getActiveSessionsCount())
    }

    @Test
    fun `test multiple WebSocket connections`() {
        val c1 = mockConnection("conn-a")
        val c2 = mockConnection("conn-b")
        val c3 = mockConnection("conn-c")
        val before = gameWebSocket.getActiveSessionsCount()

        gameWebSocket.onOpen(c1)
        gameWebSocket.onOpen(c2)
        gameWebSocket.onOpen(c3)
        assertEquals(before + 3, gameWebSocket.getActiveSessionsCount())

        gameWebSocket.onClose(c2)
        assertEquals(before + 2, gameWebSocket.getActiveSessionsCount())

        gameWebSocket.onClose(c1)
        gameWebSocket.onClose(c3)
        assertEquals(before, gameWebSocket.getActiveSessionsCount())
    }

    @Test
    fun `test handle toggle pause command`() {
        val conn = mockConnection("pause-conn")
        assertFalse(gameState.paused, "Should start unpaused")

        gameWebSocket.onMessage(command("TOGGLE_PAUSE"), conn)
        assertTrue(gameState.paused, "Should be paused after first toggle")

        gameWebSocket.onMessage(command("TOGGLE_PAUSE"), conn)
        assertFalse(gameState.paused, "Should be unpaused after second toggle")
    }

    @Test
    fun `test handle reset command resets puck to centre`() {
        val conn = mockConnection("reset-conn")
        gameState.puckX = 100.0
        gameState.puckY = 100.0
        gameState.paused = true

        gameWebSocket.onMessage(command("RESET"), conn)

        assertEquals(gameState.canvasWidth / 2, gameState.puckX, 0.001)
        assertEquals(gameState.canvasHeight / 2, gameState.puckY, 0.001)
    }

    @Test
    fun `test handle clear lines command`() {
        val conn = mockConnection("clear-conn")
        gameState.startNewLine(100.0, 100.0)
        gameState.updateCurrentLine(150.0, 150.0)
        gameState.finishCurrentLine()
        assertTrue(gameState.lines.isNotEmpty(), "Should have a line before clear")

        gameWebSocket.onMessage(command("CLEAR_LINES"), conn)

        assertEquals(0, gameState.lines.size)
        assertFalse(gameState.isDrawing)
    }

    @Test
    fun `test handle start line command`() {
        val conn = mockConnection("start-line-conn")
        gameWebSocket.onMessage(
            command("START_LINE", mapOf("x" to 100.0, "y" to 200.0)),
            conn,
        )

        assertNotNull(gameState.currentLine)
        assertEquals(
            100.0,
            gameState.currentLine!!
                .controlPoints
                .first()
                .x,
            0.001,
        )
        assertEquals(
            200.0,
            gameState.currentLine!!
                .controlPoints
                .first()
                .y,
            0.001,
        )
    }

    @Test
    fun `test handle update line command appends point`() {
        val conn = mockConnection("update-line-conn")
        gameState.startNewLine(100.0, 100.0)

        gameWebSocket.onMessage(
            command("UPDATE_LINE", mapOf("x" to 150.0, "y" to 250.0)),
            conn,
        )

        assertNotNull(gameState.currentLine)
        assertTrue(gameState.currentLine!!.controlPoints.size > 1)
    }

    @Test
    fun `test handle set speed command with valid speed`() {
        val conn = mockConnection("speed-conn")
        gameWebSocket.onMessage(command("SET_SPEED", mapOf("speed" to 2.5)), conn)
        assertEquals(2.5, gameState.speedMultiplier, 0.001)
    }

    @Test
    fun `test handle move paddle command updates gameState paddle2Y`() {
        val conn = mockConnection("paddle-conn")
        gameWebSocket.onMessage(command("MOVE_PADDLE", mapOf("y" to 300.0)), conn)
        assertEquals(300.0, gameState.paddle2Y, 0.001)
    }

    @Test
    fun `test onMessage with invalid JSON does not throw`() {
        val conn = mockConnection("invalid-json-conn")
        gameWebSocket.onMessage("not valid json {{{", conn)
    }

    @Test
    fun `test getActiveSessionsCount accuracy`() {
        val conns = listOf(mockConnection("cnt-1"), mockConnection("cnt-2"), mockConnection("cnt-3"))
        val before = gameWebSocket.getActiveSessionsCount()

        conns.forEach { gameWebSocket.onOpen(it) }
        assertEquals(before + 3, gameWebSocket.getActiveSessionsCount())

        conns.subList(0, 2).forEach { gameWebSocket.onClose(it) }
        assertEquals(before + 1, gameWebSocket.getActiveSessionsCount())

        conns.subList(2, 3).forEach { gameWebSocket.onClose(it) }
        assertEquals(before, gameWebSocket.getActiveSessionsCount())
    }

    @Test
    fun `test spawn powerup with invalid type does not throw`() {
        val conn = mockConnection("powerup-bad-conn")
        gameWebSocket.onMessage(command("SPAWN_POWERUP", mapOf("type" to "INVALID_TYPE")), conn)
    }

    private fun command(
        type: String,
        data: Map<String, Any> = emptyMap(),
    ): String = objectMapper.writeValueAsString(mapOf("type" to type, "data" to data))

    private fun mockConnection(id: String): WebSocketConnection {
        val conn = mock(WebSocketConnection::class.java)
        `when`(conn.id()).thenReturn(id)

        val voidUni = Uni.createFrom().voidItem()
        `when`(conn.sendText(anyString())).thenReturn(voidUni)

        return conn
    }
}
