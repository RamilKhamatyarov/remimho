package ru.rkhamatyarov.determinism

import org.junit.jupiter.api.Test
import ru.rkhamatyarov.service.mvi.MviGameState
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StateHashTest {
    @Test
    fun `fnv1a 64 matches the published reference values`() {
        assertEquals("cbf29ce484222325", hex(fnv("")))
        assertEquals("af63dc4c8601ec8c", hex(fnv("a")))
        assertEquals("85944171f73967e8", hex(fnv("foobar")))
    }

    @Test
    fun `adjacent doubles hash differently`() {
        val value = 123.456
        assertNotEquals(hashOfDouble(value), hashOfDouble(Math.nextUp(value)))
        assertNotEquals(hashOfDouble(value), hashOfDouble(Math.nextDown(value)))
    }

    @Test
    fun `signed zeros hash differently`() {
        assertNotEquals(hashOfDouble(0.0), hashOfDouble(-0.0))
    }

    @Test
    fun `every not-a-number payload hashes alike`() {
        val quiet = Double.NaN
        val payload = Double.fromBits(0x7ff8000000000abcL)
        val negative = Double.fromBits(-0x8000000000001L)
        assertTrue(payload.isNaN() && negative.isNaN())
        assertEquals(hashOfDouble(quiet), hashOfDouble(payload))
        assertEquals(hashOfDouble(quiet), hashOfDouble(negative))
        assertEquals(
            DumpSink().also { it.double("x", quiet) }.fields,
            DumpSink().also { it.double("x", payload) }.fields,
        )
    }

    @Test
    fun `null text differs from empty text`() {
        assertNotEquals(HashSink().also { it.text("t", null) }.value, HashSink().also { it.text("t", "") }.value)
    }

    @Test
    fun `map insertion order does not change the hash`() {
        val forward = MviGameState(teleports = linkedMapOf("a" to "b", "c" to "d"))
        val backward = MviGameState(teleports = linkedMapOf("c" to "d", "a" to "b"))
        assertEquals(MviGameStateEncoder.hashOf(forward), MviGameStateEncoder.hashOf(backward))
    }

    @Test
    fun `equal states built separately hash equally`() {
        HashVectors.states().forEach { (name, state) ->
            assertEquals(
                MviGameStateEncoder.hashOf(state),
                MviGameStateEncoder.hashOf(HashVectors.states().getValue(name)),
                name,
            )
        }
    }

    @Test
    fun `the encoder covers every constructor property of the state`() {
        val sample = HashVectors.states().getValue("populated")
        val expected = leafPaths(sample, "").toSortedSet()
        val encoded =
            MviGameStateEncoder
                .dumpOf(sample)
                .filterNot { it.exact.startsWith("n:") }
                .map { it.path }
                .toSortedSet()
        val missing = expected - encoded - MviGameStateEncoder.EXCLUDED_PATHS
        val unknown = encoded - expected
        assertTrue(missing.isEmpty(), "state properties neither encoded nor excluded: $missing")
        assertTrue(unknown.isEmpty(), "encoded paths that are not state properties: $unknown")
    }

    @Test
    fun `the completeness walk notices an unencoded property`() {
        val partial = StateEncoder<Fixture> { state, sink -> sink.double("physics", state.physics) }
        val expected = leafPaths(Fixture(1.0, "glow"), "").toSet()
        val encoded = partial.dumpOf(Fixture(1.0, "glow")).map { it.path }.toSet()
        assertEquals(setOf("tint"), expected - encoded)
    }

    @Test
    fun `changing any single encoded field changes the hash`() {
        HashVectors.states().forEach { (name, state) ->
            val recorded = RecordingSink().also { MviGameStateEncoder.encode(state, it) }
            val original = recorded.replayHash(mutated = -1)
            assertEquals(MviGameStateEncoder.hashOf(state), original, name)
            for (index in recorded.fields.indices) {
                assertNotEquals(original, recorded.replayHash(mutated = index), "$name: ${recorded.fields[index].path}")
            }
        }
    }

    @Test
    fun `presentation-only data outside the encoder does not change the hash`() {
        val encoder = StateEncoder<Fixture> { state, sink -> sink.double("physics", state.physics) }
        assertEquals(encoder.hashOf(Fixture(2.5, "red glow")), encoder.hashOf(Fixture(2.5, "blue trail")))
        assertNotEquals(encoder.hashOf(Fixture(2.5, "red glow")), encoder.hashOf(Fixture(2.6, "red glow")))
    }

    @Test
    fun `committed hash vectors match`() {
        assertEquals(emptyList(), HashVectors.mismatches(Path.of("").toAbsolutePath()))
    }

    private data class Fixture(
        val physics: Double,
        val tint: String,
    )

    private fun fnv(text: String): Long = Fnv1a64().also { it.update(text.toByteArray(Charsets.US_ASCII)) }.value

    private fun hashOfDouble(value: Double): Long = HashSink().also { it.double("x", value) }.value

    /** Paths of every leaf value reachable through primary-constructor properties. */
    private fun leafPaths(
        value: Any?,
        path: String,
    ): List<String> =
        when (value) {
            null, is Number, is Boolean, is String, is Enum<*> -> listOf(path)
            is List<*> -> value.flatMapIndexed { index, element -> leafPaths(element, "$path[$index]") }
            is Map<*, *> -> value.entries.flatMap { (key, element) -> leafPaths(element, "$path[$key]") }
            else ->
                constructorProperties(value::class).flatMap { (name, read) ->
                    leafPaths(read(value), if (path.isEmpty()) name else "$path.$name")
                }
        }

    private fun constructorProperties(type: KClass<*>): List<Pair<String, (Any) -> Any?>> {
        val names =
            checkNotNull(
                type.primaryConstructor,
            ) { "$type has no primary constructor" }.parameters.map { it.name }
        return names.map { name ->
            val property = type.memberProperties.first { it.name == name }
            property.isAccessible = true
            name!! to { instance: Any -> property.getter.call(instance) }
        }
    }

    /** Captures a field stream so it can be replayed into a hash with one field nudged. */
    private class RecordingSink : FieldSink {
        data class Recorded(
            val path: String,
            val replay: (FieldSink, Boolean) -> Unit,
        )

        val fields = ArrayList<Recorded>()

        fun replayHash(mutated: Int): Long {
            val sink = HashSink()
            fields.forEachIndexed { index, field -> field.replay(sink, index == mutated) }
            return sink.value
        }

        override fun double(
            path: String,
            value: Double,
        ) {
            val adjacent = if (value.isNaN() || value.isInfinite()) 0.0 else Math.nextUp(value)
            fields += Recorded(path) { sink, mutate -> sink.double(path, if (mutate) adjacent else value) }
        }

        override fun long(
            path: String,
            value: Long,
        ) {
            fields += Recorded(path) { sink, mutate -> sink.long(path, if (mutate) value + 1 else value) }
        }

        override fun int(
            path: String,
            value: Int,
        ) {
            fields += Recorded(path) { sink, mutate -> sink.int(path, if (mutate) value + 1 else value) }
        }

        override fun boolean(
            path: String,
            value: Boolean,
        ) {
            fields += Recorded(path) { sink, mutate -> sink.boolean(path, if (mutate) !value else value) }
        }

        override fun text(
            path: String,
            value: String?,
        ) {
            fields += Recorded(path) { sink, mutate -> sink.text(path, if (mutate) (value ?: "") + "x" else value) }
        }

        override fun count(
            path: String,
            value: Int,
        ) {
            fields += Recorded(path) { sink, mutate -> sink.count(path, if (mutate) value + 1 else value) }
        }
    }
}
