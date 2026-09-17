package ca.humiditylogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class CsvImporterTest {
    @Test
    fun parsesQuotedCellsAndSkipsDuplicates() {
        val existing = Reading(1_700_000_000_000, "Dining, Room", "device-1", 20.0, 45.0)
        val csv = "timestamp_ms,source,device_id,temperature_c,humidity_percent\n" +
            "1700000000000,\"Dining, Room\",device-1,20.0,45.0\n" +
            "1700000001000,\"Office \"\"North\"\"\",device-2,21.5,50.0\n" +
            "bad,Broken,,x,\n"

        val preview = CsvImporter.preview(csv, listOf(existing))

        assertEquals(1, preview.readings.size)
        assertEquals("Office \"North\"", preview.readings.single().source)
        assertEquals(1, preview.duplicateCount)
        assertEquals(1, preview.invalidCount)
    }

    @Test
    fun importsLegacyExportWithoutDeviceId() {
        val csv = "timestamp_ms,source,temperature_c,humidity_percent\n1700000000000,Nest,22.0,55.0"
        val preview = CsvImporter.preview(csv, emptyList())
        assertEquals(1, preview.readings.size)
        assertTrue(preview.readings.single().deviceId == null)
    }

    @Test
    fun rejectsFutureNonFiniteAndImplausibleValues() {
        val now = 1_700_000_000_000
        val csv = "timestamp_ms,source,temperature_c,humidity_percent\n" +
            "1700172800000,Future,22,50\n" +
            "1700000000000,Nan,NaN,50\n" +
            "1700000000000,Humidity,22,101\n"

        val preview = CsvImporter.preview(csv, emptyList(), now)

        assertEquals(0, preview.readings.size)
        assertEquals(3, preview.invalidCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun boundedReaderRejectsOversizedFiles() {
        CsvImporter.readLimited(StringReader("x".repeat(CsvImporter.MAX_CSV_CHARS + 1)))
    }

    @Test
    fun exportNeutralizesSpreadsheetFormulasInTextFields() {
        val row = CsvExporter.row(Reading(1_700_000_000_000, "=IMPORTXML(\"bad\")", null, -5.0, 50.0))

        assertTrue(row.contains("'=IMPORTXML"))
        assertTrue(row.contains(",-5.0,"))
    }
}
