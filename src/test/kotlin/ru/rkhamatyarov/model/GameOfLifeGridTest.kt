package ru.rkhamatyarov.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GameOfLifeGridTest {
    private lateinit var grid: GameOfLifeGrid

    @BeforeAll
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
}
