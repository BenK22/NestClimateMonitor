package ca.humiditylogger

import android.content.Context

object DataRetention {
    private const val PREFS = "data_retention"
    private const val KEY_ONE_YEAR = "one_year"
    private const val YEAR_MS = 365L * 24L * 60L * 60L * 1000L

    fun isOneYear(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ONE_YEAR, false)

    fun setOneYear(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ONE_YEAR, enabled)
            .apply()
    }

    fun apply(context: Context, store: ReadingStore, nowMs: Long = System.currentTimeMillis()) {
        if (isOneYear(context)) store.deleteBefore(cutoff(nowMs))
    }

    internal fun cutoff(nowMs: Long): Long = nowMs - YEAR_MS
}
