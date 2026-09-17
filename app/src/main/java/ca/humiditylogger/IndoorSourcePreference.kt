package ca.humiditylogger

import android.content.Context

enum class IndoorSource {
    DEVICE_ACCESS,
    GOOGLE_HOME,
}

object IndoorSourcePreference {
    private const val PREFS = "indoor_source"
    private const val KEY_SOURCE = "source"

    fun selected(context: Context): IndoorSource {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SOURCE, null)
        return resolve(saved, DeviceAccessStore(context).isConnected())
    }

    fun set(context: Context, source: IndoorSource) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SOURCE, source.name)
            .apply()
    }

    internal fun resolve(saved: String?, deviceAccessConnected: Boolean): IndoorSource =
        runCatching { saved?.let(IndoorSource::valueOf) }.getOrNull()
            ?: if (deviceAccessConnected) IndoorSource.DEVICE_ACCESS else IndoorSource.GOOGLE_HOME
}
