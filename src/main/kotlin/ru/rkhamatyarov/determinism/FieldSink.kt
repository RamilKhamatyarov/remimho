package ru.rkhamatyarov.determinism

/**
 * Receives one simulation state as an ordered stream of typed fields.
 *
 * An encoder decides which fields count and in which order; sinks only consume. Feeding the hash
 * and the dump from the same encoder keeps them in agreement about what the state is.
 */
interface FieldSink {
    fun double(
        path: String,
        value: Double,
    )

    fun long(
        path: String,
        value: Long,
    )

    fun int(
        path: String,
        value: Int,
    )

    fun boolean(
        path: String,
        value: Boolean,
    )

    /** Strings and enum names; null is distinct from the empty string. */
    fun text(
        path: String,
        value: String?,
    )

    /** Element count of a list or map, written before its elements. */
    fun count(
        path: String,
        value: Int,
    )
}

/** Writes one state into a sink. Implementations fix the field order by hand. */
fun interface StateEncoder<in S> {
    fun encode(
        state: S,
        sink: FieldSink,
    )
}

/** Every NaN hashes and dumps as this one pattern; signed zeros stay distinct. */
internal fun canonicalBits(value: Double): Long = if (value.isNaN()) CANONICAL_NAN_BITS else value.toRawBits()

internal const val CANONICAL_NAN_BITS = 0x7ff8000000000000L

/** Integer-only FNV-1a, so the result cannot depend on the CPU architecture. */
class Fnv1a64 {
    var value: Long = OFFSET_BASIS
        private set

    fun update(byte: Int) {
        value = (value xor (byte and BYTE_MASK).toLong()) * PRIME
    }

    fun update(bytes: ByteArray) {
        for (byte in bytes) update(byte.toInt())
    }

    fun updateLong(bits: Long) {
        var remaining = bits
        repeat(Long.SIZE_BYTES) {
            update(remaining.toInt())
            remaining = remaining ushr Byte.SIZE_BITS
        }
    }

    fun updateInt(bits: Int) {
        var remaining = bits
        repeat(Int.SIZE_BYTES) {
            update(remaining)
            remaining = remaining ushr Byte.SIZE_BITS
        }
    }

    companion object {
        const val OFFSET_BASIS = -0x340d631b7bdddcdbL
        const val PRIME = 0x100000001b3L
        private const val BYTE_MASK = 0xff
    }
}

/** 64-bit state hash: a type tag and the exact little-endian value bytes of every field. */
class HashSink : FieldSink {
    private val hash = Fnv1a64()

    val value: Long get() = hash.value

    override fun double(
        path: String,
        value: Double,
    ) {
        hash.update(TAG_DOUBLE)
        hash.updateLong(canonicalBits(value))
    }

    override fun long(
        path: String,
        value: Long,
    ) {
        hash.update(TAG_LONG)
        hash.updateLong(value)
    }

    override fun int(
        path: String,
        value: Int,
    ) {
        hash.update(TAG_INT)
        hash.updateInt(value)
    }

    override fun boolean(
        path: String,
        value: Boolean,
    ) {
        hash.update(TAG_BOOLEAN)
        hash.update(if (value) 1 else 0)
    }

    override fun text(
        path: String,
        value: String?,
    ) {
        if (value == null) {
            hash.update(TAG_NULL)
            return
        }
        val bytes = value.toByteArray(Charsets.UTF_8)
        hash.update(TAG_TEXT)
        hash.updateInt(bytes.size)
        hash.update(bytes)
    }

    override fun count(
        path: String,
        value: Int,
    ) {
        hash.update(TAG_COUNT)
        hash.updateInt(value)
    }

    private companion object {
        const val TAG_DOUBLE = 1
        const val TAG_LONG = 2
        const val TAG_INT = 3
        const val TAG_BOOLEAN = 4
        const val TAG_TEXT = 5
        const val TAG_NULL = 6
        const val TAG_COUNT = 7
    }
}

/** One dumped field: an exact token for comparison and a readable rendering for people. */
data class DumpedField(
    val path: String,
    val exact: String,
    val readable: String,
)

/** Collects the field stream as text so two states can be compared field by field. */
class DumpSink : FieldSink {
    private val collected = mutableListOf<DumpedField>()

    val fields: List<DumpedField> get() = collected

    override fun double(
        path: String,
        value: Double,
    ) {
        collected += DumpedField(path, "d:" + hex(canonicalBits(value)), value.toString())
    }

    override fun long(
        path: String,
        value: Long,
    ) {
        collected += DumpedField(path, "l:" + hex(value), value.toString())
    }

    override fun int(
        path: String,
        value: Int,
    ) {
        collected += DumpedField(path, "i:$value", value.toString())
    }

    override fun boolean(
        path: String,
        value: Boolean,
    ) {
        collected += DumpedField(path, "b:$value", value.toString())
    }

    override fun text(
        path: String,
        value: String?,
    ) {
        val exact =
            if (value == null) {
                "null"
            } else {
                "t:" +
                    value.toByteArray(Charsets.UTF_8).joinToString("") {
                        "%02x".format(it)
                    }
            }
        collected += DumpedField(path, exact, value ?: "null")
    }

    override fun count(
        path: String,
        value: Int,
    ) {
        collected += DumpedField(path, "n:$value", value.toString())
    }
}

internal fun hex(bits: Long): String = "%016x".format(bits)

fun <S> StateEncoder<S>.hashOf(state: S): Long = HashSink().also { encode(state, it) }.value

fun <S> StateEncoder<S>.dumpOf(state: S): List<DumpedField> = DumpSink().also { encode(state, it) }.fields
