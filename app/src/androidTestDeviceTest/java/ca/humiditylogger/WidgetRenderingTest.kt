package ca.humiditylogger

import android.content.Context
import android.content.res.Configuration
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Bitmap and RemoteViews tests operate only in the isolated device-test package. */
@RunWith(AndroidJUnit4::class)
class WidgetRenderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun chooseSyntheticIndoorSource() {
        IndoorSourcePreference.set(context, IndoorSource.GOOGLE_HOME)
        ThermostatSelection.setSelectedSource(context, null)
    }

    @After fun clearSyntheticSelection() {
        context.getSharedPreferences("indoor_source", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("thermostat_selection", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun singleTemperatureOrHumidityIsVisibleWithoutTwoSamples() {
        val now = 100_000_000L
        val cases = listOf(
            Reading(now - 3_600_000L, "Synthetic", null, 20.0, null) to Color.rgb(255, 133, 112),
            Reading(now - 3_600_000L, "Synthetic", null, null, 45.0) to Color.rgb(86, 190, 255),
        )
        for ((reading, expected) in cases) {
            val bitmap = WidgetGraph.render(context, listOf(reading), 280, 140, now)
            try {
                // Exclude the colored legend; require an actual plotted measurement.
                var found = false
                for (y in bitmap.height / 3 until bitmap.height * 4 / 5) {
                    for (x in bitmap.width / 5 until bitmap.width * 9 / 10) {
                        if (bitmap.getPixel(x, y) == expected) found = true
                    }
                }
                assertTrue("Single measurement is missing from the plot", found)
            } finally { bitmap.recycle() }
        }
    }

    @Test fun crossingHistoryReachesTheLeftPlotEdgeButStaleOnlyHistoryDoesNot() {
        val now = 100_000_000L
        val start = now - WidgetReadingSelection.GRAPH_WINDOW_MS
        val prior = Reading(start - 3_600_000L, "Synthetic", null, 20.0, null)
        val next = Reading(start + 3_600_000L, "Synthetic", null, 20.0, null)
        val color = Color.rgb(255, 133, 112)
        for ((rows, shouldReachEdge) in listOf(listOf(prior, next) to true, listOf(prior) to false)) {
            val bitmap = WidgetGraph.render(context, rows, 340, 140, now)
            try {
                val columns = plottedColumns(bitmap, color, bitmap.width / 8, bitmap.width / 5)
                assertEquals(shouldReachEdge, columns.isNotEmpty())
            } finally { bitmap.recycle() }
        }
    }

    @Test fun outdoorPathsAndLegendAreDashedWhileIndoorPathsStaySolid() {
        val now = 100_000_000L
        val start = now - WidgetReadingSelection.GRAPH_WINDOW_MS
        val outdoor = WeatherLocationStore.get(context).readingSource
        val cases = listOf(
            Triple("Synthetic", Color.rgb(86, 190, 255), false),
            Triple(outdoor, Color.rgb(116, 222, 211), true),
        )
        for ((source, color, dashed) in cases) {
            val rows = listOf(Reading(start, source, null, null, 45.0), Reading(now, source, null, null, 45.0))
            val bitmap = WidgetGraph.render(context, rows, 340, 140, now)
            try {
                val plotWidth = bitmap.width / 2
                val colored = plottedColumns(bitmap, color, bitmap.width / 4, bitmap.width * 3 / 4).size
                assertTrue(if (dashed) colored in (plotWidth / 3)..(plotWidth * 4 / 5) else colored > plotWidth * 9 / 10)
                val legend = (0 until bitmap.width).filter { x ->
                    (0 until bitmap.height / 6).any { y -> bitmap.getPixel(x, y) == color }
                }
                val groups = if (legend.isEmpty()) 0 else 1 + legend.zipWithNext().count { (a, b) -> b > a + 1 }
                assertTrue(if (dashed) groups >= 2 else groups == 1)
            } finally { bitmap.recycle() }
        }
    }

    private fun plottedColumns(bitmap: Bitmap, color: Int, left: Int, right: Int): List<Int> =
        (left until right).filter { x ->
            (bitmap.height / 3 until bitmap.height * 4 / 5).any { y -> bitmap.getPixel(x, y) == color }
        }

    @Test fun pickerLayoutsInflateAsRemoteViewsWithSampleContent() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (layout in listOf(R.layout.widget_graph_preview, R.layout.widget_climate_preview)) {
                val root = RemoteViews(context.packageName, layout).apply(context, FrameLayout(context))
                root.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 600, 320)
                assertTrue(root.measuredHeight > 0)
                if (layout == R.layout.widget_graph_preview) {
                    assertNotNull(root.findViewById<ImageView>(R.id.widget_preview_graph).drawable)
                }
            }
        }
    }

    @Test fun liveGraphFillsHostBoxEvenWhenLauncherBitmapEstimateIsTooWide() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val bitmap = Bitmap.createBitmap(1200, 100, Bitmap.Config.ARGB_8888)
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_graph).apply {
                    setImageViewBitmap(R.id.graph_widget_image, bitmap)
                }
                val root = views.apply(context, FrameLayout(context))
                root.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 600, 240)
                val image = root.findViewById<ImageView>(R.id.graph_widget_image)
                val plotted = RectF(image.drawable.bounds)
                image.imageMatrix.mapRect(plotted)
                assertEquals((image.width - image.paddingLeft - image.paddingRight).toFloat(), plotted.width(), 1f)
                assertEquals((image.height - image.paddingTop - image.paddingBottom).toFloat(), plotted.height(), 1f)
            } finally { bitmap.recycle() }
        }
    }

    @Test fun responsiveHostSelectsBitmapFromMeasuredBoxNotCallingAppOrientation() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val caller = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
            })
            val provider = AppWidgetManager.getInstance(context).installedProviders.first {
                it.provider.packageName == context.packageName && it.provider.className == GraphWidgetProvider::class.java.name
            }
            val portrait = WidgetGraphSizing.ContentSize(395, 150)
            val landscape = WidgetGraphSizing.ContentSize(751, 64)
            val breakpoint = WidgetGraphSizing.orientationBreakpoint(portrait, landscape)
            val red = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            val blue = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            try {
                fun views(bitmap: Bitmap) = RemoteViews(context.packageName, R.layout.widget_graph).apply {
                    setImageViewBitmap(R.id.graph_widget_image, bitmap)
                }
                val responsive = RemoteViews(mapOf(SizeF(1f, 1f) to views(red),
                    SizeF(breakpoint.widthDp.toFloat(), breakpoint.heightDp.toFloat()) to views(blue)))
                val density = context.resources.displayMetrics.density
                for ((width, height, expected) in listOf(Triple(395, 129, Color.RED), Triple(735, 48, Color.BLUE))) {
                    val host = AppWidgetHostView(caller).apply {
                        setAppWidget(AppWidgetManager.INVALID_APPWIDGET_ID, provider)
                        updateAppWidget(responsive)
                    }
                    val widthPx = (width * density).toInt()
                    val heightPx = (height * density).toInt()
                    host.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY))
                    host.layout(0, 0, widthPx, heightPx)
                    val image = host.findViewById<ImageView>(R.id.graph_widget_image)
                    assertEquals(expected, (image.drawable as BitmapDrawable).bitmap.getPixel(0, 0))
                }
            } finally { red.recycle(); blue.recycle() }
        }
    }
}
