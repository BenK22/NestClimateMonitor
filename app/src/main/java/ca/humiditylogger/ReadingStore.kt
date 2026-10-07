package ca.humiditylogger

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite history repository with additive, non-destructive schema migrations.
 *
 * Queries return detached snapshots in chronological order. Calls are synchronous: bulk reads,
 * imports and exports should run off the UI thread. The owner must close this helper (use [use]
 * for short-lived consumers). Inserts do not deduplicate; CSV preview owns that policy.
 *
 * @param databaseName Local database filename; injectable for isolated instrumentation tests.
 */
class ReadingStore(context: Context, private val databaseName: String = "readings.db") :
    SQLiteOpenHelper(context, databaseName, null, DATABASE_VERSION) {
    private val appContext = context.applicationContext

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE readings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp_ms INTEGER NOT NULL,
                source TEXT NOT NULL,
                device_id TEXT,
                temperature_c REAL,
                humidity_percent REAL,
                heating_setpoint_c REAL,
                cooling_setpoint_c REAL,
                system_mode TEXT,
                running_state TEXT,
                hold_state TEXT,
                change_source TEXT,
                eco_state TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX readings_time ON readings(timestamp_ms)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE readings ADD COLUMN heating_setpoint_c REAL")
            db.execSQL("ALTER TABLE readings ADD COLUMN cooling_setpoint_c REAL")
            db.execSQL("ALTER TABLE readings ADD COLUMN system_mode TEXT")
            db.execSQL("ALTER TABLE readings ADD COLUMN running_state TEXT")
            db.execSQL("ALTER TABLE readings ADD COLUMN hold_state TEXT")
            db.execSQL("ALTER TABLE readings ADD COLUMN change_source TEXT")
            db.execSQL("ALTER TABLE readings ADD COLUMN eco_state TEXT")
        }
        if (oldVersion < 3) db.execSQL("ALTER TABLE readings ADD COLUMN device_id TEXT")
    }

    /** Appends one snapshot, preserving null traits; throws if SQLite cannot insert the row. */
    fun insert(reading: Reading) {
        val values = ContentValues().apply {
            put("timestamp_ms", reading.timestampMs)
            put("source", reading.source)
            put("device_id", reading.deviceId)
            reading.temperatureC?.let { put("temperature_c", it) } ?: putNull("temperature_c")
            reading.humidityPercent?.let { put("humidity_percent", it) }
                ?: putNull("humidity_percent")
            reading.heatingSetpointC?.let { put("heating_setpoint_c", it) }
                ?: putNull("heating_setpoint_c")
            reading.coolingSetpointC?.let { put("cooling_setpoint_c", it) }
                ?: putNull("cooling_setpoint_c")
            put("system_mode", reading.systemMode)
            put("running_state", reading.runningState)
            put("hold_state", reading.holdState)
            put("change_source", reading.changeSource)
            put("eco_state", reading.ecoState)
        }
        writableDatabase.insertOrThrow("readings", null, values)
    }

    /** Returns the newest [limit] rows, reordered oldest first for charts and last-item selection. */
    fun recent(limit: Int = 720): List<Reading> {
        return queryReadings(
            selection = null,
            selectionArgs = null,
            orderBy = "timestamp_ms DESC",
            limit = limit.toString(),
        ).asReversed()
    }

    /** Returns all rows oldest first; may allocate substantial memory with unlimited retention. */
    fun all(): List<Reading> = queryReadings(
        selection = null,
        selectionArgs = null,
        orderBy = "timestamp_ms ASC",
        limit = null,
    )

    /** Returns chronological rows in `startMs <= timestamp < endExclusiveMs` (epoch milliseconds). */
    fun between(startMs: Long, endExclusiveMs: Long): List<Reading> = queryReadings(
        selection = "timestamp_ms >= ? AND timestamp_ms < ?",
        selectionArgs = arrayOf(startMs.toString(), endExclusiveMs.toString()),
        orderBy = "timestamp_ms ASC",
        limit = null,
    )

    /** Full six-hour widget history, independent of how many devices/imported rows were saved. */
    fun widgetHistory(nowMs: Long): List<Reading> = between(
        nowMs - WidgetReadingSelection.GRAPH_WINDOW_MS,
        nowMs + 1,
    )

    private fun queryReadings(
        selection: String?,
        selectionArgs: Array<String>?,
        orderBy: String,
        limit: String?,
    ): List<Reading> {
        val result = mutableListOf<Reading>()
        readableDatabase.query(
            "readings",
            arrayOf(
                "timestamp_ms", "source", "device_id", "temperature_c", "humidity_percent",
                "heating_setpoint_c", "cooling_setpoint_c", "system_mode",
                "running_state", "hold_state", "change_source", "eco_state",
            ),
            selection,
            selectionArgs,
            null,
            null,
            orderBy,
            limit,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += Reading(
                    timestampMs = cursor.getLong(0),
                    source = cursor.getString(1),
                    deviceId = if (cursor.isNull(2)) null else cursor.getString(2),
                    temperatureC = if (cursor.isNull(3)) null else cursor.getDouble(3),
                    humidityPercent = if (cursor.isNull(4)) null else cursor.getDouble(4),
                    heatingSetpointC = if (cursor.isNull(5)) null else cursor.getDouble(5),
                    coolingSetpointC = if (cursor.isNull(6)) null else cursor.getDouble(6),
                    systemMode = if (cursor.isNull(7)) null else cursor.getString(7),
                    runningState = if (cursor.isNull(8)) null else cursor.getString(8),
                    holdState = if (cursor.isNull(9)) null else cursor.getString(9),
                    changeSource = if (cursor.isNull(10)) null else cursor.getString(10),
                    ecoState = if (cursor.isNull(11)) null else cursor.getString(11),
                )
            }
        }
        return result
    }

    /** Permanently deletes climate history only; caller must obtain user confirmation first. */
    fun clear() {
        writableDatabase.delete("readings", null, null)
    }

    /** Inserts the batch atomically; any failed insert rolls back every row in the batch. */
    fun insertAll(readings: List<Reading>) = writableDatabase.inTransaction {
        readings.forEach(::insert)
    }

    private inline fun SQLiteDatabase.inTransaction(block: () -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    /** Number of stored snapshots across all indoor devices and outdoor locations. */
    fun count(): Long = readableDatabase.rawQuery("SELECT COUNT(*) FROM readings", null).use { cursor ->
        cursor.moveToFirst()
        cursor.getLong(0)
    }

    /** Main database file size only; excludes journals and is not total app storage usage. */
    fun databaseSizeBytes(): Long = appContext.getDatabasePath(databaseName).length()

    /** Deletes rows strictly older than [timestampMs] and returns the deleted count. */
    fun deleteBefore(timestampMs: Long): Int = writableDatabase.delete(
        "readings",
        "timestamp_ms < ?",
        arrayOf(timestampMs.toString()),
    )

    private companion object {
        const val DATABASE_VERSION = 3
    }
}
