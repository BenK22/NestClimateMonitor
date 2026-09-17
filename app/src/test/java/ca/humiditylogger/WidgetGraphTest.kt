package ca.humiditylogger

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetGraphTest {
    @Test
    fun graphOnlyBitmapAccountsForLayoutPadding() {
        assertEquals(
            WidgetGraphSizing.ContentSize(224, 104),
            WidgetGraphSizing.graphOnlyContentSize(240, 120),
        )
    }

    @Test
    fun climateBitmapAccountsForPaddingHeaderAndFooter() {
        assertEquals(
            WidgetGraphSizing.ContentSize(220, 41),
            WidgetGraphSizing.climateContentSize(240, 120),
        )
    }

    @Test
    fun smallestWidgetStillProducesPositiveContentDimensions() {
        assertEquals(
            WidgetGraphSizing.ContentSize(1, 1),
            WidgetGraphSizing.graphOnlyContentSize(1, 1),
        )
    }
}
