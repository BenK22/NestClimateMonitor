package ca.humiditylogger

import org.json.JSONObject

object DeviceAccessParser {
    fun thermostats(json: String, timestampMs: Long = System.currentTimeMillis()): List<Reading> {
        val devices = JSONObject(json).optJSONArray("devices") ?: return emptyList()
        return buildList {
            for (index in 0 until devices.length()) {
                val device = devices.getJSONObject(index)
                if (device.optString("type") != THERMOSTAT_TYPE) continue
                val traits = device.optJSONObject("traits") ?: JSONObject()
                val resourceName = device.optString("name")
                val info = traits.optJSONObject(INFO)
                val room = device.optJSONArray("parentRelations")
                    ?.optJSONObject(0)?.optString("displayName")?.takeIf { it.isNotBlank() }
                val source = info?.optString("customName")?.takeIf { it.isNotBlank() }
                    ?: room?.let { "$it Thermostat" }
                    ?: "Nest Thermostat"
                val temperature = traits.optJSONObject(TEMPERATURE)
                    ?.optionalDouble("ambientTemperatureCelsius")
                val humidity = traits.optJSONObject(HUMIDITY)
                    ?.optionalDouble("ambientHumidityPercent")
                val setpoints = traits.optJSONObject(SETPOINT)
                val mode = traits.optJSONObject(MODE)?.optString("mode")?.nullIfBlank()
                val hvac = traits.optJSONObject(HVAC)?.optString("status")?.nullIfBlank()
                val eco = traits.optJSONObject(ECO)?.optString("mode")?.nullIfBlank()
                add(
                    Reading(
                        timestampMs = timestampMs,
                        source = source,
                        deviceId = resourceName,
                        temperatureC = temperature,
                        humidityPercent = humidity,
                        heatingSetpointC = setpoints?.optionalDouble("heatCelsius"),
                        coolingSetpointC = setpoints?.optionalDouble("coolCelsius"),
                        systemMode = mode,
                        runningState = hvac,
                        ecoState = eco,
                    )
                )
            }
        }
    }

    private fun JSONObject.optionalDouble(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeUnless { it.isNaN() } else null

    private fun String.nullIfBlank(): String? = takeIf { it.isNotBlank() }

    private const val THERMOSTAT_TYPE = "sdm.devices.types.THERMOSTAT"
    private const val INFO = "sdm.devices.traits.Info"
    private const val TEMPERATURE = "sdm.devices.traits.Temperature"
    private const val HUMIDITY = "sdm.devices.traits.Humidity"
    private const val SETPOINT = "sdm.devices.traits.ThermostatTemperatureSetpoint"
    private const val MODE = "sdm.devices.traits.ThermostatMode"
    private const val HVAC = "sdm.devices.traits.ThermostatHvac"
    private const val ECO = "sdm.devices.traits.ThermostatEco"
}
