package ru.rkhamatyarov.config

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import ru.rkhamatyarov.service.mvi.Combo

/** Captures deployment settings once when a room is created. */
@ApplicationScoped
class ComboSettings {
    @ConfigProperty(name = "remimho.combos.enabled", defaultValue = "true")
    var enabled: Boolean = true

    @ConfigProperty(name = "remimho.combos.give-and-go-multiplier", defaultValue = "2.5")
    var giveAndGoMultiplier: Double = 2.5

    @ConfigProperty(name = "remimho.combos.maximum-raw-speed", defaultValue = "800")
    var maximumRawSpeed: Double = 800.0

    @ConfigProperty(name = "remimho.combos.give-and-go-window-ns", defaultValue = "3000000000")
    var giveAndGoWindowNs: Long = 3_000_000_000L

    @ConfigProperty(name = "remimho.combos.super-goal-threshold", defaultValue = "4")
    var superGoalThreshold: Int = 4

    @ConfigProperty(name = "remimho.combos.super-goal-window-ns", defaultValue = "10000000000")
    var superGoalWindowNs: Long = 10_000_000_000L

    fun snapshot(): Combo =
        Combo(
            enabled = enabled,
            giveAndGoMultiplier = giveAndGoMultiplier,
            maximumRawSpeed = maximumRawSpeed,
            giveAndGoWindowNs = giveAndGoWindowNs,
            superGoalThreshold = superGoalThreshold,
            superGoalWindowNs = superGoalWindowNs,
        )
}
