package ru.rkhamatyarov.tunnel

import ru.rkhamatyarov.determinism.dumpOf
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TunnelStateTest {
    @Test
    fun `canonical encoder covers every state and nested rules field`() {
        val state = TunnelState()
        val expectedPaths = leafPaths(state, "").toSet()
        val encodedPaths = TunnelStateEncoder.dumpOf(state).map { it.path }.toSet()

        assertEquals(expectedPaths - TunnelStateEncoder.EXCLUDED_PATHS, encodedPaths)
    }

    @Test
    fun `changing each top level simulation value changes the hash`() {
        val initial = TunnelState()
        val changed =
            listOf(
                initial.copy(puck = initial.puck.copy(radius = TunnelMath.fromInt(2L))),
                initial.copy(
                    paddleA = initial.paddleA.copy(target = TunnelVector2(TunnelMath.fromInt(1L), TunnelScalar.ZERO)),
                ),
                initial.copy(
                    paddleB = initial.paddleB.copy(target = TunnelVector2(TunnelMath.fromInt(1L), TunnelScalar.ZERO)),
                ),
                initial.copy(scoreA = 1),
                initial.copy(scoreB = 1),
                initial.copy(step = 1L),
                initial.copy(rules = initial.rules.copy(physicsVersion = "next")),
            )

        changed.forEach { assertNotEquals(TunnelStateEncoder.hashOf(initial), TunnelStateEncoder.hashOf(it)) }
    }

    private fun leafPaths(
        value: Any,
        prefix: String,
    ): List<String> =
        constructorProperties(value::class).flatMap { (name, getter) ->
            val path = if (prefix.isEmpty()) name else "$prefix.$name"
            val child = getter(value)
            if (constructorPropertiesOrEmpty(child::class).isEmpty()) listOf(path) else leafPaths(child, path)
        }

    private fun constructorProperties(type: KClass<*>): List<Pair<String, (Any) -> Any>> {
        val names =
            checkNotNull(type.primaryConstructor) {
                "$type has no primary constructor"
            }.parameters.mapNotNull { it.name }
        return names.map { name ->
            val property = type.memberProperties.first { it.name == name }
            property.isAccessible = true
            name to { instance -> property.getter.call(instance)!! }
        }
    }

    private fun constructorPropertiesOrEmpty(type: KClass<*>): List<String> =
        type.primaryConstructor?.parameters?.mapNotNull { it.name }.orEmpty()
}
