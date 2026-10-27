package ru.rkhamatyarov.telemetry

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CaptureEntryTest {
    @Test
    fun `distinct arrays with equal content identify the same entry`() {
        val first = entry()
        val second = entry()
        assertEquals(first, second)
        assertEquals(second, first)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(1, hashSetOf(first, second).size)
    }

    @Test
    fun `nullable arrays distinguish absent from empty evidence`() {
        val first = entry().copy(baseline = null, intent = null)
        val second = entry().copy(baseline = null, intent = null)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, first.copy(baseline = byteArrayOf()))
        assertNotEquals(first, first.copy(intent = byteArrayOf()))
    }

    @Test
    fun `all constructor fields participate in equality`() {
        val first = entry()
        val different =
            listOf(
                first.copy(sequence = 2),
                first.copy(observedNs = 20),
                first.copy(action = "RESET"),
                first.copy(boundary = BoundaryKind.RESET),
                first.copy(humanSides = listOf("A")),
                first.copy(domainEvents = listOf("LineDeflect")),
                first.copy(baseline = byteArrayOf(9)),
                first.copy(after = byteArrayOf(9)),
                first.copy(intent = byteArrayOf(9)),
            )
        different.forEach { assertNotEquals(first, it) }
        assertNotEquals<Any?>(first, null)
        assertNotEquals<Any?>(first, "entry")
    }

    private fun entry(): CaptureEntry =
        CaptureEntry(
            sequence = 1,
            observedNs = 10,
            action = "TICK",
            boundary = BoundaryKind.NONE,
            humanSides = listOf("B"),
            domainEvents = emptyList(),
            baseline = byteArrayOf(1, 2),
            after = byteArrayOf(3, 4),
            intent = byteArrayOf(5, 6),
        )
}
