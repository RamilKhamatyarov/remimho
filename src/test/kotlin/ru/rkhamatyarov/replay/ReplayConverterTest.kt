package ru.rkhamatyarov.replay

import org.junit.jupiter.api.Test
import ru.rkhamatyarov.model.AiOpponentConfig
import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.model.SpeedConfig
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.GameIntent
import ru.rkhamatyarov.service.mvi.MviActivePowerUp
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviLine
import ru.rkhamatyarov.service.mvi.MviPoint
import ru.rkhamatyarov.service.mvi.MviPowerUp
import ru.rkhamatyarov.service.mvi.MviPuck
import ru.rkhamatyarov.service.mvi.MviScore
import ru.rkhamatyarov.service.mvi.PaddleSide
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplayConverterTest {
    @Test
    fun `Tick intent round-trips through proto with elapsedNs preserved`() {
        // g
        val intent =
            GameIntent.Reliable(
                GameAction.Tick(0.016, 1_234_567_890L, playerAControlledByHuman = true, turboSpeedMultiplier = 2.5),
            )

        // w
        val proto = ReplayConverter.toProto(intent)!!
        val (restored, elapsedNs) = ReplayConverter.fromProto(proto)

        // t
        val action = restored.action as GameAction.Tick
        assertEquals(0.016, action.deltaSeconds, 1e-9)
        assertEquals(1_234_567_890L, action.elapsedNs)
        assertTrue(action.playerAControlledByHuman)
        assertEquals(2.5, action.turboSpeedMultiplier, 1e-9)
        assertEquals(1_234_567_890L, elapsedNs)
    }

    @Test
    fun `MovePaddle intent round-trips through proto`() {
        // g
        val intent = GameIntent.Reliable(GameAction.MovePaddle(275.5, PaddleSide.A))

        // w
        val proto = ReplayConverter.toProto(intent)!!
        val (restored, _) = ReplayConverter.fromProto(proto)

        // t
        val action = restored.action as GameAction.MovePaddle
        assertEquals(275.5, action.y, 1e-9)
        assertEquals(PaddleSide.A, action.side)
    }

    @Test
    fun `ActivateTurbo intent round-trips through proto`() {
        val intent = GameIntent.Reliable(GameAction.ActivateTurbo(PaddleSide.A))

        val proto = ReplayConverter.toProto(intent)!!
        val (restored, _) = ReplayConverter.fromProto(proto)

        val action = restored.action as GameAction.ActivateTurbo
        assertEquals(PaddleSide.A, action.side)
    }

    @Test
    fun `RestoreSnapshot intent produces null proto to mark it as starting state`() {
        // g
        val intent = GameIntent.Reliable(GameAction.RestoreSnapshot(MviGameState()))

        // w
        val proto = ReplayConverter.toProto(intent)

        // t
        assertNull(proto)
    }

    @Test
    fun `CommitLine intent round-trips through proto preserving all points`() {
        // g
        val line = MviLine("line-1", listOf(MviPoint(10.0, 20.0), MviPoint(30.0, 40.0)), 5.0)
        val intent = GameIntent.Reliable(GameAction.CommitLine(line))

        // w
        val proto = ReplayConverter.toProto(intent)!!
        val (restored, _) = ReplayConverter.fromProto(proto)

        // t
        val action = restored.action as GameAction.CommitLine
        assertEquals("line-1", action.line.id)
        assertEquals(2, action.line.points.size)
        assertEquals(10.0, action.line.points[0].x, 1e-9)
        assertEquals(40.0, action.line.points[1].y, 1e-9)
    }

    @Test
    fun `SpawnPowerUp intent round-trips through proto with createdElapsedNs preserved`() {
        // g
        val powerUp = MviPowerUp("pu-1", 200.0, 300.0, PowerUpType.MAGNET_BALL, 50_000_000L, 15_000_000_000L, 15.0)
        val intent = GameIntent.Reliable(GameAction.SpawnPowerUp(powerUp))

        // w
        val proto = ReplayConverter.toProto(intent)!!
        val (restored, _) = ReplayConverter.fromProto(proto)

        // t
        val action = restored.action as GameAction.SpawnPowerUp
        assertEquals("pu-1", action.powerUp.id)
        assertEquals(PowerUpType.MAGNET_BALL, action.powerUp.type)
        assertEquals(50_000_000L, action.powerUp.createdNs)
    }

    @Test
    fun `ApplyAiConfig intent round-trips through proto`() {
        // g
        val config = AiOpponentConfig(enabled = true, reactionDelayMs = 120L, maxSpeed = 250.0, trackingError = 3.0, reactZoneRatio = 0.9)
        val intent = GameIntent.Reliable(GameAction.ApplyAiConfig(config))

        // w
        val proto = ReplayConverter.toProto(intent)!!
        val (restored, _) = ReplayConverter.fromProto(proto)

        // t
        val action = restored.action as GameAction.ApplyAiConfig
        assertEquals(120L, action.config.reactionDelayMs)
        assertEquals(250.0, action.config.maxSpeed, 1e-9)
        assertEquals(0.9, action.config.reactZoneRatio, 1e-9)
    }

    @Test
    fun `ApplyTeleports intent round-trips through proto`() {
        // g
        val portals = mapOf("portal-a" to "portal-b", "portal-b" to "portal-a")
        val intent = GameIntent.Reliable(GameAction.ApplyTeleports(portals))

        // w
        val proto = ReplayConverter.toProto(intent)!!
        val (restored, _) = ReplayConverter.fromProto(proto)

        // t
        val action = restored.action as GameAction.ApplyTeleports
        assertEquals(portals, action.portals)
    }

    @Test
    fun `stateToSnapshot then snapshotToState round-trips MviGameState losslessly`() {
        // g
        val state =
            MviGameState(
                puck = MviPuck(x = 123.0, y = 456.0, vx = -300.0, vy = 150.0, teleportCooldownUntilNs = 5_000_000L),
                score = MviScore(playerA = 3, playerB = 2),
                paddle1Y = 100.0,
                paddle2Y = 200.0,
                paused = true,
                elapsedSeconds = 42.5,
                speedConfig = SpeedConfig(baseMultiplier = 1.5, maxMultiplier = 4.0),
                aiConfig = AiOpponentConfig(enabled = false, reactionDelayMs = 90L, maxSpeed = 300.0),
                lines = listOf(MviLine("l1", listOf(MviPoint(10.0, 20.0), MviPoint(30.0, 40.0)), 3.0)),
                teleports = mapOf("l1" to "l2"),
                powerUps = listOf(MviPowerUp("pu-1", 400.0, 300.0, PowerUpType.SPEED_BOOST, 100_000L)),
                activePowerUps = listOf(MviActivePowerUp(PowerUpType.GHOST_MODE, 1_000_000L, 6_000_000_000L)),
                ghostMode = true,
                paddleShield = false,
                speedMultiplier = 1.5,
            )

        // w
        val snapshot = ReplayConverter.stateToSnapshot(state)
        val restored = ReplayConverter.snapshotToState(snapshot)

        // t
        assertEquals(state.puck.x, restored.puck.x, 1e-9)
        assertEquals(state.puck.teleportCooldownUntilNs, restored.puck.teleportCooldownUntilNs)
        assertEquals(state.score.playerA, restored.score.playerA)
        assertEquals(42.5, restored.elapsedSeconds, 1e-9)
        assertEquals(state.aiConfig.reactionDelayMs, restored.aiConfig.reactionDelayMs)
        assertEquals("l1", restored.lines[0].id)
        assertEquals(mapOf("l1" to "l2"), restored.teleports)
        assertEquals(1, restored.powerUps.size)
        assertEquals(PowerUpType.SPEED_BOOST, restored.powerUps[0].type)
        assertEquals(100_000L, restored.powerUps[0].createdNs)
        assertEquals(1, restored.activePowerUps.size)
        assertEquals(PowerUpType.GHOST_MODE, restored.activePowerUps[0].type)
        assertEquals(1_000_000L, restored.activePowerUps[0].activatedNs)
        assertTrue(restored.ghostMode)
        assertEquals(1.5, restored.speedMultiplier, 1e-9)
    }
}
