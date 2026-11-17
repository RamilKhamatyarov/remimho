package ru.rkhamatyarov.tunnel

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.fail

/** Pending acceptance contracts. Enable only after the real TunnelState/reduceTunnel are added. */
@Disabled("awaiting the real fixed-step tunnel reducer; no stub may satisfy this suite")
@Tag("tunnel-contract-pending")
class TunnelReducerContractTest {
    @Test
    fun `reducer accepts immutable state ordered inputs and explicit rules`(): Unit = pending()

    @Test
    fun `reducer has no random or wall clock dependencies`(): Unit = pending()

    @Test
    fun `fixed tape vectors produce the same per-step state hashes`(): Unit = pending()

    private fun pending(): Nothing =
        fail("Implement this contract against the production reducer before removing @Disabled")
}
