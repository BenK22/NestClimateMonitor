package ca.humiditylogger

import org.junit.Assert.assertEquals
import org.junit.Test

/** Selected-mode shading must stay distinct from running state and bounded by saved evidence. */
class ThermostatModeTest {
    @Test fun normalizesSdmAndHomeModesButNotRunningStates() {
        val expected = mapOf(" heat " to ThermostatMode.HEAT, "Cool" to ThermostatMode.COOL,
            "HEATCOOL" to ThermostatMode.HEAT_COOL, "Auto" to ThermostatMode.HEAT_COOL,
            "OFF" to ThermostatMode.OFF)
        expected.forEach { (raw, mode) -> assertEquals(mode, ThermostatMode.fromReported(raw)) }
        listOf(null, "", "Heating", "Cooling", "UnknownValue", "4", "Eco", "FanOnly").forEach {
            assertEquals(ThermostatMode.UNKNOWN, ThermostatMode.fromReported(it))
        }
    }

    @Test fun usesSelectedModeEvenWhenEquipmentIsOffOrRunningInAnotherDirection() {
        val rows = listOf(row(10, "HEATCOOL").copy(runningState = "OFF"),
            row(20, "OFF").copy(runningState = "HEATING"))
        assertEquals(listOf(ThermostatModeBand(10, 20, ThermostatMode.HEAT_COOL),
            ThermostatModeBand(20, 30, ThermostatMode.OFF)), ThermostatModeHistory.bands(rows, 0, 30))
    }

    @Test fun missingAndUnsupportedSnapshotsInterruptKnownMode() {
        val rows = listOf(row(10, "HEAT"), row(20, null), row(30, "FutureValue"), row(40, "COOL"))
        assertEquals(listOf(ThermostatModeBand(10, 20, ThermostatMode.HEAT),
            ThermostatModeBand(40, 50, ThermostatMode.COOL)), ThermostatModeHistory.bands(rows, 0, 50))
    }

    @Test fun neverFillsLongGapsOrTimeBeforeFirstSample() {
        val cap = ThermostatModeHistory.MAX_HOLD_MS
        val rows = listOf(row(10, "HEAT"), row(cap * 3, "COOL"))
        assertEquals(listOf(ThermostatModeBand(10, 10 + cap, ThermostatMode.HEAT),
            ThermostatModeBand(cap * 3, cap * 4, ThermostatMode.COOL)),
            ThermostatModeHistory.bands(rows, 0, cap * 5))
    }

    @Test fun clipsPredecessorAtWindowStartAndEndAtNowWithoutUsingFutureRows() {
        val rows = listOf(row(5, "HEAT"), row(15, "COOL"), row(30, "OFF"))
        assertEquals(listOf(ThermostatModeBand(10, 15, ThermostatMode.HEAT),
            ThermostatModeBand(15, 20, ThermostatMode.COOL)), ThermostatModeHistory.bands(rows, 10, 20))
        assertEquals(emptyList<ThermostatModeBand>(), ThermostatModeHistory.bands(rows, 20, 20))
    }

    @Test fun excludesOutdoorModesAndUsesLastInputAtDuplicateTimestamp() {
        val rows = listOf(row(20, "COOL"), row(10, "HEAT"),
            row(15, "OFF").copy(source = WeatherClient.SOURCE_PREFIX + "Synthetic"), row(20, "OFF"))
        assertEquals(listOf(ThermostatModeBand(10, 20, ThermostatMode.HEAT),
            ThermostatModeBand(20, 30, ThermostatMode.OFF)), ThermostatModeHistory.bands(rows, 0, 30))
    }

    @Test fun rawModeHistorySurvivesCsvBackup() {
        val rows = listOf("HEAT", "COOL", "HEATCOOL", "OFF", "FutureMode", null)
            .mapIndexed { index, mode -> row(1_700_000_000_000L + index, mode) }
        val csv = CsvExporter.header + "\n" + rows.joinToString("\n", transform = CsvExporter::row)
        assertEquals(rows, CsvImporter.preview(csv, emptyList()).readings)
    }

    private fun row(time: Long, mode: String?) =
        Reading(time, "Synthetic", "indoor", 20.0, 50.0, systemMode = mode)
}
