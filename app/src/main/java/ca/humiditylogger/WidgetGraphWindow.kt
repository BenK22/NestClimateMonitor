package ca.humiditylogger

/** A plotted measurement; a left-edge point may interpolate between two stored samples. */
data class WidgetGraphPoint(val timestampMs: Long, val value: Double)

/** Clips saved measurement paths to a rolling window without extending unbracketed history. */
object WidgetGraphWindow {
    /**
     * Returns chronological measured points inside the inclusive window, with a crossing segment.
     *
     * If an earlier sample brackets the left edge with an in-window sample, interpolate exactly
     * at [startMs]. This is a drawing point only, never a new database reading. Without both
     * samples no backward/forward extrapolation is performed. Null traits are omitted as in the
     * existing widget paths; elapsed gaps alone do not break lines, matching the daily chart.
     */
    fun points(
        readings: List<Reading>,
        startMs: Long,
        endMs: Long,
        measurement: (Reading) -> Double?,
    ): List<WidgetGraphPoint> {
        val measured = readings.asSequence()
            .filter { it.timestampMs <= endMs }
            .mapNotNull { reading -> measurement(reading)?.let { WidgetGraphPoint(reading.timestampMs, it) } }
            .sortedBy { it.timestampMs }
            .toList()
        val visible = measured.filter { it.timestampMs >= startMs }
        val first = visible.firstOrNull() ?: return emptyList()
        val previous = measured.lastOrNull { it.timestampMs < startMs }
        if (previous == null || first.timestampMs == startMs) return visible
        val fraction = (startMs - previous.timestampMs).toDouble() / (first.timestampMs - previous.timestampMs)
        val boundary = WidgetGraphPoint(startMs, previous.value + fraction * (first.value - previous.value))
        return listOf(boundary) + visible
    }
}
