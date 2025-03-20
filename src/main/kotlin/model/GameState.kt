package ru.rkhamatyarov.model

import org.springframework.stereotype.Component

@Component
class GameState {
    var puckX = 390.0
    var puckY = 290.0
    var puckVX = 3.0
    var puckVY = 3.0
    var paddle1Y = 250.0
    var paddle2Y = 250.0
}
