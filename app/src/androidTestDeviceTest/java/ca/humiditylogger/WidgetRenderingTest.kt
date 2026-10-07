package ca.humiditylogger

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
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
}
