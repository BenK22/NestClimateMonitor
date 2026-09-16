package ca.humiditylogger

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil
import kotlin.math.floor

object WidgetGraph {
    private const val GRAPH_WINDOW_MS = 6L * 60L * 60L * 1000L
    private val INDOOR_TEMP = Color.rgb(255, 133, 112)
    private val INDOOR_HUMID = Color.rgb(86, 190, 255)
    private val OUTDOOR_TEMP = Color.rgb(255, 190, 92)
    private val OUTDOOR_HUMID = Color.rgb(116, 222, 211)

    fun useFahrenheit(context: Context): Boolean =
        context.getSharedPreferences("display_preferences", Context.MODE_PRIVATE)
            .getBoolean("use_fahrenheit", true)

    fun render(context: Context, all: List<Reading>, widthDp: Int, heightDp: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).toInt().coerceIn(360, 1200)
        val height = (heightDp * density).toInt().coerceIn(130, 750)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val labelSize = (height * 0.095f).coerceIn(16f, 32f)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(205, 220, 214)
            textSize = labelSize
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
        }
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 75, 103, 94)
            strokeWidth = 2f
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }
        val left = ceil(textPaint.measureText("100°") + 12f)
        val right = floor(width - textPaint.measureText("100%") - 12f)
        val legendBaseline = labelSize
        val top = floor(labelSize * 1.65f + 8f)
        val bottom = floor(height - labelSize - 12f)
        if (right <= left || bottom <= top) return bitmap

        val now = System.currentTimeMillis()
        val start = now - GRAPH_WINDOW_MS
        val readings = all.filter { it.timestampMs in start..now }
        val fahrenheit = useFahrenheit(context)
        fun displayTemp(c: Double) = if (fahrenheit) c * 9.0 / 5.0 + 32.0 else c

        val temperatures = readings.mapNotNull { it.temperatureC }.map(::displayTemp)
        val tempMin = floor((temperatures.minOrNull() ?: if (fahrenheit) 60.0 else 15.0) - 2.0)
        val tempMax = ceil((temperatures.maxOrNull() ?: if (fahrenheit) 80.0 else 25.0) + 2.0)
            .coerceAtLeast(tempMin + 4.0)

        val humidities = readings.mapNotNull { it.humidityPercent }
        var humidityMin = floor(((humidities.minOrNull() ?: 40.0) - 5.0) / 5.0) * 5.0
        var humidityMax = ceil(((humidities.maxOrNull() ?: 60.0) + 5.0) / 5.0) * 5.0
        if (humidityMax - humidityMin < 20.0) {
            val middle = (humidityMax + humidityMin) / 2.0
            humidityMin = floor((middle - 10.0) / 5.0) * 5.0
            humidityMax = humidityMin + 20.0
        }
        if (humidityMin < 0.0) {
            humidityMax -= humidityMin
            humidityMin = 0.0
        }
        if (humidityMax > 100.0) {
            humidityMin -= humidityMax - 100.0
            humidityMax = 100.0
        }

        drawLegend(canvas, textPaint, left, right, legendBaseline)
        for (step in 0..2) {
            val fraction = step / 2f
            val y = floor(bottom - fraction * (bottom - top)) + 0.5f
            canvas.drawLine(left, y, right, y, gridPaint)
            val temp = tempMin + fraction * (tempMax - tempMin)
            val humidity = humidityMin + fraction * (humidityMax - humidityMin)
            canvas.drawText("${temp.toInt()}°", 2f, y + labelSize * 0.35f, textPaint)
            val humidityLabel = "${humidity.toInt()}%"
            canvas.drawText(
                humidityLabel,
                width - textPaint.measureText(humidityLabel) - 2f,
                y + labelSize * 0.35f,
                textPaint,
            )
        }
        canvas.drawText("6h ago", left, height - 3f, textPaint)
        val nowLabel = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now))
        canvas.drawText(nowLabel, right - textPaint.measureText(nowLabel), height - 3f, textPaint)

        fun draw(values: List<Reading>, selector: (Reading) -> Double?, color: Int, humidity: Boolean) {
            val points = values.mapNotNull { reading -> selector(reading)?.let { reading.timestampMs to it } }
            if (points.size < 2) return
            val path = Path()
            points.forEachIndexed { index, (time, raw) ->
                val x = left + ((time - start).toFloat() / GRAPH_WINDOW_MS) * (right - left)
                val value = if (humidity) raw else displayTemp(raw)
                val fraction = if (humidity) {
                    (value - humidityMin) / (humidityMax - humidityMin)
                } else {
                    (value - tempMin) / (tempMax - tempMin)
                }
                val y = bottom - fraction.toFloat().coerceIn(0f, 1f) * (bottom - top)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val lineWidth = (width / 330f).coerceIn(2.5f, 4.5f)
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.argb(210, 0, 0, 0)
                strokeWidth = lineWidth + 4f
                style = Paint.Style.STROKE
            })
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                strokeWidth = lineWidth
                style = Paint.Style.STROKE
            })
        }

        val indoor = readings.filter { ThermostatSelection.matches(context, it) }
        val outdoor = readings.filter { WeatherClient.isOutdoor(it.source) }
        draw(indoor, { it.temperatureC }, INDOOR_TEMP, false)
        draw(indoor, { it.humidityPercent }, INDOOR_HUMID, true)
        draw(outdoor, { it.temperatureC }, OUTDOOR_TEMP, false)
        draw(outdoor, { it.humidityPercent }, OUTDOOR_HUMID, true)
        return bitmap
    }

    private fun drawLegend(canvas: Canvas, textPaint: Paint, left: Float, right: Float, baseline: Float) {
        val entries = listOf(
            "IN T" to INDOOR_TEMP,
            "IN H" to INDOOR_HUMID,
            "OUT T" to OUTDOOR_TEMP,
            "OUT H" to OUTDOOR_HUMID,
        )
        val slot = (right - left) / entries.size
        entries.forEachIndexed { index, (label, color) ->
            val x = left + index * slot
            val sampleEnd = x + (textPaint.textSize * 0.75f)
            canvas.drawLine(x, baseline - textPaint.textSize * 0.3f, sampleEnd, baseline - textPaint.textSize * 0.3f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                strokeWidth = 4f
                setShadowLayer(2f, 1f, 1f, Color.BLACK)
            })
            canvas.drawText(label, sampleEnd + 5f, baseline, textPaint)
        }
    }
}
