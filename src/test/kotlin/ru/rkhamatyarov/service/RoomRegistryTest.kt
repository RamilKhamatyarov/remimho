package ru.rkhamatyarov.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class RoomRegistryTest {
    @Test
    fun `get returns same room for same id`() {
        val registry = RoomRegistry()

        val first = registry.get("alpha")
        val second = registry.get("alpha")

        assertSame(first, second)
        assertEquals(1, registry.roomCount())
        registry.shutdown()
    }

    @Test
    fun `get isolates different rooms`() {
        val registry = RoomRegistry()

        val first = registry.get("alpha")
        val second = registry.get("beta")

        assertNotSame(first.reliableState, second.reliableState)
        assertEquals(2, registry.roomCount())
        registry.shutdown()
    }
}
