package ru.rkhamatyarov.model

import org.springframework.stereotype.Component

@Component
class GameState {
    var puckX = 390.0
    var puckY = 290.0
    var puckVX = 3.0
    var puckVY = (Math.random() - 0.5) * 5
    var paddle1Y = 250.0
    var paddle2Y = 250.0

    fun reset() {
        puckX = 400.0
        puckY = 300.0
        puckVX = -3.0
        puckVY = (Math.random() - 0.5) * 5
        paddle1Y = 250.0
        paddle2Y = 250.0
    }
}