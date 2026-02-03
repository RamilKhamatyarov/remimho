package ru.rkhamatyarov.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.mockito.InjectSpy
import jakarta.inject.Inject
import jakarta.websocket.Session
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import ru.rkhamatyarov.model.GameState
import ru.rkhamatyarov.service.GameEngine
import ru.rkhamatyarov.websocket.dto.GameCommand

@QuarkusTest
class GameWebSocketTest {
    @Inject
    lateinit var gameWebSocket: GameWebSocket

    @InjectMock
    lateinit var gameEngine: GameEngine

    @InjectSpy
    lateinit var gameState: GameState

    @Inject
    lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        gameState.reset()
    }

    @Test
    fun `test WebSocket connection and disconnection`() {
        val mockSession = createMockSession("test-session-1")
        val initialCount = gameWebSocket.getActiveSessionsCount()

        gameWebSocket.onOpen(mockSession)
        assertEquals(initialCount + 1, gameWebSocket.getActiveSessionsCount())

        gameWebSocket.onClose(mockSession)
        assertEquals(initialCount, gameWebSocket.getActiveSessionsCount())
    }

    @Test
    fun `test multiple WebSocket connections`() {
        val session1 = createMockSession("session-1")
        val session2 = createMockSession("session-2")
        val session3 = createMockSession("session-3")

        gameWebSocket.onOpen(session1)
        gameWebSocket.onOpen(session2)
        gameWebSocket.onOpen(session3)
        assertEquals(3, gameWebSocket.getActiveSessionsCount())

        gameWebSocket.onClose(session2)
        assertEquals(2, gameWebSocket.getActiveSessionsCount())

        gameWebSocket.onClose(session1)
        gameWebSocket.onClose(session3)
    }

    @Test
    fun `test handle toggle pause command`() {
        val mockSession = createMockSession("pause-session")
        assertFalse(gameState.paused)

        val command = GameCommand(type = "TOGGLE_PAUSE")
        gameWebSocket.onMessage(objectMapper.writeValueAsString(command), mockSession)
        assertTrue(gameState.paused)

        gameWebSocket.onMessage(objectMapper.writeValueAsString(command), mockSession)
        assertFalse(gameState.paused)
    }

    @Test
    fun `test handle reset command`() {
        val mockSession = createMockSession("reset-session")
        gameState.puckX = 100.0
        gameState.puckY = 100.0
        gameState.puckVX = 20.0
        gameState.puckVY = 20.0
        gameState.paddle1Y = 100.0
        gameState.paddle2Y = 200.0
        gameState.paused = true

        val command = GameCommand(type = "RESET")
        gameWebSocket.onMessage(objectMapper.writeValueAsString(command), mockSession)

        assertEquals(gameState.canvasWidth / 2, gameState.puckX)
        assertEquals(gameState.canvasHeight / 2, gameState.puckY)
    }

    @Test
    fun `test handle clear lines command`() {
        val mockSession = createMockSession("clear-lines")
        gameState.startNewLine(100.0, 100.0)
        gameState.updateCurrentLine(150.0, 150.0)
        gameState.finishCurrentLine()
        assertTrue(gameState.lines.isNotEmpty())

        val command = GameCommand(type = "CLEAR_LINES")
        gameWebSocket.onMessage(objectMapper.writeValueAsString(command), mockSession)
        assertEquals(0, gameState.lines.size)
        assertFalse(gameState.isDrawing)
    }

    @Test
    fun `test handle start line command`() {
        val mockSession = createMockSession("start-line")
        val command =
            GameCommand(
                type = "START_LINE",
                data = mapOf("x" to 100.0, "y" to 200.0),
            )

        gameWebSocket.onMessage(objectMapper.writeValueAsString(command), mockSession)
        assertNotNull(gameState.currentLine)
        assertEquals(
            100.0,
            gameState.currentLine
                ?.controlPoints
                ?.first()
                ?.x,
        )
        assertEquals(
            200.0,
            gameState.currentLine
                ?.controlPoints
                ?.first()
                ?.y,
        )
    }

    @Test
    fun `test handle update line command`() {
        val mockSession = createMockSession("update-line")
        gameState.startNewLine(100.0, 100.0)

        val command =
            GameCommand(
                type = "UPDATE_LINE",
                data = mapOf("x" to 150.0, "y" to 250.0),
            )
        gameWebSocket.onMessage(objectMapper.writeValueAsString(command), mockSession)

        assertNotNull(gameState.currentLine)
        assertTrue(gameState.currentLine!!.controlPoints.size > 1)
    }

    @Test
    fun `test handle set speed command with valid speed`() {
        val mockSession = createMockSession("speed-session")

        val command =
            GameCommand(
                type = "SET_SPEED",
                data = mapOf("speed" to 2.5),
            )
        gameWebSocket.onMessage(objectMapper.writeValueAsString(command), mockSession)
        assertEquals(2.5, gameState.speedMultiplier)
    }

    @Test
    fun `test handle move paddle command`() {
        val mockSession = createMockSession("move-paddle")

        val command =
            GameCommand(
                type = "MOVE_PADDLE",
                data = mapOf("y" to 300.0),
            )
        gameWebSocket.onMessage(objectMapper.writeValueAsString(command), mockSession)
        assertEquals(300.0, gameState.paddle2Y)
    }

    @Test
    fun `test onMessage with invalid JSON`() {
        val mockSession = createMockSession("invalid-json-session")
        val invalidJson = "not valid json"

        gameWebSocket.onMessage(invalidJson, mockSession)

        verify(mockSession.asyncRemote, atLeastOnce()).sendText(anyString())
    }

    @Test
    fun `test broadcastGameState when no sessions`() {
        gameWebSocket.broadcastGameState()
        assertEquals(0, gameWebSocket.getActiveSessionsCount())
    }

    @Test
    fun `test broadcastGameState with active sessions`() {
        val mockSession = createMockSession("broadcast-session")
        val asyncRemote = mock(jakarta.websocket.RemoteEndpoint.Async::class.java)

        `when`(mockSession.asyncRemote).thenReturn(asyncRemote)
        `when`(mockSession.isOpen).thenReturn(true)

        gameWebSocket.onOpen(mockSession)
        gameWebSocket.broadcastGameState()

        verify(asyncRemote, atLeastOnce()).sendText(anyString())
        gameWebSocket.onClose(mockSession)
    }

    @Test
    fun `test getActiveSessionsCount accuracy`() {
        val sessions =
            listOf(
                createMockSession("session-1"),
                createMockSession("session-2"),
                createMockSession("session-3"),
            )

        assertEquals(0, gameWebSocket.getActiveSessionsCount())
        sessions.forEach { gameWebSocket.onOpen(it) }
        assertEquals(3, gameWebSocket.getActiveSessionsCount())

        sessions.subList(0, 2).forEach { gameWebSocket.onClose(it) }
        assertEquals(1, gameWebSocket.getActiveSessionsCount())

        sessions.subList(2, 3).forEach { gameWebSocket.onClose(it) }
        assertEquals(0, gameWebSocket.getActiveSessionsCount())
    }

    @Test
    fun `test spawn powerup with invalid type`() {
        val mockSession = createMockSession("powerup-session")
        val command =
            GameCommand(
                type = "SPAWNPOWERUP",
                data = mapOf("type" to "INVALIDTYPE"),
            )

        gameWebSocket.onMessage(objectMapper.writeValueAsString(command), mockSession)

        verify(mockSession.asyncRemote, atLeastOnce()).sendText(anyString())
    }

    private fun createMockSession(sessionId: String): Session {
        val mockSession = mock(Session::class.java)
        `when`(mockSession.id).thenReturn(sessionId)
        `when`(mockSession.isOpen).thenReturn(true)

        val asyncRemote = mock(jakarta.websocket.RemoteEndpoint.Async::class.java)
        `when`(mockSession.asyncRemote).thenReturn(asyncRemote)

        return mockSession
    }
}
