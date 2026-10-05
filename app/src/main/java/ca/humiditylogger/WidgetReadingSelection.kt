package ca.humiditylogger

/** Filtered indoor/outdoor histories in their input order; [displayed] is concatenated, not sorted. */
data class WidgetReadingSeries(
    val indoor: List<Reading>,
    val outdoor: List<Reading>,
) {
    /** Combined rows for scale calculations; sort explicitly before time-ordered traversal. */
    val displayed: List<Reading> get() = indoor + outdoor
}

/** Shared presentation filters that prevent stale locations or unselected devices entering scales. */
object WidgetReadingSelection {
    /** Selects matching indoor rows and the exact current outdoor label without reordering either. */
    fun select(
        readings: List<Reading>,
        outdoorSource: String,
        indoorMatches: (Reading) -> Boolean,
    ): WidgetReadingSeries = WidgetReadingSeries(
        indoor = readings.filter(indoorMatches),
        outdoor = readings.filter { it.source == outdoorSource },
    )

    /** Last eligible ambient snapshot; [readings] must be chronological for this to be the newest. */
    fun latestIndoorClimate(
        readings: List<Reading>,
        indoorMatches: (Reading) -> Boolean,
    ): Reading? = readings.lastOrNull { indoorMatches(it) && it.hasClimateMeasurement() }
}
