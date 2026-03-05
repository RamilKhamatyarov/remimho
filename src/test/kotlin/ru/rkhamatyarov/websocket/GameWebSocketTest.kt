package ru.rkhamatyarov.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.websockets.next.WebSocketConnection
import io.smallrye.mutiny.Uni
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import ru.rkhamatyarov.model.Line
import ru.rkhamatyarov.model.Puck
import ru.rkhamatyarov.model.Score
import ru.rkhamatyarov.service.GameEngine

@QuarkusTest
class GameWebSocketTest {
    @Inject
    lateinit var gameWebSocket: GameWebSocket

    @InjectMock
    lateinit var engine: GameEngine

    @Inject
    lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        val puck = Puck(x = 400.0, y = 300.0, vx = 0.0, vy = 0.0, radius = 10.0)
        val score = Score(playerA = 0, playerB = 0)
        doReturn(puck).`when`(engine).puck
        doReturn(score).`when`(engine).score
        doReturn(800.0).`when`(engine).canvasWidth
        doReturn(600.0).`when`(engine).canvasHeight
        doReturn(100.0).`when`(engine).paddleHeight
        doReturn(250.0).`when`(engine).paddle1Y
        doReturn(250.0).`when`(engine).paddle2Y
        doReturn(false).`when`(engine).paused
        doReturn(emptyList<Line>()).`when`(engine).lines
    }

    @Test fun `connect increments session count`() {
        val conn = mockConn("c1")
        val before = gameWebSocket.getActiveSessionsCount()
        gameWebSocket.onOpen(conn)
        assertEquals(before + 1, gameWebSocket.getActiveSessionsCount())
        gameWebSocket.onClose(conn)
        assertEquals(before, gameWebSocket.getActiveSessionsCount())
    }

    @Test fun `three connections then selective disconnect`() {
        val c1 = mockConn("m1")
        val c2 = mockConn("m2")
        val c3 = mockConn("m3")
        val before = gameWebSocket.getActiveSessionsCount()
        listOf(c1, c2, c3).forEach { gameWebSocket.onOpen(it) }
        assertEquals(before + 3, gameWebSocket.getActiveSessionsCount())
        gameWebSocket.onClose(c2)
        assertEquals(before + 2, gameWebSocket.getActiveSessionsCount())
        gameWebSocket.onClose(c1)
        gameWebSocket.onClose(c3)
        assertEquals(before, gameWebSocket.getActiveSessionsCount())
    }

    @Test fun `TOGGLE_PAUSE calls engine paused toggle`() {
        val conn = mockConn("p1")
        gameWebSocket.onMessage(cmd("TOGGLE_PAUSE"), conn)

        verify(engine, atLeastOnce()).paused
    }

    @Test fun `RESET calls resetPuck and clearLines`() {
        val conn = mockConn("r1")
        gameWebSocket.onMessage(cmd("RESET"), conn)
        verify(engine).resetPuck()
        verify(engine).clearLines()
    }

    @Test fun `MOVE_PADDLE delegates to engine movePaddle2`() {
        val conn = mockConn("paddle1")
        gameWebSocket.onMessage(cmd("MOVE_PADDLE", mapOf("y" to 300.0)), conn)
        verify(engine).movePaddle2(300.0)
    }

    @Test fun `MOVE_PADDLE without y sends error and does not call engine`() {
        val conn = mockConn("paddle2")
        gameWebSocket.onMessage(cmd("MOVE_PADDLE", emptyMap()), conn)
        verify(engine, never()).movePaddle2(anyDouble())
        verify(conn).sendTextAndAwait(anyString())
    }

    @Test fun `CLEAR_LINES calls engine clearLines`() {
        val conn = mockConn("cl1")
        gameWebSocket.onMessage(cmd("CLEAR_LINES"), conn)
        verify(engine).clearLines()
    }

    @Test fun `START_LINE calls engine startNewLine with correct coords`() {
        val conn = mockConn("sl1")
        gameWebSocket.onMessage(cmd("START_LINE", mapOf("x" to 100.0, "y" to 200.0)), conn)
        verify(engine).startNewLine(100.0, 200.0)
    }

    @Test fun `START_LINE without coords sends error`() {
        val conn = mockConn("sl2")
        gameWebSocket.onMessage(cmd("START_LINE", mapOf("x" to 100.0)), conn)
        verify(engine, never()).startNewLine(anyDouble(), anyDouble())
        verify(conn).sendTextAndAwait(anyString())
    }

    @Test fun `UPDATE_LINE calls engine updateCurrentLine`() {
        val conn = mockConn("ul1")
        gameWebSocket.onMessage(cmd("UPDATE_LINE", mapOf("x" to 150.0, "y" to 250.0)), conn)
        verify(engine).updateCurrentLine(150.0, 250.0)
    }

    @Test fun `UPDATE_LINE without coords sends error`() {
        val conn = mockConn("ul2")
        gameWebSocket.onMessage(cmd("UPDATE_LINE", mapOf("x" to 150.0)), conn)
        verify(engine, never()).updateCurrentLine(anyDouble(), anyDouble())
        verify(conn).sendTextAndAwait(anyString())
    }

    @Test fun `FINISH_LINE calls engine finishCurrentLine`() {
        val conn = mockConn("fl1")
        gameWebSocket.onMessage(cmd("FINISH_LINE"), conn)
        verify(engine).finishCurrentLine()
    }

    @Test fun `puck deflects off a horizontal line`() {
        val realEngine = GameEngine()

        realEngine.startNewLine(100.0, 300.0)
        realEngine.updateCurrentLine(700.0, 300.0)
        realEngine.finishCurrentLine()

        realEngine.puck.x = 400.0
        realEngine.puck.y = 290.0
        realEngine.puck.vx = 0.0
        realEngine.puck.vy = 200.0

        realEngine.tick(0.016)

        assertTrue(realEngine.puck.vy < 0.0, "vy should reverse after hitting horizontal line")
    }

    @Test fun `puck deflects off a vertical line`() {
        val realEngine = GameEngine()
        realEngine.startNewLine(400.0, 100.0)
        realEngine.updateCurrentLine(400.0, 500.0)
        realEngine.finishCurrentLine()

        realEngine.puck.x = 390.0
        realEngine.puck.y = 300.0
        realEngine.puck.vx = 200.0
        realEngine.puck.vy = 0.0

        realEngine.tick(0.016)

        assertTrue(realEngine.puck.vx < 0.0, "vx should reverse after hitting vertical line")
    }

    @Test fun `clearLines removes all lines so no deflection occurs`() {
        val realEngine = GameEngine()
        realEngine.startNewLine(100.0, 300.0)
        realEngine.updateCurrentLine(700.0, 300.0)
        realEngine.finishCurrentLine()
        realEngine.clearLines()

        realEngine.puck.x = 400.0
        realEngine.puck.y = 290.0
        realEngine.puck.vx = 0.0
        realEngine.puck.vy = 200.0

        realEngine.tick(0.016)

        assertTrue(realEngine.puck.vy > 0.0, "vy should remain positive, no line to deflect")
    }

    @Test fun `invalid JSON does not throw`() {
        gameWebSocket.onMessage("not valid json {{{", mockConn("bad"))
    }

    @Test fun `unknown command sends error frame`() {
        val conn = mockConn("unk")
        gameWebSocket.onMessage(cmd("DOES_NOT_EXIST"), conn)
        verify(conn).sendTextAndAwait(anyString())
    }

    @Test fun `blank message sends error frame`() {
        val conn = mockConn("blank")
        gameWebSocket.onMessage("   ", conn)
        verify(conn).sendTextAndAwait(anyString())
    }

    @Test fun `SPAWN_POWERUP with invalid type sends error`() {
        val conn = mockConn("pu")
        gameWebSocket.onMessage(cmd("SPAWN_POWERUP", mapOf("type" to "INVALID")), conn)
    }

    private fun cmd(
        type: String,
        data: Map<String, Any> = emptyMap(),
    ): String = objectMapper.writeValueAsString(mapOf("type" to type, "data" to data))

    private fun mockConn(id: String): WebSocketConnection {
        val conn = mock(WebSocketConnection::class.java)
        `when`(conn.id()).thenReturn(id)
        `when`(conn.sendText(anyString())).thenReturn(Uni.createFrom().voidItem())
        return conn
    }
}
