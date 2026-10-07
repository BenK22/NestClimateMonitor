package ca.humiditylogger

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Build
import android.util.SizeF
import android.widget.RemoteViews
import androidx.core.os.BundleCompat

/** Resizable graph-only RemoteViews host; uses the same local history/filter/renderer as climate tiles. */
class GraphWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) =
        ids.forEach { updateWidget(context, manager, it) }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        updateWidget(context, manager, id)

    override fun onDeleted(context: Context, ids: IntArray) =
        ids.forEach { WidgetAppearance.remove(context, it) }

    companion object {
        /** Re-renders all graph-only instances; does not enqueue network or sampling work. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, GraphWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { updateWidget(context, manager, it) }
        }

        /** Fits a six-hour bitmap into one launcher's widget bounds with per-instance appearance. */
        fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val options = manager.getAppWidgetOptions(id)
            val now = System.currentTimeMillis()
            val readings = ReadingStore(context).use { it.widgetHistory(now) }
            fun viewsFor(width: Int, height: Int): RemoteViews {
                val contentSize = WidgetGraphSizing.graphOnlyContentSize(width, height)
                return RemoteViews(context.packageName, R.layout.widget_graph).apply {
                    setInt(R.id.graph_widget_root, "setBackgroundResource", WidgetAppearance.background(context, id))
                    setImageViewBitmap(
                        R.id.graph_widget_image,
                        WidgetGraph.render(context, readings, contentSize.widthDp, contentSize.heightDp, now),
                    )
                    setOnClickPendingIntent(R.id.graph_widget_root, WidgetAppearance.launchApp(context))
                }
            }
            // Select a single bitmap for the current orientation. A host can choose the wrong
            // variant from a size map when its measured box differs from its reported bounds.
            val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val estimate = WidgetGraphSizing.orientationSize(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).coerceAtLeast(110),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).coerceAtLeast(80),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
                landscape,
            )
            val sizes = if (Build.VERSION.SDK_INT >= 31) {
                BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
                    ?.filter { it.width.isFinite() && it.height.isFinite() && it.width > 0 && it.height > 0 }
                    ?.map { WidgetGraphSizing.ContentSize(it.width.toInt(), it.height.toInt()) }
            } else null
            val size = WidgetGraphSizing.closestSize(sizes.orEmpty(), estimate)
            manager.updateAppWidget(id, viewsFor(size.widthDp, size.heightDp))
        }
    }
}
