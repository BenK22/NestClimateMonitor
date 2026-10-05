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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Owns unique WorkManager schedules and persistent sampling-health information.
 *
 * Periodic and manual requests have independent names. KEEP preserves an existing periodic
 * cadence and coalesces repeated manual clicks. Android may defer the requested 15-minute
 * interval; this is not an exact alarm or a foreground service.
 */
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
    private const val KEY_ERROR_HISTORY = "error_history"

    /** Ensures enabled periodic work exists; never requests an immediate sample on app launch. */
    fun start(context: Context) {
        if (!isEnabled(context)) return

        val workManager = WorkManager.getInstance(context)
        // Remove the old catch-up job so upgrades cannot introduce implicit extra samples.
        workManager.cancelUniqueWork(LEGACY_CATCH_UP_WORK_NAME)

        val request = PeriodicWorkRequestBuilder<SamplingWorker>(SAMPLE_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setInitialDelay(SAMPLE_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            PERIODIC_WORK_POLICY,
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

    /** Cancels periodic work and persists the disabled choice; manual work remains independent. */
    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, false)
            .putString(KEY_STATUS, "Background logging stopped")
            .apply()
        ClimateWidgetProvider.updateAll(context.applicationContext)
    }

    /** Persists the user choice before ensuring or cancelling the periodic schedule. */
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

    /** Enqueues one network-constrained manual sample without changing the periodic interval. */
    fun refreshNow(context: Context) {
        enqueueOneTimeSample(context)
    }

    private fun enqueueOneTimeSample(context: Context) {
        val request = OneTimeWorkRequestBuilder<SamplingWorker>()
            .setInputData(workDataOf(SamplingWorker.KEY_FORCE to true))
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

    /** Observable WorkManager state for the periodic schedule, not a timer guarantee. */
    fun periodicWork(context: Context): LiveData<List<WorkInfo>> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(WORK_NAME)

    /** Observable state for explicit refresh requests, separate from periodic work. */
    fun manualWork(context: Context): LiveData<List<WorkInfo>> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(MANUAL_WORK_NAME)

    /** Saved logging choice; new installations default to enabled. */
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    /** Last schedule/sample status, or null before any status was recorded. */
    fun status(context: Context): String? = prefs(context).getString(KEY_STATUS, null)

    /** Last provider diagnostics for local display; may include device names and identifiers. */
    fun diagnostics(context: Context): String? = prefs(context).getString(KEY_DIAGNOSTICS, null)

    /** Last successful indoor collection time in epoch milliseconds, across source selections. */
    fun lastSuccessMs(context: Context): Long? = prefs(context)
        .takeIf { it.contains(KEY_LAST_SUCCESS_MS) }
        ?.getLong(KEY_LAST_SUCCESS_MS, 0L)

    /** Time the latest sample result was recorded, in epoch milliseconds. */
    fun lastAttemptMs(context: Context): Long? = prefs(context)
        .takeIf { it.contains(KEY_LAST_ATTEMPT_MS) }
        ?.getLong(KEY_LAST_ATTEMPT_MS, 0L)

    /** Latest unsuccessful status; cleared by a subsequent indoor success. */
    fun lastError(context: Context): String? = prefs(context).getString(KEY_LAST_ERROR, null)

    /** A bounded-history failure with epoch-millisecond time and sanitized status text. */
    data class SampleError(val timestampMs: Long, val message: String)

    /** Returns up to 20 sanitized failures, oldest first; success does not erase history. */
    fun recentErrors(context: Context): List<SampleError> =
        decodeErrorHistory(prefs(context).getString(KEY_ERROR_HISTORY, null))

    /** Completed run counts and elapsed milliseconds; these are not battery-energy measurements. */
    data class RuntimeStats(val runCount: Long, val lastDurationMs: Long, val totalDurationMs: Long)

    /** Reads cumulative runtime counters used alongside external battery diagnostics. */
    fun runtimeStats(context: Context): RuntimeStats {
        val prefs = prefs(context)
        return RuntimeStats(
            prefs.getLong(KEY_RUN_COUNT, 0L),
            prefs.getLong(KEY_LAST_DURATION_MS, 0L),
            prefs.getLong(KEY_TOTAL_DURATION_MS, 0L),
        )
    }

    /** Adds elapsed runtime for a started run, including failure or cancellation. */
    fun recordRun(context: Context, durationMs: Long) {
        val prefs = prefs(context)
        prefs.edit()
            .putLong(KEY_RUN_COUNT, prefs.getLong(KEY_RUN_COUNT, 0L) + 1L)
            .putLong(KEY_LAST_DURATION_MS, durationMs)
            .putLong(KEY_TOTAL_DURATION_MS, prefs.getLong(KEY_TOTAL_DURATION_MS, 0L) + durationMs)
            .apply()
    }

    /**
     * Stores the latest status and diagnostics, appending failures to bounded history.
     *
     * @param successfulAtMs Indoor success time in epoch milliseconds; null marks a failure.
     * Outdoor-only success must not update the indoor freshness indicator. Callers must avoid
     * secrets in status/diagnostics: only the bounded history applies additional redaction.
     */
    fun recordResult(
        context: Context,
        status: String,
        diagnostics: List<String>,
        successfulAtMs: Long? = null,
    ) {
        val preferences = prefs(context)
        val attemptedAtMs = System.currentTimeMillis()
        val editor = preferences.edit()
            .putString(KEY_STATUS, status)
            .putString(KEY_DIAGNOSTICS, diagnostics.joinToString("\n\n"))
            .putLong(KEY_LAST_ATTEMPT_MS, attemptedAtMs)
        if (successfulAtMs != null) {
            editor.putLong(KEY_LAST_SUCCESS_MS, successfulAtMs).remove(KEY_LAST_ERROR)
        } else {
            editor
                .putString(KEY_LAST_ERROR, status)
                .putString(
                    KEY_ERROR_HISTORY,
                    appendErrorHistory(
                        preferences.getString(KEY_ERROR_HISTORY, null),
                        attemptedAtMs,
                        status,
                    ),
                )
        }
        editor.apply()
    }

    /** Appends a redacted, length-limited entry while keeping the newest 20 failures. */
    internal fun appendErrorHistory(
        serialized: String?,
        timestampMs: Long,
        message: String,
    ): String {
        val entries = (decodeErrorHistory(serialized) + SampleError(
            timestampMs,
            sanitizeErrorMessage(message),
        )).takeLast(MAX_ERROR_HISTORY)
        return JSONArray().apply {
            entries.forEach { error ->
                put(JSONObject().put("timestamp_ms", error.timestampMs).put("message", error.message))
            }
        }.toString()
    }

    /** Decodes oldest-first history; missing or malformed JSON yields an empty list. */
    internal fun decodeErrorHistory(serialized: String?): List<SampleError> = runCatching {
        val array = JSONArray(serialized ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val timestamp = item.optLong("timestamp_ms", 0L)
                val message = item.optString("message").takeIf { it.isNotBlank() }
                if (timestamp > 0L && message != null) add(SampleError(timestamp, message))
            }
        }
    }.getOrDefault(emptyList())

    /** Collapses whitespace and redacts recognized credential assignments; not a general scrubber. */
    internal fun sanitizeErrorMessage(message: String): String {
        val singleLine = message.trim().replace(Regex("\\s+"), " ")
        return SENSITIVE_VALUE.replace(singleLine) { match -> "${match.groupValues[1]}=[redacted]" }
            .take(MAX_ERROR_MESSAGE_LENGTH)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    internal const val SAMPLE_INTERVAL_MINUTES = 15L
    internal val PERIODIC_WORK_POLICY = ExistingPeriodicWorkPolicy.KEEP
    private const val MAX_ERROR_HISTORY = 20
    private const val MAX_ERROR_MESSAGE_LENGTH = 300
    private val SENSITIVE_VALUE = Regex(
        "(?i)\\b(access_token|refresh_token|client_secret|authorization|code)\\s*[=:]\\s*[^&\\s]+",
    )
}
