package ru.rkhamatyarov.service

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import org.jboss.logging.Logger
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@ApplicationScoped
class FormulaRegistry {
    private val log = Logger.getLogger(javaClass)

    @Inject
    lateinit var engine: GameEngine

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
        val available = formulas?.toList() ?: return
        if (available.isEmpty()) return

        currentFormula = available.random()
        currentFormula?.let { formula ->
            val line =
                formula.createLine().apply {
                    flattenedPoints = engine.flattenBezierSpline(controlPoints)
                    isAnimating = true
                }
            engine.lines.add(line)
        }
    }

    fun handleResize() {
        currentFormula?.let { formula ->
            engine.clearLines()
            val line =
                formula.createLine().apply {
                    flattenedPoints = engine.flattenBezierSpline(controlPoints)
                    isAnimating = false
                }
            engine.lines.add(line)
        }
    }
}
