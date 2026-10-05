package ca.humiditylogger

import android.content.Context

/**
 * Shared indoor identity filter for storage consumers, graphs and alerts.
 *
 * SDM requires an exact device resource in the configured enterprise. Google Home prefers
 * stable device ID, with display-name fallback for legacy rows without identity. Outdoor rows
 * are never eligible, and switching providers must not show the previous provider's readings.
 */
object ThermostatSelection {
    private const val PREFS = "thermostat_selection"
    private const val KEY_SOURCE = "source"
    private const val KEY_DEVICE_ID = "device_id"

    /** Saved Google Home display name, or null for its unfiltered default. */
    fun selectedSource(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SOURCE, null)
            ?.takeIf { it.isNotBlank() }

    /** Saved Google Home stable ID; Device Access selection lives in [DeviceAccessStore]. */
    fun selectedDeviceId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }

    /** Saves Google Home selection without sampling; null values clear the respective keys. */
    fun setSelectedSource(context: Context, source: String?, deviceId: String? = null) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (source == null) remove(KEY_SOURCE) else putString(KEY_SOURCE, source)
            if (deviceId == null) remove(KEY_DEVICE_ID) else putString(KEY_DEVICE_ID, deviceId)
        }.apply()
    }

    /** Checks provider and selected identity against current settings; does not require a measurement. */
    fun matches(context: Context, reading: Reading): Boolean {
        if (WeatherClient.isOutdoor(reading.source)) return false
        val source = IndoorSourcePreference.selected(context)
        val isDeviceAccessReading = reading.deviceId?.startsWith("enterprises/") == true
        if (source == IndoorSource.DEVICE_ACCESS && !isDeviceAccessReading) return false
        if (source == IndoorSource.GOOGLE_HOME && isDeviceAccessReading) return false
        val deviceAccess = DeviceAccessStore(context)
        if (isDeviceAccessReading) {
            return matchesDeviceAccess(
                deviceAccess.configuration().projectId,
                deviceAccess.selectedDeviceId(),
                reading,
            )
        }
        return matchesSelection(selectedSource(context), selectedDeviceId(context), reading)
    }

    /** Google Home compatibility filter: no source accepts all; missing IDs fall back to names. */
    internal fun matchesSelection(source: String?, deviceId: String?, reading: Reading): Boolean =
        source == null || if (deviceId != null && reading.deviceId != null) {
            deviceId == reading.deviceId
        } else {
            source == reading.source
        }

    /** Requires both configured project membership and the exact selected SDM resource name. */
    internal fun matchesDeviceAccess(
        projectId: String,
        selectedDeviceId: String?,
        reading: Reading,
    ): Boolean {
        if (projectId.isBlank()) return false
        val resourceName = reading.deviceId ?: return false
        if (!resourceName.startsWith("enterprises/$projectId/devices/")) return false
        return selectedDeviceId != null && resourceName == selectedDeviceId
    }

    /** Returns only the explicit SDM selection; an absent selection deliberately produces no rows. */
    internal fun selectDeviceAccessReadings(
        available: List<Reading>,
        selectedDeviceId: String?,
    ): List<Reading> = selectedDeviceId?.let { selected ->
        available.filter { it.deviceId == selected }
    }.orEmpty()

    /** Applies current indoor selection while preserving the input ordering. */
    fun filter(context: Context, readings: List<Reading>): List<Reading> =
        readings.filter { matches(context, it) }
}
