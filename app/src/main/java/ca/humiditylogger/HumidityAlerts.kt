package ca.humiditylogger

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object HumidityAlerts {
    private const val PREFS = "humidity_alerts"
    private const val CHANNEL_ID = "humidity_alerts"
    private const val NOTIFICATION_ID = 4102
    private const val SAMPLE_INTERVAL_MS = 15L * 60L * 1000L

    data class Settings(
        val enabled: Boolean,
        val low: Int,
        val high: Int,
        val durationMinutes: Int,
    )

    fun settings(context: Context): Settings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Settings(
            enabled = prefs.getBoolean("enabled", false),
            low = prefs.getInt("low", 30),
            high = prefs.getInt("high", 60),
            durationMinutes = prefs.getInt("duration", 60),
        )
    }

    fun save(context: Context, settings: Settings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", settings.enabled)
            .putInt("low", settings.low)
            .putInt("high", settings.high)
            .putInt("duration", settings.durationMinutes)
            .apply()
        if (!settings.enabled) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("active_state").apply()
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }
    }

    @SuppressLint("MissingPermission")
    fun evaluate(context: Context, store: ReadingStore) {
        val settings = settings(context)
        if (!settings.enabled) return
        val indoor = store.recent(200).filter { ThermostatSelection.matches(context, it) }
            .filter { it.humidityPercent != null }
        val latest = indoor.lastOrNull() ?: return
        val durationMs = settings.durationMinutes * 60_000L
        val cutoff = latest.timestampMs - durationMs
        val window = indoor.filter { it.timestampMs >= cutoff }
        val coversDuration = window.firstOrNull()?.timestampMs?.let {
            latest.timestampMs - it >= durationMs - SAMPLE_INTERVAL_MS
        } == true
        val state = when {
            coversDuration && window.all { it.humidityPercent!! >= settings.high } -> "high"
            coversDuration && window.all { it.humidityPercent!! <= settings.low } -> "low"
            else -> "normal"
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getString("active_state", "normal")
        if (state == "normal") {
            if (previous != "normal") NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            prefs.edit().putString("active_state", state).apply()
            return
        }
        if (state == previous || !canNotify(context)) return
        createChannel(context)
        val humidity = latest.humidityPercent!!.toInt()
        val title = if (state == "high") "Indoor humidity is high" else "Indoor humidity is low"
        val boundary = if (state == "high") settings.high else settings.low
        val message = "$humidity% for about ${settings.durationMinutes} minutes (threshold $boundary%)"
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setContentIntent(openApp)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build(),
            )
        } catch (_: SecurityException) {
            return
        }
        prefs.edit().putString("active_state", state).apply()
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Humidity alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Sustained high or low indoor humidity" }
        )
    }
}
