package ca.humiditylogger

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import androidx.work.WorkManager
import androidx.lifecycle.LiveData
import androidx.work.WorkInfo
import java.util.concurrent.TimeUnit

object LoggerScheduler {
    private const val WORK_NAME = "nest_environment_sampling"
    private const val MANUAL_WORK_NAME = "nest_environment_manual_sample"
    private const val LEGACY_CATCH_UP_WORK_NAME = "nest_environment_sampling_catch_up"
    private const val PREFS_NAME = "logger_state"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_STATUS = "status"
    private const val KEY_DIAGNOSTICS = "diagnostics"
    private const val KEY_LAST_SUCCESS_MS = "last_success_ms"
    private const val KEY_LAST_ATTEMPT_MS = "last_attempt_ms"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_RUN_COUNT = "run_count"
    private const val KEY_LAST_DURATION_MS = "last_duration_ms"
    private const val KEY_TOTAL_DURATION_MS = "total_duration_ms"

    fun start(context: Context) {
        if (!isEnabled(context)) return

        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(LEGACY_CATCH_UP_WORK_NAME)

        val request = PeriodicWorkRequestBuilder<SamplingWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )

        val preferences = prefs(context)
        preferences.edit().putBoolean(KEY_ENABLED, true).apply()
        if (!preferences.contains(KEY_STATUS)) {
            preferences.edit()
                .putString(KEY_STATUS, "15-minute background logging enabled")
                .apply()
        }
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, false)
            .putString(KEY_STATUS, "Background logging stopped")
            .apply()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        if (enabled) {
            prefs(context).edit()
                .putBoolean(KEY_ENABLED, true)
                .putString(KEY_STATUS, "15-minute background logging enabled")
                .apply()
            start(context)
        } else {
            stop(context)
        }
    }

    fun refreshNow(context: Context) {
        enqueueOneTimeSample(context, force = true)
    }

    fun catchUpIfOverdue(context: Context) {
        enqueueOneTimeSample(context, force = false)
    }

    private fun enqueueOneTimeSample(context: Context, force: Boolean) {
        val request = OneTimeWorkRequestBuilder<SamplingWorker>()
            .setInputData(workDataOf(SamplingWorker.KEY_FORCE to force))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MANUAL_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun periodicWork(context: Context): LiveData<List<WorkInfo>> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(WORK_NAME)

    fun manualWork(context: Context): LiveData<List<WorkInfo>> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(MANUAL_WORK_NAME)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun status(context: Context): String? = prefs(context).getString(KEY_STATUS, null)

    fun diagnostics(context: Context): String? = prefs(context).getString(KEY_DIAGNOSTICS, null)

    fun lastSuccessMs(context: Context): Long? = prefs(context)
        .takeIf { it.contains(KEY_LAST_SUCCESS_MS) }
        ?.getLong(KEY_LAST_SUCCESS_MS, 0L)

    fun lastAttemptMs(context: Context): Long? = prefs(context)
        .takeIf { it.contains(KEY_LAST_ATTEMPT_MS) }
        ?.getLong(KEY_LAST_ATTEMPT_MS, 0L)

    fun lastError(context: Context): String? = prefs(context).getString(KEY_LAST_ERROR, null)

    data class RuntimeStats(val runCount: Long, val lastDurationMs: Long, val totalDurationMs: Long)

    fun runtimeStats(context: Context): RuntimeStats {
        val prefs = prefs(context)
        return RuntimeStats(
            prefs.getLong(KEY_RUN_COUNT, 0L),
            prefs.getLong(KEY_LAST_DURATION_MS, 0L),
            prefs.getLong(KEY_TOTAL_DURATION_MS, 0L),
        )
    }

    fun recordRun(context: Context, durationMs: Long) {
        val prefs = prefs(context)
        prefs.edit()
            .putLong(KEY_RUN_COUNT, prefs.getLong(KEY_RUN_COUNT, 0L) + 1L)
            .putLong(KEY_LAST_DURATION_MS, durationMs)
            .putLong(KEY_TOTAL_DURATION_MS, prefs.getLong(KEY_TOTAL_DURATION_MS, 0L) + durationMs)
            .apply()
    }

    fun recordResult(
        context: Context,
        status: String,
        diagnostics: List<String>,
        successfulAtMs: Long? = null,
    ) {
        if (!isEnabled(context)) return

        val editor = prefs(context).edit()
            .putString(KEY_STATUS, status)
            .putString(KEY_DIAGNOSTICS, diagnostics.joinToString("\n\n"))
            .putLong(KEY_LAST_ATTEMPT_MS, System.currentTimeMillis())
        if (successfulAtMs != null) {
            editor.putLong(KEY_LAST_SUCCESS_MS, successfulAtMs).remove(KEY_LAST_ERROR)
        } else {
            editor.putString(KEY_LAST_ERROR, status)
        }
        editor.apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    const val SAMPLE_INTERVAL_MS = 15L * 60L * 1000L
}
