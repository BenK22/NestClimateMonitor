package ca.humiditylogger

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/** Synthetic chart-only tests never open the user's database or change logging settings. */
@RunWith(AndroidJUnit4::class)
class HvacChartTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun dailyBackgroundUsesSelectedModesAndLeavesMissingHistoryUnshaded() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val interval = 600_000L
            val modes = listOf("HEAT", "COOL", "HEATCOOL", "OFF", null)
            val chart = ReadingChartView(context).apply {
                dayStartMs = 0
                dayEndMs = interval * 6
                readings = modes.mapIndexed { index, mode ->
                    Reading(index * interval, "Synthetic", "device", 20.0, null,
                        systemMode = mode, runningState = "OFF")
                }
                useFahrenheit = false
            }
            chart.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY))
            chart.layout(0, 0, 1000, 700)
            val bitmap = Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888)
            val reference = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            try {
                chart.draw(Canvas(bitmap))
                java.io.File(context.getExternalFilesDir(null), "mode-background-test.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                val density = context.resources.displayMetrics.density
                val left = 48f * density
                val right = 1000 - left
                val y = (35f * density).roundToInt() // Below the top grid, above all paths.
                modes.forEachIndexed { index, raw ->
                    reference.eraseColor(Color.rgb(18, 27, 25))
                    val mode = ThermostatMode.fromReported(raw)
                    if (mode != ThermostatMode.UNKNOWN) Canvas(reference).drawColor(
                        Color.argb(38, Color.red(mode.color), Color.green(mode.color), Color.blue(mode.color)))
                    val x = (left + (right - left) * (index + 0.5f) / 6f).roundToInt()
                    assertEquals("Wrong background for ${mode.label}", reference.getPixel(0, 0), bitmap.getPixel(x, y))
                    val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP,
                        left + (right - left) * index / 6f, y.toFloat(), 0)
                    try { chart.onTouchEvent(event) } finally { event.recycle() }
                    assertTrue(chart.contentDescription.toString().contains("Mode: ${mode.label}"))
                }
            } finally { bitmap.recycle(); reference.recycle() }
        }
    }

    @Test fun dailyUnknownLegendTracksVisibleTemperatureSamples() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val known = Reading(10_000L, "Synthetic", "device", 20.0, null, runningState = "OFF")
            val unknown = known.copy(timestampMs = 20_000L, runningState = null)
            val cases = listOf(
                listOf(known) to false,
                listOf(unknown.copy(timestampMs = -1L), known) to false,
                listOf(known, unknown.copy(temperatureC = null)) to false,
                listOf(known, unknown) to true,
            )
            val chart = ReadingChartView(context).apply { dayStartMs = 0; dayEndMs = 50_000 }
            chart.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY))
            chart.layout(0, 0, 1000, 700)
            for ((rows, expected) in cases) {
                chart.readings = rows
                val bitmap = Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888)
                try {
                    chart.draw(Canvas(bitmap))
                    val legendBottom = (25f * context.resources.displayMetrics.density).toInt()
                    val hasUnknown = (0 until legendBottom).any { y ->
                        (0 until bitmap.width).any { x -> bitmap.getPixel(x, y) == HvacActivity.UNKNOWN.color }
                    }
                    assertEquals("Unknown key must follow the displayed samples", expected, hasUnknown)
                } finally { bitmap.recycle() }
            }
        }
    }

    @Test fun dailyGraphDrawsAllActivityShapesAndExposesSampleStateOnTap() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val states = listOf("HEATING", "COOLING", "OFF", null)
            val rows = states.mapIndexed { index, state ->
                Reading((index + 1) * 10_000L, "Synthetic", "device", 20.0, null, runningState = state)
            }
            val chart = ReadingChartView(context).apply {
                dayStartMs = 0
                dayEndMs = 50_000
                readings = rows
                useFahrenheit = false
            }
            chart.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY))
            chart.layout(0, 0, 1000, 700)
            val bitmap = Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888)
            try {
                chart.draw(Canvas(bitmap))
                // Synthetic-only artifact for an optional host-side visual check after the test.
                java.io.File(context.getExternalFilesDir(null), "hvac-chart-test.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                val density = context.resources.displayMetrics.density
                val left = 48f * density
                val right = 1000 - left
                val top = 25f * density
                val bottom = 700 - 34f * density
                val y = bottom - (bottom - top) / 3f // 20C with scale 19..22C.
                rows.forEach { row ->
                    val activity = HvacActivity.fromReported(row.runningState)
                    val x = left + (right - left) * row.timestampMs / 50_000f
                    val margin = (5 * density).roundToInt()
                    val colored = (x.roundToInt() - margin..x.roundToInt() + margin).any { px ->
                        (y.roundToInt() - margin..y.roundToInt() + margin).any { py ->
                            bitmap.getPixel(px, py) == activity.color
                        }
                    }
                    assertTrue("Missing ${activity.label} marker", colored)
                    val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, x, y, 0)
                    try { chart.onTouchEvent(event) } finally { event.recycle() }
                    assertTrue(chart.contentDescription.toString().contains("HVAC: ${activity.label} @"))
                }
            } finally { bitmap.recycle() }
        }
    }
}
