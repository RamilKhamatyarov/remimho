package ru.rkhamatyarov.tunnel

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class TunnelMathVectorsTest {
    @Test
    fun `fixed point arithmetic matches committed x86 reference vectors`() {
        assertEquals(emptyList(), TunnelMathVectors.mismatches(Path.of("").toAbsolutePath()))
    }

    @Test
    fun `fixed point operations reject division by zero and overflow`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            TunnelMath.divide(TunnelScalar.ONE, TunnelScalar.ZERO)
        }
        kotlin.test.assertFailsWith<ArithmeticException> {
            TunnelMath.add(TunnelScalar(Long.MAX_VALUE), TunnelScalar.ONE)
        }
    }
}
