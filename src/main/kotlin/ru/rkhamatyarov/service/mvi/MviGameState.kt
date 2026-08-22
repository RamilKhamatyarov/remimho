package ru.rkhamatyarov.service.mvi

import ru.rkhamatyarov.model.AiOpponentConfig
import ru.rkhamatyarov.model.PowerUpType
import ru.rkhamatyarov.model.SpeedConfig
import ru.rkhamatyarov.proto.GameStateDelta
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class MviPuck(
    val x: Double = 400.0,
    val y: Double = 300.0,
    val vx: Double = 300.0,
    val vy: Double = 200.0,
    val radius: Double = 10.0,
    val spin: Double = 0.0,
    val spinRemainingNs: Long = 0L,
    val teleportCooldownUntilNs: Long = 0L,
    val lastTeleportPairId: String? = null,
)

data class MviScore(
    val playerA: Int = 0,
    val playerB: Int = 0,
)

data class MviPoint(
    val x: Double,
    val y: Double,
)

data class MviLine(
    val id: String,
    val points: List<MviPoint>,
    val width: Double = 5.0,
)

data class MviPowerUp(
    val id: String,
    val x: Double,
    val y: Double,
    val type: PowerUpType,
    val createdNs: Long,
    val lifetimeNs: Long = 15_000_000_000L,
    val radius: Double = 15.0,
)

data class MviActivePowerUp(
    val type: PowerUpType,
    val activatedNs: Long,
    val durationNs: Long,
) {
    fun isExpired(nowNs: Long): Boolean = durationNs > 0L && nowNs - activatedNs > durationNs

    fun remainingSeconds(nowNs: Long): Long = ((durationNs - (nowNs - activatedNs)).coerceAtLeast(0L) / 1_000_000_000L)
}

data class MviGameState(
    val puck: MviPuck = MviPuck(),
    val score: MviScore = MviScore(),
    val paddle1Y: Double = 250.0,
    val paddle2Y: Double = 250.0,
    val paddle1Velocity: Double = 0.0,
    val paddle2Velocity: Double = 0.0,
    val paused: Boolean = false,
    val canvasWidth: Double = 800.0,
    val canvasHeight: Double = 600.0,
    val paddleHeight: Double = 100.0,
    val lines: List<MviLine> = emptyList(),
    val teleports: Map<String, String> = emptyMap(),
    val speedConfig: SpeedConfig = SpeedConfig(),
    val elapsedSeconds: Double = 0.0,
    val aiConfig: AiOpponentConfig = AiOpponentConfig(),
    val powerUps: List<MviPowerUp> = emptyList(),
    val activePowerUps: List<MviActivePowerUp> = emptyList(),
    val speedMultiplier: Double = 1.0,
    val ghostMode: Boolean = false,
    val paddleShield: Boolean = false,
) {
    fun toDelta(): GameStateDelta {
        val logicalNowNs = (elapsedSeconds * 1_000_000_000L).toLong()
        return GameStateDelta
            .newBuilder()
            .setPuckX(puck.x)
            .setPuckY(puck.y)
            .setPuckVx(puck.vx)
            .setPuckVy(puck.vy)
            .setPuckSpin(puck.spin)
            .setPuckSpinRemainingMs(puck.spinRemainingNs / 1_000_000L)
            .setPaddle1Y(paddle1Y)
            .setPaddle2Y(paddle2Y)
            .setScoreA(score.playerA)
            .setScoreB(score.playerB)
            .setPaused(paused)
            .setFullState(true)
            .setElapsedSeconds(elapsedSeconds)
            .addAllLines(lines.map { it.toProto() })
            .addAllPowerUps(powerUps.filter { logicalNowNs - it.createdNs <= it.lifetimeNs }.map { it.toProto() })
            .addAllActivePowerUps(activePowerUps.map { it.toProto(logicalNowNs) })
            .build()
    }
}

fun mviStateFromDelta(delta: GameStateDelta): MviGameState {
    val logicalNowNs = (if (delta.hasElapsedSeconds()) delta.elapsedSeconds else 0.0).let { (it * 1_000_000_000L).toLong() }
    return MviGameState(
        puck =
            MviPuck(
                x = if (delta.hasPuckX()) delta.puckX else 400.0,
                y = if (delta.hasPuckY()) delta.puckY else 300.0,
                vx = if (delta.hasPuckVx()) delta.puckVx else 300.0,
                vy = if (delta.hasPuckVy()) delta.puckVy else 200.0,
                spin = if (delta.hasPuckSpin()) delta.puckSpin else 0.0,
                spinRemainingNs = if (delta.hasPuckSpinRemainingMs()) delta.puckSpinRemainingMs * 1_000_000L else 0L,
            ),
        score =
            MviScore(
                playerA = if (delta.hasScoreA()) delta.scoreA else 0,
                playerB = if (delta.hasScoreB()) delta.scoreB else 0,
            ),
        paddle1Y = if (delta.hasPaddle1Y()) delta.paddle1Y else 250.0,
        paddle2Y = if (delta.hasPaddle2Y()) delta.paddle2Y else 250.0,
        paused = if (delta.hasPaused()) delta.paused else false,
        elapsedSeconds = if (delta.hasElapsedSeconds()) delta.elapsedSeconds else 0.0,
        lines =
            delta.linesList.map { protoLine ->
                MviLine(
                    id = protoLine.id,
                    points = protoLine.pointsList.map { MviPoint(it.x, it.y) },
                    width = protoLine.width,
                )
            },
        powerUps =
            delta.powerUpsList.mapNotNull { pu ->
                runCatching {
                    MviPowerUp(
                        id = pu.hashCode().toString(),
                        x = pu.x,
                        y = pu.y,
                        type = PowerUpType.valueOf(pu.type),
                        createdNs = logicalNowNs,
                    )
                }.getOrNull()
            },
        activePowerUps =
            delta.activePowerUpsList.mapNotNull { apu ->
                runCatching {
                    val type = PowerUpType.valueOf(apu.type)
                    val durationNs = PowerUpType.getDuration(type)
                    val remainingNs = apu.remainingSeconds * 1_000_000_000L
                    MviActivePowerUp(
                        type = type,
                        activatedNs = logicalNowNs - (durationNs - remainingNs).coerceAtLeast(0L),
                        durationNs = durationNs,
                    )
                }.getOrNull()
            },
    )
}

fun reduce(
    state: MviGameState,
    action: GameAction,
): MviGameState =
    when (action) {
        is GameAction.Tick -> {
            reduceTick(state, action.deltaSeconds, action.elapsedNs, action.turboSpeedMultiplier)
        }

        is GameAction.MovePaddle -> {
            val y = action.y.coerceIn(0.0, state.canvasHeight - state.paddleHeight)
            when (action.side) {
                PaddleSide.A -> state.copy(paddle1Y = y, paddle1Velocity = y - state.paddle1Y)
                PaddleSide.B -> state.copy(paddle2Y = y, paddle2Velocity = y - state.paddle2Y)
            }
        }

        is GameAction.ActivateTurbo -> {
            state
        }

        GameAction.TogglePause -> {
            state.copy(paused = !state.paused)
        }

        GameAction.Reset -> {
            state.copy(
                puck =
                    state.puck.copy(
                        x = state.canvasWidth / 2,
                        y = state.canvasHeight / 2,
                        vx = if (state.puck.vx > 0) 300.0 else -300.0,
                        vy = 200.0,
                        spin = 0.0,
                        spinRemainingNs = 0L,
                        teleportCooldownUntilNs = 0L,
                        lastTeleportPairId = null,
                    ),
                paused = false,
                lines = emptyList(),
                elapsedSeconds = 0.0,
                powerUps = emptyList(),
                activePowerUps = emptyList(),
                speedMultiplier = 1.0,
                ghostMode = false,
                paddleShield = false,
            )
        }

        is GameAction.CommitLine -> {
            if (action.line.id.isBlank()) {
                state
            } else {
                state.copy(lines = state.lines.filterNot { it.id == action.line.id } + action.line)
            }
        }

        is GameAction.EraseLine -> {
            state.copy(lines = state.lines.filterNot { it.id == action.lineId })
        }

        GameAction.ClearLines -> {
            state.copy(lines = emptyList())
        }

        is GameAction.RestoreSnapshot -> {
            action.state
        }

        is GameAction.ApplyTeleports -> {
            state.copy(teleports = action.portals)
        }

        is GameAction.SpawnPowerUp -> {
            state.copy(powerUps = state.powerUps + action.powerUp)
        }

        is GameAction.ApplySpeedConfig -> {
            state.copy(speedConfig = action.config)
        }

        is GameAction.ApplyAiConfig -> {
            state.copy(aiConfig = action.config)
        }
    }

private fun reduceTick(
    state: MviGameState,
    deltaSeconds: Double,
    elapsedNs: Long,
    turboSpeedMultiplier: Double,
): MviGameState {
    check(deltaSeconds.isFinite()) { "Tick delta must be finite" }
    if (state.paused || deltaSeconds <= 0.0) return state

    val progressiveSpeed = computeProgressiveSpeed(state)
    val effectiveTurboSpeed = if (turboSpeedMultiplier.isFinite() && turboSpeedMultiplier > 0.0) turboSpeedMultiplier else 1.0
    val effectiveSpeed = state.speedMultiplier * progressiveSpeed * effectiveTurboSpeed

    var puck = applySpinCurve(state.puck, deltaSeconds)
    puck =
        puck.copy(
            x = puck.x + puck.vx * effectiveSpeed * deltaSeconds,
            y = puck.y + puck.vy * effectiveSpeed * deltaSeconds,
        )

    if (puck.y - puck.radius <= 0.0) {
        puck = puck.copy(y = puck.radius, vy = abs(puck.vy), spin = puck.spin * WALL_SPIN_RETENTION)
    } else if (puck.y + puck.radius >= state.canvasHeight) {
        puck = puck.copy(y = state.canvasHeight - puck.radius, vy = -abs(puck.vy), spin = puck.spin * WALL_SPIN_RETENTION)
    }

    if (!state.ghostMode) {
        val leftPaddleRight = PADDLE_WIDTH
        val rightPaddleLeft = state.canvasWidth - PADDLE_WIDTH

        if (puck.vx < 0 &&
            puck.x - puck.radius <= leftPaddleRight &&
            puck.x + puck.radius >= 0.0 &&
            overlapsPaddleY(puck, state.paddle1Y, state.paddleHeight)
        ) {
            MviDomainEvents.record(MviDomainEvent.PaddleHit(PaddleSide.A))
            puck =
                redirectFromPaddle(
                    puck = puck,
                    paddleY = state.paddle1Y,
                    paddleHeight = state.paddleHeight,
                    paddleVelocity = state.paddle1Velocity,
                    horizontalDirection = 1.0,
                    x = leftPaddleRight + puck.radius,
                )
        }
        if (puck.vx > 0 &&
            puck.x + puck.radius >= rightPaddleLeft &&
            puck.x - puck.radius <= state.canvasWidth &&
            overlapsPaddleY(puck, state.paddle2Y, state.paddleHeight)
        ) {
            MviDomainEvents.record(MviDomainEvent.PaddleHit(PaddleSide.B))
            puck =
                redirectFromPaddle(
                    puck = puck,
                    paddleY = state.paddle2Y,
                    paddleHeight = state.paddleHeight,
                    paddleVelocity = state.paddle2Velocity,
                    horizontalDirection = -1.0,
                    x = rightPaddleLeft - puck.radius,
                )
        }
        if (state.paddleShield) {
            if (puck.vx < 0 && puck.x - puck.radius <= 0.0) {
                puck = puck.copy(x = puck.radius, vx = abs(puck.vx), spin = puck.spin * WALL_SPIN_RETENTION)
            }
        }
    }

    puck = applyLineCollisions(puck, state.lines, state.teleports, elapsedNs)

    val score =
        when {
            puck.x - puck.radius <= 0.0 -> state.score.copy(playerB = state.score.playerB + 1)
            puck.x + puck.radius >= state.canvasWidth -> state.score.copy(playerA = state.score.playerA + 1)
            else -> state.score
        }
    if (score != state.score) {
        puck =
            puck.copy(
                x = state.canvasWidth / 2,
                y = state.canvasHeight / 2,
                vx = if (puck.vx > 0) 300.0 else -300.0,
                vy = 200.0,
                spin = 0.0,
                spinRemainingNs = 0L,
                teleportCooldownUntilNs = 0L,
                lastTeleportPairId = null,
            )
    }

    val activeAfterExpiry = state.activePowerUps.filter { !it.isExpired(elapsedNs) }
    val validFieldPowerUps = state.powerUps.filter { elapsedNs - it.createdNs <= it.lifetimeNs }

    val (remainingFieldPowerUps, justCollected) =
        validFieldPowerUps.partition { pu ->
            hypot(puck.x - pu.x, puck.y - pu.y) >= pu.radius + puck.radius
        }
    val newActivePowerUps =
        activeAfterExpiry +
            justCollected.map { pu ->
                MviActivePowerUp(pu.type, elapsedNs, PowerUpType.getDuration(pu.type))
            }

    val hasMagnet = newActivePowerUps.any { it.type == PowerUpType.MAGNET_BALL }
    if (hasMagnet) {
        puck = applyMagnetEffect(puck, state, deltaSeconds)
    }

    val newSpeedMultiplier = if (newActivePowerUps.any { it.type == PowerUpType.SPEED_BOOST }) 1.5 else 1.0
    val newGhostMode = newActivePowerUps.any { it.type == PowerUpType.GHOST_MODE }
    val newPaddleShield = newActivePowerUps.any { it.type == PowerUpType.PADDLE_SHIELD }

    return state.copy(
        puck = puck,
        score = score,
        paddle1Velocity = 0.0,
        paddle2Velocity = 0.0,
        elapsedSeconds = state.elapsedSeconds + deltaSeconds,
        powerUps = remainingFieldPowerUps,
        activePowerUps = newActivePowerUps,
        speedMultiplier = newSpeedMultiplier,
        ghostMode = newGhostMode,
        paddleShield = newPaddleShield,
    )
}

private fun applySpinCurve(
    puck: MviPuck,
    deltaSeconds: Double,
): MviPuck {
    if (puck.spinRemainingNs <= 0L || !puck.spin.isFinite() || abs(puck.spin) < MIN_SPIN) {
        return puck.copy(spin = 0.0, spinRemainingNs = 0L)
    }

    val elapsedNs = (deltaSeconds * 1_000_000_000.0).toLong().coerceAtLeast(0L)
    val remainingNs = (puck.spinRemainingNs - elapsedNs).coerceAtLeast(0L)
    if (remainingNs == 0L) return puck.copy(spin = 0.0, spinRemainingNs = 0L)

    val nextSpin = puck.spin * SPIN_DECAY_PER_TICK
    return puck.copy(
        vy = puck.vy + nextSpin * SPIN_CURVE_ACCELERATION * deltaSeconds,
        spin = nextSpin,
        spinRemainingNs = remainingNs,
    )
}

private fun redirectFromPaddle(
    puck: MviPuck,
    paddleY: Double,
    paddleHeight: Double,
    paddleVelocity: Double,
    horizontalDirection: Double,
    x: Double,
): MviPuck {
    val speed = hypot(puck.vx, puck.vy).coerceAtLeast(MIN_PUCK_SPEED)
    val paddleCenter = paddleY + paddleHeight / 2.0
    val contactOffset = ((puck.y - paddleCenter) / (paddleHeight / 2.0)).coerceIn(-1.0, 1.0)
    val movementInfluence = (paddleVelocity / PADDLE_MOVEMENT_NORMALIZER).coerceIn(-1.0, 1.0)
    val angle =
        (contactOffset * MAX_PADDLE_BOUNCE_ANGLE + movementInfluence * PADDLE_MOVEMENT_ANGLE_INFLUENCE)
            .coerceIn(-MAX_PADDLE_BOUNCE_ANGLE, MAX_PADDLE_BOUNCE_ANGLE)
    val spin = (paddleVelocity / PADDLE_SPIN_NORMALIZER).coerceIn(-MAX_SPIN, MAX_SPIN)
    val hasSpin = abs(spin) >= MIN_SPIN

    return puck.copy(
        x = x,
        vx = horizontalDirection * cos(angle) * speed,
        vy = sin(angle) * speed,
        spin = if (hasSpin) spin else 0.0,
        spinRemainingNs = if (hasSpin) SPIN_DURATION_NS else 0L,
    )
}

private fun overlapsPaddleY(
    puck: MviPuck,
    paddleY: Double,
    paddleHeight: Double,
): Boolean = puck.y + puck.radius >= paddleY && puck.y - puck.radius <= paddleY + paddleHeight

private fun computeProgressiveSpeed(state: MviGameState): Double {
    val timeFactor = (state.elapsedSeconds / 60.0) * state.speedConfig.timeAccelerationRate
    val levelFactor = state.lines.size * state.speedConfig.levelAccelerationPerLine
    return (state.speedConfig.baseMultiplier + timeFactor + levelFactor)
        .coerceAtMost(state.speedConfig.maxMultiplier)
}

private fun applyMagnetEffect(
    puck: MviPuck,
    state: MviGameState,
    deltaSeconds: Double,
): MviPuck {
    val cx = state.canvasWidth - puck.radius
    val cy = state.paddle2Y + state.paddleHeight / 2
    val d = hypot(puck.x - cx, puck.y - cy)
    if (d !in 1e-9..MAGNET_RANGE) return puck
    val acceleration = MAGNET_STRENGTH * deltaSeconds * 60.0
    return puck.copy(
        vx = puck.vx + (cx - puck.x) / d * acceleration,
        vy = puck.vy + (cy - puck.y) / d * acceleration,
    )
}

private fun applyLineCollisions(
    puck: MviPuck,
    lines: List<MviLine>,
    teleports: Map<String, String>,
    elapsedNs: Long,
): MviPuck {
    for (line in lines) {
        val pts = line.points
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            if (!segmentCircleIntersects(a, b, puck.x, puck.y, puck.radius)) continue

            val partnerLineId = teleports[line.id]
            if (partnerLineId != null) {
                val partner = lines.firstOrNull { it.id == partnerLineId }
                if (partner != null) {
                    val pairId = teleportPairId(line.id, partnerLineId)
                    if (!canUseTeleport(puck, pairId, elapsedNs)) return puck
                    val mid = lineMidpoint(partner)
                    val (newVx, newVy) = rotateVelocityThroughPortal(puck.vx, puck.vy, a, b, partner)
                    return puck.copy(
                        x = mid.x,
                        y = mid.y,
                        vx = newVx,
                        vy = newVy,
                        teleportCooldownUntilNs = elapsedNs + TELEPORT_COOLDOWN_NS,
                        lastTeleportPairId = pairId,
                    )
                }
            }

            MviDomainEvents.record(MviDomainEvent.LineDeflect)
            val (newVx, newVy) = reflectVelocity(puck.vx, puck.vy, a, b)
            return puck.copy(vx = newVx, vy = newVy, spin = puck.spin * LINE_SPIN_RETENTION)
        }
    }
    return puck
}

private fun canUseTeleport(
    puck: MviPuck,
    pairId: String,
    elapsedNs: Long,
): Boolean = puck.lastTeleportPairId != pairId || elapsedNs >= puck.teleportCooldownUntilNs

private fun teleportPairId(
    firstLineId: String,
    secondLineId: String,
): String =
    if (firstLineId <= secondLineId) {
        "$firstLineId:$secondLineId"
    } else {
        "$secondLineId:$firstLineId"
    }

private fun rotateVelocityThroughPortal(
    vx: Double,
    vy: Double,
    entryA: MviPoint,
    entryB: MviPoint,
    exitLine: MviLine,
): Pair<Double, Double> {
    val exitSegment = exitLine.firstValidSegment() ?: return vx to vy
    val entryAngle = segmentAngle(entryA, entryB) ?: return vx to vy
    val exitAngle = segmentAngle(exitSegment.first, exitSegment.second) ?: return vx to vy
    val rotation = exitAngle - entryAngle + PI
    val rotatedVx = vx * cos(rotation) - vy * sin(rotation)
    val rotatedVy = vx * sin(rotation) + vy * cos(rotation)
    return rotatedVx to rotatedVy
}

private fun MviLine.firstValidSegment(): Pair<MviPoint, MviPoint>? =
    points
        .zipWithNext()
        .firstOrNull { (a, b) -> segmentLengthSquared(a, b) >= MIN_SEGMENT_LENGTH_SQUARED }

private fun segmentAngle(
    a: MviPoint,
    b: MviPoint,
): Double? {
    if (segmentLengthSquared(a, b) < MIN_SEGMENT_LENGTH_SQUARED) return null
    return atan2(b.y - a.y, b.x - a.x)
}

private fun segmentLengthSquared(
    a: MviPoint,
    b: MviPoint,
): Double {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return dx * dx + dy * dy
}

private fun segmentCircleIntersects(
    a: MviPoint,
    b: MviPoint,
    px: Double,
    py: Double,
    radius: Double,
): Boolean {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val fx = a.x - px
    val fy = a.y - py
    val lenSq = dx * dx + dy * dy
    if (lenSq < MIN_SEGMENT_LENGTH_SQUARED) return false
    val t = ((-fx * dx - fy * dy) / lenSq).coerceIn(0.0, 1.0)
    val closestX = a.x + t * dx - px
    val closestY = a.y + t * dy - py
    return closestX * closestX + closestY * closestY <= radius * radius
}

private fun reflectVelocity(
    vx: Double,
    vy: Double,
    a: MviPoint,
    b: MviPoint,
): Pair<Double, Double> {
    val nx = -(b.y - a.y)
    val ny = b.x - a.x
    val len = hypot(nx, ny)
    if (len * len < MIN_SEGMENT_LENGTH_SQUARED) return vx to vy
    val nnx = nx / len
    val nny = ny / len
    val dot = vx * nnx + vy * nny
    return (vx - 2 * dot * nnx) to (vy - 2 * dot * nny)
}

private fun lineMidpoint(line: MviLine): MviPoint {
    val pts = line.points
    if (pts.isEmpty()) return MviPoint(0.0, 0.0)
    return MviPoint(pts.sumOf { it.x } / pts.size, pts.sumOf { it.y } / pts.size)
}

private fun MviPowerUp.toProto(): ru.rkhamatyarov.proto.PowerUp =
    ru.rkhamatyarov.proto.PowerUp
        .newBuilder()
        .setX(x)
        .setY(y)
        .setRadius(radius)
        .setType(type.name)
        .setEmoji(type.emoji)
        .setColor(PowerUpType.getColorCode(type))
        .build()

private fun MviActivePowerUp.toProto(nowNs: Long): ru.rkhamatyarov.proto.ActivePowerUp =
    ru.rkhamatyarov.proto.ActivePowerUp
        .newBuilder()
        .setType(type.name)
        .setEmoji(type.emoji)
        .setRemainingSeconds(remainingSeconds(nowNs))
        .build()

private fun MviLine.toProto(): ru.rkhamatyarov.proto.Line =
    ru.rkhamatyarov.proto.Line
        .newBuilder()
        .setId(id)
        .setWidth(width)
        .setIsAnimating(false)
        .addAllPoints(points.map { it.toProto() })
        .build()

private fun MviPoint.toProto(): ru.rkhamatyarov.proto.Point =
    ru.rkhamatyarov.proto.Point
        .newBuilder()
        .setX(x)
        .setY(y)
        .build()

private const val PADDLE_WIDTH = 20.0
private const val MAGNET_RANGE = 150.0
private const val MAGNET_STRENGTH = 0.3
private const val TELEPORT_COOLDOWN_NS = 100_000_000L
private const val MIN_SEGMENT_LENGTH_SQUARED = 1e-9
private const val MAX_PADDLE_BOUNCE_ANGLE = PI * 0.36
private const val PADDLE_MOVEMENT_ANGLE_INFLUENCE = PI * 0.08
private const val PADDLE_MOVEMENT_NORMALIZER = 80.0
private const val PADDLE_SPIN_NORMALIZER = 90.0
private const val SPIN_DURATION_NS = 750_000_000L
private const val SPIN_CURVE_ACCELERATION = 260.0
private const val SPIN_DECAY_PER_TICK = 0.96
private const val WALL_SPIN_RETENTION = 0.75
private const val LINE_SPIN_RETENTION = 0.70
private const val MIN_PUCK_SPEED = 1.0
private const val MAX_SPIN = 1.0
private const val MIN_SPIN = 0.05
