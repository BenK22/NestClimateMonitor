package ca.humiditylogger

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

class ClimateWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) =
        ids.forEach { updateWidget(context, manager, it) }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        updateWidget(context, manager, id)

    override fun onDeleted(context: Context, ids: IntArray) =
        ids.forEach { WidgetAppearance.remove(context, it) }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, ClimateWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { updateWidget(context, manager, it) }
            GraphWidgetProvider.updateAll(context)
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val options = manager.getAppWidgetOptions(id)
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).coerceAtLeast(110)
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).coerceAtLeast(48)
            val showOutside = widthDp >= 220
            val showGraph = showOutside && heightDp >= 100
            val readings = ReadingStore(context).use { it.recent(400) }
            val indoor = readings.lastOrNull { ThermostatSelection.matches(context, it) }
            val outdoor = readings.lastOrNull { WeatherClient.isOutdoor(it.source) }
            val fahrenheit = WidgetGraph.useFahrenheit(context)
            val views = RemoteViews(context.packageName, R.layout.widget_climate).apply {
                setInt(R.id.widget_root, "setBackgroundResource", WidgetAppearance.background(context, id))
                setTextViewText(R.id.widget_indoor_temp, formatTemp(indoor?.temperatureC, fahrenheit))
                setTextViewText(R.id.widget_indoor_humidity, formatHumidity(indoor?.humidityPercent))
                setTextViewText(R.id.widget_outdoor_temp, formatTemp(outdoor?.temperatureC, fahrenheit))
                setTextViewText(R.id.widget_outdoor_humidity, formatHumidity(outdoor?.humidityPercent))
                setViewVisibility(R.id.widget_outdoor_temp_tile, if (showOutside) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.widget_outdoor_humidity_tile, if (showOutside) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.widget_graph, if (showGraph) View.VISIBLE else View.GONE)
                if (showGraph) setImageViewBitmap(
                    R.id.widget_graph,
                    WidgetGraph.render(context, readings, widthDp, (heightDp * 0.48).roundToInt()),
                )
                val latest = indoor?.timestampMs ?: outdoor?.timestampMs
                setTextViewText(
                    R.id.widget_updated,
                    latest?.let { "Updated ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))}" }
                        ?: "Waiting for first reading",
                )
                val age = latest?.let { System.currentTimeMillis() - it } ?: Long.MAX_VALUE
                setTextColor(
                    R.id.widget_updated,
                    when {
                        age < 30L * 60L * 1000L -> Color.rgb(112, 219, 181)
                        age < 60L * 60L * 1000L -> Color.rgb(255, 190, 92)
                        else -> Color.rgb(255, 112, 96)
                    },
                )
                setOnClickPendingIntent(R.id.widget_root, WidgetAppearance.launchApp(context))
            }
            manager.updateAppWidget(id, views)
        }

        private fun formatTemp(celsius: Double?, fahrenheit: Boolean): String {
            if (celsius == null) return "--"
            val value = if (fahrenheit) celsius * 9.0 / 5.0 + 32.0 else celsius
            return "${value.roundToInt()}°"
        }

        private fun formatHumidity(value: Double?) = value?.let { "${it.roundToInt()}%" } ?: "--"
    }
}
