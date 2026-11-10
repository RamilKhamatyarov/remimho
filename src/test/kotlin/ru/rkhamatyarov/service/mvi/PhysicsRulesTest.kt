package ru.rkhamatyarov.service.mvi

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PhysicsRulesTest {
    @Test
    fun `wire versions map to explicit rules`() {
        val versions = mapOf("1" to PhysicsRules.V1, "2" to PhysicsRules.V2, "3" to PhysicsRules.V3)
        for ((version, rules) in versions) {
            assertEquals(rules, PhysicsRules.fromReplayVersion(version))
            assertEquals(version, rules.replayVersion)
            assertEquals(rules == PhysicsRules.V1, rules.legacyGhostMode)
        }
        assertEquals(PhysicsRules.V1, PhysicsRules.fromReplayVersion(""))
        assertEquals(PhysicsRules.V3, PhysicsRules.CURRENT)
    }

    @Test
    fun `unknown versions are rejected rather than upgraded`() {
        for (version in listOf("0", "4", "V1", " 1", " ")) {
            assertFailsWith<IllegalArgumentException> { PhysicsRules.fromReplayVersion(version) }
        }
    }
}
