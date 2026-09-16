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
    private val testDatabase = "readings-instrumentation-test.db"
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: ReadingStore

    @Before fun setUp() {
        context.deleteDatabase(testDatabase)
        store = ReadingStore(context, testDatabase)
    }

    @After fun tearDown() {
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

    @Test fun bulkInsertAndDeleteBeforeAreTransactional() {
        store.insertAll(listOf(
            Reading(1_000, "Nest", "id", 20.0, 40.0),
            Reading(2_000, "Nest", "id", 21.0, 41.0),
        ))
        assertEquals(2, store.count())
        assertEquals(1, store.deleteBefore(1_500))
        assertEquals(2_000, store.all().single().timestampMs)
    }
}
