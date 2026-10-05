package ca.humiditylogger

import android.content.Context

/** Optional rolling 365-day retention across all devices/locations; unlimited is the default. */
object DataRetention {
    private const val PREFS = "data_retention"
    private const val KEY_ONE_YEAR = "one_year"
    private const val YEAR_MS = 365L * 24L * 60L * 60L * 1000L

    /** Whether the saved one-year policy is enabled. */
    fun isOneYear(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ONE_YEAR, false)

    /** Saves policy without deleting rows; [apply] performs pruning explicitly. */
    fun setOneYear(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ONE_YEAR, enabled)
            .apply()
    }

    /** Deletes rows strictly before the rolling cutoff if enabled; caller owns [store]. */
    fun apply(context: Context, store: ReadingStore, nowMs: Long = System.currentTimeMillis()) {
        if (isOneYear(context)) store.deleteBefore(cutoff(nowMs))
    }

    /** Epoch-millisecond cutoff using 365 elapsed days, not calendar-year arithmetic. */
    internal fun cutoff(nowMs: Long): Long = nowMs - YEAR_MS
}
