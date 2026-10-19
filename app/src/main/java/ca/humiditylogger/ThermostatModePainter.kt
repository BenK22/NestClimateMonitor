package ca.humiditylogger

import android.graphics.Canvas
import android.graphics.Paint

/** Shared low-opacity mode backgrounds, painted before axes, paths and actual-sample markers. */
internal object ThermostatModePainter {
    /** Fills only the plot rectangle; the supplied bands are already time-clipped by the history helper. */
    fun draw(canvas: Canvas, bands: List<ThermostatModeBand>, startMs: Long, endMs: Long,
        left: Float, right: Float, top: Float, bottom: Float) {
        if (endMs <= startMs || right <= left || bottom <= top) return
        val paint = Paint()
        bands.forEach { band ->
            paint.color = band.mode.color
            paint.alpha = 38
            val x1 = left + ((band.startMs - startMs).toDouble() / (endMs - startMs)).toFloat() * (right - left)
            val x2 = left + ((band.endMs - startMs).toDouble() / (endMs - startMs)).toFloat() * (right - left)
            canvas.drawRect(x1.coerceIn(left, right), top, x2.coerceIn(left, right), bottom, paint)
        }
    }
}
