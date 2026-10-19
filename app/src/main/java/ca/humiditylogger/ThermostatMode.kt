package ca.humiditylogger

import java.util.Locale

/** Selected thermostat setting, independent of whether heating/cooling equipment is running. */
enum class ThermostatMode(val label: String, val color: Int) {
    HEAT("Heat", 0xFFFFA05A.toInt()),
    COOL("Cool", 0xFF79BEFF.toInt()),
    HEAT_COOL("Heat/Cool", 0xFFBAA1FF.toInt()),
    OFF("Off", 0xFF9BAEA7.toInt()),
    UNKNOWN("Unknown", 0);

    companion object {
        /** SDM HEATCOOL and Home Auto both allow heating/cooling; other modes stay unclassified. */
        fun fromReported(value: String?): ThermostatMode = when (value?.trim()?.uppercase(Locale.ROOT)) {
            "HEAT" -> HEAT
            "COOL" -> COOL
            "HEATCOOL", "AUTO" -> HEAT_COOL
            "OFF" -> OFF
            else -> UNKNOWN
        }
    }
}

/** A bounded last-observed-mode interval for presentation, not an exact change/event timestamp. */
data class ThermostatModeBand(val startMs: Long, val endMs: Long, val mode: ThermostatMode)

/** Builds mode backgrounds from stored selected-device snapshots without changing stored history. */
object ThermostatModeHistory {
    /** Two requested intervals tolerate scheduling jitter, but never color hours of missing history. */
    const val MAX_HOLD_MS = LoggerScheduler.SAMPLE_INTERVAL_MINUTES * 2L * 60_000L

    /**
     * Holds each observed mode until the next snapshot, capped at [MAX_HOLD_MS] and the window end.
     * Missing/unsupported modes stop the preceding band. Equal timestamps use the last input row.
     * The caller selects the thermostat and caps the window at now, so future time stays unshaded.
     */
    fun bands(readings: List<Reading>, startMs: Long, endMs: Long): List<ThermostatModeBand> {
        if (endMs <= startMs) return emptyList()
        val indoor = readings.filter { !WeatherClient.isOutdoor(it.source) && it.timestampMs < endMs }
            .sortedBy { it.timestampMs }.associateBy { it.timestampMs }.values.toList()
        return indoor.mapIndexedNotNull { index, row ->
            val mode = ThermostatMode.fromReported(row.systemMode)
            val start = maxOf(startMs, row.timestampMs)
            val expires = if (row.timestampMs > Long.MAX_VALUE - MAX_HOLD_MS) Long.MAX_VALUE
                else row.timestampMs + MAX_HOLD_MS
            val end = minOf(endMs, expires, indoor.getOrNull(index + 1)?.timestampMs ?: endMs)
            if (mode == ThermostatMode.UNKNOWN || end <= start) null
                else ThermostatModeBand(start, end, mode)
        }
    }
}
