package ca.humiditylogger

import android.content.Context
import android.app.KeyguardManager
import android.os.PowerManager
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.home.PermissionsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Collects the selected indoor source plus independent outdoor weather and updates local consumers.
 *
 * A process-wide mutex serializes manual/periodic runs without merging their requests. Expected
 * provider failures are recorded and return WorkManager success so retries do not add samples
 * between intervals. Cancellation is propagated. Google Home is skipped when locked/screen-off;
 * Device Access does not use that gate. Neither source is an automatic fallback for the other.
 */
class SamplingWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    /** Runs one request, closes its database and records elapsed runtime even on cancellation. */
    override suspend fun doWork(): Result = sampleMutex.withLock {
        val force = inputData.getBoolean(KEY_FORCE, false)
        if (!shouldRun(LoggerScheduler.isEnabled(applicationContext), force)) return Result.success()

        val startedAt = SystemClock.elapsedRealtime()
        val store = ReadingStore(applicationContext)
        return try {
            val deviceAccessStore = DeviceAccessStore(applicationContext)
            val indoorSource = IndoorSourcePreference.selected(applicationContext)
            val indoorReadings: List<Reading>
            val indoorDiagnostics: List<String>
            var deviceSelectionRequired = false
            if (indoorSource == IndoorSource.DEVICE_ACCESS) {
                if (!deviceAccessStore.isConnected()) {
                    val weatherStatus = saveOutdoorReading(store)
                    LoggerScheduler.recordResult(
                        applicationContext,
                        "Nest Device Access is selected but not connected.$weatherStatus",
                        listOf("Open Settings → Nest Device Access to connect."),
                    )
                    DataRetention.apply(applicationContext, store)
                    ClimateWidgetProvider.updateAll(applicationContext)
                    return Result.success()
                }
                val available = runCatchingCancellable {
                    DeviceAccessClient(applicationContext).thermostats()
                }.getOrElse { error ->
                    val weatherStatus = saveOutdoorReading(store)
                    LoggerScheduler.recordResult(
                        applicationContext,
                        "Nest Device Access failed: ${error.message ?: error.javaClass.simpleName}.$weatherStatus",
                        listOf("Open Settings → Nest Device Access to test or reconnect."),
                    )
                    DataRetention.apply(applicationContext, store)
                    ClimateWidgetProvider.updateAll(applicationContext)
                    return Result.success()
                }
                var selectedId = deviceAccessStore.selectedDeviceId()
                if (selectedId != null && available.none { it.deviceId == selectedId }) {
                    deviceAccessStore.setSelectedDeviceId(null)
                    selectedId = null
                }
                // Auto-select only an unambiguous device; never mix multiple homes' thermostats.
                if (selectedId == null && available.size == 1) {
                    deviceAccessStore.setSelectedDeviceId(available.single().deviceId)
                    selectedId = available.single().deviceId
                }
                deviceSelectionRequired = selectedId == null && available.size > 1
                indoorReadings = ThermostatSelection.selectDeviceAccessReadings(
                    available,
                    selectedId,
                )
                indoorDiagnostics = listOf(
                    "Nest Device Access (Google SDM)",
                    "${available.size} thermostat${if (available.size == 1) "" else "s"} available",
                    if (deviceSelectionRequired) {
                        "Choose one thermostat in Settings → Nest Device Access."
                    } else {
                        "Selected thermostat ${selectedId?.substringAfterLast('/') ?: "unavailable"}"
                    },
                )
            } else {
                val keyguardManager = applicationContext.getSystemService(KeyguardManager::class.java)
                val powerManager = applicationContext.getSystemService(PowerManager::class.java)
                if (powerManager?.isInteractive == false || keyguardManager?.isDeviceLocked == true) {
                    val weatherStatus = saveOutdoorReading(store)
                    LoggerScheduler.recordResult(
                        applicationContext,
                        "Connect Nest Device Access for screen-off indoor readings.$weatherStatus",
                        listOf(
                            "Google Home is selected and requires the display to be on and the phone unlocked.",
                            "Select Nest Device Access in Settings for screen-off readings.",
                        ),
                    )
                    DataRetention.apply(applicationContext, store)
                    ClimateWidgetProvider.updateAll(applicationContext)
                    return Result.success()
                }

                val reader = HomeReader.getInstance(applicationContext)
                if (reader.permissionState() != PermissionsState.GRANTED) {
                    val weatherStatus = saveOutdoorReading(store)
                    LoggerScheduler.recordResult(
                        applicationContext,
                        "Google Home permission is not granted.$weatherStatus",
                        emptyList(),
                    )
                    DataRetention.apply(applicationContext, store)
                    ClimateWidgetProvider.updateAll(applicationContext)
                    return Result.success()
                }
                val sample = reader.sample()
                indoorReadings = ThermostatSelection.filter(applicationContext, sample.readings)
                indoorDiagnostics = sample.diagnostics
            }

            indoorReadings.forEach(store::insert)
            val weatherStatus = saveOutdoorReading(store)
            val outcome = climateOutcome(indoorReadings, deviceSelectionRequired)
            val status = "${outcome.status}$weatherStatus"
            LoggerScheduler.recordResult(
                applicationContext,
                status,
                indoorDiagnostics,
                successfulAtMs = outcome.successful.takeIf { it }?.let { System.currentTimeMillis() },
            )
            HumidityAlerts.evaluate(applicationContext, store)
            DataRetention.apply(applicationContext, store)
            ClimateWidgetProvider.updateAll(applicationContext)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            LoggerScheduler.recordResult(
                applicationContext,
                "Background read failed: ${error.message ?: error.javaClass.simpleName}",
                emptyList(),
            )
            Result.success()
        } finally {
            LoggerScheduler.recordRun(
                applicationContext,
                SystemClock.elapsedRealtime() - startedAt,
            )
            store.close()
        }
    }

    /** Saves weather independently; its success must not imply a successful indoor sample. */
    private suspend fun saveOutdoorReading(store: ReadingStore): String =
        runCatchingCancellable {
            store.insert(WeatherClient.fetchCurrent(applicationContext))
            " Outdoor weather saved."
        }.getOrElse { error ->
            " Outdoor weather failed: ${error.message}."
        }

    companion object {
        /** Manual-request input that permits a sample even when periodic logging is disabled. */
        const val KEY_FORCE = "force_sample"
        private val sampleMutex = Mutex()

        /** Keeps explicit refresh available without re-enabling periodic logging. */
        internal fun shouldRun(loggingEnabled: Boolean, force: Boolean): Boolean =
            loggingEnabled || force

        /** Human-readable indoor result and whether an ambient measurement was returned. */
        internal data class ClimateOutcome(val status: String, val successful: Boolean)

        /** Counts temperature or humidity as success; setpoint/status-only records do not qualify. */
        internal fun climateOutcome(
            readings: List<Reading>,
            selectionRequired: Boolean = false,
        ): ClimateOutcome {
            val savedTemperature = readings.any { it.temperatureC != null }
            val savedHumidity = readings.any { it.humidityPercent != null }
            val status = when {
                savedTemperature && savedHumidity -> "Saved indoor temperature and humidity."
                savedTemperature -> "Saved indoor temperature; no humidity trait."
                savedHumidity -> "Saved indoor humidity; no temperature trait."
                selectionRequired -> "Choose one Nest thermostat before sampling."
                else -> "No indoor climate measurement was returned."
            }
            return ClimateOutcome(status, savedTemperature || savedHumidity)
        }
    }
}
