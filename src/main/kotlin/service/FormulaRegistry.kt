package ru.rkhamatyarov.service

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import ru.rkhamatyarov.model.GameState

@ApplicationScoped
class FormulaRegistry {
    @Inject
    lateinit var gameState: GameState

    @Inject
    var formulas: Instance<Formula>? = null

    fun addAllFormulas() {
        formulas?.forEach { formula ->
            val line =
                formula.createLine().apply {
                    flattenedPoints = gameState.flattenBezierSpline(controlPoints)
                    isAnimating = true
                }
            gameState.lines.add(line)
        }
    }
}
