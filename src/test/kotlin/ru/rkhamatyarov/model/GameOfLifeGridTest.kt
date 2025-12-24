package ru.rkhamatyarov.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GameOfLifeGridTest {
    private lateinit var grid: GameOfLifeGrid

    @BeforeEach
    fun setup() {
        grid = GameOfLifeGrid()
    }

    @Test
    fun `reset initializes grid with random values`() {
        // g
        grid.reset()
        // w
        val cells = grid.grid.flatMap { it.toList() }
        // t
        assertTrue(cells.any { it })
        assertTrue(cells.any { !it })
    }

    @Test
    fun `update applies Game of Life rules correctly`() {
        // g
        for (i in 0 until grid.rows) {
            for (j in 0 until grid.cols) {
                grid.grid[i][j] = false
            }
        }
        val midRow = grid.rows / 2
        val midCol = grid.cols / 2
        grid.grid[midRow - 1][midCol] = true
        grid.grid[midRow][midCol] = true
        grid.grid[midRow + 1][midCol] = true
        // w
        grid.update()
        // t
        assertTrue(grid.grid[midRow][midCol - 1])
        assertTrue(grid.grid[midRow][midCol])
        assertTrue(grid.grid[midRow][midCol + 1])
        assertFalse(grid.grid[midRow - 1][midCol])
        assertFalse(grid.grid[midRow + 1][midCol])
    }

    @Test
    fun `grid wraps around at edges`() {
        // g
        for (i in 0 until grid.rows) {
            for (j in 0 until grid.cols) {
                grid.grid[i][j] = false
            }
        }
        grid.grid[grid.rows - 1][0] = true
        grid.grid[0][0] = true
        grid.grid[0][grid.cols - 1] = true
        // w
        grid.update()
        // t
        assertTrue(grid.grid.any { row -> row.any { it } })
        assertTrue(grid.grid.sumOf { row -> row.count { it } } > 0)
    }

    @Test
    fun `repositionGrid sets gridX and gridY within boundaries`() {
        // g
        val canvasWidth = 800.0
        val canvasHeight = 600.0
        val gridWidth = grid.cols * grid.cellSize
        val gridHeight = grid.rows * grid.cellSize
        val minX = 50.0
        val minY = 50.0
        val maxX = canvasWidth - gridWidth - 50
        val maxY = canvasHeight - gridHeight - 50

        // w
        grid.repositionGrid(canvasWidth, canvasHeight)

        // t
        assertTrue(grid.gridX in minX..maxX, "gridX out of range: ${grid.gridX}")
        assertTrue(grid.gridY in minY..maxY, "gridY out of range: ${grid.gridY}")
    }

    @Test
    fun `live cell with 0 neighbors dies from underpopulation`() {
        // g
        grid.grid = Array(grid.rows) { BooleanArray(grid.cols) { false } }
        val centerRow = grid.rows / 2
        val centerCol = grid.cols / 2
        grid.grid[centerRow][centerCol] = true

        // w
        grid.update()

        // t
        assertFalse(grid.grid[centerRow][centerCol])
    }

    @Test
    fun `live cell with 1 neighbor dies from underpopulation`() {
        // g
        grid.grid = Array(grid.rows) { BooleanArray(grid.cols) { false } }
        val centerRow = grid.rows / 2
        val centerCol = grid.cols / 2
        grid.grid[centerRow][centerCol] = true
        grid.grid[centerRow][centerCol + 1] = true

        // w
        grid.update()

        // t
        assertFalse(grid.grid[centerRow][centerCol])
        assertFalse(grid.grid[centerRow][centerCol + 1])
    }

    @Test
    fun `live cell with 2 neighbors survives`() {
        // g
        grid.grid = Array(grid.rows) { BooleanArray(grid.cols) { false } }
        val centerRow = grid.rows / 2
        val centerCol = grid.cols / 2

        grid.grid[centerRow][centerCol - 1] = true
        grid.grid[centerRow][centerCol] = true
        grid.grid[centerRow][centerCol + 1] = true

        // w
        grid.update()

        // t
        assertTrue(grid.grid[centerRow - 1][centerCol])
        assertTrue(grid.grid[centerRow][centerCol])
        assertTrue(grid.grid[centerRow + 1][centerCol])
    }

    @Test
    fun `live cell with 3 neighbors survives`() {
        // g
        grid.grid = Array(grid.rows) { BooleanArray(grid.cols) { false } }
        val centerRow = grid.rows / 2
        val centerCol = grid.cols / 2

        grid.grid[centerRow][centerCol] = true
        grid.grid[centerRow][centerCol + 1] = true
        grid.grid[centerRow + 1][centerCol] = true
        grid.grid[centerRow + 1][centerCol + 1] = true

        // w
        grid.update()

        // t
        assertTrue(grid.grid[centerRow][centerCol])
    }

    @Test
    fun `live cell with 4 neighbors dies from overpopulation`() {
        // g
        grid.grid = Array(grid.rows) { BooleanArray(grid.cols) { false } }
        val centerRow = grid.rows / 2
        val centerCol = grid.cols / 2

        grid.grid[centerRow][centerCol] = true
        grid.grid[centerRow - 1][centerCol] = true
        grid.grid[centerRow + 1][centerCol] = true
        grid.grid[centerRow][centerCol - 1] = true
        grid.grid[centerRow][centerCol + 1] = true

        // w
        grid.update()

        // t
        assertFalse(grid.grid[centerRow][centerCol])
    }

    @Test
    fun `dead cell with exactly 3 neighbors becomes alive`() {
        // g
        grid.grid = Array(grid.rows) { BooleanArray(grid.cols) { false } }
        val centerRow = grid.rows / 2
        val centerCol = grid.cols / 2

        grid.grid[centerRow - 1][centerCol] = true
        grid.grid[centerRow][centerCol - 1] = true
        grid.grid[centerRow + 1][centerCol] = true

        // w
        grid.update()

        // t
        assertTrue(grid.grid[centerRow][centerCol])
    }

    @Test
    fun `dead cell with 2 neighbors remains dead`() {
        // g
        grid.grid = Array(grid.rows) { BooleanArray(grid.cols) { false } }
        val centerRow = grid.rows / 2
        val centerCol = grid.cols / 2

        grid.grid[centerRow - 1][centerCol] = true
        grid.grid[centerRow][centerCol - 1] = true

        // w
        grid.update()

        // t
        assertFalse(grid.grid[centerRow][centerCol])
    }

    @Test
    fun `edge wrapping works for top row`() {
        // g
        grid.grid = Array(grid.rows) { BooleanArray(grid.cols) { false } }

        grid.grid[0][grid.cols / 2 - 1] = true
        grid.grid[0][grid.cols / 2] = true
        grid.grid[0][grid.cols / 2 + 1] = true

        // w
        grid.update()

        // t
        assertTrue(grid.grid[grid.rows - 1][grid.cols / 2])
    }

    @Test
    fun `edge wrapping works for left column`() {
        // g
        grid.grid = Array(grid.rows) { BooleanArray(grid.cols) { false } }

        grid.grid[grid.rows / 2 - 1][0] = true
        grid.grid[grid.rows / 2][0] = true
        grid.grid[grid.rows / 2 + 1][0] = true

        // w
        grid.update()

        // t
        assertTrue(grid.grid[grid.rows / 2][grid.cols - 1])
    }

    @Test
    fun `still life block pattern remains unchanged`() {
        // g
        grid.grid = Array(grid.rows) { BooleanArray(grid.cols) { false } }
        val centerRow = grid.rows / 2
        val centerCol = grid.cols / 2

        grid.grid[centerRow][centerCol] = true
        grid.grid[centerRow][centerCol + 1] = true
        grid.grid[centerRow + 1][centerCol] = true
        grid.grid[centerRow + 1][centerCol + 1] = true

        // w
        grid.update()

        // t
        assertTrue(grid.grid[centerRow][centerCol])
        assertTrue(grid.grid[centerRow][centerCol + 1])
        assertTrue(grid.grid[centerRow + 1][centerCol])
        assertTrue(grid.grid[centerRow + 1][centerCol + 1])
    }

    @Test
    fun `blinker oscillator alternates correctly`() {
        // g
        grid.grid = Array(grid.rows) { BooleanArray(grid.cols) { false } }
        val centerRow = grid.rows / 2
        val centerCol = grid.cols / 2

        grid.grid[centerRow][centerCol - 1] = true
        grid.grid[centerRow][centerCol] = true
        grid.grid[centerRow][centerCol + 1] = true

        // w
        grid.update()

        // t
        assertTrue(grid.grid[centerRow - 1][centerCol])
        assertTrue(grid.grid[centerRow][centerCol])
        assertTrue(grid.grid[centerRow + 1][centerCol])

        // w
        grid.update()

        // t
        assertTrue(grid.grid[centerRow][centerCol - 1])
        assertTrue(grid.grid[centerRow][centerCol])
        assertTrue(grid.grid[centerRow][centerCol + 1])
    }
}
