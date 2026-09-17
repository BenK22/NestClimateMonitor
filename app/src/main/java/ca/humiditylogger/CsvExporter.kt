package ca.humiditylogger

import android.content.Context
import java.io.File
import java.time.Instant

object CsvExporter {
    fun export(context: Context, readings: List<Reading>, name: String): File {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, name)
        file.bufferedWriter().use { writer ->
            writer.appendLine(
                "timestamp_utc,timestamp_ms,source,device_id,temperature_c,humidity_percent," +
                    "heating_setpoint_c,cooling_setpoint_c,system_mode,running_state," +
                    "hold_state,change_source,eco_state"
            )
            readings.forEach { reading ->
                writer.appendLine(row(reading))
            }
        }
        return file
    }

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
                    ).joinToString(",", transform = ::csvCell)

    internal fun spreadsheetSafeText(value: String): String =
        if (value.firstOrNull() in setOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value

    private fun csvCell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
}
