package ru.rkhamatyarov.service

import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import ru.rkhamatyarov.proto.GameStateDelta

@ApplicationScoped
class StateHistory {
    private val log = Logger.getLogger(javaClass)

    private val lock = Any()

    private val buffer = ArrayDeque<ByteArray>(MAX_CAPACITY + 1)

    private val timestamps = ArrayDeque<Long>(MAX_CAPACITY + 1)

    fun add(
        snapshot: ByteArray,
        timestampNs: Long = System.nanoTime(),
    ) {
        synchronized(lock) {
            if (buffer.size >= MAX_CAPACITY) {
                buffer.removeFirst()
                timestamps.removeFirst()
            }
            buffer.addLast(snapshot)
            timestamps.addLast(timestampNs)
        }
    }

    fun getByOffsetSeconds(
        offset: Double,
        referenceNs: Long = System.nanoTime(),
    ): ByteArray? {
        val effectiveOffset = offset.coerceAtLeast(0.0)
        if (effectiveOffset > MAX_RETENTION_SECONDS) {
            log.warnf(
                "[StateHistory] Requested offset %.2f s exceeds max retention of %d s",
                effectiveOffset,
                MAX_RETENTION_SECONDS,
            )
            return null
        }

        val targetNs = referenceNs - (effectiveOffset * NANOSECONDS_PER_SECOND).toLong()

        return synchronized(lock) {
            if (buffer.isEmpty()) return@synchronized null
            closestSnapshotIndex(targetNs)?.let { buffer[it] }
        }
    }

    private fun closestSnapshotIndex(targetNs: Long): Int? {
        var lo = 0
        var hi = timestamps.size - 1
        var before = -1

        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (timestamps[mid] <= targetNs) {
                before = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }

        if (before < 0) return null

        val after = before + 1
        return if (after < timestamps.size &&
            kotlin.math.abs(timestamps[after] - targetNs) < kotlin.math.abs(targetNs - timestamps[before])
        ) {
            after
        } else {
            before
        }
    }

    fun size(): Int = synchronized(lock) { buffer.size }

    fun clear() =
        synchronized(lock) {
            buffer.clear()
            timestamps.clear()
        }

    fun exportRange(
        startOffsetSeconds: Double,
        endOffsetSeconds: Double,
        referenceNs: Long = System.nanoTime(),
    ): List<Pair<Long, ByteArray>> {
        if (startOffsetSeconds < 0.0 ||
            endOffsetSeconds < startOffsetSeconds ||
            endOffsetSeconds > MAX_RETENTION_SECONDS
        ) {
            return emptyList()
        }

        val newestNs = referenceNs - (startOffsetSeconds * NANOSECONDS_PER_SECOND).toLong()
        val oldestNs = referenceNs - (endOffsetSeconds * NANOSECONDS_PER_SECOND).toLong()

        return synchronized(lock) {
            timestamps
                .indices
                .asSequence()
                .filter { timestamps[it] in oldestNs..newestNs }
                .map { timestamps[it] to buffer[it].copyOf() }
                .sortedByDescending { it.first }
                .toList()
        }
    }

    fun importRange(frames: List<Pair<Long, ByteArray>>) =
        synchronized(lock) {
            frames
                .asSequence()
                .filter { (_, bytes) -> bytes.isValidGameStateDelta() }
                .sortedBy { it.first }
                .forEach { (timestampNs, bytes) ->
                    if (buffer.size >= MAX_CAPACITY) {
                        buffer.removeFirst()
                        timestamps.removeFirst()
                    }
                    buffer.addLast(bytes.copyOf())
                    timestamps.addLast(timestampNs)
                }
        }

    private fun ByteArray.isValidGameStateDelta(): Boolean = runCatching { GameStateDelta.parseFrom(this) }.isSuccess

    companion object {
        const val MAX_CAPACITY: Int = 900

        const val MAX_RETENTION_SECONDS: Int = 15

        private const val NANOSECONDS_PER_SECOND: Double = 1_000_000_000.0
    }
}
