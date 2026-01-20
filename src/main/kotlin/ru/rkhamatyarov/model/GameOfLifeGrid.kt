package ru.rkhamatyarov.model

import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class GameOfLifeGrid {
    val rows = 20
    val cols = 20
    val cellSize = 15.0

    // Поле хранит null до первого использования
    private var _grid: Array<BooleanArray>? = null

    // Custom getter гарантирует инициализацию при первом доступе
    var grid: Array<BooleanArray>
        get() {
            if (_grid == null) {
                _grid = Array(rows) { BooleanArray(cols) }
                reset()
            }
            return _grid!!
        }
        set(value) {
            _grid = value
        }

    var gridX = 0.0
    var gridY = 0.0
    var lastUpdate = 0L
    val updateInterval = 500_000_000L

    // Публичный метод для гарантированной инициализации
    fun ensureInitialized() {
        if (_grid == null) {
            _grid = Array(rows) { BooleanArray(cols) }
            reset()
        }
    }

    // Все методы вызывают ensureInitialized()
    fun reset() {
        ensureInitialized()
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
        ensureInitialized()
        val gridWidth = cols * cellSize
        val gridHeight = rows * cellSize

        val maxX = canvasWidth - gridWidth - 100
        val maxY = canvasHeight - gridHeight - 100

        gridX = 50 + Math.random() * maxX
        gridY = 50 + Math.random() * maxY
    }

    fun update() {
        ensureInitialized()
        val nextGrid =
            Array(rows) { i ->
                BooleanArray(cols) { j ->
                    calculateNextCellState(i, j)
                }
            }
        grid = nextGrid
    }

    fun getAliveCells(): List<Cell> {
        ensureInitialized()
        return grid.flatMapIndexed { i, row ->
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
