package ca.humiditylogger

object CsvImporter {
    data class Preview(
        val readings: List<Reading>,
        val duplicateCount: Int,
        val invalidCount: Int,
    )

    fun preview(csv: String, existing: List<Reading>): Preview {
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
                Reading(
                    timestampMs = value("timestamp_ms")!!.toLong(),
                    source = value("source")!!,
                    deviceId = value("device_id"),
                    temperatureC = value("temperature_c")?.toDouble(),
                    humidityPercent = value("humidity_percent")?.toDouble(),
                    heatingSetpointC = value("heating_setpoint_c")?.toDouble(),
                    coolingSetpointC = value("cooling_setpoint_c")?.toDouble(),
                    systemMode = value("system_mode"),
                    runningState = value("running_state"),
                    holdState = value("hold_state"),
                    changeSource = value("change_source"),
                    ecoState = value("eco_state"),
                )
            }.getOrNull()
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

    internal fun key(reading: Reading): String =
        "${reading.timestampMs}\u0000${reading.deviceId.orEmpty()}\u0000${reading.source}"

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
                }
                else -> cell.append(char)
            }
            index++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows
    }
}
