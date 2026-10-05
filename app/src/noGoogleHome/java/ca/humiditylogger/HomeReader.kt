package ca.humiditylogger

import android.content.Context
import androidx.activity.result.ActivityResultCaller

/**
 * Explicitly unavailable Home adapter for SDK-free verification builds, never a fake SDK.
 *
 * SDM/weather/database/widget code is real in this build; Google Home integration is not tested.
 * The build uses a different application ID and cannot be signed as an official release.
 */
class HomeReader private constructor() {
    /** No permission registration is possible without the SDK. */
    fun registerPermissionCaller(@Suppress("UNUSED_PARAMETER") caller: ActivityResultCaller) = Unit

    /** Fails explicitly rather than pretending consent or device discovery succeeded. */
    suspend fun requestPermission(): HomePermissionResult = error(UNAVAILABLE_MESSAGE)

    /** Identifies the excluded integration without initiating network work. */
    suspend fun permissionState(): HomePermissionState = HomePermissionState.UNAVAILABLE

    /** Returns no snapshots and an actionable build limitation; never fabricates measurements. */
    suspend fun sample(): SampleResult = SampleResult(emptyList(), listOf(UNAVAILABLE_MESSAGE))

    companion object {
        /** Per-flow bound used by the full SDK build, in milliseconds. */
        const val HOME_SYNC_TIMEOUT_MS = 30_000L
        /** Permission bound used by the full SDK build, in milliseconds. */
        const val PERMISSION_TIMEOUT_MS = 15_000L
        /** Overall bound used by the full SDK build, in milliseconds. */
        const val HOME_SAMPLE_DEADLINE_MS = 45_000L
        private const val UNAVAILABLE_MESSAGE = "Google Home is not included in this verification build. Use a full build or Nest Device Access."
        private val instance = HomeReader()

        /** Returns the inert adapter without retaining a context or initializing the SDK. */
        fun getInstance(@Suppress("UNUSED_PARAMETER") context: Context): HomeReader = instance
    }
}
