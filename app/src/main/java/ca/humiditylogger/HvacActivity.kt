package ca.humiditylogger

import java.util.Locale

/** Reported HVAC activity at collection time, never inferred from the thermostat's selected mode. */
enum class HvacActivity(val label: String, val legend: String, val color: Int) {
    HEATING("Heat", "Heat", 0xFFFFA05A.toInt()),
    COOLING("Cool", "Cool", 0xFFBAA1FF.toInt()),
    IDLE("Off", "Off", 0xFFA6B8B2.toInt()),
    UNKNOWN("Unknown (not reported)", "?", 0xFFCBD5D1.toInt());

    companion object {
        /** Maps known SDM/Home running values only; absent, numeric and future values stay unknown. */
        fun fromReported(value: String?): HvacActivity = when (value?.trim()?.uppercase(Locale.ROOT)) {
            "HEATING", "HEAT" -> HEATING
            "COOLING", "COOL" -> COOLING
            "OFF", "IDLE" -> IDLE
            else -> UNKNOWN
        }
    }
}

/** A real stored indoor temperature sample with its contemporaneous reported activity. */
data class HvacSample(val timestampMs: Long, val temperatureC: Double, val activity: HvacActivity)

/** Produces discrete markers only: no interpolation, carry-forward, or cycle-duration estimation. */
object HvacHistory {
    /** Caller selects the thermostat; weather, invalid temperatures and out-of-window rows are excluded. */
    fun samples(readings: List<Reading>, startMs: Long, endExclusiveMs: Long): List<HvacSample> =
        readings.mapNotNull { reading ->
            val temperature = reading.temperatureC
            if (WeatherClient.isOutdoor(reading.source) || reading.timestampMs < startMs ||
                reading.timestampMs >= endExclusiveMs || temperature == null || !temperature.isFinite()
            ) null else HvacSample(reading.timestampMs, temperature, HvacActivity.fromReported(reading.runningState))
        }
}
