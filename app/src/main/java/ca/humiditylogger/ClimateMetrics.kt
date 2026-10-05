package ca.humiditylogger

import kotlin.math.ln

/** Display-only climate estimates; comfort labels are heuristics, not health or building advice. */
object ClimateMetrics {
    /** Magnus-formula dew point in Celsius; returns null for humidity outside (0, 100]. */
    fun dewPointC(temperatureC: Double, humidityPercent: Double): Double? {
        if (humidityPercent <= 0.0 || humidityPercent > 100.0) return null
        val a = 17.62
        val b = 243.12
        val gamma = ln(humidityPercent / 100.0) + (a * temperatureC) / (b + temperatureC)
        return (b * gamma) / (a - gamma)
    }

    /** Classifies humidity first, then temperature; inputs are Celsius and percent. */
    fun comfortStatus(temperatureC: Double, humidityPercent: Double): String = when {
        humidityPercent >= 70.0 -> "Condensation risk"
        humidityPercent > 60.0 -> "Humid"
        humidityPercent < 30.0 -> "Dry"
        temperatureC < 18.0 -> "Cool"
        temperatureC > 25.0 -> "Warm"
        else -> "Comfortable"
    }
}
