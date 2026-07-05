package ru.rkhamatyarov.service.turbo

import ru.rkhamatyarov.service.StateHistory
import kotlin.math.abs

class TurboHistory {
    private val snapshotLock = Any()
    private val snapshots = ArrayDeque<TurboSnapshot>(StateHistory.MAX_CAPACITY + 1)
    private val timestamps = ArrayDeque<Long>(StateHistory.MAX_CAPACITY + 1)

    fun add(
        snapshot: TurboSnapshot,
        timestampNs: Long,
    ) = synchronized(snapshotLock) {
        if (snapshots.size >= StateHistory.MAX_CAPACITY) {
            snapshots.removeFirst()
            timestamps.removeFirst()
        }
        snapshots.addLast(snapshot)
        timestamps.addLast(timestampNs)
    }

    fun getByOffsetSeconds(
        offset: Double,
        referenceNs: Long,
    ): TurboSnapshot? {
        val effectiveOffset = offset.coerceAtLeast(0.0)
        if (effectiveOffset > StateHistory.MAX_RETENTION_SECONDS) return null
        val targetNs = referenceNs - (effectiveOffset * NANOSECONDS_PER_SECOND).toLong()
        return synchronized(snapshotLock) {
            closestSnapshotIndex(targetNs)?.let { snapshots[it] }
        }
    }

    fun clear() =
        synchronized(snapshotLock) {
            snapshots.clear()
            timestamps.clear()
        }

    private fun closestSnapshotIndex(targetNs: Long): Int? {
        var closestIndex: Int? = null
        var closestDistance = Long.MAX_VALUE
        timestamps.forEachIndexed { index, timestamp ->
            val distance = abs(timestamp - targetNs)
            if (distance < closestDistance) {
                closestIndex = index
                closestDistance = distance
            }
        }
        return closestIndex
    }

    companion object {
        private const val NANOSECONDS_PER_SECOND = 1_000_000_000.0
    }
}
