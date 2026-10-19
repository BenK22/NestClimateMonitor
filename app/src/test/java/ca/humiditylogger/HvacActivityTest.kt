package ca.humiditylogger

import org.junit.Assert.assertEquals
import org.junit.Test

/** Snapshot semantics must not turn selected modes or absent provider values into runtime history. */
class HvacActivityTest {
    @Test fun mapsSdmAndHomeRunningValuesWithoutCaseOrWhitespaceAssumptions() {
        for (value in listOf("HEATING", "Heat", " heating ")) {
            assertEquals(HvacActivity.HEATING, HvacActivity.fromReported(value))
        }
        for (value in listOf("COOLING", "Cool", " cooling ")) {
            assertEquals(HvacActivity.COOLING, HvacActivity.fromReported(value))
        }
        for (value in listOf("OFF", "Off", "IDLE", " idle ")) {
            assertEquals(HvacActivity.IDLE, HvacActivity.fromReported(value))
        }
    }

    @Test fun missingNumericAndUnrecognizedStatesAreNotOffOrOn() {
        for (value in listOf(null, "", " ", "UnknownValue", "HEATCOOL", "ON", "4", "fanRunning=true")) {
            assertEquals(HvacActivity.UNKNOWN, HvacActivity.fromReported(value))
        }
    }

    @Test fun neverUsesSelectedModeToInferWhetherEquipmentIsOn() {
        val readings = listOf(
            reading(10, "OFF").copy(systemMode = "HEAT"),
            reading(20, "HEATING").copy(systemMode = "OFF"),
            reading(30, null).copy(systemMode = "COOL"),
        )
        assertEquals(listOf(HvacActivity.IDLE, HvacActivity.HEATING, HvacActivity.UNKNOWN),
            HvacHistory.samples(readings, 0, 100).map { it.activity })
    }

    @Test fun emitsOnlyActualIndoorFiniteTemperatureSamplesInHalfOpenWindow() {
        val readings = listOf(
            reading(9, "HEATING"), reading(10, "OFF"), reading(19, "COOLING"), reading(20, "OFF"),
            reading(12, "HEATING").copy(source = WeatherClient.SOURCE_PREFIX + "Synthetic"),
            reading(13, "OFF").copy(temperatureC = null),
            reading(14, "OFF").copy(temperatureC = Double.NaN),
            reading(15, "OFF").copy(temperatureC = Double.POSITIVE_INFINITY),
        )
        assertEquals(listOf(HvacSample(10, 20.0, HvacActivity.IDLE),
            HvacSample(19, 20.0, HvacActivity.COOLING)), HvacHistory.samples(readings, 10, 20))
    }

    @Test fun doesNotCarryForwardOrInterpolateAtWindowBoundaryOrAcrossGaps() {
        assertEquals(emptyList<HvacSample>(), HvacHistory.samples(listOf(reading(9, "HEATING")), 10, 20))
        val actual = listOf(reading(9, "HEATING"), reading(15, null), reading(80, "OFF"))
        assertEquals(listOf(HvacSample(15, 20.0, HvacActivity.UNKNOWN),
            HvacSample(80, 20.0, HvacActivity.IDLE)), HvacHistory.samples(actual, 10, 100))
    }

    @Test fun rawRunningStatesSurviveCsvBackupIncludingMissingAndFutureValues() {
        val original = listOf("HEATING", "COOLING", "OFF", "UnknownFutureValue", null)
            .mapIndexed { index, state -> reading(1_700_000_000_000L + index, state) }
        val csv = CsvExporter.header + "\n" + original.joinToString("\n", transform = CsvExporter::row)
        assertEquals(original, CsvImporter.preview(csv, emptyList()).readings)
    }

    private fun reading(time: Long, state: String?) =
        Reading(time, "Synthetic", "indoor", 20.0, 50.0, runningState = state)
}
