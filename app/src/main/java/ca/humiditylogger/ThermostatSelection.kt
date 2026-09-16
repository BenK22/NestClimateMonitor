package ca.humiditylogger

import android.content.Context

object ThermostatSelection {
    private const val PREFS = "thermostat_selection"
    private const val KEY_SOURCE = "source"

    fun selectedSource(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SOURCE, null)
            ?.takeIf { it.isNotBlank() }

    fun setSelectedSource(context: Context, source: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (source == null) remove(KEY_SOURCE) else putString(KEY_SOURCE, source)
        }.apply()
    }

    fun matches(context: Context, reading: Reading): Boolean =
        !WeatherClient.isOutdoor(reading.source) &&
            (selectedSource(context) == null || selectedSource(context) == reading.source)

    fun filter(context: Context, readings: List<Reading>): List<Reading> =
        readings.filter { matches(context, it) }
}
