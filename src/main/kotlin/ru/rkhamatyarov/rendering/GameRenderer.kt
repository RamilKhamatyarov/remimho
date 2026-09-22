package ru.rkhamatyarov.rendering

import ru.rkhamatyarov.service.mvi.MviGameState
import java.awt.BasicStroke
import java.awt.Color
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
        val digitHeight = (SCORE_DIGIT_HEIGHT * scaleY).roundToInt().coerceAtLeast(MIN_DIGIT_HEIGHT)
        val top = (SCORE_BASELINE * scaleY).roundToInt() - digitHeight
        drawCenteredNumber(graphics, state.score.playerA, (width * 0.25).roundToInt(), top, digitHeight)
        drawCenteredNumber(graphics, state.score.playerB, (width * 0.75).roundToInt(), top, digitHeight)
    }

    private fun drawCenteredNumber(
        graphics: Graphics2D,
        value: Int,
        centerX: Int,
        top: Int,
        digitHeight: Int,
    ) {
        val digits = value.coerceAtLeast(0).toString()
        val digitWidth = (digitHeight * DIGIT_WIDTH_RATIO).roundToInt().coerceAtLeast(3)
        val spacing = (digitWidth * DIGIT_SPACING_RATIO).roundToInt().coerceAtLeast(1)
        val totalWidth = digits.length * digitWidth + (digits.length - 1) * spacing
        var left = centerX - totalWidth / 2
        for (digit in digits) {
            drawDigit(graphics, digit - '0', left, top, digitWidth, digitHeight)
            left += digitWidth + spacing
        }
    }

    private fun drawDigit(
        graphics: Graphics2D,
        digit: Int,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
    ) {
        val thickness = (height * SEGMENT_THICKNESS_RATIO).roundToInt().coerceAtLeast(1)
        val half = height / 2
        val mask = SEGMENT_MASKS[digit]
        if (mask and SEG_A != 0) graphics.fillRect(left, top, width, thickness)
        if (mask and SEG_B != 0) graphics.fillRect(left + width - thickness, top, thickness, half)
        if (mask and SEG_C != 0) graphics.fillRect(left + width - thickness, top + half, thickness, height - half)
        if (mask and SEG_D != 0) graphics.fillRect(left, top + height - thickness, width, thickness)
        if (mask and SEG_E != 0) graphics.fillRect(left, top + half, thickness, height - half)
        if (mask and SEG_F != 0) graphics.fillRect(left, top, thickness, half)
        if (mask and SEG_G != 0) graphics.fillRect(left, top + half - thickness / 2, width, thickness)
    }

    private companion object {
        const val SCORE_DIGIT_HEIGHT = 32.0
        const val SCORE_BASELINE = 50.0
        const val MIN_DIGIT_HEIGHT = 12
        const val DIGIT_WIDTH_RATIO = 0.6
        const val DIGIT_SPACING_RATIO = 0.35
        const val SEGMENT_THICKNESS_RATIO = 0.14

        const val SEG_A = 1
        const val SEG_B = 2
        const val SEG_C = 4
        const val SEG_D = 8
        const val SEG_E = 16
        const val SEG_F = 32
        const val SEG_G = 64

        val SEGMENT_MASKS =
            intArrayOf(
                SEG_A or SEG_B or SEG_C or SEG_D or SEG_E or SEG_F,
                SEG_B or SEG_C,
                SEG_A or SEG_B or SEG_D or SEG_E or SEG_G,
                SEG_A or SEG_B or SEG_C or SEG_D or SEG_G,
                SEG_B or SEG_C or SEG_F or SEG_G,
                SEG_A or SEG_C or SEG_D or SEG_F or SEG_G,
                SEG_A or SEG_C or SEG_D or SEG_E or SEG_F or SEG_G,
                SEG_A or SEG_B or SEG_C,
                SEG_A or SEG_B or SEG_C or SEG_D or SEG_E or SEG_F or SEG_G,
                SEG_A or SEG_B or SEG_C or SEG_D or SEG_F or SEG_G,
            )
    }
}
