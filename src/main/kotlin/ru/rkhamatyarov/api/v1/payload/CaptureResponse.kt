package ru.rkhamatyarov.api.v1.payload

import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection
data class CaptureResponse(
    val schemaVersion: Int = 2,
    val roomId: String,
    val applicationVersion: String,
    val capturedAt: String,
    val capacityLimited: Boolean,
    val frames: List<CaptureFrame>,
    val segments: List<CaptureSegment>,
)

@RegisterForReflection
data class CaptureFrame(
    val sequence: Long,
    val ageMs: Double,
    val action: String,
    val boundary: String,
    val humanSides: List<String>,
    val domainEvents: List<String>,
    val tickElapsedNs: Long?,
    val deltaSeconds: Double?,
    val turboSpeedMultiplier: Double?,
    val before: CaptureMotion,
    val after: CaptureMotion,
)

@RegisterForReflection
data class CaptureMotion(
    val puck: CapturePuck,
    val paddle1Y: Double,
    val paddle2Y: Double,
    val paddle1Velocity: Double,
    val paddle2Velocity: Double,
    val paddleHeight: Double,
    val score: CaptureScore,
    val elapsedSeconds: Double,
    val ghostMode: Boolean,
    val paddleShield: Boolean,
    val touchLedger: List<CaptureTouch>,
)

@RegisterForReflection
data class CapturePuck(
    val x: Double,
    val y: Double,
    val vx: Double,
    val vy: Double,
    val radius: Double,
    val spin: Double,
    val spinRemainingNs: Long,
    val teleportCooldownUntilNs: Long,
    val lastTeleportPairId: String?,
)

@RegisterForReflection
data class CaptureScore(val playerA: Int, val playerB: Int)

@RegisterForReflection
data class CaptureTouch(
    val source: String,
    val ownerSide: String?,
    val identifier: String,
    val elapsedNs: Long,
    val speedAtContact: Double,
)

@RegisterForReflection
data class CaptureSegment(
    val firstSequence: Long,
    val lastSequence: Long,
    val reason: String,
    val settings: CaptureSettings,
    val replayBase64: String,
    val expectedSnapshotBase64: String,
)

@RegisterForReflection
data class CaptureSettings(
    val canvasWidth: Double,
    val canvasHeight: Double,
    val speed: CaptureSpeed,
    val ai: CaptureAi,
    val combo: CaptureCombo,
    val oneTimer: CaptureOneTimer,
)

@RegisterForReflection
data class CaptureSpeed(
    val baseMultiplier: Double,
    val timeAccelerationRate: Double,
    val levelAccelerationPerLine: Double,
    val maxMultiplier: Double,
    val rallyGrowthPerHit: Double,
    val smashBoost: Double,
    val maxRallySpeed: Double,
    val maxEffectiveSpeed: Double,
)

@RegisterForReflection
data class CaptureAi(
    val enabled: Boolean,
    val reactionDelayMs: Long,
    val aimError: Double,
    val predictionDepth: Int,
    val aggression: Double,
)

@RegisterForReflection
data class CaptureCombo(
    val enabled: Boolean,
    val giveAndGoMultiplier: Double,
    val maximumRawSpeed: Double,
    val giveAndGoWindowNs: Long,
    val superGoalThreshold: Int,
    val superGoalWindowNs: Long,
)

@RegisterForReflection
data class CaptureOneTimer(
    val minimumIncomingSpeed: Double,
    val freshnessWindowNs: Long,
    val minimumMultiplier: Double,
    val maximumMultiplier: Double,
    val fullStrengthIncomingSpeed: Double,
    val maximumRawSpeed: Double,
)
