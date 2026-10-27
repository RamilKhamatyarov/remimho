package ru.rkhamatyarov.telemetry

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CaptureAccessTest {
    @Test
    fun `disabled capture issues no tokens`() {
        val access = CaptureAccess().apply { enabled = false }
        assertNull(access.issue("connection", "room"))
        assertEquals(CapturePermission.DISABLED, access.acquire("room", "anything", 0))
    }

    @Test
    fun `tokens are connection scoped and cooldown is room scoped`() {
        val access = CaptureAccess()
        val first = assertNotNull(access.issue("first", "room"))
        val second = assertNotNull(access.issue("second", "room"))
        assertEquals(CapturePermission.FORBIDDEN, access.acquire("other-room", first, 0))
        assertEquals(CapturePermission.FORBIDDEN, access.acquire("room", "invalid", 0))
        assertEquals(CapturePermission.ALLOWED, access.acquire("room", first, 0))
        assertEquals(CapturePermission.COOLDOWN, access.acquire("room", second, 1))
        access.revoke("first")
        assertEquals(CapturePermission.FORBIDDEN, access.acquire("room", first, 20_000_000_000L))
        assertEquals(CapturePermission.ALLOWED, access.acquire("room", second, 20_000_000_000L))
        access.clear()
        assertEquals(CapturePermission.FORBIDDEN, access.acquire("room", second, 40_000_000_000L))
    }
}
