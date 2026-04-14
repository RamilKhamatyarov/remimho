package ru.rkhamatyarov.service

import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger

/**
 * Ring buffer that retains the last capcity serialized game state delta
 * snapshots (ByteArray) together with their wall-clock timestamps.
 *
 * Storage strategy:
 *   - Full snapshots are stored every ~250 ms (every 15th tick at 60 Hz).
 *   - Delta frames are stored in the frames between full snapshots.
 *   - At 60 Hz / 15 s the buffer holds ≤ 900 entries.
 *   - Estimated peak memory: ~8–12 MB (full ~8 KB × 60 + deltas ~2 KB × 840).
 *
 * Thread safety: all mutations and reads are guarded by [lock].
 * The game-tick thread calls [add] at 60 Hz; the WebSocket handler thread
 * calls [getByOffsetSeconds] on TIMESHIFT commands.  The synchronized block
 * is sub-microsecond for ≤ 900 entries, which is safe at 60 Hz.
 */
@ApplicationScoped
class StateHistory {
    private val log = Logger.getLogger(javaClass)

    /** Protects [buffer] and [timestamps] for cross-thread access. */
    private val lock = Any()

    /** Serialized Protobuf frames in insertion order (oldest first). */
    private val buffer = ArrayDeque<ByteArray>(MAX_CAPACITY + 1)

    /** Monotonic timestamps in nanoseconds, parallel to [buffer]. */
    private val timestamps = ArrayDeque<Long>(MAX_CAPACITY + 1)

    /**
     * Append a snapshot.  Evicts the oldest entry if [MAX_CAPACITY] is reached.
     *
     * @param snapshot  Serialized [GameStateDelta] byte array.
     * @param timestampNs  Capture time in nanoseconds (default: [System.nanoTime]).
     */
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

    /**
     * Retrieve the snapshot closest to `offset` seconds ago.
     *
     * Uses a binary search on the timestamp deque (O(log n)) after converting
     * to a snapshot array to avoid holding the lock during iteration.
     *
     * @param offset  Seconds into the past (0.0 = latest, 15.0 = oldest retained).
     *                Negative values are treated as 0.0.
     * @return The matching [ByteArray], or `null` if the buffer is empty or
     *          no snapshot exists for the requested offset.
     */
    fun getByOffsetSeconds(offset: Double): ByteArray? {
        val effectiveOffset = offset.coerceAtLeast(0.0)
        if (effectiveOffset > MAX_RETENTION_SECONDS) {
            log.warnf(
                "[StateHistory] Requested offset %.2f s exceeds max retention of %d s",
                effectiveOffset,
                MAX_RETENTION_SECONDS,
            )
            return null
        }

        val targetNs = System.nanoTime() - (effectiveOffset * 1_000_000_000.0).toLong()

        return synchronized(lock) {
            if (buffer.isEmpty()) return@synchronized null

            var lo = 0
            var hi = timestamps.size - 1
            var best = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                when {
                    timestamps[mid] <= targetNs -> {
                        best = mid
                        lo = mid + 1
                    }

                    else -> {
                        hi = mid - 1
                    }
                }
            }
            if (best < 0) null else buffer[best]
        }
    }

    /** Number of snapshots currently in the buffer (test / monitoring helper). */
    fun size(): Int = synchronized(lock) { buffer.size }

    /** Clear all history (used in tests). */
    fun clear() =
        synchronized(lock) {
            buffer.clear()
            timestamps.clear()
        }

    companion object {
        /** 60 Hz × 15 s */
        const val MAX_CAPACITY: Int = 900

        /** Maximum rewind depth exposed to clients. */
        const val MAX_RETENTION_SECONDS: Int = 15
    }
}
