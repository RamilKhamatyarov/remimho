package ru.rkhamatyarov.mapping

import ru.rkhamatyarov.api.v1.payload.CaptureAi
import ru.rkhamatyarov.api.v1.payload.CaptureCombo
import ru.rkhamatyarov.api.v1.payload.CaptureMotion
import ru.rkhamatyarov.api.v1.payload.CaptureOneTimer
import ru.rkhamatyarov.api.v1.payload.CapturePuck
import ru.rkhamatyarov.api.v1.payload.CaptureScore
import ru.rkhamatyarov.api.v1.payload.CaptureSettings
import ru.rkhamatyarov.api.v1.payload.CaptureSpeed
import ru.rkhamatyarov.api.v1.payload.CaptureTouch
import ru.rkhamatyarov.proto.FullGameSnapshot

/** Explicit schema mapping: domain type changes cannot silently change diagnostic JSON. */
fun FullGameSnapshot.captureMotion(): CaptureMotion =
    CaptureMotion(
        puck =
            CapturePuck(
                x = puckX,
                y = puckY,
                vx = puckVx,
                vy = puckVy,
                radius = puckRadius,
                spin = puckSpin,
                spinRemainingNs = puckSpinRemainingNs,
                teleportCooldownUntilNs = puckTeleportCooldownElapsedNs,
                lastTeleportPairId = puckLastTeleportPairId.takeIf { it.isNotEmpty() },
            ),
        paddle1Y = paddle1Y,
        paddle2Y = paddle2Y,
        paddle1Velocity = paddle1Velocity,
        paddle2Velocity = paddle2Velocity,
        paddleHeight = paddleHeight,
        score = CaptureScore(scoreA, scoreB),
        elapsedSeconds = elapsedSeconds,
        ghostMode = ghostMode,
        paddleShield = paddleShield,
        touchLedger =
            touchLedgerList.map {
                CaptureTouch(
                    it.source,
                    it.ownerSide.takeIf { side -> side.isNotEmpty() },
                    it.identifier,
                    it.elapsedNs,
                    it.speedAtContact,
                )
            },
    )

fun FullGameSnapshot.captureSettings(): CaptureSettings =
    CaptureSettings(
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        speed =
            CaptureSpeed(
                baseMultiplier = speedBaseMultiplier,
                timeAccelerationRate = speedTimeAccelerationRate,
                levelAccelerationPerLine = speedLevelAccelerationPerLine,
                maxMultiplier = speedMaxMultiplier,
                rallyGrowthPerHit = speedRallyGrowthPerHit,
                smashBoost = speedSmashBoost,
                maxRallySpeed = speedMaxRallySpeed,
                maxEffectiveSpeed = speedMaxEffectiveSpeed,
            ),
        ai = CaptureAi(aiEnabled, aiReactionDelayMs, aiAimError, aiPredictionDepth, aiAggression),
        combo =
            CaptureCombo(
                enabled = combo.enabled,
                giveAndGoMultiplier = combo.giveAndGoMultiplier,
                maximumRawSpeed = combo.maximumRawSpeed,
                giveAndGoWindowNs = combo.giveAndGoWindowNs,
                superGoalThreshold = combo.superGoalThreshold,
                superGoalWindowNs = combo.superGoalWindowNs,
            ),
        oneTimer =
            CaptureOneTimer(
                minimumIncomingSpeed = oneTimerConfig.minimumIncomingSpeed,
                freshnessWindowNs = oneTimerConfig.freshnessWindowNs,
                minimumMultiplier = oneTimerConfig.minimumMultiplier,
                maximumMultiplier = oneTimerConfig.maximumMultiplier,
                fullStrengthIncomingSpeed = oneTimerConfig.fullStrengthIncomingSpeed,
                maximumRawSpeed = oneTimerConfig.maximumRawSpeed,
            ),
    )
