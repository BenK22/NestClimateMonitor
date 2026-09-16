package ca.humiditylogger

import org.junit.Assert.assertEquals
import org.junit.Test

class HumidityAlertsTest {
    private val settings = HumidityAlerts.Settings(true, 30, 60, 60)

    @Test fun sustainedHighTriggers() {
        val readings = (0..4).map { index ->
            Reading(index * 15L * 60_000L, "Nest", humidityPercent = 65.0, temperatureC = 20.0)
        }
        assertEquals("high", HumidityAlerts.determineState(readings, settings))
    }

    @Test fun shortOrInterruptedWindowStaysNormal() {
        val readings = listOf(
            Reading(0, "Nest", humidityPercent = 65.0, temperatureC = 20.0),
            Reading(45L * 60_000L, "Nest", humidityPercent = 50.0, temperatureC = 20.0),
            Reading(60L * 60_000L, "Nest", humidityPercent = 65.0, temperatureC = 20.0),
        )
        assertEquals("normal", HumidityAlerts.determineState(readings, settings))
    }
}
