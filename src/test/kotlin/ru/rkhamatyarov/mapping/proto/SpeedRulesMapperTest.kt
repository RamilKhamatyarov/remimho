package ru.rkhamatyarov.mapping.proto

import org.junit.jupiter.api.Test
import ru.rkhamatyarov.model.SpeedConfig
import ru.rkhamatyarov.proto.GameStateDelta
import ru.rkhamatyarov.service.mvi.MviGameState
import kotlin.test.assertEquals

/** History deltas carry the speed rules, so a rewind restores the rules active at that moment. */
class SpeedRulesMapperTest {
    @Test
    fun `history delta round-trips every speed rule`() {
        val rules =
            SpeedConfig(
                baseMultiplier = 1.4,
                timeAccelerationRate = 0.2,
                levelAccelerationPerLine = 0.07,
                maxMultiplier = 4.5,
                rallyGrowthPerHit = 1.1,
                smashBoost = 0.0,
                maxRallySpeed = 2_000.0,
                maxEffectiveSpeed = 2_500.0,
            )

        val restored =
            mviStateFromDelta(GameStateDelta.parseFrom(MviGameState(speedConfig = rules).toDelta().toByteArray()))

        assertEquals(rules, restored.speedConfig, "an explicit zero smash boost must survive the round trip")
    }

    @Test
    fun `delta recorded before speed rules decodes to the defaults`() {
        val legacy =
            MviGameState(
                speedConfig = SpeedConfig(baseMultiplier = 2.0),
            ).toDelta().toBuilder().clearSpeedRules().build()

        assertEquals(SpeedConfig(), mviStateFromDelta(legacy).speedConfig)
    }

    @Test
    fun `rules recorded before rally speed keep their old values and default the rally fields`() {
        val preRally =
            SpeedConfig(baseMultiplier = 2.0, maxMultiplier = 5.0)
                .toProto()
                .toBuilder()
                .clearRallyGrowthPerHit()
                .clearSmashBoost()
                .clearMaxRallySpeed()
                .clearMaxEffectiveSpeed()
                .build()

        assertEquals(SpeedConfig(baseMultiplier = 2.0, maxMultiplier = 5.0), preRally.toDomain())
    }
}
