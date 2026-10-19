package ca.humiditylogger

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/** Synthetic chart-only tests never open the user's database or change logging settings. */
@RunWith(AndroidJUnit4::class)
class HvacChartTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

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
