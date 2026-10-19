package ca.humiditylogger

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/** Per-render, allocation-reusing marker painter shared by daily and widget graphs. */
internal class HvacMarkerPainter(private val radius: Float) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        // Keep hollow/cross symbols fully visible even in a compact widget bitmap.
        strokeWidth = (radius * 0.45f).coerceAtLeast(2f)
        strokeCap = Paint.Cap.ROUND
    }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(18, 27, 25) }
    private val shape = Path()

    /** Up/down triangles mean heating/cooling; an open circle means idle, a cross unknown. */
    fun draw(canvas: Canvas, x: Float, y: Float, activity: HvacActivity) {
        fill.color = activity.color
        edge.color = activity.color
        when (activity) {
            HvacActivity.HEATING, HvacActivity.COOLING -> {
                canvas.drawCircle(x, y, radius + edge.strokeWidth, halo)
                val direction = if (activity == HvacActivity.HEATING) -1f else 1f
                shape.reset()
                shape.moveTo(x, y + direction * radius)
                shape.lineTo(x + radius, y - direction * radius)
                shape.lineTo(x - radius, y - direction * radius)
                shape.close()
                canvas.drawPath(shape, fill)
            }
            HvacActivity.IDLE -> canvas.drawCircle(x, y, radius * 0.8f, edge)
            HvacActivity.UNKNOWN -> {
                val offset = radius * 0.65f
                canvas.drawLine(x - offset, y - offset, x + offset, y + offset, edge)
                canvas.drawLine(x - offset, y + offset, x + offset, y - offset, edge)
            }
        }
    }

    /** Draws the actual sample symbols with short labels when all four fit, including in landscape. */
    fun drawLegend(canvas: Canvas, text: Paint, left: Float, right: Float, baseline: Float) {
        val states = HvacActivity.entries
        val slot = (right - left) / states.size
        if (states.any { text.measureText(it.legend) + radius * 4f > slot }) return
        states.forEachIndexed { index, activity ->
            val x = left + slot * index
            draw(canvas, x + radius, baseline - text.textSize * 0.3f, activity)
            canvas.drawText(activity.legend, x + radius * 3f, baseline, text)
        }
    }
}
