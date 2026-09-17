package ca.humiditylogger

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetFreshnessPolicyTest {
    private val now = 10_000_000L

    @Test
    fun distinguishesFreshDelayedStaleAndDisabled() {
        assertEquals(WidgetFreshness.WAITING, WidgetFreshnessPolicy.evaluate(null, now, true))
        assertEquals(WidgetFreshness.FRESH, WidgetFreshnessPolicy.evaluate(now - 29 * 60_000L, now, true))
        assertEquals(WidgetFreshness.DELAYED, WidgetFreshnessPolicy.evaluate(now - 30 * 60_000L, now, true))
        assertEquals(WidgetFreshness.STALE, WidgetFreshnessPolicy.evaluate(now - 60 * 60_000L, now, true))
        assertEquals(WidgetFreshness.DISABLED, WidgetFreshnessPolicy.evaluate(now, now, false))
    }
}
