package ca.humiditylogger

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SamplingWorkerTest {
    @Test
    fun periodicWorkRunsOnlyWhenLoggingIsEnabled() {
        assertTrue(SamplingWorker.shouldRun(loggingEnabled = true, force = false))
        assertFalse(SamplingWorker.shouldRun(loggingEnabled = false, force = false))
    }

    @Test
    fun forcedWorkRunsWhenLoggingIsDisabled() {
        assertTrue(SamplingWorker.shouldRun(loggingEnabled = false, force = true))
    }

    @Test
    fun traitlessThermostatIsNotAClimateSuccess() {
        val outcome = SamplingWorker.climateOutcome(
            listOf(Reading(1, "Nest", "device", null, null)),
        )

        assertFalse(outcome.successful)
        assertEquals("No indoor climate measurement was returned.", outcome.status)
    }

    @Test
    fun temperatureAndHumidityOnlyResponsesHaveAccurateStatus() {
        assertEquals(
            "Saved indoor temperature; no humidity trait.",
            SamplingWorker.climateOutcome(
                listOf(Reading(1, "Nest", "device", 20.0, null)),
            ).status,
        )
        assertEquals(
            "Saved indoor humidity; no temperature trait.",
            SamplingWorker.climateOutcome(
                listOf(Reading(1, "Nest", "device", null, 50.0)),
            ).status,
        )
    }
}
