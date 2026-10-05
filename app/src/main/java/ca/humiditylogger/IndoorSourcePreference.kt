package ca.humiditylogger

import android.content.Context

/** Mutually exclusive indoor adapters; provider choice is separate from device selection. */
enum class IndoorSource {
    DEVICE_ACCESS,
    GOOGLE_HOME,
}

/** Persists explicit provider choice; a saved choice always wins over connectivity-based defaults. */
object IndoorSourcePreference {
    private const val PREFS = "indoor_source"
    private const val KEY_SOURCE = "source"

    /** Resolves saved choice, defaulting to Device Access only when locally connected. */
    fun selected(context: Context): IndoorSource {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SOURCE, null)
        return resolve(saved, DeviceAccessStore(context).isConnected())
    }

    /** Saves provider choice only; neither starts a refresh nor deletes the other provider's grant. */
    fun set(context: Context, source: IndoorSource) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SOURCE, source.name)
            .apply()
    }

    /** Missing/unknown saved values use defaults; there is no automatic failure fallback. */
    internal fun resolve(saved: String?, deviceAccessConnected: Boolean): IndoorSource =
        runCatching { saved?.let(IndoorSource::valueOf) }.getOrNull()
            ?: if (deviceAccessConnected) IndoorSource.DEVICE_ACCESS else IndoorSource.GOOGLE_HOME
}
