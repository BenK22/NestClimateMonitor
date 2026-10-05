package ca.humiditylogger

import android.content.Context

/** User-selected display label and latitude/longitude in decimal degrees, not GPS tracking state. */
data class WeatherLocation(
    val label: String,
    val latitude: Double,
    val longitude: Double,
) {
    /** Label-based outdoor history key; changing the label creates a different displayed series. */
    val readingSource: String get() = "${WeatherClient.SOURCE_PREFIX}$label"
}

/** Stores one outdoor location; new installations use a city-level, non-personal default. */
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

    /** Reads saved coordinates as exact Double bit patterns, falling back to the default city. */
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

    /** Saves an already geocoded location; does not fetch weather or discard older-location rows. */
    fun set(context: Context, location: WeatherLocation) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LABEL, location.label)
            .putLong(KEY_LATITUDE, location.latitude.toBits())
            .putLong(KEY_LONGITUDE, location.longitude.toBits())
            .apply()
    }
}
