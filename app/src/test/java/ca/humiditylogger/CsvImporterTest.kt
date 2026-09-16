package ca.humiditylogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvImporterTest {
    @Test
    fun parsesQuotedCellsAndSkipsDuplicates() {
        val existing = Reading(100, "Dining, Room", "device-1", 20.0, 45.0)
        val csv = "timestamp_ms,source,device_id,temperature_c,humidity_percent\n" +
            "100,\"Dining, Room\",device-1,20.0,45.0\n" +
            "200,\"Office \"\"North\"\"\",device-2,21.5,50.0\n" +
            "bad,Broken,,x,\n"

        val preview = CsvImporter.preview(csv, listOf(existing))

        assertEquals(1, preview.readings.size)
        assertEquals("Office \"North\"", preview.readings.single().source)
        assertEquals(1, preview.duplicateCount)
        assertEquals(1, preview.invalidCount)
    }

    @Test
    fun importsLegacyExportWithoutDeviceId() {
        val csv = "timestamp_ms,source,temperature_c,humidity_percent\n100,Nest,22.0,55.0"
        val preview = CsvImporter.preview(csv, emptyList())
        assertEquals(1, preview.readings.size)
        assertTrue(preview.readings.single().deviceId == null)
    }
}
