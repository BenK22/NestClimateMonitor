package ca.humiditylogger

import android.content.Context
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
        if (!LoggerScheduler.isEnabled(applicationContext)) return Result.success()

        val startedAt = SystemClock.elapsedRealtime()
        val store = ReadingStore(applicationContext)
        return try {
            val latestIndoorMs = store.recent()
                .lastOrNull { !WeatherClient.isOutdoor(it.source) }
                ?.timestampMs
            if (!inputData.getBoolean(KEY_FORCE, false) && latestIndoorMs != null &&
                System.currentTimeMillis() - latestIndoorMs < LoggerScheduler.SAMPLE_INTERVAL_MS
            ) {
                return Result.success()
            }

            val reader = HomeReader(applicationContext)
            if (reader.permissionState() != PermissionsState.GRANTED) {
                LoggerScheduler.recordResult(
                    applicationContext,
                    "Google Home permission is not granted",
                    emptyList(),
                )
                Result.success()
            } else {
                val sample = reader.sample()
                val indoorReadings = ThermostatSelection.filter(applicationContext, sample.readings)
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
                    sample.diagnostics,
                    successfulAtMs = indoorReadings.takeIf { it.isNotEmpty() }?.let { System.currentTimeMillis() },
                )
                HumidityAlerts.evaluate(applicationContext, store)
                DataRetention.apply(applicationContext, store)
                ClimateWidgetProvider.updateAll(applicationContext)
                Result.success()
            }
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
    }
}
