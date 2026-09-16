package ca.humiditylogger

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.MotionEvent
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.max

class ReadingChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var readings: List<Reading> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    var dayStartMs: Long = 0L
        set(value) {
            field = value
            invalidate()
        }
    var dayEndMs: Long = 1L
        set(value) {
            field = value
            invalidate()
        }
    var useFahrenheit: Boolean = true
        set(value) {
            field = value
            invalidate()
        }
    var outdoorSource: String = ""
        set(value) {
            field = value
            invalidate()
        }
    private val density = resources.displayMetrics.density
    private var selectedTimestampMs: Long? = null
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 220, 235, 230)
        strokeWidth = density
    }
    private val tooltipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 7, 13, 12)
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(42, 57, 53)
        strokeWidth = 1f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(166, 184, 178)
        textSize = 11f * resources.displayMetrics.scaledDensity
    }
    private val indoorTemperaturePaint = seriesPaint(Color.rgb(255, 133, 112))
    private val outdoorTemperaturePaint = seriesPaint(Color.rgb(255, 190, 92), dashed = true)
    private val indoorHumidityPaint = seriesPaint(Color.rgb(86, 190, 255))
    private val outdoorHumidityPaint = seriesPaint(Color.rgb(116, 222, 211), dashed = true)
    private val temperatureAxisPaint = Paint(textPaint).apply {
        color = Color.rgb(255, 133, 112)
    }
    private val humidityAxisPaint = Paint(textPaint).apply {
        color = Color.rgb(86, 190, 255)
    }

    private fun seriesPaint(colorValue: Int, dashed: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorValue
            strokeWidth = 1.4f * density
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (dashed) pathEffect = DashPathEffect(floatArrayOf(9f * density, 6f * density), 0f)
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(18, 27, 25))

        val left = 48f * density
        val right = width - 48f * density
        val top = 25f * density
        val bottom = height - 34f * density
        if (right <= left || bottom <= top || dayEndMs <= dayStartMs) return

        val temperatures = readings.mapNotNull { it.temperatureC }
        val tempMin = (temperatures.minOrNull() ?: 10.0) - 1.0
        val tempMax = max(tempMin + 3.0, (temperatures.maxOrNull() ?: 25.0) + 1.0)

        repeat(5) { index ->
            val y = top + (bottom - top) * index / 4f
            canvas.drawLine(left, y, right, y, gridPaint)

            val temperatureC = tempMax - (tempMax - tempMin) * index / 4.0
            val temperatureLabel = if (useFahrenheit) {
                "%.0f°".format(celsiusToFahrenheit(temperatureC))
            } else {
                "%.1f°".format(temperatureC)
            }
            canvas.drawText(
                temperatureLabel,
                left - 7f * density - temperatureAxisPaint.measureText(temperatureLabel),
                y + temperatureAxisPaint.textSize / 3f,
                temperatureAxisPaint,
            )

            val humidityLabel = "${100 - index * 25}%"
            canvas.drawText(
                humidityLabel,
                right + 7f * density,
                y + humidityAxisPaint.textSize / 3f,
                humidityAxisPaint,
            )
        }

        drawSeries(canvas, left, right, top, bottom, indoorHumidityPaint, false) {
            it.humidityPercent?.coerceIn(0.0, 100.0)?.div(100.0)
        }
        drawSeries(canvas, left, right, top, bottom, outdoorHumidityPaint, true) {
            it.humidityPercent?.coerceIn(0.0, 100.0)?.div(100.0)
        }
        drawSeries(canvas, left, right, top, bottom, indoorTemperaturePaint, false) {
            it.temperatureC?.let { value -> (value - tempMin) / (tempMax - tempMin) }
        }
        drawSeries(canvas, left, right, top, bottom, outdoorTemperaturePaint, true) {
            it.temperatureC?.let { value -> (value - tempMin) / (tempMax - tempMin) }
        }

        selectedTimestampMs?.let { selected ->
            val x = left + (right - left) * ((selected - dayStartMs).toDouble() /
                (dayEndMs - dayStartMs)).coerceIn(0.0, 1.0).toFloat()
            canvas.drawLine(x, top, x, bottom, crosshairPaint)
            drawTooltip(canvas, selected, left, right, top)
        }

        if (readings.isEmpty()) {
            canvas.drawText("No readings for this day", left + 12f, top + 34f, textPaint)
        }

        val labels = listOf("12 AM", "6 AM", "NOON", "6 PM", "11:59")
        labels.forEachIndexed { index, label ->
            val x = left + (right - left) * index / 4f
            val labelX = when (index) {
                0 -> x
                labels.lastIndex -> x - textPaint.measureText(label)
                else -> x - textPaint.measureText(label) / 2f
            }
            canvas.drawText(label, labelX, height - 8f * density, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (readings.isEmpty() || width == 0 || dayEndMs <= dayStartMs) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val left = 48f * density
                val right = width - 48f * density
                val fraction = ((event.x - left) / (right - left)).coerceIn(0f, 1f)
                val target = dayStartMs + ((dayEndMs - dayStartMs) * fraction).toLong()
                selectedTimestampMs = readings.minByOrNull { abs(it.timestampMs - target) }?.timestampMs
                contentDescription = selectedTimestampMs?.let(::tooltipText)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun drawTooltip(canvas: Canvas, selected: Long, left: Float, right: Float, top: Float) {
        val lines = tooltipText(selected).split('\n')
        val padding = 8f * density
        val boxWidth = lines.maxOf { textPaint.measureText(it) } + padding * 2f
        val lineHeight = textPaint.textSize * 1.35f
        val x = (left + padding).coerceAtMost(right - boxWidth)
        val bottom = top + padding * 2f + lineHeight * lines.size
        canvas.drawRoundRect(x, top, x + boxWidth, bottom, 8f * density, 8f * density, tooltipPaint)
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, x + padding, top + padding + lineHeight * (index + 0.8f), textPaint)
        }
    }

    private fun tooltipText(selected: Long): String {
        fun nearest(outdoor: Boolean): Reading? = readings
            .asSequence()
            .filter { if (outdoor) it.source == outdoorSource else !WeatherClient.isOutdoor(it.source) }
            .minByOrNull { abs(it.timestampMs - selected) }
        fun temp(value: Double?): String = value?.let {
            if (useFahrenheit) "%.1f°".format(celsiusToFahrenheit(it)) else "%.1f°C".format(it)
        } ?: "—"
        fun humid(value: Double?): String = value?.let { "%.1f%%".format(it) } ?: "—"
        val inside = nearest(false)
        val outside = nearest(true)
        return "${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(selected))}\n" +
            "Inside ${temp(inside?.temperatureC)}  ${humid(inside?.humidityPercent)}\n" +
            "Outside ${temp(outside?.temperatureC)}  ${humid(outside?.humidityPercent)}"
    }

    private fun drawSeries(
        canvas: Canvas,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        paint: Paint,
        outdoor: Boolean,
        normalizedValue: (Reading) -> Double?,
    ) {
        val path = Path()
        var drawing = false
        readings.asSequence()
            .filter {
                if (outdoor) it.source == outdoorSource else !WeatherClient.isOutdoor(it.source)
            }
            .forEach { reading ->
                val value = normalizedValue(reading)
                if (value == null) {
                    drawing = false
                } else {
                    val fraction = (reading.timestampMs - dayStartMs).toDouble() /
                        (dayEndMs - dayStartMs).toDouble()
                    val x = left + (right - left) * fraction.coerceIn(0.0, 1.0).toFloat()
                    val y = bottom - (bottom - top) * value.coerceIn(0.0, 1.0).toFloat()
                    if (drawing) path.lineTo(x, y) else path.moveTo(x, y)
                    drawing = true
                }
            }
        canvas.drawPath(path, paint)
    }

    private fun celsiusToFahrenheit(celsius: Double): Double = celsius * 9.0 / 5.0 + 32.0
}
