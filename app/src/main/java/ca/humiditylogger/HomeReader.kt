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

/**
 * Process-scoped, read-only Google Home SDK adapter for exposed climate traits.
 *
 * The worker checks screen/lock state and permission before sampling. SDK collections use
 * per-flow timeouts plus a total deadline so an empty or stalled device flow cannot monopolize
 * the sampling worker. Matter temperatures and humidity are normalized from hundredths.
 */
class HomeReader private constructor(context: Context) {
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

    /** Shared SDK client; activities must register their permission result caller before consent. */
    val client: HomeClient = Home.getClient(
        context.applicationContext,
        homeConfig = HomeConfig(
            coroutineContext = Dispatchers.IO,
            factoryRegistry = registry,
            homePlatformScope = HomeConfig.HomePlatformScope.HOME_PLATFORM_SCOPE_VERSION_1,
        ),
    )

    /** Waits for initialized permission state for up to 15 seconds; timeout is an explicit error. */
    suspend fun permissionState(): PermissionsState = withTimeoutOrNull(PERMISSION_TIMEOUT_MS) {
        client.hasPermissions().first {
            it != PermissionsState.PERMISSIONS_STATE_UNINITIALIZED
        }
    } ?: throw IllegalStateException(
        "Google Home permission check timed out. Keep the phone unlocked and try again."
    )

    /**
     * Discovers climate snapshots without persisting them or filtering the user's selection.
     * Total timeout returns an empty result with diagnostics, not a partial success. Caller
     * cancellation propagates; other SDK failures can throw and are handled by the worker.
     */
    suspend fun sample(): SampleResult = withTimeoutOrNull(HOME_SAMPLE_DEADLINE_MS) {
        sampleWithinDeadline()
    } ?: SampleResult(
        emptyList(),
        listOf("Google Home read timed out after ${HOME_SAMPLE_DEADLINE_MS / 1_000} seconds. Keep the phone unlocked and try again."),
    )

    private suspend fun sampleWithinDeadline(): SampleResult {
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

    companion object {
        /** Bound for each SDK synchronization flow, in milliseconds. */
        const val HOME_SYNC_TIMEOUT_MS = 30_000L
        /** Bound for initialized permission state, in milliseconds. */
        const val PERMISSION_TIMEOUT_MS = 15_000L
        /** Overall sample deadline, in milliseconds, covering all structures/devices. */
        const val HOME_SAMPLE_DEADLINE_MS = 45_000L

        @Volatile
        private var instance: HomeReader? = null

        /** Lazily creates one SDK adapter using application context, avoiding an activity reference. */
        fun getInstance(context: Context): HomeReader = instance ?: synchronized(this) {
            instance ?: HomeReader(context.applicationContext).also { instance = it }
        }
    }
}

/** Unselected SDK snapshots and local diagnostics; diagnostics may contain private home/device names. */
data class SampleResult(
    val readings: List<Reading>,
    val diagnostics: List<String>,
)
