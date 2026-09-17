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

    @Test
    fun deviceAccessReadingsMustBelongToCurrentProject() {
        val current = Reading(
            1,
            "Dining Room",
            "enterprises/current-project/devices/thermostat",
            20.0,
            50.0,
        )
        val old = current.copy(deviceId = "enterprises/old-project/devices/thermostat")

        assertFalse(ThermostatSelection.matchesDeviceAccess("current-project", null, current))
        assertFalse(ThermostatSelection.matchesDeviceAccess("current-project", null, old))
        assertFalse(ThermostatSelection.matchesDeviceAccess("", null, current))
        assertFalse(
            ThermostatSelection.matchesDeviceAccess(
                "current-project",
                "enterprises/current-project/devices/other",
                current,
            )
        )
    }

    @Test
    fun deviceAccessRequiresOneExplicitMatchingThermostat() {
        val dining = Reading(1, "Dining", "device-1", 20.0, 50.0)
        val bedroom = Reading(1, "Bedroom", "device-2", 21.0, 45.0)
        val available = listOf(dining, bedroom)

        assertTrue(ThermostatSelection.selectDeviceAccessReadings(available, null).isEmpty())
        assertTrue(ThermostatSelection.selectDeviceAccessReadings(available, "missing").isEmpty())
        assertTrue(
            ThermostatSelection.selectDeviceAccessReadings(available, "device-1") == listOf(dining)
        )
    }
}
