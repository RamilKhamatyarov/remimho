package ru.rkhamatyarov.service

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import javafx.application.Platform
import org.jboss.logging.Logger
import ru.rkhamatyarov.model.GameState
import java.util.Timer
import java.util.TimerTask

@ApplicationScoped
class FormulaRegistry {
    private val log = Logger.getLogger(javaClass.name)

    @Inject
    lateinit var gameState: GameState

    @Inject
    var formulas: Instance<Formula>? = null

    private var timer: Timer? = null
    private var currentFormula: Formula? = null

    fun startRandomCurveScheduler() {
        gameState.clearLines()

        timer?.cancel()

        timer = Timer()
        scheduleNextCurve()
    }

    private fun scheduleNextCurve() {
        val delay = (5000L..15000L).random()
        try {
            timer?.schedule(
                object : TimerTask() {
                    override fun run() {
                        Platform.runLater {
                            showRandomCurve()
                            scheduleNextCurve()
                        }
                    }
                },
                delay,
            )
        } catch (e: IllegalArgumentException) {
            log.error("Invalid delay specified: $delay;", e)
        } catch (e: IllegalStateException) {
            log.error("Timer was cancelled or task already scheduled;", e)
        }
    }

    private fun showRandomCurve() {
        gameState.clearLines()

        val availableFormulas = formulas?.toList() ?: return

        if (availableFormulas.isEmpty()) return

        currentFormula = availableFormulas.random()

        val line =
            currentFormula!!.createLine().apply {
                flattenedPoints = gameState.flattenBezierSpline(controlPoints)
                isAnimating = true
            }
        gameState.lines.add(line)
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
