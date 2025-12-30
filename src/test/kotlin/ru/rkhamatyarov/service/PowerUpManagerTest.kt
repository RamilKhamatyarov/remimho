package ru.rkhamatyarov.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.rkhamatyarov.model.ActivePowerUpEffect
import ru.rkhamatyarov.model.GameState
import ru.rkhamatyarov.model.PowerUpType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PowerUpManagerTest {
    private lateinit var gameState: GameState
    private lateinit var manager: PowerUpManager

    @BeforeEach
    fun setUp() {
        manager = PowerUpManager()
        gameState = GameState()
        manager.gameState = gameState
    }

    @Test
    fun `update applies speed boost effect to gameState`() {
        gameState.activePowerUpEffects.add(
            ActivePowerUpEffect(
                type = PowerUpType.SPEED_BOOST,
                duration = Long.MAX_VALUE,
                activationTime = System.nanoTime(),
            ),
        )
        manager.update(0L)
        assertEquals(1.5, gameState.powerUpSpeedMultiplier)
    }

    @Test
    fun `deactivatePowerUpEffect disables ghost mode when effect expires`() {
        gameState.isGhostMode = true

        gameState.activePowerUpEffects.add(
            ActivePowerUpEffect(
                type = PowerUpType.GHOST_MODE,
                duration = 1L,
                activationTime = System.nanoTime() - 2_000_000_000L,
            ),
        )

        manager.update(0L)

        assertFalse(gameState.isGhostMode)
        assertTrue(gameState.activePowerUpEffects.isEmpty())
    }

    @Test
    fun `applyActivePowerUpEffects sets correct speed multiplier without active powerups`() {
        gameState.activePowerUpEffects.clear()
        manager.update(0L)

        assertEquals(1.0, gameState.powerUpSpeedMultiplier)
    }

    @Test
    fun `applyActivePowerUpEffects sets correct speed multiplier with speed boost`() {
        gameState.activePowerUpEffects.add(
            ActivePowerUpEffect(
                type = PowerUpType.SPEED_BOOST,
                duration = Long.MAX_VALUE,
                activationTime = System.nanoTime(),
            ),
        )
        manager.update(0L)
        assertEquals(1.5, gameState.powerUpSpeedMultiplier)
    }
}
