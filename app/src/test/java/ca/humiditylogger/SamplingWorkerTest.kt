package ca.humiditylogger

import org.junit.Assert.assertFalse
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
}
