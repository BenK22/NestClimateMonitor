package ca.humiditylogger

data class Reading(
    val timestampMs: Long,
    val source: String,
    val deviceId: String? = null,
    val temperatureC: Double?,
    val humidityPercent: Double?,
    val heatingSetpointC: Double? = null,
    val coolingSetpointC: Double? = null,
    val systemMode: String? = null,
    val runningState: String? = null,
    val holdState: String? = null,
    val changeSource: String? = null,
    val ecoState: String? = null,
) {
    fun hasClimateMeasurement(): Boolean = temperatureC != null || humidityPercent != null
}
