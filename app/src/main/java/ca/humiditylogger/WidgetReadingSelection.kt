package ca.humiditylogger

data class WidgetReadingSeries(
    val indoor: List<Reading>,
    val outdoor: List<Reading>,
) {
    val displayed: List<Reading> get() = indoor + outdoor
}

object WidgetReadingSelection {
    fun select(
        readings: List<Reading>,
        outdoorSource: String,
        indoorMatches: (Reading) -> Boolean,
    ): WidgetReadingSeries = WidgetReadingSeries(
        indoor = readings.filter(indoorMatches),
        outdoor = readings.filter { it.source == outdoorSource },
    )
}
