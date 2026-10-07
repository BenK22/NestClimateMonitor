package ca.humiditylogger

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
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
            // Exact dimensions avoid fitCenter letterboxing on modern hosts. Older hosts receive
            // separate orientation views; MIN_WIDTH and MIN_HEIGHT are not usually one real size.
            val sizes = if (Build.VERSION.SDK_INT >= 31) {
                BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
                    ?.filter { it.width.isFinite() && it.height.isFinite() && it.width > 0 && it.height > 0 }
                    ?.distinct()?.take(2)
            } else null
            val views = if (Build.VERSION.SDK_INT >= 31 && !sizes.isNullOrEmpty()) {
                RemoteViews(sizes.associateWith { viewsFor(it.width.toInt(), it.height.toInt()) })
            } else {
                val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).coerceAtLeast(110)
                val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).coerceAtLeast(80)
                val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
                val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                val portrait = WidgetGraphSizing.orientationSize(minWidth, minHeight, maxWidth, maxHeight, false)
                val landscape = WidgetGraphSizing.orientationSize(minWidth, minHeight, maxWidth, maxHeight, true)
                RemoteViews(viewsFor(landscape.widthDp, landscape.heightDp), viewsFor(portrait.widthDp, portrait.heightDp))
            }
            manager.updateAppWidget(id, views)
        }
    }
}
