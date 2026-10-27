package ru.rkhamatyarov.telemetry

import ru.rkhamatyarov.replay.ReplayConverter
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.GameIntent
import ru.rkhamatyarov.service.mvi.MviDomainEvent
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.PaddleSide

interface RoomCapture {
    val enabled: Boolean

    fun record(
        before: MviGameState,
        after: MviGameState,
        action: GameAction,
        humanSides: List<PaddleSide>,
        domainEvents: List<MviDomainEvent> = emptyList(),
    )

    fun capture(): CaptureWindow
}

/** Explicit opt-out: no snapshot allocation or diagnostic history. */
object DisabledCapture : RoomCapture {
    override val enabled = false

    override fun record(
        before: MviGameState,
        after: MviGameState,
        action: GameAction,
        humanSides: List<PaddleSide>,
        domainEvents: List<MviDomainEvent>,
    ) = Unit

    override fun capture(): CaptureWindow = CaptureWindow(0, false, emptyList())
}

/** Retains encoded snapshots, sharing each predecessor's after-state as the next baseline. */
class CaptureRecorder(
    private val clock: () -> Long = System::nanoTime,
    private val maxEntries: Int = 4096,
    private val maxBytes: Int = 8 * 1024 * 1024,
) : RoomCapture {
    override val enabled = true
    private val entries = ArrayDeque<CaptureEntry>()
    private var bytes = 0
    private var sequence = 0L
    private var limitedUntilNs = Long.MIN_VALUE
    private var needsBaseline = true

    init {
        require(maxEntries > 0 && maxBytes > 0)
    }

    @Synchronized
    override fun record(
        before: MviGameState,
        after: MviGameState,
        action: GameAction,
        humanSides: List<PaddleSide>,
        domainEvents: List<MviDomainEvent>,
    ) {
        val now = clock()
        prune(now)
        val boundary = BoundaryKind.of(action)
        val intent = ReplayConverter.toProto(GameIntent.Reliable(action))
        val entry =
            CaptureEntry(
                sequence = ++sequence,
                observedNs = now,
                action = intent?.payloadCase?.name ?: "RESTORE",
                boundary = boundary,
                humanSides = humanSides.map { it.name },
                domainEvents = domainEvents.map { it.toString() },
                baseline =
                    if (entries.isEmpty() || needsBaseline || boundary != BoundaryKind.NONE) {
                        ReplayConverter.stateToSnapshot(before).toByteArray()
                    } else {
                        null
                    },
                after = ReplayConverter.stateToSnapshot(after).toByteArray(),
                intent = intent?.toByteArray(),
            )
        // Budget a standalone baseline before eviction so an oversized record cannot erase history.
        val standaloneBaseline = entry.baseline ?: entries.last().after
        if (entry.size + (if (entry.baseline == null) standaloneBaseline.size else 0) > maxBytes) {
            needsBaseline = true
            limitedUntilNs = now + WINDOW_NS
            return
        }
        while (entries.isNotEmpty() && (entries.size >= maxEntries || bytes + entry.size > maxBytes)) {
            removeFirst()
            limitedUntilNs = now + WINDOW_NS
        }
        val retained =
            if (entries.isEmpty() && entry.baseline == null) entry.copy(baseline = standaloneBaseline) else entry
        entries.addLast(retained)
        bytes += retained.size
        needsBaseline = false
    }

    /** Detaches a completed prefix; only immutable encoded data is shared with the exporter. */
    @Synchronized
    override fun capture(): CaptureWindow {
        val now = clock()
        prune(now)
        return CaptureWindow(now, now < limitedUntilNs, entries.toList())
    }

    private fun prune(now: Long) {
        while (entries.isNotEmpty() && now - entries.first().observedNs > WINDOW_NS) removeFirst()
    }

    private fun removeFirst() {
        val removed = entries.removeFirst()
        bytes -= removed.size
        if (entries.isNotEmpty() && entries.first().baseline == null) {
            entries[0] = entries.first().copy(baseline = removed.after)
            bytes += removed.after.size
        }
    }

    companion object {
        private const val WINDOW_NS = 15_000_000_000L
    }
}

enum class BoundaryKind {
    NONE,
    RESET,
    RESTORE,
    ;

    companion object {
        fun of(action: GameAction): BoundaryKind =
            when (action) {
                GameAction.Reset -> RESET
                is GameAction.RestoreSnapshot -> RESTORE
                else -> NONE
            }
    }
}

data class CaptureWindow(
    val observedNs: Long,
    val capacityLimited: Boolean,
    val entries: List<CaptureEntry>,
)

data class CaptureEntry(
    val sequence: Long,
    val observedNs: Long,
    val action: String,
    val boundary: BoundaryKind,
    val humanSides: List<String>,
    val domainEvents: List<String>,
    val baseline: ByteArray?,
    val after: ByteArray,
    val intent: ByteArray?,
) {
    val size: Int = (baseline?.size ?: 0) + after.size + (intent?.size ?: 0) + domainEvents.sumOf { it.length * 2 }

    /** Encoded evidence is compared by content, not by array identity. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CaptureEntry) return false
        return sequence == other.sequence &&
            observedNs == other.observedNs &&
            action == other.action &&
            boundary == other.boundary &&
            humanSides == other.humanSides &&
            domainEvents == other.domainEvents &&
            baseline.contentEquals(other.baseline) &&
            after.contentEquals(other.after) &&
            intent.contentEquals(other.intent)
    }

    override fun hashCode(): Int {
        var result = sequence.hashCode()
        result = 31 * result + observedNs.hashCode()
        result = 31 * result + action.hashCode()
        result = 31 * result + boundary.hashCode()
        result = 31 * result + humanSides.hashCode()
        result = 31 * result + domainEvents.hashCode()
        result = 31 * result + (baseline?.contentHashCode() ?: 0)
        result = 31 * result + after.contentHashCode()
        result = 31 * result + (intent?.contentHashCode() ?: 0)
        return result
    }
}
