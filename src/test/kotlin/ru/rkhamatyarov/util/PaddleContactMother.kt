package ru.rkhamatyarov.util

import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviPuck
import ru.rkhamatyarov.service.mvi.PaddlePhysics
import ru.rkhamatyarov.service.mvi.PaddleSide
import ru.rkhamatyarov.service.mvi.PhysicsRules
import ru.rkhamatyarov.service.mvi.TickFrame

internal object PaddleContactMother {
    fun edge(
        side: PaddleSide = PaddleSide.A,
        above: Boolean = true,
        travel: Double,
        spin: Double = 0.0,
        vx: Double = 10.0,
        rules: PhysicsRules = PhysicsRules.V3,
    ): TickFrame {
        val start =
            MviPuck(
                x = if (side == PaddleSide.A) 15.0 else 785.0,
                y = if (above) 224.0 else 376.0,
                vx = vx,
                vy = if (above) 2000.0 else -2000.0,
                spin = spin,
                spinRemainingNs = if (spin == 0.0) 0L else 700_000_000L,
            )
        val state =
            MviGameState(
                puck = start,
                paddle1Velocity = travel,
                paddle2Velocity = travel,
            )
        val frame =
            TickFrame(
                puck = start.copy(y = if (above) 256.0 else 344.0),
                touchLedger = state.touchLedger,
            )
        return PaddlePhysics.resolve(
            frame = frame,
            state = state,
            effectiveSpeed = 1.0,
            elapsedNs = 16_000_000L,
            rules = rules,
        )
    }
}
