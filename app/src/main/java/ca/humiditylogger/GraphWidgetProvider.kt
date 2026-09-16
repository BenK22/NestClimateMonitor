package ca.humiditylogger

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews

class GraphWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) =
        ids.forEach { updateWidget(context, manager, it) }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        updateWidget(context, manager, id)

    override fun onDeleted(context: Context, ids: IntArray) =
        ids.forEach { WidgetAppearance.remove(context, it) }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, GraphWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { updateWidget(context, manager, it) }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val options = manager.getAppWidgetOptions(id)
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).coerceAtLeast(110)
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).coerceAtLeast(80)
            val readings = ReadingStore(context).use { it.recent(400) }
            val views = RemoteViews(context.packageName, R.layout.widget_graph).apply {
                setInt(R.id.graph_widget_root, "setBackgroundResource", WidgetAppearance.background(context, id))
                setImageViewBitmap(R.id.graph_widget_image, WidgetGraph.render(context, readings, widthDp, heightDp))
                setOnClickPendingIntent(R.id.graph_widget_root, WidgetAppearance.launchApp(context))
            }
            manager.updateAppWidget(id, views)
        }
    }
}
