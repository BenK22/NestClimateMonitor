package ca.humiditylogger

import org.junit.Assert.assertEquals
import org.junit.Test

class DataRetentionTest {
    @Test fun cutoffIsExactly365Days() {
        val year = 365L * 24L * 60L * 60L * 1000L
        assertEquals(1_000L, DataRetention.cutoff(year + 1_000L))
    }
}
