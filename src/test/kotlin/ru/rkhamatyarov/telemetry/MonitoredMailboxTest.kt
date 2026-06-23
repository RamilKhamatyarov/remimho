package ru.rkhamatyarov.telemetry

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonitoredMailboxTest {
    @Test
    fun `drops oldest values when capacity is exceeded`() =
        runTest {
            val mailbox = MonitoredMailbox<Int>(capacity = 64)

            repeat(70) { value ->
                assertTrue(mailbox.trySend(value))
            }

            assertEquals(64, mailbox.depth)
            assertEquals(6, mailbox.receive())
            assertEquals(63, mailbox.depth)
            mailbox.close()
        }
}
