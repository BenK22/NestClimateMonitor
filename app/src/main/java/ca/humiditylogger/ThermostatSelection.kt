package ca.humiditylogger

import android.content.Context

object ThermostatSelection {
    private const val PREFS = "thermostat_selection"
    private const val KEY_SOURCE = "source"
    private const val KEY_DEVICE_ID = "device_id"

    fun selectedSource(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SOURCE, null)
            ?.takeIf { it.isNotBlank() }

    fun selectedDeviceId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }

    fun setSelectedSource(context: Context, source: String?, deviceId: String? = null) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (source == null) remove(KEY_SOURCE) else putString(KEY_SOURCE, source)
            if (deviceId == null) remove(KEY_DEVICE_ID) else putString(KEY_DEVICE_ID, deviceId)
        }.apply()
    }

    fun matches(context: Context, reading: Reading): Boolean {
        if (WeatherClient.isOutdoor(reading.source)) return false
        val source = IndoorSourcePreference.selected(context)
        val isDeviceAccessReading = reading.deviceId?.startsWith("enterprises/") == true
        if (source == IndoorSource.DEVICE_ACCESS && !isDeviceAccessReading) return false
        if (source == IndoorSource.GOOGLE_HOME && isDeviceAccessReading) return false
        val deviceAccess = DeviceAccessStore(context)
        if (isDeviceAccessReading) {
            val deviceId = deviceAccess.selectedDeviceId()
            return deviceId == null || reading.deviceId == deviceId
        }
        return matchesSelection(selectedSource(context), selectedDeviceId(context), reading)
    }

    internal fun matchesSelection(source: String?, deviceId: String?, reading: Reading): Boolean =
        source == null || if (deviceId != null && reading.deviceId != null) {
            deviceId == reading.deviceId
        } else {
            source == reading.source
        }

    fun filter(context: Context, readings: List<Reading>): List<Reading> =
        readings.filter { matches(context, it) }
}
