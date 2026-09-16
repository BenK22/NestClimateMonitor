package ca.humiditylogger

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object WidgetAppearance {
    private const val PREFS = "widget_appearance"

    fun save(context: Context, id: Int, transparent: Boolean, border: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("transparent_$id", transparent)
            .putBoolean("border_$id", border)
            .apply()
    }

    fun remove(context: Context, id: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("transparent_$id")
            .remove("border_$id")
            .apply()
    }

    fun background(context: Context, id: Int): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val transparent = prefs.getBoolean("transparent_$id", false)
        val border = prefs.getBoolean("border_$id", true)
        return when {
            transparent && border -> R.drawable.widget_background_transparent_border
            transparent -> R.drawable.widget_background_transparent
            border -> R.drawable.widget_background
            else -> R.drawable.widget_background_borderless
        }
    }

    fun launchApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
