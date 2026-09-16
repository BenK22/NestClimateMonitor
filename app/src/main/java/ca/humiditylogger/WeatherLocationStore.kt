package ca.humiditylogger

import android.content.Context

data class WeatherLocation(
    val label: String,
    val latitude: Double,
    val longitude: Double,
) {
    val readingSource: String get() = "${WeatherClient.SOURCE_PREFIX}$label"
}

object WeatherLocationStore {
    private const val PREFS = "weather_location"
    private const val KEY_LABEL = "label"
    private const val KEY_LATITUDE = "latitude"
    private const val KEY_LONGITUDE = "longitude"

    private val defaultLocation = WeatherLocation(
        label = "St. Catharines, Ontario",
        latitude = 43.1594,
        longitude = -79.2469,
    )

    fun get(context: Context): WeatherLocation {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return WeatherLocation(
            label = prefs.getString(KEY_LABEL, defaultLocation.label) ?: defaultLocation.label,
            latitude = Double.fromBits(
                prefs.getLong(KEY_LATITUDE, defaultLocation.latitude.toBits())
            ),
            longitude = Double.fromBits(
                prefs.getLong(KEY_LONGITUDE, defaultLocation.longitude.toBits())
            ),
        )
    }

    fun set(context: Context, location: WeatherLocation) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LABEL, location.label)
            .putLong(KEY_LATITUDE, location.latitude.toBits())
            .putLong(KEY_LONGITUDE, location.longitude.toBits())
            .apply()
    }
}
