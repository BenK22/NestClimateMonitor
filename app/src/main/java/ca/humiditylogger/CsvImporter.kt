package ca.humiditylogger

import java.io.Reader
import java.util.Base64

/** Bounded CSV validation and deduplication for current and older climate-history exports. */
object CsvImporter {
    /** Accepted new rows plus counts excluded by duplicate identity or validation failures. */
    data class Preview(
        val readings: List<Reading>,
        val duplicateCount: Int,
        val invalidCount: Int,
    )

    /**
     * Parses without writing storage, validates rows and removes existing/in-file duplicates.
     *
     * Encoded companion columns restore original text neutralized for spreadsheet safety.
     * Identity is timestamp + device ID + source, not measurement values. Missing required
     * headers produce an invalid-row preview; individual invalid rows do not reject good rows.
     *
     * @param nowMs Epoch milliseconds used for the five-minute future-time tolerance.
     * @throws IllegalArgumentException if the character or row-count limit is exceeded.
     */
    fun preview(
        csv: String,
        existing: List<Reading>,
        nowMs: Long = System.currentTimeMillis(),
    ): Preview {
        require(csv.length <= MAX_CSV_CHARS) { "CSV files are limited to ${MAX_CSV_CHARS / 1_000_000} MB." }
        val rows = parseRows(csv)
        if (rows.isEmpty()) return Preview(emptyList(), 0, 0)
        val header = rows.first().map { it.trim() }
        val indices = header.withIndex().associate { it.value to it.index }
        if (indices["timestamp_ms"] == null || indices["source"] == null) {
            return Preview(emptyList(), 0, (rows.size - 1).coerceAtLeast(1))
        }
        val keys = existing.mapTo(mutableSetOf(), ::key)
        val imported = mutableListOf<Reading>()
        var duplicates = 0
        var invalid = 0
        rows.drop(1).filter { row -> row.any { it.isNotBlank() } }.forEach { row ->
            val reading = runCatching {
                fun value(name: String): String? = indices[name]?.let(row::getOrNull)?.takeIf { it.isNotBlank() }
                fun textValue(name: String): String? {
                    val encoded = value("${name}_b64")
                    return if (encoded != null) {
                        decodeText(encoded).takeIf { it.isNotBlank() }
                    } else {
                        value(name)
                    }
                }
                Reading(
                    timestampMs = value("timestamp_ms")!!.toLong(),
                    source = textValue("source")!!,
                    deviceId = textValue("device_id"),
                    temperatureC = value("temperature_c")?.toDouble(),
                    humidityPercent = value("humidity_percent")?.toDouble(),
                    heatingSetpointC = value("heating_setpoint_c")?.toDouble(),
                    coolingSetpointC = value("cooling_setpoint_c")?.toDouble(),
                    systemMode = textValue("system_mode"),
                    runningState = textValue("running_state"),
                    holdState = textValue("hold_state"),
                    changeSource = textValue("change_source"),
                    ecoState = textValue("eco_state"),
                )
            }.getOrNull()?.takeIf { isValid(it, nowMs) }
            if (reading == null) {
                invalid++
            } else if (!keys.add(key(reading))) {
                duplicates++
            } else {
                imported += reading
            }
        }
        return Preview(imported, duplicates, invalid)
    }

    /** Import identity shared by existing and incoming rows; not a database uniqueness constraint. */
    internal fun key(reading: Reading): String =
        "${reading.timestampMs}\u0000${reading.deviceId.orEmpty()}\u0000${reading.source}"

    /** Parses escaped quotes, embedded newlines and CRLF, subject to the row-count limit. */
    internal fun parseRows(csv: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < csv.length) {
            val char = csv[index]
            when {
                quoted && char == '"' && csv.getOrNull(index + 1) == '"' -> {
                    cell.append('"'); index++
                }
                char == '"' -> quoted = !quoted
                !quoted && char == ',' -> { row += cell.toString(); cell.clear() }
                !quoted && (char == '\n' || char == '\r') -> {
                    if (char == '\r' && csv.getOrNull(index + 1) == '\n') index++
                    row += cell.toString(); cell.clear(); rows += row; row = mutableListOf()
                    require(rows.size <= MAX_ROWS) { "CSV files are limited to $MAX_ROWS rows." }
                }
                else -> cell.append(char)
            }
            index++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            rows += row
            require(rows.size <= MAX_ROWS) { "CSV files are limited to $MAX_ROWS rows." }
        }
        return rows
    }

    /**
     * Reads at most [MAX_CSV_CHARS] characters before parsing; the caller owns closing [reader].
     *
     * @throws IllegalArgumentException if the character budget is exceeded (not a byte limit).
     */
    fun readLimited(reader: Reader): String {
        val result = StringBuilder()
        val buffer = CharArray(8_192)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            if (result.length + count > MAX_CSV_CHARS) {
                throw IllegalArgumentException("CSV files are limited to ${MAX_CSV_CHARS / 1_000_000} MB.")
            }
            result.append(buffer, 0, count)
        }
        return result.toString()
    }

    private fun isValid(reading: Reading, nowMs: Long): Boolean {
        if (reading.timestampMs !in MIN_TIMESTAMP_MS..(nowMs + MAX_FUTURE_SKEW_MS)) return false
        if (reading.source.isBlank() || reading.source.length > MAX_TEXT_LENGTH) return false
        if (reading.deviceId?.length.orZero() > MAX_TEXT_LENGTH) return false
        if (!validTemperature(reading.temperatureC) || !validTemperature(reading.heatingSetpointC) ||
            !validTemperature(reading.coolingSetpointC)
        ) return false
        val humidity = reading.humidityPercent
        if (humidity != null && (!humidity.isFinite() || humidity !in 0.0..100.0)) return false
        return listOf(
            reading.systemMode,
            reading.runningState,
            reading.holdState,
            reading.changeSource,
            reading.ecoState,
        ).all { it == null || it.length <= MAX_TEXT_LENGTH }
    }

    private fun validTemperature(value: Double?): Boolean =
        value == null || (value.isFinite() && value in -100.0..100.0)

    private fun decodeText(value: String): String {
        require(value.startsWith("b64:")) { "Unsupported encoded CSV text." }
        return String(Base64.getDecoder().decode(value.removePrefix("b64:")), Charsets.UTF_8)
    }

    private fun Int?.orZero(): Int = this ?: 0

    /** Maximum decoded character count, independent of the source file's byte encoding. */
    const val MAX_CSV_CHARS = 10_000_000
    private const val MAX_ROWS = 100_000
    private const val MAX_TEXT_LENGTH = 512
    private const val MIN_TIMESTAMP_MS = 946_684_800_000L // 2000-01-01 UTC
    private const val MAX_FUTURE_SKEW_MS = 5L * 60L * 1000L
}
