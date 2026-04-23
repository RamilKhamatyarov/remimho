package ru.rkhamatyarov.service

import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger

/**
 * Ring buffer that retains the last capacity serialized full game state
 * snapshots (ByteArray) together with their wall-clock timestamps.
 *
 * Storage strategy:
 *   - Every stored frame is a standalone full snapshot.
 *   - Live WebSocket clients still receive deltas; history is for rewind.
 *   - At 60 Hz / 15 s the buffer holds ≤ 900 entries.
 *   - Estimated peak memory remains small for the current game state size.
 *
 */
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

        val targetNs = referenceNs - (effectiveOffset * 1_000_000_000.0).toLong()

        return synchronized(lock) {
            if (buffer.isEmpty()) return@synchronized null

            var lo = 0
            var hi = timestamps.size - 1
            var before = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                when {
                    timestamps[mid] <= targetNs -> {
                        before = mid
                        lo = mid + 1
                    }

                    else -> {
                        hi = mid - 1
                    }
                }
            }

            if (before < 0) {
                null
            } else {
                val after = before + 1
                val best =
                    if (after < timestamps.size &&
                        kotlin.math.abs(timestamps[after] - targetNs) < kotlin.math.abs(targetNs - timestamps[before])
                    ) {
                        after
                    } else {
                        before
                    }
                buffer[best]
            }
        }
    }

    fun size(): Int = synchronized(lock) { buffer.size }

    fun clear() =
        synchronized(lock) {
            buffer.clear()
            timestamps.clear()
        }

    companion object {
        const val MAX_CAPACITY: Int = 900

        const val MAX_RETENTION_SECONDS: Int = 15
    }
}
