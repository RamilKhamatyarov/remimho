package ru.rkhamatyarov.service.turbo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.MviDomainEvent
import ru.rkhamatyarov.service.mvi.PaddleSide

class TurboBoostStrategy(
    initialElapsedNs: Long = 0L,
) {
    private var snapshot = TurboSnapshot.initial(initialElapsedNs)
    private var lastPaddleTouch: PaddleSide = PaddleSide.B
    private val mutableHudState = MutableStateFlow(snapshot.toHudState(initialElapsedNs))

    val hudState: StateFlow<TurboHudState> = mutableHudState.asStateFlow()

    fun onAction(
        action: GameAction,
        elapsedNs: Long,
    ) {
        snapshot = snapshot.afterTime(elapsedNs)
        when (action) {
            is GameAction.ActivateTurbo -> activate(action.side, elapsedNs)
            GameAction.Reset -> reset(elapsedNs)
            else -> Unit
        }
        publish(elapsedNs)
    }

    fun onEvents(
        events: List<MviDomainEvent>,
        elapsedNs: Long,
    ) {
        snapshot = snapshot.afterTime(elapsedNs)
        events.forEach { event ->
            when (event) {
                is MviDomainEvent.PaddleHit -> {
                    lastPaddleTouch = event.side
                    addCharge(event.side, elapsedNs)
                }

                MviDomainEvent.LineDeflect -> {
                    addCharge(lastPaddleTouch, elapsedNs)
                }
            }
        }
        publish(elapsedNs)
    }

    fun speedMultiplier(elapsedNs: Long): Double {
        snapshot = snapshot.afterTime(elapsedNs)
        publish(elapsedNs)
        return if (snapshot.states.any { it.statusAt(elapsedNs) == TurboStatus.ACTIVE }) TURBO_SPEED_MULTIPLIER else 1.0
    }

    fun snapshot(elapsedNs: Long): TurboSnapshot {
        snapshot = snapshot.afterTime(elapsedNs)
        publish(elapsedNs)
        return snapshot
    }

    fun restore(snapshot: TurboSnapshot) {
        this.snapshot = snapshot.afterTime(snapshot.elapsedNs)
        lastPaddleTouch = snapshot.lastPaddleTouch
        publish(snapshot.elapsedNs)
    }

    fun reset(elapsedNs: Long) {
        snapshot = TurboSnapshot.initial(elapsedNs)
        lastPaddleTouch = PaddleSide.B
        publish(elapsedNs)
    }

    private fun activate(
        side: PaddleSide,
        elapsedNs: Long,
    ) {
        snapshot =
            snapshot.update(side) { state ->
                if (state.statusAt(elapsedNs) != TurboStatus.READY) return@update state
                state.copy(
                    charge = MAX_CHARGE,
                    activeUntilNs = elapsedNs + ACTIVE_DURATION_NS,
                    cooldownUntilNs = elapsedNs + ACTIVE_DURATION_NS + COOLDOWN_DURATION_NS,
                )
            }
    }

    private fun addCharge(
        side: PaddleSide,
        elapsedNs: Long,
    ) {
        snapshot =
            snapshot
                .update(side) { state ->
                    if (state.statusAt(elapsedNs) == TurboStatus.ACTIVE ||
                        state.statusAt(elapsedNs) == TurboStatus.COOLDOWN
                    ) {
                        return@update state
                    }
                    state.copy(charge = (state.charge + CHARGE_GAIN).coerceAtMost(MAX_CHARGE))
                }.copy(lastPaddleTouch = lastPaddleTouch)
    }

    private fun publish(elapsedNs: Long) {
        mutableHudState.value = snapshot.toHudState(elapsedNs)
    }

    companion object {
        const val MAX_CHARGE = 100.0
        const val CHARGE_GAIN = 20.0
        const val TURBO_SPEED_MULTIPLIER = 2.5
        const val ACTIVE_DURATION_NS = 1_000_000_000L
        const val COOLDOWN_DURATION_NS = 6_000_000_000L
    }
}

data class TurboSnapshot(
    val elapsedNs: Long,
    val states: List<TurboSideSnapshot>,
    val lastPaddleTouch: PaddleSide = PaddleSide.B,
) {
    fun afterTime(elapsedNs: Long): TurboSnapshot =
        copy(
            elapsedNs = elapsedNs,
            states = states.map { it.afterTime(elapsedNs) },
        )

    fun update(
        side: PaddleSide,
        update: (TurboSideSnapshot) -> TurboSideSnapshot,
    ): TurboSnapshot = copy(states = states.map { if (it.side == side) update(it) else it })

    fun toHudState(elapsedNs: Long): TurboHudState = TurboHudState(states.map { it.toHudEntry(elapsedNs) })

    companion object {
        fun initial(elapsedNs: Long = 0L): TurboSnapshot =
            TurboSnapshot(
                elapsedNs = elapsedNs,
                states = PaddleSide.entries.map { TurboSideSnapshot(side = it) },
            )
    }
}

data class TurboSideSnapshot(
    val side: PaddleSide,
    val charge: Double = 0.0,
    val activeUntilNs: Long = 0L,
    val cooldownUntilNs: Long = 0L,
) {
    fun statusAt(elapsedNs: Long): TurboStatus =
        when {
            elapsedNs < activeUntilNs -> TurboStatus.ACTIVE
            elapsedNs < cooldownUntilNs -> TurboStatus.COOLDOWN
            charge >= TurboBoostStrategy.MAX_CHARGE -> TurboStatus.READY
            else -> TurboStatus.CHARGING
        }

    fun afterTime(elapsedNs: Long): TurboSideSnapshot =
        if (cooldownUntilNs in 1L..elapsedNs) {
            copy(charge = 0.0, activeUntilNs = 0L, cooldownUntilNs = 0L)
        } else {
            this
        }

    fun toHudEntry(elapsedNs: Long): TurboSideHudState {
        val status = statusAt(elapsedNs)
        return TurboSideHudState(
            side = side,
            charge = charge,
            status = status,
            activeMs = if (status == TurboStatus.ACTIVE) ((activeUntilNs - elapsedNs).coerceAtLeast(0L) / 1_000_000L) else 0L,
            cooldownMs = if (status == TurboStatus.COOLDOWN) ((cooldownUntilNs - elapsedNs).coerceAtLeast(0L) / 1_000_000L) else 0L,
        )
    }
}

data class TurboHudState(
    val states: List<TurboSideHudState>,
)

data class TurboSideHudState(
    val side: PaddleSide,
    val charge: Double,
    val status: TurboStatus,
    val activeMs: Long,
    val cooldownMs: Long,
)

enum class TurboStatus {
    CHARGING,
    READY,
    ACTIVE,
    COOLDOWN,
}
