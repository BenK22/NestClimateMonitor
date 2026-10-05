package ca.humiditylogger

/**
 * Immutable snapshot shared by both indoor adapters, weather, persistence and CSV backup.
 *
 * Temperatures are always Celsius in storage; Fahrenheit conversion belongs to presentation.
 * Missing traits remain null rather than becoming zero. Status strings are provider values,
 * not commands: the app never changes thermostat settings.
 *
 * @property timestampMs Collection time as Unix epoch milliseconds, not provider event time.
 * @property source Display label; outdoor labels use [WeatherClient.SOURCE_PREFIX].
 * @property deviceId Provider identity, or null for weather/older imported readings.
 * @property temperatureC Ambient temperature in degrees Celsius, if exposed.
 * @property humidityPercent Relative humidity in percent (0–100), if exposed.
 * @property heatingSetpointC Reported heating target in Celsius.
 * @property coolingSetpointC Reported cooling target in Celsius.
 * @property systemMode Reported thermostat mode, such as COOL or HEAT.
 * @property runningState Reported HVAC activity, which can differ from the selected mode.
 * @property holdState Provider's setpoint-hold status, if available.
 * @property changeSource Provider's attribution of a setpoint change, if available.
 * @property ecoState Provider's Eco mode status, if available.
 */
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
    /** Whether this snapshot contains an ambient measurement, rather than only metadata. */
    fun hasClimateMeasurement(): Boolean = temperatureC != null || humidityPercent != null
}
