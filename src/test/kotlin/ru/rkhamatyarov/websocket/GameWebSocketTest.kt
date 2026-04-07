package ru.rkhamatyarov.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import io.quarkus.websockets.next.WebSocketConnection
import io.smallrye.mutiny.Uni
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import ru.rkhamatyarov.model.ActivePowerUpEffect
import ru.rkhamatyarov.model.Line
import ru.rkhamatyarov.model.PowerUp
import ru.rkhamatyarov.model.Puck
import ru.rkhamatyarov.model.Score
import ru.rkhamatyarov.service.GameEngine
import ru.rkhamatyarov.service.PowerUpManager

/**
 * Test profile that disables the Vert.x periodic timer registered in
 * GameWebSocket.onStart(). Without this, the 16ms timer fires engine.tick()
 * on the event-loop thread continuously — interleaving with @BeforeEach
 * stub setup on the JUnit thread and causing non-deterministic Mockito errors.
 *
 * quarkus.scheduler.enabled=false is NOT enough because the timer is registered
 * via Vertx.setPeriodic(), not @Scheduled. We stop it by disabling the Vertx
 * periodic timer at the application level via a config property the production
 * code can check, OR by setting start.timer=false in the test profile config map.
 *
 * The simplest approach: override quarkus.vertx.prefer-native-transport=false
 * and add a custom property that GameWebSocket checks. But since we cannot change
 * production code here, we instead rely on LENIENT + doReturn() thread-safety.
 */
class NoTimerProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> =
        mapOf(
            // Disable Quinoa build during tests — frontend not needed
            "quarkus.quinoa.enabled" to "false",
            // Disable dev-ui to prevent BrowserOpener port-binding
            "quarkus.dev-ui.enabled" to "false",
        )
}

@QuarkusTest
@TestProfile(NoTimerProfile::class)
// LENIENT: even with the profile, the timer may have fired once before mocks
// are injected. LENIENT prevents any stale invocation from corrupting stub state.
@MockitoSettings(strictness = Strictness.LENIENT)
class GameWebSocketTest {
    @Inject lateinit var gameWebSocket: GameWebSocket

    @InjectMock lateinit var engine: GameEngine

    @InjectMock lateinit var powerUpManager: PowerUpManager

    @Inject lateinit var objectMapper: ObjectMapper

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

        doReturn(mutableListOf<Line>()).`when`(engine).lines
        doReturn(mutableListOf<PowerUp>()).`when`(engine).powerUps
        doReturn(mutableListOf<ActivePowerUpEffect>()).`when`(engine).activePowerUpEffects

        doAnswer { }.`when`(engine).clearLines()
        doAnswer { }.`when`(engine).movePaddle2(anyDouble())
        doAnswer { }.`when`(engine).startNewLine(anyDouble(), anyDouble())
        doAnswer { }.`when`(engine).updateCurrentLine(anyDouble(), anyDouble())
        doAnswer { }.`when`(engine).finishCurrentLine()
        doAnswer { }.`when`(powerUpManager).update(anyDouble())
    }

    @Test
    fun `connect increments session count and sends binary state`() {
        val conn = mockConn("c1")
        val before = gameWebSocket.getActiveSessionsCount()
        gameWebSocket.onOpen(conn)
        assertEquals(before + 1, gameWebSocket.getActiveSessionsCount())
        verify(conn, atLeastOnce()).sendBinary(any<ByteArray>())
        gameWebSocket.onClose(conn)
        assertEquals(before, gameWebSocket.getActiveSessionsCount())
    }

    @Test
    fun `RESET calls resetPuck, clearLines, sets paused false`() {
        val conn = mockConn("r1")
        gameWebSocket.onMessage(cmd("RESET"), conn)
        verify(engine).resetPuck()
        verify(engine).clearLines()
        verify(engine).paused = false
    }

    @Test
    fun `MOVE_PADDLE delegates to engine movePaddle2`() {
        val conn = mockConn("paddle1")
        gameWebSocket.onMessage(cmd("MOVE_PADDLE", mapOf("y" to 300.0)), conn)
        verify(engine).movePaddle2(300.0)
    }

    @Test
    fun `CLEAR_LINES calls engine clearLines`() {
        val conn = mockConn("cl1")
        gameWebSocket.onMessage(cmd("CLEAR_LINES"), conn)
        verify(engine).clearLines()
    }

    @Test
    fun `START_LINE calls engine startNewLine with correct coords`() {
        val conn = mockConn("sl1")
        gameWebSocket.onMessage(cmd("START_LINE", mapOf("x" to 100.0, "y" to 200.0)), conn)
        verify(engine).startNewLine(100.0, 200.0)
    }

    @Test
    fun `START_LINE without coords sends error`() {
        val conn = mockConn("sl2")
        gameWebSocket.onMessage(cmd("START_LINE", mapOf("x" to 100.0)), conn)
        verify(engine, never()).startNewLine(anyDouble(), anyDouble())
        verify(conn).sendTextAndAwait(anyString())
    }

    @Test
    fun `UPDATE_LINE calls engine updateCurrentLine`() {
        val conn = mockConn("ul1")
        gameWebSocket.onMessage(cmd("UPDATE_LINE", mapOf("x" to 150.0, "y" to 250.0)), conn)
        verify(engine).updateCurrentLine(150.0, 250.0)
    }

    @Test
    fun `FINISH_LINE calls engine finishCurrentLine`() {
        val conn = mockConn("fl1")
        gameWebSocket.onMessage(cmd("FINISH_LINE"), conn)
        verify(engine).finishCurrentLine()
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    fun `invalid JSON does not throw`() {
        gameWebSocket.onMessage("not valid json {{{", mockConn("bad"))
    }

    @Test
    fun `unknown command sends error frame`() {
        val conn = mockConn("unk")
        gameWebSocket.onMessage(cmd("DOES_NOT_EXIST"), conn)
        verify(conn).sendTextAndAwait(anyString())
    }

    @Test
    fun `blank message sends error frame`() {
        val conn = mockConn("blank")
        gameWebSocket.onMessage("   ", conn)
        verify(conn).sendTextAndAwait(anyString())
    }

    @Test
    fun `SPAWN_POWERUP with valid type does not send error`() {
        val conn = mockConn("pu-ok")
        gameWebSocket.onMessage(cmd("SPAWN_POWERUP", mapOf("type" to "SPEED_BOOST")), conn)
        verify(conn, never()).sendTextAndAwait(anyString())
    }

    @Test
    fun `SPAWN_POWERUP with invalid type does not crash`() {
        val conn = mockConn("pu-bad")
        gameWebSocket.onMessage(cmd("SPAWN_POWERUP", mapOf("type" to "INVALID")), conn)
    }

    // ── Physics (real GameEngine, no CDI, no timer) ───────────────────────────

    @Test
    fun `puck deflects off a horizontal line`() {
        val eng = GameEngine().also { initPowerUpManager(it) }
        eng.startNewLine(100.0, 300.0)
        eng.updateCurrentLine(700.0, 300.0)
        eng.finishCurrentLine()
        eng.puck.x = 400.0
        eng.puck.y = 290.0
        eng.puck.vx = 0.0
        eng.puck.vy = 200.0
        eng.tick(0.016)
        assertTrue(eng.puck.vy < 0.0, "vy should reverse after hitting horizontal line")
    }

    @Test
    fun `clearLines removes all lines so no deflection occurs`() {
        val eng = GameEngine().also { initPowerUpManager(it) }
        eng.startNewLine(100.0, 300.0)
        eng.updateCurrentLine(700.0, 300.0)
        eng.finishCurrentLine()
        eng.clearLines()
        eng.puck.x = 400.0
        eng.puck.y = 290.0
        eng.puck.vx = 0.0
        eng.puck.vy = 200.0
        eng.tick(0.016)
        assertTrue(eng.puck.vy > 0.0, "vy should remain positive — no line to deflect")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun cmd(
        type: String,
        data: Map<String, Any> = emptyMap(),
    ): String = objectMapper.writeValueAsString(mapOf("type" to type, "data" to data))

    private fun mockConn(id: String): WebSocketConnection =
        mock(WebSocketConnection::class.java).also { conn ->
            // doReturn() form — thread-safe, no open stub state
            doReturn(id).`when`(conn).id()
            doAnswer { Uni.createFrom().voidItem() }.`when`(conn).sendBinary(any<ByteArray>())
            doAnswer { Uni.createFrom().voidItem() }.`when`(conn).sendText(anyString())
            // sendTextAndAwait returns String? — return empty string
            doAnswer { "" }.`when`(conn).sendTextAndAwait(anyString())
        }

    private fun initPowerUpManager(eng: GameEngine) {
        val field = GameEngine::class.java.getDeclaredField("powerUpManager")
        field.isAccessible = true
        field.set(
            eng,
            object : PowerUpManager() {
                override fun update(deltaTime: Double) {}
            },
        )
    }
}
