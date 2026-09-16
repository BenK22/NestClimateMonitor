package ca.humiditylogger

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ReadingStore(context: Context) :
    SQLiteOpenHelper(context, "readings.db", null, DATABASE_VERSION) {
    private val appContext = context.applicationContext

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE readings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp_ms INTEGER NOT NULL,
                source TEXT NOT NULL,
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
    }

    fun insert(reading: Reading) {
        val values = ContentValues().apply {
            put("timestamp_ms", reading.timestampMs)
            put("source", reading.source)
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

    fun recent(limit: Int = 720): List<Reading> {
        return queryReadings(
            selection = null,
            selectionArgs = null,
            orderBy = "timestamp_ms DESC",
            limit = limit.toString(),
        ).asReversed()
    }

    fun all(): List<Reading> = queryReadings(
        selection = null,
        selectionArgs = null,
        orderBy = "timestamp_ms ASC",
        limit = null,
    )

    fun between(startMs: Long, endExclusiveMs: Long): List<Reading> = queryReadings(
        selection = "timestamp_ms >= ? AND timestamp_ms < ?",
        selectionArgs = arrayOf(startMs.toString(), endExclusiveMs.toString()),
        orderBy = "timestamp_ms ASC",
        limit = null,
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
                "timestamp_ms", "source", "temperature_c", "humidity_percent",
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
                    temperatureC = if (cursor.isNull(2)) null else cursor.getDouble(2),
                    humidityPercent = if (cursor.isNull(3)) null else cursor.getDouble(3),
                    heatingSetpointC = if (cursor.isNull(4)) null else cursor.getDouble(4),
                    coolingSetpointC = if (cursor.isNull(5)) null else cursor.getDouble(5),
                    systemMode = if (cursor.isNull(6)) null else cursor.getString(6),
                    runningState = if (cursor.isNull(7)) null else cursor.getString(7),
                    holdState = if (cursor.isNull(8)) null else cursor.getString(8),
                    changeSource = if (cursor.isNull(9)) null else cursor.getString(9),
                    ecoState = if (cursor.isNull(10)) null else cursor.getString(10),
                )
            }
        }
        return result
    }

    fun clear() {
        writableDatabase.delete("readings", null, null)
    }

    fun count(): Long = readableDatabase.rawQuery("SELECT COUNT(*) FROM readings", null).use { cursor ->
        cursor.moveToFirst()
        cursor.getLong(0)
    }

    fun databaseSizeBytes(): Long = appContext.getDatabasePath("readings.db").length()

    fun deleteBefore(timestampMs: Long): Int = writableDatabase.delete(
        "readings",
        "timestamp_ms < ?",
        arrayOf(timestampMs.toString()),
    )

    private companion object {
        const val DATABASE_VERSION = 2
    }
}
