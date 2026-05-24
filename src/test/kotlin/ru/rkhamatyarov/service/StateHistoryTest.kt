package ru.rkhamatyarov.service

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.rkhamatyarov.proto.GameStateDelta

class StateHistoryTest {
    private lateinit var history: StateHistory

    @BeforeEach
    fun setUp() {
        history = StateHistory()
    }

    @Test
    fun `add and getByOffsetSeconds 0 returns the latest snapshot`() {
        val snapshot = byteArrayOf(1, 2, 3, 4)
        history.add(snapshot)

        val result = history.getByOffsetSeconds(0.0)

        assertNotNull(result)
        assertArrayEquals(snapshot, result)
    }

    @Test
    fun `add multiple snapshots and getByOffsetSeconds 0 returns the most recent`() {
        history.add(byteArrayOf(1))
        history.add(byteArrayOf(2))
        val latest = byteArrayOf(3)
        history.add(latest)

        val result = history.getByOffsetSeconds(0.0)

        assertArrayEquals(latest, result)
    }

    @Test
    fun `getByOffsetSeconds retrieves snapshot close to requested offset`() {
        val fiveSecondsAgoNs = System.nanoTime() - 5_000_000_000L
        val oldSnapshot = byteArrayOf(10, 20, 30)
        history.add(oldSnapshot, fiveSecondsAgoNs)

        history.add(byteArrayOf(99), System.nanoTime())

        val result = history.getByOffsetSeconds(5.0)

        assertNotNull(result)
        assertArrayEquals(oldSnapshot, result)
    }

    @Test
    fun `getByOffsetSeconds chooses newer frame when it is closer to target`() {
        val now = System.nanoTime()
        val older = byteArrayOf(1)
        val closer = byteArrayOf(2)
        history.add(older, now - 5_200_000_000L)
        history.add(closer, now - 4_900_000_000L)

        val result = history.getByOffsetSeconds(5.0)

        assertNotNull(result)
        assertArrayEquals(closer, result)
    }

    @Test
    fun `getByOffsetSeconds returns null when buffer is empty`() {
        val result = history.getByOffsetSeconds(0.0)
        assertNull(result)
    }

    @Test
    fun `getByOffsetSeconds with negative offset is coerced to 0 and returns latest`() {
        val snapshot = byteArrayOf(5, 6, 7)
        history.add(snapshot)

        val result = history.getByOffsetSeconds(-3.0)

        assertNotNull(result)
        assertArrayEquals(snapshot, result)
    }

    @Test
    fun `getByOffsetSeconds returns null when offset exceeds MAX_RETENTION_SECONDS`() {
        history.add(byteArrayOf(1, 2, 3))

        val result = history.getByOffsetSeconds(16.0)

        assertNull(result)
    }

    @Test
    fun `getByOffsetSeconds returns null when all snapshots are newer than requested time`() {
        history.add(byteArrayOf(42), System.nanoTime())

        val result = history.getByOffsetSeconds(14.0)

        assertNull(result)
    }

    @Test
    fun `ring buffer evicts oldest entry when MAX_CAPACITY is exceeded`() {
        val overCapacity = StateHistory.MAX_CAPACITY + 50
        repeat(overCapacity) { i ->
            history.add(byteArrayOf(i.toByte()))
        }

        val size = history.size()

        assertEquals(StateHistory.MAX_CAPACITY, size)
    }

    @Test
    fun `ring buffer retains newest entries after eviction`() {
        val overCapacity = StateHistory.MAX_CAPACITY + 1
        repeat(overCapacity) { i ->
            history.add(byteArrayOf(i.toByte()))
        }
        val latestExpected = byteArrayOf((overCapacity - 1).toByte())

        val result = history.getByOffsetSeconds(0.0)

        assertNotNull(result)
        assertArrayEquals(latestExpected, result)
    }

    @Test
    fun `size returns 0 after clear`() {
        repeat(10) { history.add(byteArrayOf(it.toByte())) }

        history.clear()

        assertEquals(0, history.size())
    }

    @Test
    fun `export import roundtrip preserves deltas`() {
        val now = 10_000_000_000L
        val older = deltaBytes(1.0)
        val newer = deltaBytes(2.0)
        history.add(older, now - 1_000_000_000L)
        history.add(newer, now - 500_000_000L)

        val exported = history.exportRange(0.0, 2.0, now)
        val fresh = StateHistory()
        fresh.importRange(exported)
        val reimported = fresh.exportRange(0.0, 2.0, now)

        assertEquals(2, exported.size)
        assertEquals(now - 500_000_000L, exported[0].first)
        assertEquals(2.0, GameStateDelta.parseFrom(reimported[0].second).puckX)
    }

    @Test
    fun `exportRange returns empty when buffer is empty`() {
        val exported = history.exportRange(0.0, 15.0, 10_000_000_000L)

        assertTrue(exported.isEmpty())
    }

    @Test
    fun `exportRange returns empty for invalid range`() {
        history.add(deltaBytes(1.0), 9_000_000_000L)

        val exported = history.exportRange(5.0, 1.0, 10_000_000_000L)

        assertTrue(exported.isEmpty())
    }

    @Test
    fun `importRange ignores malformed protobuf frames`() {
        history.importRange(listOf(1_000_000_000L to byteArrayOf(1, 2, 3)))

        assertEquals(0, history.size())
    }

    @Test
    fun `importRange does not mutate source arrays`() {
        val now = 10_000_000_000L
        val bytes = deltaBytes(3.0)
        history.importRange(listOf(now to bytes))

        bytes[0] = 0
        val exported = history.exportRange(0.0, 1.0, now)

        assertEquals(3.0, GameStateDelta.parseFrom(exported[0].second).puckX)
    }

    @Test
    fun `importRange does not exceed max capacity`() {
        val now = 10_000_000_000L
        val frames =
            List(StateHistory.MAX_CAPACITY + 10) { i ->
                now + i to deltaBytes(i.toDouble())
            }

        history.importRange(frames)

        assertEquals(StateHistory.MAX_CAPACITY, history.size())
    }

    @Test
    fun `concurrent add and read do not throw`() {
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
                    history.getByOffsetSeconds(0.0)
                    Thread.sleep(1)
                }
            }

        writeThread.start()
        readThread.start()
        writeThread.join(5_000)
        readThread.join(5_000)

        assert(history.size() <= StateHistory.MAX_CAPACITY)
    }

    private fun deltaBytes(puckX: Double): ByteArray =
        GameStateDelta
            .newBuilder()
            .setPuckX(puckX)
            .build()
            .toByteArray()
}
