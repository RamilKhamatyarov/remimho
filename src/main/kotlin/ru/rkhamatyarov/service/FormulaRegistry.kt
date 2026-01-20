package ru.rkhamatyarov.service

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import org.jboss.logging.Logger
import ru.rkhamatyarov.model.GameState
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@ApplicationScoped
class FormulaRegistry {
    private val log = Logger.getLogger(javaClass)

    @Inject
    lateinit var gameState: GameState

    @Inject
    var formulas: Instance<Formula>? = null

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var currentFormula: Formula? = null

    fun startRandomCurveScheduler() {
        scheduleNextCurve()
    }

    private fun scheduleNextCurve() {
        val delay = (5000L..15000L).random()

        executor.schedule(
            {
                showRandomCurve()
                scheduleNextCurve()
            },
            delay,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun showRandomCurve() {
        val availableFormulas = formulas?.toList() ?: return

        if (availableFormulas.isEmpty()) return

        currentFormula = availableFormulas.random()

        currentFormula?.let { formula ->
            val line =
                formula.createLine().apply {
                    flattenedPoints = gameState.flattenBezierSpline(controlPoints)
                    isAnimating = true
                }
            gameState.lines.add(line)
        }
    }

    fun handleResize() {
        currentFormula?.let {
            gameState.clearLines()

            val line =
                it.createLine().apply {
                    flattenedPoints = gameState.flattenBezierSpline(controlPoints)
                    isAnimating = false
                }

            gameState.lines.add(line)
        }
    }
}
