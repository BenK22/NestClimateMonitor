package ca.humiditylogger

import android.content.Context
import java.io.File
import java.time.Instant
import java.util.Base64

/** Produces UTF-8, Celsius-based CSV backups with spreadsheet-safe and lossless text columns. */
object CsvExporter {
    /**
     * Writes an export into the app's private cache; sharing is a separate FileProvider action.
     *
     * @param name Trusted app-generated filename, not an unchecked user-supplied path.
     * @return Cached file; not a durable backup until the user copies/shares it elsewhere.
     */
    fun export(context: Context, readings: List<Reading>, name: String): File {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, name)
        file.bufferedWriter().use { writer ->
            writer.appendLine(header)
            readings.forEach { reading ->
                writer.appendLine(row(reading))
            }
        }
        return file
    }

    /** Serializes one record with blank null traits and an ISO-8601 UTC display timestamp. */
    internal fun row(reading: Reading): String =
        listOf(
                        Instant.ofEpochMilli(reading.timestampMs).toString(),
                        reading.timestampMs.toString(),
                        spreadsheetSafeText(reading.source),
                        spreadsheetSafeText(reading.deviceId.orEmpty()),
                        reading.temperatureC?.toString().orEmpty(),
                        reading.humidityPercent?.toString().orEmpty(),
                        reading.heatingSetpointC?.toString().orEmpty(),
                        reading.coolingSetpointC?.toString().orEmpty(),
                        spreadsheetSafeText(reading.systemMode.orEmpty()),
                        spreadsheetSafeText(reading.runningState.orEmpty()),
                        spreadsheetSafeText(reading.holdState.orEmpty()),
                        spreadsheetSafeText(reading.changeSource.orEmpty()),
                        spreadsheetSafeText(reading.ecoState.orEmpty()),
                        encodedText(reading.source),
                        encodedText(reading.deviceId.orEmpty()),
                        encodedText(reading.systemMode.orEmpty()),
                        encodedText(reading.runningState.orEmpty()),
                        encodedText(reading.holdState.orEmpty()),
                        encodedText(reading.changeSource.orEmpty()),
                        encodedText(reading.ecoState.orEmpty()),
                    ).joinToString(",", transform = ::csvCell)

    /** Stable interchange columns; importer also accepts older files without encoded companions. */
    internal val header =
        "timestamp_utc,timestamp_ms,source,device_id,temperature_c,humidity_percent," +
            "heating_setpoint_c,cooling_setpoint_c,system_mode,running_state," +
            "hold_state,change_source,eco_state,source_b64,device_id_b64,system_mode_b64," +
            "running_state_b64,hold_state_b64,change_source_b64,eco_state_b64"

    /** Prefixes formula-triggering text with an apostrophe instead of trusting CSV quoting alone. */
    internal fun spreadsheetSafeText(value: String): String =
        if (needsSpreadsheetNeutralization(value)) "'$value" else value

    /** Retains the original unsafe text for exact re-import; Base64 is encoding, not encryption. */
    internal fun encodedText(value: String): String =
        if (needsSpreadsheetNeutralization(value)) {
            "b64:" + Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
        } else {
            ""
        }

    private fun needsSpreadsheetNeutralization(value: String): Boolean =
        value.firstOrNull() in setOf('=', '+', '-', '@', '\t', '\r')

    private fun csvCell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
}
