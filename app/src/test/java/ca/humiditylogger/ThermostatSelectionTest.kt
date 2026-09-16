package ca.humiditylogger

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermostatSelectionTest {
    @Test
    fun stableIdWinsWhenAvailable() {
        val renamed = Reading(1, "Renamed thermostat", "abc", 20.0, 50.0)
        assertTrue(ThermostatSelection.matchesSelection("Old name", "abc", renamed))
        assertFalse(ThermostatSelection.matchesSelection("Renamed thermostat", "other", renamed))
    }

    @Test
    fun oldReadingsFallBackToName() {
        val legacy = Reading(1, "Dining Room", null, 20.0, 50.0)
        assertTrue(ThermostatSelection.matchesSelection("Dining Room", "abc", legacy))
    }
}
