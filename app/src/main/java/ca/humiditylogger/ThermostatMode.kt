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

/** A last-observed-mode interval clipped to the display window, not an exact change/event timestamp. */
data class ThermostatModeBand(val startMs: Long, val endMs: Long, val mode: ThermostatMode)

/** Builds mode backgrounds from stored selected-device snapshots without changing stored history. */
object ThermostatModeHistory {
    /**
     * Holds each observed mode until the next snapshot or window end, even across sampling gaps.
     * Missing/unsupported modes stop the preceding band. Equal timestamps use the last input row.
     * Adjacent equal modes merge into one uninterrupted background rather than per-sample strips.
     * The caller selects the thermostat and caps the window at now, so future time stays unshaded.
     */
    fun bands(readings: List<Reading>, startMs: Long, endMs: Long): List<ThermostatModeBand> {
        if (endMs <= startMs) return emptyList()
        val indoor = readings.filter { !WeatherClient.isOutdoor(it.source) && it.timestampMs < endMs }
            .sortedBy { it.timestampMs }.associateBy { it.timestampMs }.values.toList()
        return buildList {
            indoor.forEachIndexed { index, row ->
                val mode = ThermostatMode.fromReported(row.systemMode)
                val start = maxOf(startMs, row.timestampMs)
                val end = minOf(endMs, indoor.getOrNull(index + 1)?.timestampMs ?: endMs)
                if (mode == ThermostatMode.UNKNOWN || end <= start) return@forEachIndexed
                val previous = lastOrNull()
                if (previous?.mode == mode && previous.endMs == start) {
                    set(lastIndex, previous.copy(endMs = end))
                } else add(ThermostatModeBand(start, end, mode))
            }
        }
    }
}
