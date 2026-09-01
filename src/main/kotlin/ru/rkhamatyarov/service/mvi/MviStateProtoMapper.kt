package ru.rkhamatyarov.service.mvi

import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.proto.GameStateDelta

/** Converts authoritative state to a full protobuf snapshot. */
fun MviGameState.toDelta(): GameStateDelta {
    val logicalNowNs = (elapsedSeconds * NANOS_PER_SECOND).toLong()
    return GameStateDelta
        .newBuilder()
        .setPuckX(puck.x)
        .setPuckY(puck.y)
        .setPuckVx(puck.vx)
        .setPuckVy(puck.vy)
        .setPuckSpin(puck.spin)
        .setPuckSpinRemainingMs(puck.spinRemainingNs / NANOS_PER_MILLISECOND)
        .setPaddle1Y(paddle1Y)
        .setPaddle2Y(paddle2Y)
        .setScoreA(score.playerA)
        .setScoreB(score.playerB)
        .setPaused(paused)
        .setFullState(true)
        .setElapsedSeconds(elapsedSeconds)
        .addAllLines(lines.map { it.toProto() })
        .addAllPowerUps(activeFieldPowerUps(logicalNowNs).map { it.toProto() })
        .addAllActivePowerUps(activePowerUps.map { it.toProto(logicalNowNs) })
        .addAllTouchLedger(touchLedger.entries.map { it.toProto() })
        .setOneTimerConfig(oneTimerConfig.toProto())
        .build()
}

/** Restores authoritative state from a full protobuf snapshot. */
fun mviStateFromDelta(delta: GameStateDelta): MviGameState {
    val elapsedSeconds = delta.elapsedSecondsOrDefault()
    val logicalNowNs = (elapsedSeconds * NANOS_PER_SECOND).toLong()
    return MviGameState(
        puck = delta.toPuck(),
        score = delta.toScore(),
        paddle1Y = delta.paddle1Y.takeIf { delta.hasPaddle1Y() } ?: DEFAULT_PADDLE_Y,
        paddle2Y = delta.paddle2Y.takeIf { delta.hasPaddle2Y() } ?: DEFAULT_PADDLE_Y,
        paused = delta.paused.takeIf { delta.hasPaused() } ?: false,
        elapsedSeconds = elapsedSeconds,
        lines = delta.linesList.map { it.toDomain() },
        powerUps = delta.powerUpsList.mapNotNull { it.toDomain(logicalNowNs) },
        activePowerUps = delta.activePowerUpsList.mapNotNull { it.toDomain(logicalNowNs) },
        touchLedger = delta.toTouchLedger(),
        oneTimerConfig = delta.oneTimerConfigOrDefault(),
    )
}

private fun MviGameState.activeFieldPowerUps(nowNs: Long): List<MviPowerUp> =
    powerUps.filter {
        nowNs - it.createdNs <= it.lifetimeNs
    }

private fun GameStateDelta.elapsedSecondsOrDefault(): Double = if (hasElapsedSeconds()) elapsedSeconds else 0.0

private fun GameStateDelta.toPuck(): MviPuck =
    MviPuck(
        x = puckX.takeIf { hasPuckX() } ?: DEFAULT_PUCK_X,
        y = puckY.takeIf { hasPuckY() } ?: DEFAULT_PUCK_Y,
        vx = puckVx.takeIf { hasPuckVx() } ?: DEFAULT_PUCK_VX,
        vy = puckVy.takeIf { hasPuckVy() } ?: DEFAULT_PUCK_VY,
        spin = puckSpin.takeIf { hasPuckSpin() } ?: 0.0,
        spinRemainingNs =
            if (hasPuckSpinRemainingMs()) {
                puckSpinRemainingMs * NANOS_PER_MILLISECOND
            } else {
                0L
            },
    )

private fun GameStateDelta.toScore(): MviScore =
    MviScore(
        playerA = scoreA.takeIf { hasScoreA() } ?: 0,
        playerB = scoreB.takeIf { hasScoreB() } ?: 0,
    )

private fun GameStateDelta.toTouchLedger(): TouchLedger =
    TouchLedger(
        touchLedgerList
            .mapNotNull { it.toDomainOrNull() }
            .takeLast(TouchLedger.MAX_ENTRIES),
    )

private fun GameStateDelta.oneTimerConfigOrDefault(): OneTimerConfig =
    if (hasOneTimerConfig()) oneTimerConfig.toDomain() else OneTimerConfig()

private fun ru.rkhamatyarov.proto.Line.toDomain(): MviLine =
    MviLine(
        id = id,
        points = pointsList.map { MviPoint(it.x, it.y) },
        width = width,
        ownerSide = ownerSide.takeIf { hasOwnerSide() }?.toPaddleSideOrNull(),
    )

private fun ru.rkhamatyarov.proto.PowerUp.toDomain(logicalNowNs: Long): MviPowerUp? =
    runCatching {
        MviPowerUp(
            id = id.takeIf { it.isNotBlank() } ?: hashCode().toString(),
            x = x,
            y = y,
            type = PowerUpType.valueOf(type),
            createdNs = logicalNowNs,
        )
    }.getOrNull()

private fun ru.rkhamatyarov.proto.ActivePowerUp.toDomain(logicalNowNs: Long): MviActivePowerUp? =
    runCatching {
        val powerUpType = PowerUpType.valueOf(type)
        val durationNs = PowerUpType.getDuration(powerUpType)
        val remainingNs = remainingSeconds * NANOS_PER_SECOND
        MviActivePowerUp(
            type = powerUpType,
            activatedNs = logicalNowNs - (durationNs - remainingNs).coerceAtLeast(0L),
            durationNs = durationNs,
        )
    }.getOrNull()

private fun MviPowerUp.toProto(): ru.rkhamatyarov.proto.PowerUp =
    ru.rkhamatyarov.proto.PowerUp
        .newBuilder()
        .setX(x)
        .setY(y)
        .setRadius(radius)
        .setType(type.name)
        .setEmoji(type.emoji)
        .setColor(PowerUpType.getColorCode(type))
        .setId(id)
        .build()

private fun MviActivePowerUp.toProto(nowNs: Long): ru.rkhamatyarov.proto.ActivePowerUp =
    ru.rkhamatyarov.proto.ActivePowerUp
        .newBuilder()
        .setType(type.name)
        .setEmoji(type.emoji)
        .setRemainingSeconds(remainingSeconds(nowNs))
        .build()

private fun MviLine.toProto(): ru.rkhamatyarov.proto.Line {
    val builder =
        ru.rkhamatyarov.proto.Line
            .newBuilder()
            .setId(id)
            .setWidth(width)
            .setIsAnimating(false)
            .addAllPoints(points.map { it.toProto() })
    ownerSide?.let { builder.ownerSide = it.name }
    return builder.build()
}

private fun MviPoint.toProto(): ru.rkhamatyarov.proto.Point =
    ru.rkhamatyarov.proto.Point
        .newBuilder()
        .setX(x)
        .setY(y)
        .build()

private fun PuckTouch.toProto(): ru.rkhamatyarov.proto.TouchLedgerEntry {
    val builder =
        ru.rkhamatyarov.proto.TouchLedgerEntry
            .newBuilder()
            .setSource(source.name)
            .setIdentifier(identifier)
            .setElapsedNs(elapsedNs)
            .setSpeedAtContact(speedAtContact)
    ownerSide?.let { builder.ownerSide = it.name }
    return builder.build()
}

private fun OneTimerConfig.toProto(): ru.rkhamatyarov.proto.OneTimerConfig =
    ru.rkhamatyarov.proto.OneTimerConfig
        .newBuilder()
        .setMinimumIncomingSpeed(minimumIncomingSpeed)
        .setFreshnessWindowNs(freshnessWindowNs)
        .setMinimumMultiplier(minimumMultiplier)
        .setMaximumMultiplier(maximumMultiplier)
        .setFullStrengthIncomingSpeed(fullStrengthIncomingSpeed)
        .setMaximumRawSpeed(maximumRawSpeed)
        .build()

private fun ru.rkhamatyarov.proto.TouchLedgerEntry.toDomainOrNull(): PuckTouch? =
    runCatching {
        PuckTouch(
            source = TouchSource.valueOf(source),
            ownerSide = ownerSide.takeIf { hasOwnerSide() }?.toPaddleSideOrNull(),
            identifier = identifier,
            elapsedNs = elapsedNs,
            speedAtContact = speedAtContact,
        )
    }.getOrNull()

private fun ru.rkhamatyarov.proto.OneTimerConfig.toDomain(): OneTimerConfig =
    OneTimerConfig(
        minimumIncomingSpeed = minimumIncomingSpeed,
        freshnessWindowNs = freshnessWindowNs,
        minimumMultiplier = minimumMultiplier,
        maximumMultiplier = maximumMultiplier,
        fullStrengthIncomingSpeed = fullStrengthIncomingSpeed,
        maximumRawSpeed = maximumRawSpeed,
    )

private fun String.toPaddleSideOrNull(): PaddleSide? = PaddleSide.entries.firstOrNull { it.name == uppercase() }

private const val DEFAULT_PUCK_X = 400.0
private const val DEFAULT_PUCK_Y = 300.0
private const val DEFAULT_PUCK_VX = 300.0
private const val DEFAULT_PUCK_VY = 200.0
private const val DEFAULT_PADDLE_Y = 250.0
private const val NANOS_PER_SECOND = 1_000_000_000L
private const val NANOS_PER_MILLISECOND = 1_000_000L
