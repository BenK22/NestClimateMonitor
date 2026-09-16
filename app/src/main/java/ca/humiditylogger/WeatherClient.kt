package ca.humiditylogger

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object WeatherClient {
    const val SOURCE_PREFIX = "Outdoor · "

    fun isOutdoor(source: String): Boolean = source.startsWith(SOURCE_PREFIX)

    suspend fun fetchCurrent(context: android.content.Context): Reading = withContext(Dispatchers.IO) {
        val location = WeatherLocationStore.get(context)
        val endpoint = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${location.latitude}&longitude=${location.longitude}" +
            "&current=temperature_2m,relative_humidity_2m" +
            "&timezone=America%2FToronto"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "HumidityLogger/0.1")
            if (connection.responseCode !in 200..299) {
                error("Weather service returned HTTP ${connection.responseCode}")
            }
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(json).getJSONObject("current")
            Reading(
                timestampMs = System.currentTimeMillis(),
                source = location.readingSource,
                temperatureC = current.optDouble("temperature_2m").takeUnless { it.isNaN() },
                humidityPercent = current.optDouble("relative_humidity_2m")
                    .takeUnless { it.isNaN() },
            )
        } finally {
            connection.disconnect()
        }
    }
}
