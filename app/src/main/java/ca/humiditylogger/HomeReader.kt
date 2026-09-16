package ca.humiditylogger

import android.content.Context
import com.google.home.FactoryRegistry
import com.google.home.Home
import com.google.home.HomeClient
import com.google.home.HomeConfig
import com.google.home.PermissionsState
import com.google.home.google.ExtendedThermostat
import com.google.home.matter.standard.HumiditySensorDevice
import com.google.home.matter.standard.RelativeHumidityMeasurement
import com.google.home.matter.standard.RootNodeDevice
import com.google.home.matter.standard.TemperatureMeasurement
import com.google.home.matter.standard.TemperatureSensorDevice
import com.google.home.matter.standard.Thermostat
import com.google.home.matter.standard.ThermostatDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class HomeReader(context: Context) {
    private val registry = FactoryRegistry(
        types = listOf(
            RootNodeDevice,
            ThermostatDevice,
            TemperatureSensorDevice,
            HumiditySensorDevice,
        ),
        traits = listOf(
            Thermostat,
            ExtendedThermostat,
            TemperatureMeasurement,
            RelativeHumidityMeasurement,
        ),
    )

    val client: HomeClient = Home.getClient(
        context.applicationContext,
        homeConfig = HomeConfig(
            coroutineContext = Dispatchers.IO,
            factoryRegistry = registry,
            homePlatformScope = HomeConfig.HomePlatformScope.HOME_PLATFORM_SCOPE_VERSION_1,
        ),
    )

    suspend fun permissionState(): PermissionsState = client.hasPermissions().first {
        it != PermissionsState.PERMISSIONS_STATE_UNINITIALIZED
    }

    suspend fun sample(): SampleResult {
        val diagnostics = mutableListOf<String>()
        val readings = mutableListOf<Reading>()
        val now = System.currentTimeMillis()

        val structures = withTimeoutOrNull(HOME_SYNC_TIMEOUT_MS) {
            client.structures().first { it.isNotEmpty() }
        } ?: return SampleResult(
            readings,
            listOf("Google Home did not return a structure within 30 seconds."),
        )

        structures.forEach { structure ->
            diagnostics += "Home: ${structure.name}"
            val devices = withTimeoutOrNull(HOME_SYNC_TIMEOUT_MS) {
                structure.devices(enableMultipartDevices = false).first { it.isNotEmpty() }
            }.orEmpty()
            devices.forEach { device ->
                var temperature: Double? = null
                var humidity: Double? = null
                var heatingSetpoint: Double? = null
                var coolingSetpoint: Double? = null
                var systemMode: String? = null
                var runningState: String? = null
                var holdState: String? = null
                var changeSource: String? = null
                var ecoState: String? = null
                val typeNames = mutableListOf<String>()
                val traitNames = mutableSetOf<String>()

                val types = withTimeoutOrNull(HOME_SYNC_TIMEOUT_MS) {
                    device.types().first { it.isNotEmpty() }
                }.orEmpty()
                types.forEach { type ->
                    typeNames += type.factory.toString().substringAfterLast('.')
                    type.traits().forEach { trait ->
                        traitNames += trait.factory.toString().substringAfterLast('.')
                        when (trait) {
                            is RelativeHumidityMeasurement -> {
                                humidity = trait.measuredValue?.toDouble()?.div(100.0)
                            }
                            is TemperatureMeasurement -> {
                                temperature = trait.measuredValue?.toDouble()?.div(100.0)
                            }
                            is Thermostat -> {
                                if (temperature == null) {
                                    temperature = trait.localTemperature?.toDouble()?.div(100.0)
                                }
                                heatingSetpoint = trait.occupiedHeatingSetpoint
                                    ?.toDouble()?.div(100.0) ?: heatingSetpoint
                                coolingSetpoint = trait.occupiedCoolingSetpoint
                                    ?.toDouble()?.div(100.0) ?: coolingSetpoint
                                systemMode = simpleValue(trait.systemMode) ?: systemMode
                                runningState = simpleValue(trait.thermostatRunningMode)
                                    ?: simpleValue(trait.thermostatRunningState)
                                    ?: runningState
                                holdState = simpleValue(trait.temperatureSetpointHold) ?: holdState
                                changeSource = simpleValue(trait.setpointChangeSource) ?: changeSource
                            }
                            is ExtendedThermostat -> {
                                val target = trait.targetTemperatureSettings?.targetTemperature
                                heatingSetpoint = target?.heatingTarget
                                    ?.toDouble()?.div(100.0) ?: heatingSetpoint
                                coolingSetpoint = target?.coolingTarget
                                    ?.toDouble()?.div(100.0) ?: coolingSetpoint
                                systemMode = simpleValue(trait.extendedSystemMode) ?: systemMode
                                runningState = simpleValue(trait.extendedRunningMode) ?: runningState
                                changeSource = simpleValue(trait.extendedSetpointChangeSource)
                                    ?: changeSource
                                ecoState = simpleValue(trait.ecoModeState?.ecoMode) ?: ecoState
                            }
                        }
                    }
                }

                val isClimateDevice = temperature != null || humidity != null ||
                    heatingSetpoint != null || coolingSetpoint != null ||
                    typeNames.any {
                        it.contains("Thermostat", ignoreCase = true) ||
                            it.contains("TemperatureSensor", ignoreCase = true) ||
                            it.contains("HumiditySensor", ignoreCase = true)
                    }

                if (isClimateDevice) {
                    diagnostics += buildString {
                        append(device.name)
                        append("\n  types: ")
                        append(typeNames.joinToString())
                        append("\n  traits: ")
                        append(traitNames.joinToString())
                        append("\n  value: ")
                        append(temperature?.let { "%.1f°F".format(celsiusToFahrenheit(it)) }
                            ?: "no temperature")
                        append(", ")
                        append(humidity?.let { "%.1f%%".format(it) } ?: "no humidity")
                    }
                    readings += Reading(
                        timestampMs = now,
                        source = device.name,
                        deviceId = device.id.toString(),
                        temperatureC = temperature,
                        humidityPercent = humidity,
                        heatingSetpointC = heatingSetpoint,
                        coolingSetpointC = coolingSetpoint,
                        systemMode = systemMode,
                        runningState = runningState,
                        holdState = holdState,
                        changeSource = changeSource,
                        ecoState = ecoState,
                    )
                }
            }
        }
        return SampleResult(readings, diagnostics)
    }

    private fun simpleValue(value: Any?): String? = value?.toString()
        ?.substringAfterLast('.')
        ?.takeUnless { it.equals("null", ignoreCase = true) || it.isBlank() }

    private fun celsiusToFahrenheit(celsius: Double): Double = celsius * 9.0 / 5.0 + 32.0

    private companion object {
        const val HOME_SYNC_TIMEOUT_MS = 30_000L
    }
}

data class SampleResult(
    val readings: List<Reading>,
    val diagnostics: List<String>,
)
