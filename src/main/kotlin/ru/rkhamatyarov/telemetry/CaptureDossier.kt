package ru.rkhamatyarov.telemetry

import ru.rkhamatyarov.api.v1.payload.CaptureFrame
import ru.rkhamatyarov.api.v1.payload.CaptureResponse
import ru.rkhamatyarov.api.v1.payload.CaptureSegment
import ru.rkhamatyarov.mapping.captureMotion
import ru.rkhamatyarov.mapping.captureSettings
import ru.rkhamatyarov.proto.FullGameSnapshot
import ru.rkhamatyarov.proto.ReplayFile
import ru.rkhamatyarov.proto.ReplayIntent
import java.time.Instant
import java.util.Base64

/** Decodes each retained after-state once, sharing it with the following frame. */
object CaptureDossier {
    fun build(
        roomId: String,
        version: String,
        window: CaptureWindow,
    ): CaptureResponse {
        var previous: FullGameSnapshot? = null
        val decoded =
            window.entries.map { entry ->
                val before = entry.baseline?.let(FullGameSnapshot::parseFrom) ?: checkNotNull(previous)
                val after = FullGameSnapshot.parseFrom(entry.after)
                previous = after
                DecodedEntry(entry, before, after, entry.intent?.let(ReplayIntent::parseFrom))
            }
        val groups = mutableListOf<MutableList<DecodedEntry>>()
        for (entry in decoded) {
            if (groups.isEmpty() || entry.record.baseline != null) groups.add(mutableListOf())
            groups.last().add(entry)
        }
        return CaptureResponse(
            roomId = roomId,
            applicationVersion = version,
            capturedAt = Instant.now().toString(),
            capacityLimited = window.capacityLimited,
            frames = decoded.map { it.frame(window.observedNs) },
            segments = groups.mapIndexed { index, entries -> segment(roomId, entries, index) },
        )
    }

    private fun segment(
        roomId: String,
        entries: List<DecodedEntry>,
        index: Int,
    ): CaptureSegment {
        val first = entries.first()
        val snapshot = if (first.record.boundary == BoundaryKind.RESTORE) first.after else first.before
        val intents = entries.mapNotNull { it.intent }
        val replay =
            ReplayFile.newBuilder()
                .setVersion("1")
                .setRoomId(roomId)
                .setStartingState(snapshot)
                .setCombo(snapshot.combo)
                .setOneTimerConfig(snapshot.oneTimerConfig)
                .setFrameCount(intents.size)
                .addAllIntents(intents)
                .build()
        val reason =
            when (first.record.boundary) {
                BoundaryKind.NONE -> if (index == 0) "window-start" else "recording-gap"
                else -> first.record.boundary.name
            }
        return CaptureSegment(
            firstSequence = first.record.sequence,
            lastSequence = entries.last().record.sequence,
            reason = reason,
            settings = snapshot.captureSettings(),
            replayBase64 = Base64.getEncoder().encodeToString(replay.toByteArray()),
            expectedSnapshotBase64 = Base64.getEncoder().encodeToString(entries.last().record.after),
        )
    }

    private data class DecodedEntry(
        val record: CaptureEntry,
        val before: FullGameSnapshot,
        val after: FullGameSnapshot,
        val intent: ReplayIntent?,
    ) {
        fun frame(nowNs: Long): CaptureFrame =
            CaptureFrame(
                sequence = record.sequence,
                ageMs = (nowNs - record.observedNs) / 1_000_000.0,
                action = record.action,
                boundary = record.boundary.name,
                humanSides = record.humanSides,
                domainEvents = record.domainEvents,
                tickElapsedNs = intent?.takeIf { it.hasTick() }?.elapsedNs,
                deltaSeconds = intent?.takeIf { it.hasTick() }?.tick?.deltaSeconds,
                turboSpeedMultiplier = intent?.takeIf { it.hasTick() }?.tick?.turboSpeedMultiplier,
                before = before.captureMotion(),
                after = after.captureMotion(),
            )
    }
}
