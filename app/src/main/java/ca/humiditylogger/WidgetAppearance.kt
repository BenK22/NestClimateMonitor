package ca.humiditylogger

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Per-widget background preferences and immutable app-launch intents; never initiates sampling. */
object WidgetAppearance {
    private const val PREFS = "widget_appearance"

    /** Saves appearance for one launcher widget ID, not globally for every widget. */
    fun save(context: Context, id: Int, transparent: Boolean, border: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("transparent_$id", transparent)
            .putBoolean("border_$id", border)
            .apply()
    }

    /** Removes settings when the launcher deletes that widget. */
    fun remove(context: Context, id: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("transparent_$id")
            .remove("border_$id")
            .apply()
    }

    /** Drawable for saved appearance; new widgets default to opaque with a border. */
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

    /** Opens the dashboard only; clicking a widget does not request a measurement. */
    fun launchApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
