package ru.rkhamatyarov.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.rkhamatyarov.model.ActivePowerUpEffect
import ru.rkhamatyarov.model.GameState
import ru.rkhamatyarov.model.PowerUpType

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
            ActivePowerUpEffect(PowerUpType.SPEED_BOOST, duration = Long.MAX_VALUE),
        )
        manager.update(0L)
        assertEquals(1.5, gameState.powerUpSpeedMultiplier)
    }
}
