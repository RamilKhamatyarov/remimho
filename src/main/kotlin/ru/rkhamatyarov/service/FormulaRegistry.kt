package ru.rkhamatyarov.service

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import javafx.application.Platform
import org.jboss.logging.Logger
import ru.rkhamatyarov.model.GameState
import java.util.Timer
import java.util.TimerTask

/**
 * Manages the scheduling and display of random mathematical curves in the application.
 *
 * This service is responsible for:
 * - Scheduling random curve displays at intervals between 5-15 seconds
 * - Managing the lifecycle of curve animations
 * - Handling screen resize events for curve re-rendering
 * - Coordinating with the game state and available formula implementations
 *
 * @property gameState The current state container for game lines and rendering data
 * @property formulas Injectable collection of available formula implementations
 * @property timer Scheduler for random curve display tasks
 * @property currentFormula The currently active formula being displayed
 */
@ApplicationScoped
class FormulaRegistry {
    private val log = Logger.getLogger(javaClass.name)

    @Inject
    lateinit var gameState: GameState

    @Inject
    var formulas: Instance<Formula>? = null

    private var timer: Timer? = null
    private var currentFormula: Formula? = null

    /**
     * Starts the random curve scheduling system.
     *
     * Clears existing lines and initializes a new timer that will
     * display random curves at random intervals between 5-15 seconds.
     */
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

        currentFormula?.let { formula ->
            val line =
                formula.createLine().apply {
                    flattenedPoints = gameState.flattenBezierSpline(controlPoints)
                    isAnimating = true
                }
            gameState.lines.add(line)
        }
    }

    /**
     * Handles application resize events for the current curve.
     *
     * Re-renders the currently active formula with updated dimensions
     * while preserving the curve type but disabling animation.
     */
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
