package ru.rkhamatyarov.model

import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class GameOfLifeGrid {
    val rows = 20
    val cols = 20
    var grid = Array(rows) { BooleanArray(cols) }
    var cellSize = 15.0
    var gridX = 0.0
    var gridY = 0.0
    var lastUpdate = 0L
    val updateInterval = 500_000_000L

    init {
        reset()
    }

    fun reset() {
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                grid[i][j] = Math.random() < 0.3
            }
        }
    }

    fun repositionGrid(
        canvasWidth: Double,
        canvasHeight: Double,
    ) {
        val gridWidth = cols * cellSize
        val gridHeight = rows * cellSize

        val maxX = canvasWidth - gridWidth - 100
        val maxY = canvasHeight - gridHeight - 100

        gridX = 50 + Math.random() * maxX
        gridY = 50 + Math.random() * maxY
    }

    fun update() {
        val nextGrid = Array(rows) { BooleanArray(cols) }
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                var neighbors = 0
                for (di in -1..1) {
                    for (dj in -1..1) {
                        if (di == 0 && dj == 0) continue
                        val ni = (i + di + rows) % rows
                        val nj = (j + dj + cols) % cols
                        if (grid[ni][nj]) neighbors++
                    }
                }
                nextGrid[i][j] =
                    when {
                        grid[i][j] && neighbors in 2..3 -> true
                        !grid[i][j] && neighbors == 3 -> true
                        else -> false
                    }
            }
        }
        grid = nextGrid
    }

    fun getAliveCells(): List<Cell> {
        val cells = mutableListOf<Cell>()
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                if (grid[i][j]) {
                    cells.add(
                        Cell(
                            x = gridX + j * cellSize,
                            y = gridY + i * cellSize,
                        ),
                    )
                }
            }
        }
        return cells
    }
}
