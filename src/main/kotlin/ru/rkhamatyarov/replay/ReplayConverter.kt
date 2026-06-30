package ru.rkhamatyarov.replay

import ru.rkhamatyarov.model.AiOpponentConfig
import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.model.SpeedConfig
import ru.rkhamatyarov.proto.FullGameSnapshot
import ru.rkhamatyarov.proto.ReplayApplyAiConfig
import ru.rkhamatyarov.proto.ReplayApplySpeedConfig
import ru.rkhamatyarov.proto.ReplayApplyTeleports
import ru.rkhamatyarov.proto.ReplayClearLines
import ru.rkhamatyarov.proto.ReplayCommitLine
import ru.rkhamatyarov.proto.ReplayEraseLine
import ru.rkhamatyarov.proto.ReplayIntent
import ru.rkhamatyarov.proto.ReplayMovePaddle
import ru.rkhamatyarov.proto.ReplayReset
import ru.rkhamatyarov.proto.ReplaySpawnPowerUp
import ru.rkhamatyarov.proto.ReplayTick
import ru.rkhamatyarov.proto.ReplayTogglePause
import ru.rkhamatyarov.proto.SnapshotActivePowerUp
import ru.rkhamatyarov.proto.SnapshotLine
import ru.rkhamatyarov.proto.SnapshotPoint
import ru.rkhamatyarov.proto.SnapshotPowerUp
import ru.rkhamatyarov.proto.TeleportEntry
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.GameIntent
import ru.rkhamatyarov.service.mvi.MviActivePowerUp
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviLine
import ru.rkhamatyarov.service.mvi.MviPoint
import ru.rkhamatyarov.service.mvi.MviPowerUp
import ru.rkhamatyarov.service.mvi.MviPuck
import ru.rkhamatyarov.service.mvi.MviScore

object ReplayConverter {
    fun toProto(intent: GameIntent.Reliable): ReplayIntent? {
        val elapsedNs =
            when (val a = intent.action) {
                is GameAction.Tick -> a.elapsedNs
                else -> 0L
            }
        val builder = ReplayIntent.newBuilder().setElapsedNs(elapsedNs)
        when (val action = intent.action) {
            is GameAction.Tick -> {
                builder.tick = ReplayTick.newBuilder().setDeltaSeconds(action.deltaSeconds).build()
            }

            is GameAction.MovePaddle -> {
                builder.movePaddle = ReplayMovePaddle.newBuilder().setY(action.y).build()
            }

            GameAction.TogglePause -> {
                builder.togglePause = ReplayTogglePause.getDefaultInstance()
            }

            GameAction.Reset -> {
                builder.reset = ReplayReset.getDefaultInstance()
            }

            is GameAction.CommitLine -> {
                builder.commitLine = action.line.toReplayProto()
            }

            is GameAction.EraseLine -> {
                builder.eraseLine = ReplayEraseLine.newBuilder().setLineId(action.lineId).build()
            }

            GameAction.ClearLines -> {
                builder.clearLines = ReplayClearLines.getDefaultInstance()
            }

            is GameAction.RestoreSnapshot -> {
                return null
            }

            is GameAction.ApplyTeleports -> {
                builder.applyTeleports =
                    ReplayApplyTeleports
                        .newBuilder()
                        .addAllPortals(action.portals.map { (k, v) -> teleportEntry(k, v) })
                        .build()
            }

            is GameAction.SpawnPowerUp -> {
                builder.spawnPowerUp = action.powerUp.toReplayProto()
            }

            is GameAction.ApplySpeedConfig -> {
                builder.applySpeedConfig =
                    ReplayApplySpeedConfig
                        .newBuilder()
                        .setBaseMultiplier(action.config.baseMultiplier)
                        .setTimeAccelerationRate(action.config.timeAccelerationRate)
                        .setLevelAccelerationPerLine(action.config.levelAccelerationPerLine)
                        .setMaxMultiplier(action.config.maxMultiplier)
                        .build()
            }

            is GameAction.ApplyAiConfig -> {
                builder.applyAiConfig =
                    ReplayApplyAiConfig
                        .newBuilder()
                        .setEnabled(action.config.enabled)
                        .setReactionDelayMs(action.config.reactionDelayMs)
                        .setMaxSpeed(action.config.maxSpeed)
                        .setTrackingError(action.config.trackingError)
                        .setReactZoneRatio(action.config.reactZoneRatio)
                        .build()
            }
        }
        return builder.build()
    }

    fun fromProto(proto: ReplayIntent): Pair<GameIntent.Reliable, Long> {
        val action =
            when (proto.payloadCase) {
                ReplayIntent.PayloadCase.TICK -> {
                    GameAction.Tick(proto.tick.deltaSeconds, proto.elapsedNs)
                }

                ReplayIntent.PayloadCase.MOVE_PADDLE -> {
                    GameAction.MovePaddle(proto.movePaddle.y)
                }

                ReplayIntent.PayloadCase.TOGGLE_PAUSE -> {
                    GameAction.TogglePause
                }

                ReplayIntent.PayloadCase.RESET -> {
                    GameAction.Reset
                }

                ReplayIntent.PayloadCase.COMMIT_LINE -> {
                    GameAction.CommitLine(proto.commitLine.toMviLine())
                }

                ReplayIntent.PayloadCase.ERASE_LINE -> {
                    GameAction.EraseLine(proto.eraseLine.lineId)
                }

                ReplayIntent.PayloadCase.CLEAR_LINES -> {
                    GameAction.ClearLines
                }

                ReplayIntent.PayloadCase.APPLY_TELEPORTS -> {
                    GameAction.ApplyTeleports(
                        proto.applyTeleports.portalsList.associate { it.lineId to it.partnerLineId },
                    )
                }

                ReplayIntent.PayloadCase.SPAWN_POWER_UP -> {
                    GameAction.SpawnPowerUp(proto.spawnPowerUp.toMviPowerUp())
                }

                ReplayIntent.PayloadCase.APPLY_SPEED_CONFIG -> {
                    GameAction.ApplySpeedConfig(
                        SpeedConfig(
                            baseMultiplier = proto.applySpeedConfig.baseMultiplier,
                            timeAccelerationRate = proto.applySpeedConfig.timeAccelerationRate,
                            levelAccelerationPerLine = proto.applySpeedConfig.levelAccelerationPerLine,
                            maxMultiplier = proto.applySpeedConfig.maxMultiplier,
                        ),
                    )
                }

                ReplayIntent.PayloadCase.APPLY_AI_CONFIG -> {
                    GameAction.ApplyAiConfig(
                        AiOpponentConfig(
                            enabled = proto.applyAiConfig.enabled,
                            reactionDelayMs = proto.applyAiConfig.reactionDelayMs,
                            maxSpeed = proto.applyAiConfig.maxSpeed,
                            trackingError = proto.applyAiConfig.trackingError,
                            reactZoneRatio = proto.applyAiConfig.reactZoneRatio,
                        ),
                    )
                }

                else -> {
                    throw IllegalArgumentException("Unknown payload case: ${proto.payloadCase}")
                }
            }
        return GameIntent.Reliable(action) to proto.elapsedNs
    }

    fun stateToSnapshot(state: MviGameState): FullGameSnapshot =
        FullGameSnapshot
            .newBuilder()
            .setPuckX(state.puck.x)
            .setPuckY(state.puck.y)
            .setPuckVx(state.puck.vx)
            .setPuckVy(state.puck.vy)
            .setPuckRadius(state.puck.radius)
            .setPuckTeleportCooldownElapsedNs(state.puck.teleportCooldownUntilNs)
            .setPuckLastTeleportPairId(state.puck.lastTeleportPairId ?: "")
            .setScoreA(state.score.playerA)
            .setScoreB(state.score.playerB)
            .setPaddle1Y(state.paddle1Y)
            .setPaddle2Y(state.paddle2Y)
            .setPaused(state.paused)
            .setCanvasWidth(state.canvasWidth)
            .setCanvasHeight(state.canvasHeight)
            .setPaddleHeight(state.paddleHeight)
            .addAllLines(state.lines.map { it.toSnapshotProto() })
            .addAllTeleports(state.teleports.map { (k, v) -> teleportEntry(k, v) })
            .setSpeedBaseMultiplier(state.speedConfig.baseMultiplier)
            .setSpeedTimeAccelerationRate(state.speedConfig.timeAccelerationRate)
            .setSpeedLevelAccelerationPerLine(state.speedConfig.levelAccelerationPerLine)
            .setSpeedMaxMultiplier(state.speedConfig.maxMultiplier)
            .setElapsedSeconds(state.elapsedSeconds)
            .setAiEnabled(state.aiConfig.enabled)
            .setAiReactionDelayMs(state.aiConfig.reactionDelayMs)
            .setAiMaxSpeed(state.aiConfig.maxSpeed)
            .setAiTrackingError(state.aiConfig.trackingError)
            .setAiReactZoneRatio(state.aiConfig.reactZoneRatio)
            .setAiSmoothedPuckY(state.aiSmoothedPuckY)
            .addAllPowerUps(state.powerUps.map { it.toSnapshotProto() })
            .addAllActivePowerUps(state.activePowerUps.map { it.toSnapshotProto() })
            .setSpeedMultiplier(state.speedMultiplier)
            .setGhostMode(state.ghostMode)
            .setPaddleShield(state.paddleShield)
            .build()

    fun snapshotToState(proto: FullGameSnapshot): MviGameState =
        MviGameState(
            puck =
                MviPuck(
                    x = proto.puckX,
                    y = proto.puckY,
                    vx = proto.puckVx,
                    vy = proto.puckVy,
                    radius = if (proto.puckRadius > 0.0) proto.puckRadius else 10.0,
                    teleportCooldownUntilNs = proto.puckTeleportCooldownElapsedNs,
                    lastTeleportPairId = proto.puckLastTeleportPairId.takeIf { it.isNotEmpty() },
                ),
            score = MviScore(proto.scoreA, proto.scoreB),
            paddle1Y = proto.paddle1Y,
            paddle2Y = proto.paddle2Y,
            paused = proto.paused,
            canvasWidth = if (proto.canvasWidth > 0.0) proto.canvasWidth else 800.0,
            canvasHeight = if (proto.canvasHeight > 0.0) proto.canvasHeight else 600.0,
            paddleHeight = if (proto.paddleHeight > 0.0) proto.paddleHeight else 100.0,
            lines = proto.linesList.map { it.toMviLine() },
            teleports = proto.teleportsList.associate { it.lineId to it.partnerLineId },
            speedConfig =
                SpeedConfig(
                    baseMultiplier = if (proto.speedBaseMultiplier > 0.0) proto.speedBaseMultiplier else 1.0,
                    timeAccelerationRate = proto.speedTimeAccelerationRate,
                    levelAccelerationPerLine = proto.speedLevelAccelerationPerLine,
                    maxMultiplier = if (proto.speedMaxMultiplier > 0.0) proto.speedMaxMultiplier else 3.0,
                ),
            elapsedSeconds = proto.elapsedSeconds,
            aiConfig =
                AiOpponentConfig(
                    enabled = proto.aiEnabled,
                    reactionDelayMs = if (proto.aiReactionDelayMs > 0L) proto.aiReactionDelayMs else 180L,
                    maxSpeed = if (proto.aiMaxSpeed > 0.0) proto.aiMaxSpeed else 180.0,
                    trackingError = proto.aiTrackingError,
                    reactZoneRatio = if (proto.aiReactZoneRatio > 0.0) proto.aiReactZoneRatio else 0.7,
                ),
            aiSmoothedPuckY = if (proto.aiSmoothedPuckY > 0.0) proto.aiSmoothedPuckY else 300.0,
            powerUps = proto.powerUpsList.mapNotNull { it.toMviPowerUp() },
            activePowerUps = proto.activePowerUpsList.mapNotNull { it.toMviActivePowerUp() },
            speedMultiplier = if (proto.speedMultiplier > 0.0) proto.speedMultiplier else 1.0,
            ghostMode = proto.ghostMode,
            paddleShield = proto.paddleShield,
        )

    private fun teleportEntry(
        lineId: String,
        partnerLineId: String,
    ): TeleportEntry =
        TeleportEntry
            .newBuilder()
            .setLineId(lineId)
            .setPartnerLineId(partnerLineId)
            .build()

    private fun MviLine.toSnapshotProto(): SnapshotLine =
        SnapshotLine
            .newBuilder()
            .setId(id)
            .setWidth(width)
            .addAllPoints(
                points.map {
                    SnapshotPoint
                        .newBuilder()
                        .setX(it.x)
                        .setY(it.y)
                        .build()
                },
            ).build()

    private fun MviLine.toReplayProto(): ReplayCommitLine =
        ReplayCommitLine
            .newBuilder()
            .setId(id)
            .setWidth(width)
            .addAllPoints(
                points.map {
                    SnapshotPoint
                        .newBuilder()
                        .setX(it.x)
                        .setY(it.y)
                        .build()
                },
            ).build()

    private fun SnapshotLine.toMviLine(): MviLine =
        MviLine(
            id = id,
            points = pointsList.map { MviPoint(it.x, it.y) },
            width = width,
        )

    private fun ReplayCommitLine.toMviLine(): MviLine =
        MviLine(
            id = id,
            points = pointsList.map { MviPoint(it.x, it.y) },
            width = width,
        )

    private fun MviPowerUp.toSnapshotProto(): SnapshotPowerUp =
        SnapshotPowerUp
            .newBuilder()
            .setId(id)
            .setX(x)
            .setY(y)
            .setType(type.name)
            .setCreatedElapsedNs(createdNs)
            .setLifetimeNs(lifetimeNs)
            .setRadius(radius)
            .build()

    private fun MviPowerUp.toReplayProto(): ReplaySpawnPowerUp =
        ReplaySpawnPowerUp
            .newBuilder()
            .setId(id)
            .setX(x)
            .setY(y)
            .setType(type.name)
            .setCreatedElapsedNs(createdNs)
            .setLifetimeNs(lifetimeNs)
            .setRadius(radius)
            .build()

    private fun SnapshotPowerUp.toMviPowerUp(): MviPowerUp? =
        runCatching {
            MviPowerUp(
                id = id,
                x = x,
                y = y,
                type = PowerUpType.valueOf(type),
                createdNs = createdElapsedNs,
                lifetimeNs = if (lifetimeNs > 0L) lifetimeNs else 15_000_000_000L,
                radius = if (radius > 0.0) radius else 15.0,
            )
        }.getOrNull()

    private fun ReplaySpawnPowerUp.toMviPowerUp(): MviPowerUp =
        MviPowerUp(
            id = id,
            x = x,
            y = y,
            type = PowerUpType.valueOf(type),
            createdNs = createdElapsedNs,
            lifetimeNs = if (lifetimeNs > 0L) lifetimeNs else 15_000_000_000L,
            radius = if (radius > 0.0) radius else 15.0,
        )

    private fun MviActivePowerUp.toSnapshotProto(): SnapshotActivePowerUp =
        SnapshotActivePowerUp
            .newBuilder()
            .setType(type.name)
            .setActivatedElapsedNs(activatedNs)
            .setDurationNs(durationNs)
            .build()

    private fun SnapshotActivePowerUp.toMviActivePowerUp(): MviActivePowerUp? =
        runCatching {
            MviActivePowerUp(
                type = PowerUpType.valueOf(type),
                activatedNs = activatedElapsedNs,
                durationNs = durationNs,
            )
        }.getOrNull()
}
