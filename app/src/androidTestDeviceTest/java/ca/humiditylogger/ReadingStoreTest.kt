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
        val predecessor = Reading(start - 1, "Old", "id", 21.0, 41.0)
        store.insertAll(listOf(predecessor) + rows + listOf(
            Reading(now, "Boundary", "id", 22.0, 42.0),
            Reading(now + 1, "Future", "id", 23.0, 43.0),
            Reading(start - 2, "Old", "id", 19.0, 39.0),
            Reading(start - 1, "Old", "id", null, null),
        ))
        assertEquals(listOf(predecessor) + rows + Reading(now, "Boundary", "id", 22.0, 42.0), store.widgetHistory(now))
    }

    @Test fun widgetHistoryKeepsPredecessorsPerTraitAndSourceDeviceIdentity() {
        val now = 100_000_000L
        val start = now - WidgetReadingSelection.GRAPH_WINDOW_MS
        val temperature = Reading(start - 20, "Synthetic", "id-a", 22.0, null)
        val humidity = Reading(start - 10, "Synthetic", "id-a", null, 45.0)
        val otherDevice = Reading(start - 5, "Synthetic", "id-b", 18.0, 55.0)
        val outdoorTemperature = Reading(start - 4, "Outdoor synthetic", null, 15.0, null)
        val outdoorHumidity = Reading(start - 2, "Outdoor synthetic", null, null, 60.0)
        val inside = Reading(start + 1, "Synthetic", "id-a", 23.0, 46.0)
        store.insertAll(listOf(
            temperature.copy(temperatureC = 21.0), temperature, humidity, otherDevice,
            outdoorTemperature, outdoorHumidity, inside,
            Reading(start - 1, "Synthetic", "id-a", null, null),
            Reading(start - 30, "Synthetic", "id-a", 17.0, 35.0),
        ))
        assertEquals(listOf(temperature, humidity, otherDevice, outdoorTemperature, outdoorHumidity, inside),
            store.widgetHistory(now))
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
