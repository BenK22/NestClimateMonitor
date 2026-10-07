package ca.humiditylogger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val testDatabase = "readings-instrumentation-test.db"
    private lateinit var store: ReadingStore

    @Before fun setUp() {
        context.deleteDatabase(testDatabase)
        store = ReadingStore(context, testDatabase)
    }

    @After fun tearDown() {
        DataRetention.setOneYear(context, false)
        store.close()
        context.deleteDatabase(testDatabase)
    }

    @Test fun roundTripsAllFieldsAndRangeQueries() {
        val reading = Reading(
            timestampMs = 1_000,
            source = "Dining Room",
            deviceId = "stable-id",
            temperatureC = 21.25,
            humidityPercent = 48.5,
            heatingSetpointC = 20.0,
            coolingSetpointC = 24.0,
            systemMode = "HEAT",
            runningState = "IDLE",
            holdState = "OFF",
            changeSource = "MANUAL",
            ecoState = "OFF",
        )
        store.insert(reading)

        assertEquals(listOf(reading), store.all())
        assertEquals(listOf(reading), store.between(999, 1_001))
        assertEquals(emptyList<Reading>(), store.between(1_001, 2_000))
    }

    @Test fun widgetHistoryKeepsEveryRowInTheWindowEvenAboveFourHundred() {
        val now = 100_000_000L
        val start = now - WidgetReadingSelection.GRAPH_WINDOW_MS
        val rows = (0..450).map { Reading(start + it, "Synthetic", "id", 20.0, 40.0) }
        store.insertAll(listOf(Reading(start - 1, "Old", "id", 21.0, 41.0)) + rows + listOf(
            Reading(now, "Boundary", "id", 22.0, 42.0),
            Reading(now + 1, "Future", "id", 23.0, 43.0),
        ))
        assertEquals(rows + Reading(now, "Boundary", "id", 22.0, 42.0), store.widgetHistory(now))
    }

    @Test fun bulkInsertAndDeleteBeforeAreTransactional() {
        store.insertAll(listOf(
            Reading(1_000, "Nest", "id", 20.0, 40.0),
            Reading(2_000, "Nest", "id", 21.0, 41.0),
        ))
        assertEquals(2, store.count())
        assertEquals(1, store.deleteBefore(1_500))
        assertEquals(2_000, store.all().single().timestampMs)
    }

    @Test fun csvImportServiceImmediatelyAppliesRetention() {
        val now = System.currentTimeMillis()
        val twoYears = 2L * 365L * 24L * 60L * 60L * 1000L
        DataRetention.setOneYear(context, true)

        ReadingImporter.insert(
            context,
            store,
            listOf(
                Reading(now - twoYears, "Old", null, 20.0, 40.0),
                Reading(now, "Current", null, 21.0, 41.0),
            ),
        )

        assertEquals(listOf("Current"), store.all().map(Reading::source))
    }
}
