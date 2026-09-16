package ca.humiditylogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClimateMetricsTest {
    @Test fun dewPointMatchesKnownApproximation() {
        assertEquals(9.3, ClimateMetrics.dewPointC(20.0, 50.0)!!, 0.2)
    }

    @Test fun dewPointRejectsImpossibleHumidity() {
        assertNull(ClimateMetrics.dewPointC(20.0, 0.0))
    }

    @Test fun comfortBoundariesAreStable() {
        assertEquals("Dry", ClimateMetrics.comfortStatus(21.0, 29.9))
        assertEquals("Comfortable", ClimateMetrics.comfortStatus(21.0, 45.0))
        assertEquals("Condensation risk", ClimateMetrics.comfortStatus(21.0, 70.0))
    }
}
