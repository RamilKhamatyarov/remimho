package ru.rkhamatyarov.service
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.rkhamatyarov.model.ActivePowerUpEffect
import ru.rkhamatyarov.model.PowerUp
import ru.rkhamatyarov.model.PowerUpType
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@QuarkusTest
class PowerUpManagerTest {
    @Inject lateinit var manager: PowerUpManager

    @Inject lateinit var engine: GameEngine

    @BeforeEach
    fun setup() {
        engine.powerUps.clear()
        engine.activePowerUpEffects.clear()
        engine.additionalPucks.clear()
        engine.powerUpSpeedMultiplier = 1.0
        engine.isGhostMode = false
        engine.hasPaddleShield = false
        engine.resetPuck()
        engine.paused = false
    }

    private fun forceSpawnTimeElapsed() {
        val field = PowerUpManager::class.java.getDeclaredField("lastSpawnTime")
        field.isAccessible = true
        field.setLong(manager, 0L)
    }

    @Test fun `expired power-ups are removed during update`() {
        val expiredPu =
            PowerUp(x = 400.0, y = 300.0, type = PowerUpType.SPEED_BOOST).apply {
                this::class.java.getDeclaredField("creationTime").also {
                    it.isAccessible = true
                    it.setLong(this, System.nanoTime() - 30_000_000_000L)
                }
            }
        engine.powerUps.add(expiredPu)
        manager.update(0.016)
        assertFalse(engine.powerUps.contains(expiredPu), "Expired power-up must be removed")
    }

    @Test fun `puck collecting power-up activates its effect`() {
        val pu = PowerUp(x = engine.puck.x, y = engine.puck.y, type = PowerUpType.SPEED_BOOST)
        engine.powerUps.add(pu)

        manager.update(0.016)

        assertTrue(
            engine.activePowerUpEffects.any { it.type == PowerUpType.SPEED_BOOST },
            "SPEED_BOOST effect should be active after puck collision",
        )
        assertFalse(engine.powerUps.contains(pu), "Collected power-up must be removed from field")
    }

    @Test fun `puck far from power-up does NOT collect it`() {
        val pu = PowerUp(x = 50.0, y = 50.0, type = PowerUpType.MAGNET_BALL)
        engine.powerUps.add(pu)
        engine.puck.x = 750.0
        engine.puck.y = 550.0

        manager.update(0.016)

        assertTrue(engine.powerUps.contains(pu), "Power-up should still be on field")
        assertTrue(engine.activePowerUpEffects.none { it.type == PowerUpType.MAGNET_BALL })
    }

    @Test fun `SPEED_BOOST sets powerUpSpeedMultiplier to 1_5`() {
        engine.activePowerUpEffects.add(
            ActivePowerUpEffect(
                type = PowerUpType.SPEED_BOOST,
                duration = 5_000_000_000L,
                activationTime = System.nanoTime(),
            ),
        )
        manager.update(0.016)
        assertEquals(1.5, engine.powerUpSpeedMultiplier, 0.001)
    }

    @Test fun `without SPEED_BOOST multiplier stays at 1_0`() {
        manager.update(0.016)
        assertEquals(1.0, engine.powerUpSpeedMultiplier, 0.001)
    }

    @Test fun `expired SPEED_BOOST is removed and multiplier resets`() {
        engine.activePowerUpEffects.add(
            ActivePowerUpEffect(
                type = PowerUpType.SPEED_BOOST,
                duration = 1L,
                activationTime = System.nanoTime() - 10_000L,
            ),
        )
        manager.update(0.016)
        assertTrue(engine.activePowerUpEffects.isEmpty(), "Expired effect should be removed")
        assertEquals(1.0, engine.powerUpSpeedMultiplier, 0.001)
    }

    @Test fun `GHOST_MODE deactivation clears isGhostMode flag`() {
        engine.isGhostMode = true
        engine.activePowerUpEffects.add(
            ActivePowerUpEffect(
                type = PowerUpType.GHOST_MODE,
                duration = 1L,
                activationTime = System.nanoTime() - 10_000L,
            ),
        )
        manager.update(0.016)
        assertFalse(engine.isGhostMode, "isGhostMode must be false after effect expires")
    }

    @Test fun `PADDLE_SHIELD deactivation clears hasPaddleShield flag`() {
        engine.hasPaddleShield = true
        engine.activePowerUpEffects.add(
            ActivePowerUpEffect(
                type = PowerUpType.PADDLE_SHIELD,
                duration = 1L,
                activationTime = System.nanoTime() - 10_000L,
            ),
        )
        manager.update(0.016)
        assertFalse(engine.hasPaddleShield, "hasPaddleShield must be false after effect expires")
    }

    @Test fun `spawned power-up is within canvas bounds`() {
        forceSpawnTimeElapsed()
        manager.update(0.016)

        engine.powerUps.forEach { pu ->
            assertTrue(pu.x in 50.0..(engine.canvasWidth - 50.0), "x=${pu.x} out of safe range")
            assertTrue(pu.y in 50.0..(engine.canvasHeight - 50.0), "y=${pu.y} out of safe range")
        }
    }

    @Test fun `two simultaneous effects both stay active until expired`() {
        val now = System.nanoTime()
        engine.activePowerUpEffects.add(
            ActivePowerUpEffect(type = PowerUpType.SPEED_BOOST, duration = 5_000_000_000L, activationTime = now),
        )
        engine.activePowerUpEffects.add(
            ActivePowerUpEffect(type = PowerUpType.MAGNET_BALL, duration = 5_000_000_000L, activationTime = now),
        )

        manager.update(0.016)

        assertEquals(2, engine.activePowerUpEffects.size)
        assertEquals(1.5, engine.powerUpSpeedMultiplier, 0.001)
    }

    @Test fun `one expired effect removed while other stays`() {
        val now = System.nanoTime()
        engine.activePowerUpEffects.add(
            ActivePowerUpEffect(type = PowerUpType.SPEED_BOOST, duration = 5_000_000_000L, activationTime = now),
        )
        engine.activePowerUpEffects.add(
            ActivePowerUpEffect(type = PowerUpType.MAGNET_BALL, duration = 1L, activationTime = now - 10_000L),
        )

        manager.update(0.016)

        assertEquals(1, engine.activePowerUpEffects.size)
        assertEquals(PowerUpType.SPEED_BOOST, engine.activePowerUpEffects.first().type)
    }
}
