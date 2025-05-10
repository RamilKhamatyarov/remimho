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

    val blocks = mutableListOf<Block>()
    var isDrawing = false
    var currentBlock: Block? = null

    var speedMultiplier = 1.0

    var paused = false

    data class Block(
        var x: Double,
        var y: Double,
        var width: Double,
        var height: Double
    )

    fun togglePause() {
        paused = !paused
    }

    fun reset() {
        puckX = 400.0
        puckY = 300.0

        puckVX = -3.0
        puckVY = (Math.random() - 0.5) * 5

        paddle1Y = 250.0
        paddle2Y = 250.0
    }

    fun startNewBlock(x: Double, y: Double) {
        currentBlock = Block(x, y, 0.0, 0.0)
        isDrawing = true
    }

    fun updateCurrentBlock(x: Double, y: Double) {
        currentBlock?.let {
            it.width = x - it.x
            it.height = y - it.y
        }
    }

    fun finishCurrentBlock() {
        currentBlock?.let {
            if (it.width < 0) {
                it.x += it.width
                it.width = -it.width
            }
            if (it.height < 0) {
                it.y += it.height
                it.height = -it.height
            }

            if (it.width > 5 && it.height > 5) {
                blocks.add(it)
            }
        }
        isDrawing = false
        currentBlock = null
    }

    fun clearBlocks() {
        blocks.clear()
    }
}