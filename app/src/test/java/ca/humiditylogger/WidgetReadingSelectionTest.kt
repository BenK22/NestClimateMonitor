package ca.humiditylogger

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetReadingSelectionTest {
    @Test
    fun selectsOnlyTheActiveIndoorDeviceAndOutdoorLocation() {
        val readings = listOf(
            Reading(1, "Old thermostat", "old", 30.0, 30.0),
            Reading(2, "Dining Room", "selected", 20.0, 50.0),
            Reading(3, "Weather:Old city", null, 40.0, 80.0),
            Reading(4, "Weather:Current city", null, 10.0, 60.0),
        )

        val result = WidgetReadingSelection.select(readings, "Weather:Current city") {
            it.deviceId == "selected"
        }

        assertEquals(listOf("Dining Room"), result.indoor.map(Reading::source))
        assertEquals(listOf("Weather:Current city"), result.outdoor.map(Reading::source))
        assertEquals(listOf(20.0, 10.0), result.displayed.mapNotNull(Reading::temperatureC))
    }
}
