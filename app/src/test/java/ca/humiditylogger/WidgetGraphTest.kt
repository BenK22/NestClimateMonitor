package ca.humiditylogger

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetGraphTest {
    @Test
    fun reportedSizesSelectCurrentOrientationRegardlessOfListOrder() {
        val portrait = WidgetGraphSizing.ContentSize(395, 150)
        val landscape = WidgetGraphSizing.ContentSize(751, 64)
        val reported = listOf(landscape, portrait)
        assertEquals(portrait, WidgetGraphSizing.closestSize(reported, WidgetGraphSizing.ContentSize(395, 150)))
        assertEquals(landscape, WidgetGraphSizing.closestSize(reported, WidgetGraphSizing.ContentSize(751, 80)))
    }

    @Test
    fun absentReportedSizesUseCurrentOrientationEstimate() {
        val estimate = WidgetGraphSizing.ContentSize(395, 150)
        assertEquals(estimate, WidgetGraphSizing.closestSize(emptyList(), estimate))
    }

    @Test
    fun legacyBoundsChooseARealOrientationInsteadOfTheTwoMinimums() {
        assertEquals(WidgetGraphSizing.ContentSize(240, 220), WidgetGraphSizing.orientationSize(240, 100, 420, 220, false))
        assertEquals(WidgetGraphSizing.ContentSize(420, 100), WidgetGraphSizing.orientationSize(240, 100, 420, 220, true))
    }

    @Test
    fun missingMaximumBoundsFallBackToMinimums() {
        assertEquals(WidgetGraphSizing.ContentSize(240, 100), WidgetGraphSizing.orientationSize(240, 100, 0, 0, false))
    }

    @Test
    fun largeBitmapCapsPreserveAspectRatioInsteadOfLetterboxing() {
        assertEquals(WidgetGraphSizing.PixelSize(1200, 300), WidgetGraphSizing.pixelSize(800, 200, 3f))
        assertEquals(WidgetGraphSizing.PixelSize(250, 750), WidgetGraphSizing.pixelSize(100, 300, 3f))
        assertEquals(WidgetGraphSizing.PixelSize(200, 100), WidgetGraphSizing.pixelSize(100, 50, 2f))
    }

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
