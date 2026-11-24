package ru.rkhamatyarov.tunnel

import java.math.BigInteger

/** Signed Q32.32 scalar; one unit is 2^32 raw ticks. */
@JvmInline
value class TunnelScalar(val raw: Long) : Comparable<TunnelScalar> {
    override fun compareTo(other: TunnelScalar): Int = raw.compareTo(other.raw)

    companion object {
        val ZERO = TunnelScalar(0L)
        val ONE = TunnelScalar(SCALE)
        const val SCALE: Long = 4_294_967_296L
    }
}

/** Integer-only numerical substrate for tunnel-mode state transitions. */
object TunnelMath {
    const val SUBSTRATE_ID = "q32.32-provisional"
    private val scale = BigInteger.valueOf(TunnelScalar.SCALE)

    fun fromInt(value: Long): TunnelScalar = TunnelScalar(BigInteger.valueOf(value).multiply(scale).longValueExact())

    fun fromRatio(
        numerator: Long,
        denominator: Long,
    ): TunnelScalar {
        require(denominator != 0L) { "division by zero" }
        val raw = BigInteger.valueOf(numerator).multiply(scale).divide(BigInteger.valueOf(denominator))
        return TunnelScalar(raw.longValueExact())
    }

    fun add(
        left: TunnelScalar,
        right: TunnelScalar,
    ): TunnelScalar = TunnelScalar(BigInteger.valueOf(left.raw).add(BigInteger.valueOf(right.raw)).longValueExact())

    fun subtract(
        left: TunnelScalar,
        right: TunnelScalar,
    ): TunnelScalar =
        TunnelScalar(BigInteger.valueOf(left.raw).subtract(BigInteger.valueOf(right.raw)).longValueExact())

    fun multiply(
        left: TunnelScalar,
        right: TunnelScalar,
    ): TunnelScalar =
        TunnelScalar(
            BigInteger.valueOf(left.raw).multiply(BigInteger.valueOf(right.raw)).divide(scale).longValueExact(),
        )

    fun divide(
        left: TunnelScalar,
        right: TunnelScalar,
    ): TunnelScalar {
        require(right.raw != 0L) { "division by zero" }
        val raw = BigInteger.valueOf(left.raw).multiply(scale).divide(BigInteger.valueOf(right.raw))
        return TunnelScalar(raw.longValueExact())
    }

    fun clamp(
        value: TunnelScalar,
        minimum: TunnelScalar,
        maximum: TunnelScalar,
    ): TunnelScalar {
        require(minimum <= maximum) { "minimum must not exceed maximum" }
        return when {
            value < minimum -> minimum
            value > maximum -> maximum
            else -> value
        }
    }

    fun moveTowards(
        current: TunnelScalar,
        target: TunnelScalar,
        maximumDelta: TunnelScalar,
    ): TunnelScalar {
        require(maximumDelta.raw >= 0L) { "maximumDelta must not be negative" }
        val distance = subtract(target, current)
        return when {
            distance.raw > maximumDelta.raw -> add(current, maximumDelta)
            distance.raw < -maximumDelta.raw -> subtract(current, maximumDelta)
            else -> target
        }
    }

    fun increment(value: Long): Long = BigInteger.valueOf(value).add(BigInteger.ONE).longValueExact()

    fun negate(value: TunnelScalar): TunnelScalar = TunnelScalar(-value.raw)

    fun absolute(value: TunnelScalar): TunnelScalar = if (value.raw < 0L) TunnelScalar(-value.raw) else value

    fun min(
        left: TunnelScalar,
        right: TunnelScalar,
    ): TunnelScalar = if (left <= right) left else right

    fun max(
        left: TunnelScalar,
        right: TunnelScalar,
    ): TunnelScalar = if (left >= right) left else right
}
