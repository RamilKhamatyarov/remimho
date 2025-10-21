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
        val nextGrid =
            Array(rows) { i ->
                BooleanArray(cols) { j ->
                    calculateNextCellState(i, j)
                }
            }
        grid = nextGrid
    }

    fun getAliveCells(): List<Cell> =
        grid.flatMapIndexed { i, row ->
            row
                .withIndex()
                .filter { it.value }
                .map { (j, _) ->
                    Cell(
                        x = gridX + j * cellSize,
                        y = gridY + i * cellSize,
                    )
                }
        }

    private fun calculateNextCellState(
        i: Int,
        j: Int,
    ): Boolean {
        val neighbors = countNeighbors(i, j)
        return when {
            grid[i][j] && neighbors in 2..3 -> true
            !grid[i][j] && neighbors == 3 -> true
            else -> false
        }
    }

    private fun countNeighbors(
        i: Int,
        j: Int,
    ): Int =
        (-1..1)
            .flatMap { di ->
                (-1..1).map { dj -> di to dj }
            }.filter { (di, dj) ->
                !(di == 0 && dj == 0)
            }.count { (di, dj) ->
                val ni = (i + di + rows) % rows
                val nj = (j + dj + cols) % cols
                grid[ni][nj]
            }
}
