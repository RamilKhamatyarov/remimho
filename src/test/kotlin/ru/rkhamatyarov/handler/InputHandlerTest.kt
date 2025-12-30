package ru.rkhamatyarov.handler

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.rkhamatyarov.model.GameState
import ru.rkhamatyarov.model.PowerUpType

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InputHandlerTest {
    private lateinit var inputHandler: InputHandler
    private lateinit var gameState: GameState

    @BeforeEach
    fun setUp() {
        gameState = mockk(relaxed = true)
        every { gameState.powerUps } returns mutableListOf()
        every { gameState.canvasWidth } returns 800.0
        every { gameState.canvasHeight } returns 600.0
        every { gameState.paddleHeight } returns 100.0
        every { gameState.paddle2Y } returns 250.0

        inputHandler = InputHandler()
        inputHandler.gameState = gameState
    }

    @Test
    fun `handleKeyPress toggles pause when SPACE is pressed`() {
        // g
        val event = mockKeyEvent(KeyCode.SPACE, isControlDown = false)

        // w
        inputHandler.handleKeyPress(event)

        // t
        verify(exactly = 1) { gameState.togglePause() }
    }

    @Test
    fun `handleKeyPress does not toggle pause on repeated SPACE press`() {
        // g
        val event = mockKeyEvent(KeyCode.SPACE, isControlDown = false)

        // w
        inputHandler.handleKeyPress(event)
        inputHandler.handleKeyPress(event)

        // t
        verify(exactly = 1) { gameState.togglePause() }
    }

    @Test
    fun `handleKeyPress toggles control mode when M is pressed`() {
        // g
        val initialMode = inputHandler.useMouseControl
        val event = mockKeyEvent(KeyCode.M, isControlDown = false)

        // w
        inputHandler.handleKeyPress(event)

        // t
        assertEquals(!initialMode, inputHandler.useMouseControl)
    }

    @Test
    fun `handleKeyPress does not exit when Q without Ctrl is pressed`() {
        // g
        val event = mockKeyEvent(KeyCode.Q, isControlDown = false)

        // w
        inputHandler.handleKeyPress(event)

        // t
        assertTrue(inputHandler.keysPressed.contains(KeyCode.Q))
    }

    @Test
    fun `handleKeyPress spawns SPEED_BOOST when Ctrl+1 is pressed`() {
        // g
        val event = mockKeyEvent(KeyCode.DIGIT1, isControlDown = true)

        // w
        inputHandler.handleKeyPress(event)

        // t
        assertEquals(1, gameState.powerUps.size)
        assertEquals(PowerUpType.SPEED_BOOST, gameState.powerUps[0].type)
    }

    @Test
    fun `handleKeyPress spawns MAGNET_BALL when Ctrl+2 is pressed`() {
        // g
        val event = mockKeyEvent(KeyCode.DIGIT2, isControlDown = true)

        // w
        inputHandler.handleKeyPress(event)

        // t
        assertEquals(1, gameState.powerUps.size)
        assertEquals(PowerUpType.MAGNET_BALL, gameState.powerUps[0].type)
    }

    @Test
    fun `handleKeyPress spawns GHOST_MODE when Ctrl+3 is pressed`() {
        // g
        val event = mockKeyEvent(KeyCode.DIGIT3, isControlDown = true)

        // w
        inputHandler.handleKeyPress(event)

        // t
        assertEquals(1, gameState.powerUps.size)
        assertEquals(PowerUpType.GHOST_MODE, gameState.powerUps[0].type)
    }

    @Test
    fun `handleKeyPress spawns MULTI_BALL when Ctrl+4 is pressed`() {
        // g
        val event = mockKeyEvent(KeyCode.DIGIT4, isControlDown = true)

        // w
        inputHandler.handleKeyPress(event)

        // t
        assertEquals(1, gameState.powerUps.size)
        assertEquals(PowerUpType.MULTI_BALL, gameState.powerUps[0].type)
    }

    @Test
    fun `handleKeyPress spawns PADDLE_SHIELD when Ctrl+5 is pressed`() {
        // g
        val event = mockKeyEvent(KeyCode.DIGIT5, isControlDown = true)

        // w
        inputHandler.handleKeyPress(event)

        // t
        assertEquals(1, gameState.powerUps.size)
        assertEquals(PowerUpType.PADDLE_SHIELD, gameState.powerUps[0].type)
    }

    @Test
    fun `handleKeyPress adds non-special keys to keysPressed set`() {
        // g
        val event = mockKeyEvent(KeyCode.UP, isControlDown = false)

        // w
        inputHandler.handleKeyPress(event)

        // t
        assertTrue(inputHandler.keysPressed.contains(KeyCode.UP))
    }

    @Test
    fun `handleKeyRelease removes SPACE flag`() {
        // g
        val pressEvent = mockKeyEvent(KeyCode.SPACE, isControlDown = false)
        val releaseEvent = mockKeyEvent(KeyCode.SPACE, isControlDown = false)

        inputHandler.handleKeyPress(pressEvent)

        // w
        inputHandler.handleKeyRelease(releaseEvent)
        inputHandler.handleKeyPress(pressEvent)

        // t
        verify(exactly = 2) { gameState.togglePause() }
    }

    @Test
    fun `handleKeyRelease removes key from keysPressed set`() {
        // g
        val pressEvent = mockKeyEvent(KeyCode.UP, isControlDown = false)
        val releaseEvent = mockKeyEvent(KeyCode.UP, isControlDown = false)

        inputHandler.handleKeyPress(pressEvent)

        // w
        inputHandler.handleKeyRelease(releaseEvent)

        // t
        assertFalse(inputHandler.keysPressed.contains(KeyCode.UP))
    }

    @Test
    fun `handleMouseMove updates paddle position when mouse control enabled`() {
        // g
        inputHandler.useMouseControl = true
        val mouseEvent = mockMouseEvent()
        every { gameState.paddle2Y = any() } just Runs

        // w
        inputHandler.handleMouseMove(mouseEvent)

        // t
        verify { gameState.paddle2Y = 250.0 }
    }

    @Test
    fun `handleMouseMove does not update paddle when mouse control disabled`() {
        // g
        inputHandler.useMouseControl = false
        val mouseEvent = mockMouseEvent()

        // w
        inputHandler.handleMouseMove(mouseEvent)

        // t
        verify(exactly = 0) { gameState.paddle2Y = any() }
    }

    @Test
    fun `update moves paddle up when UP key is pressed`() {
        // g
        inputHandler.useMouseControl = false
        inputHandler.keysPressed.add(KeyCode.UP)
        every { gameState.paddle2Y } returns 250.0
        every { gameState.paddle2Y = any() } just Runs

        // w
        inputHandler.update()

        // t
        verify { gameState.paddle2Y = 245.0 }
    }

    @Test
    fun `update moves paddle down when DOWN key is pressed`() {
        // g
        inputHandler.useMouseControl = false
        inputHandler.keysPressed.add(KeyCode.DOWN)
        every { gameState.paddle2Y } returns 250.0
        every { gameState.paddle2Y = any() } just Runs

        // w
        inputHandler.update()

        // t
        verify { gameState.paddle2Y = 255.0 }
    }

    @Test
    fun `update constrains paddle within canvas bounds at top`() {
        // g
        inputHandler.useMouseControl = false
        inputHandler.keysPressed.add(KeyCode.UP)
        every { gameState.paddle2Y } returns 0.0
        every { gameState.paddle2Y = any() } just Runs

        // w
        inputHandler.update()

        // t
        verify { gameState.paddle2Y = 0.0 }
    }

    @Test
    fun `update constrains paddle within canvas bounds at bottom`() {
        // g
        inputHandler.useMouseControl = false
        inputHandler.keysPressed.add(KeyCode.DOWN)
        every { gameState.paddle2Y } returns 500.0
        every { gameState.paddle2Y = any() } just Runs

        // w
        inputHandler.update()

        // t
        verify { gameState.paddle2Y = 500.0 }
    }

    @Test
    fun `update does not move paddle when mouse control enabled`() {
        // g
        inputHandler.useMouseControl = true
        inputHandler.keysPressed.add(KeyCode.UP)
        every { gameState.paddle2Y } returns 250.0

        // w
        inputHandler.update()

        // t
        verify(exactly = 1) { gameState.paddle2Y }
    }

    @Test
    fun `spawned power-up is placed at canvas center`() {
        // g
        val event = mockKeyEvent(KeyCode.DIGIT1, isControlDown = true)

        // w
        inputHandler.handleKeyPress(event)

        // t
        val powerUp = gameState.powerUps[0]
        assertEquals(400.0, powerUp.x)
        assertEquals(300.0, powerUp.y)
    }

    private fun mockKeyEvent(
        code: KeyCode,
        isControlDown: Boolean,
    ): KeyEvent =
        mockk<KeyEvent>(relaxed = true).apply {
            every { this@apply.code } returns code
            every { this@apply.isControlDown } returns isControlDown
        }

    private fun mockMouseEvent(): MouseEvent =
        mockk<MouseEvent>(relaxed = true).apply {
            every { this@apply.x } returns 100.0
            every { this@apply.y } returns 300.0
        }
}
