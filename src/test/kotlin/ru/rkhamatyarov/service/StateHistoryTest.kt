// TEST: src/test/kotlin/ru/rkhamatyarov/service/StateHistoryTest.kt
package ru.rkhamatyarov.service

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StateHistoryTest {
    private lateinit var history: StateHistory

    @BeforeEach
    fun setUp() {
        history = StateHistory()
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `add and getByOffsetSeconds 0 returns the latest snapshot`() {
        // Arrange
        val snapshot = byteArrayOf(1, 2, 3, 4)
        history.add(snapshot)

        // Act — offset 0 means "right now", so the most recently added entry should match
        val result = history.getByOffsetSeconds(0.0)

        // Assert
        assertNotNull(result)
        assertArrayEquals(snapshot, result)
    }

    @Test
    fun `add multiple snapshots and getByOffsetSeconds 0 returns the most recent`() {
        // Arrange
        history.add(byteArrayOf(1))
        history.add(byteArrayOf(2))
        val latest = byteArrayOf(3)
        history.add(latest)

        // Act
        val result = history.getByOffsetSeconds(0.0)

        // Assert
        assertArrayEquals(latest, result)
    }

    @Test
    fun `getByOffsetSeconds retrieves snapshot close to requested offset`() {
        // Arrange — insert a snapshot at exactly 5 seconds ago
        val fiveSecondsAgoNs = System.nanoTime() - 5_000_000_000L
        val oldSnapshot = byteArrayOf(10, 20, 30)
        history.add(oldSnapshot, fiveSecondsAgoNs)

        // Also add a "now" snapshot
        history.add(byteArrayOf(99), System.nanoTime())

        // Act — request 5 seconds ago
        val result = history.getByOffsetSeconds(5.0)

        // Assert — should return the snapshot we tagged 5 seconds ago
        assertNotNull(result)
        assertArrayEquals(oldSnapshot, result)
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `getByOffsetSeconds returns null when buffer is empty`() {
        // Act
        val result = history.getByOffsetSeconds(0.0)

        // Assert
        assertNull(result)
    }

    @Test
    fun `getByOffsetSeconds with negative offset is coerced to 0 and returns latest`() {
        // Arrange
        val snapshot = byteArrayOf(5, 6, 7)
        history.add(snapshot)

        // Act — negative offsets are treated as 0
        val result = history.getByOffsetSeconds(-3.0)

        // Assert
        assertNotNull(result)
        assertArrayEquals(snapshot, result)
    }

    @Test
    fun `getByOffsetSeconds returns null when offset exceeds MAX_RETENTION_SECONDS`() {
        // Arrange
        history.add(byteArrayOf(1, 2, 3))

        // Act — request 16 seconds ago; max retention is 15 s
        val result = history.getByOffsetSeconds(16.0)

        // Assert
        assertNull(result)
    }

    @Test
    fun `getByOffsetSeconds returns null when all snapshots are newer than requested time`() {
        // Arrange — snapshot added right now; we request a very old offset within limit
        history.add(byteArrayOf(42), System.nanoTime())

        // Act — request 14 s ago, but our only snapshot is ~0 s old
        val result = history.getByOffsetSeconds(14.0)

        // Assert — no snapshot at that timestamp → null
        assertNull(result)
    }

    // ── Ring buffer eviction ──────────────────────────────────────────────────

    @Test
    fun `ring buffer evicts oldest entry when MAX_CAPACITY is exceeded`() {
        // Arrange — fill buffer past capacity
        val overCapacity = StateHistory.MAX_CAPACITY + 50
        repeat(overCapacity) { i ->
            history.add(byteArrayOf(i.toByte()))
        }

        // Act
        val size = history.size()

        // Assert — size must not exceed MAX_CAPACITY
        assertEquals(StateHistory.MAX_CAPACITY, size)
    }

    @Test
    fun `ring buffer retains newest entries after eviction`() {
        // Arrange — write MAX_CAPACITY + 1 entries; the last entry is the "newest"
        val overCapacity = StateHistory.MAX_CAPACITY + 1
        repeat(overCapacity) { i ->
            history.add(byteArrayOf(i.toByte()))
        }
        val latestExpected = byteArrayOf((overCapacity - 1).toByte())

        // Act
        val result = history.getByOffsetSeconds(0.0)

        // Assert — most recent entry should be the last one written
        assertNotNull(result)
        assertArrayEquals(latestExpected, result)
    }

    @Test
    fun `size returns 0 after clear`() {
        // Arrange
        repeat(10) { history.add(byteArrayOf(it.toByte())) }

        // Act
        history.clear()

        // Assert
        assertEquals(0, history.size())
    }

    // ── Thread safety (smoke test — not a rigorous concurrency test) ──────────

    @Test
    fun `concurrent add and read do not throw`() {
        // Arrange
        val writeThread =
            Thread {
                repeat(200) { i ->
                    history.add(byteArrayOf(i.toByte()))
                    Thread.sleep(1)
                }
            }
        val readThread =
            Thread {
                repeat(200) {
                    history.getByOffsetSeconds(0.0) // may return null, that is fine
                    Thread.sleep(1)
                }
            }

        // Act — start both threads and wait for completion
        writeThread.start()
        readThread.start()
        writeThread.join(5_000)
        readThread.join(5_000)

        // Assert — no exception was thrown and size is within bounds
        assert(history.size() <= StateHistory.MAX_CAPACITY)
    }
}
