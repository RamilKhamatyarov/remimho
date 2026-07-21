package ru.rkhamatyarov.rendering

import ru.rkhamatyarov.service.mvi.MviGameState
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.roundToInt

class GameRenderer {
    fun render(state: MviGameState): BufferedImage {
        val width = state.canvasWidth.roundToInt().coerceAtLeast(1)
        val height = state.canvasHeight.roundToInt().coerceAtLeast(1)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        val scaleX = width / state.canvasWidth
        val scaleY = height / state.canvasHeight
        val scale = max(0.001, minOf(scaleX, scaleY))

        graphics.color = Color(0x1A, 0x1A, 0x2E)
        graphics.fillRect(0, 0, width, height)

        drawCenterLine(graphics, width, height)
        drawLines(graphics, state, scaleX, scaleY, scale)
        drawPaddles(graphics, state, width, scaleX, scaleY)
        drawPuck(graphics, state, scaleX, scaleY, scale)
        drawScore(graphics, state, width, scaleY)

        graphics.dispose()
        return image
    }

    private fun drawCenterLine(
        graphics: Graphics2D,
        width: Int,
        height: Int,
    ) {
        graphics.color = Color(255, 255, 255, 45)
        graphics.stroke = BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(10f, 10f), 0f)
        graphics.drawLine(width / 2, 0, width / 2, height)
    }

    private fun drawLines(
        graphics: Graphics2D,
        state: MviGameState,
        scaleX: Double,
        scaleY: Double,
        scale: Double,
    ) {
        graphics.color = Color(0xF0, 0xA5, 0x00)
        for (line in state.lines) {
            if (line.points.size < 2) continue
            graphics.stroke =
                BasicStroke(
                    (line.width * scale).toFloat(),
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND,
                )
            val first = line.points.first()
            val path = Path2D.Double()
            path.moveTo(first.x * scaleX, first.y * scaleY)
            line.points.drop(1).forEach { point -> path.lineTo(point.x * scaleX, point.y * scaleY) }
            graphics.draw(path)
        }
    }

    private fun drawPaddles(
        graphics: Graphics2D,
        state: MviGameState,
        width: Int,
        scaleX: Double,
        scaleY: Double,
    ) {
        val paddleWidth = 20.0 * scaleX
        val paddleHeight = state.paddleHeight * scaleY
        graphics.color = Color(0xE9, 0x45, 0x60)
        graphics.fill(Rectangle2D.Double(0.0, state.paddle1Y * scaleY, paddleWidth, paddleHeight))
        graphics.color = Color(0x4E, 0xCC, 0xA3)
        graphics.fill(Rectangle2D.Double(width - paddleWidth, state.paddle2Y * scaleY, paddleWidth, paddleHeight))
    }

    private fun drawPuck(
        graphics: Graphics2D,
        state: MviGameState,
        scaleX: Double,
        scaleY: Double,
        scale: Double,
    ) {
        val radius = state.puck.radius * scale
        graphics.color = Color.WHITE
        graphics.fill(
            Ellipse2D.Double(
                state.puck.x * scaleX - radius,
                state.puck.y * scaleY - radius,
                radius * 2.0,
                radius * 2.0,
            ),
        )
    }

    private fun drawScore(
        graphics: Graphics2D,
        state: MviGameState,
        width: Int,
        scaleY: Double,
    ) {
        graphics.color = Color(255, 255, 255, 180)
        graphics.font = Font(Font.MONOSPACED, Font.BOLD, (32 * scaleY).roundToInt().coerceAtLeast(12))
        val metrics = graphics.fontMetrics
        drawCenteredText(graphics, state.score.playerA.toString(), width * 0.25, 50.0 * scaleY, metrics)
        drawCenteredText(graphics, state.score.playerB.toString(), width * 0.75, 50.0 * scaleY, metrics)
    }

    private fun drawCenteredText(
        graphics: Graphics2D,
        text: String,
        centerX: Double,
        baselineY: Double,
        metrics: FontMetrics,
    ) {
        graphics.drawString(text, (centerX - metrics.stringWidth(text) / 2.0).toFloat(), baselineY.toFloat())
    }
}
