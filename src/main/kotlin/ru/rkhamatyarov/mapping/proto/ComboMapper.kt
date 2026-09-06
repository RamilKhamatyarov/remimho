package ru.rkhamatyarov.mapping.proto

import ru.rkhamatyarov.service.mvi.Combo
import ru.rkhamatyarov.proto.Combo as ProtoCombo

fun Combo.toProto(): ProtoCombo =
    ProtoCombo
        .newBuilder()
        .setEnabled(enabled)
        .setGiveAndGoMultiplier(giveAndGoMultiplier)
        .setMaximumRawSpeed(maximumRawSpeed)
        .setGiveAndGoWindowNs(giveAndGoWindowNs)
        .setSuperGoalThreshold(superGoalThreshold)
        .setSuperGoalWindowNs(superGoalWindowNs)
        .build()

fun ProtoCombo.toDomain(): Combo =
    Combo(
        enabled = enabled,
        giveAndGoMultiplier = giveAndGoMultiplier,
        maximumRawSpeed = maximumRawSpeed,
        giveAndGoWindowNs = giveAndGoWindowNs,
        superGoalThreshold = superGoalThreshold,
        superGoalWindowNs = superGoalWindowNs,
    )
