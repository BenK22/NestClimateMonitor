package ca.humiditylogger

import android.content.Context
import android.app.KeyguardManager
import android.os.PowerManager
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.home.PermissionsState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SamplingWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = sampleMutex.withLock {
        val force = inputData.getBoolean(KEY_FORCE, false)
        if (!shouldRun(LoggerScheduler.isEnabled(applicationContext), force)) return Result.success()

        val startedAt = SystemClock.elapsedRealtime()
        val store = ReadingStore(applicationContext)
        return try {
            val latestIndoorMs = store.recent()
                .lastOrNull {
                    !WeatherClient.isOutdoor(it.source) &&
                        ThermostatSelection.matches(applicationContext, it)
                }
                ?.timestampMs
            if (!force && latestIndoorMs != null &&
                System.currentTimeMillis() - latestIndoorMs < LoggerScheduler.SAMPLE_INTERVAL_MS
            ) {
                return Result.success()
            }

            val deviceAccessStore = DeviceAccessStore(applicationContext)
            val indoorSource = IndoorSourcePreference.selected(applicationContext)
            val indoorReadings: List<Reading>
            val indoorDiagnostics: List<String>
            if (indoorSource == IndoorSource.DEVICE_ACCESS) {
                if (!deviceAccessStore.isConnected()) {
                    val weatherStatus = runCatching {
                        store.insert(WeatherClient.fetchCurrent(applicationContext))
                        " Outdoor weather saved."
                    }.getOrElse { error ->
                        " Outdoor weather failed: ${error.message}."
                    }
                    LoggerScheduler.recordResult(
                        applicationContext,
                        "Nest Device Access is selected but not connected.$weatherStatus",
                        listOf("Open Settings → Nest Device Access to connect."),
                    )
                    DataRetention.apply(applicationContext, store)
                    ClimateWidgetProvider.updateAll(applicationContext)
                    return Result.success()
                }
                val available = runCatching {
                    DeviceAccessClient(applicationContext).thermostats()
                }.getOrElse { error ->
                    val weatherStatus = runCatching {
                        store.insert(WeatherClient.fetchCurrent(applicationContext))
                        " Outdoor weather saved."
                    }.getOrElse { weatherError ->
                        " Outdoor weather failed: ${weatherError.message}."
                    }
                    LoggerScheduler.recordResult(
                        applicationContext,
                        "Nest Device Access failed: ${error.message ?: error.javaClass.simpleName}.$weatherStatus",
                        listOf("Open Settings → Nest Device Access to test or reconnect."),
                    )
                    DataRetention.apply(applicationContext, store)
                    ClimateWidgetProvider.updateAll(applicationContext)
                    return Result.success()
                }
                val selectedId = deviceAccessStore.selectedDeviceId()
                indoorReadings = if (selectedId == null) {
                    available
                } else {
                    available.filter { it.deviceId == selectedId }
                }
                indoorDiagnostics = listOf(
                    "Nest Device Access (Google SDM)",
                    "${available.size} thermostat${if (available.size == 1) "" else "s"} available",
                )
            } else {
                val keyguardManager = applicationContext.getSystemService(KeyguardManager::class.java)
                val powerManager = applicationContext.getSystemService(PowerManager::class.java)
                if (powerManager?.isInteractive == false || keyguardManager?.isDeviceLocked == true) {
                    val weatherStatus = runCatching {
                        store.insert(WeatherClient.fetchCurrent(applicationContext))
                        " Outdoor weather saved."
                    }.getOrElse { error ->
                        " Outdoor weather failed: ${error.message}."
                    }
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
                    LoggerScheduler.recordResult(
                        applicationContext,
                        "Google Home permission is not granted",
                        emptyList(),
                    )
                    return Result.success()
                }
                val sample = reader.sample()
                indoorReadings = ThermostatSelection.filter(applicationContext, sample.readings)
                indoorDiagnostics = sample.diagnostics
            }

            indoorReadings.forEach(store::insert)
            val weatherStatus = runCatching {
                store.insert(WeatherClient.fetchCurrent(applicationContext))
                " Outdoor weather saved."
            }.getOrElse { error ->
                " Outdoor weather failed: ${error.message}."
            }
            val status = when {
                indoorReadings.any { it.humidityPercent != null } ->
                    "Saved indoor temperature and humidity.$weatherStatus"
                indoorReadings.isNotEmpty() ->
                    "Saved indoor temperature; no humidity trait.$weatherStatus"
                else -> "No indoor climate measurement was returned.$weatherStatus"
            }
            LoggerScheduler.recordResult(
                applicationContext,
                status,
                indoorDiagnostics,
                successfulAtMs = indoorReadings.takeIf { it.isNotEmpty() }?.let { System.currentTimeMillis() },
            )
            HumidityAlerts.evaluate(applicationContext, store)
            DataRetention.apply(applicationContext, store)
            ClimateWidgetProvider.updateAll(applicationContext)
            Result.success()
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

    companion object {
        const val KEY_FORCE = "force_sample"
        private val sampleMutex = Mutex()

        internal fun shouldRun(loggingEnabled: Boolean, force: Boolean): Boolean =
            loggingEnabled || force
    }
}
