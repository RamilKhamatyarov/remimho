package ru.rkhamatyarov.model

import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection
data class Score(
    var playerA: Int = 0,
    var playerB: Int = 0,
)
