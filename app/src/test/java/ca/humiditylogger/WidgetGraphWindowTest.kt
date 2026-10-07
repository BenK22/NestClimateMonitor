package ca.humiditylogger

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetGraphWindowTest {
    private fun reading(time: Long, value: Double?) = Reading(time, "Synthetic", "id", value, null)

    @Test fun crossingSegmentIsClippedAtItsInterpolatedLeftEdge() {
        val rows = listOf(reading(0, 10.0), reading(20, 30.0), reading(40, 40.0), reading(50, 500.0))
        assertEquals(listOf(WidgetGraphPoint(10, 20.0), WidgetGraphPoint(20, 30.0), WidgetGraphPoint(40, 40.0)),
            WidgetGraphWindow.points(rows, 10, 40) { it.temperatureC })
    }

    @Test fun exactBoundaryReadingIsNotDuplicated() {
        val rows = listOf(reading(0, 10.0), reading(10, 20.0), reading(20, 30.0))
        assertEquals(listOf(WidgetGraphPoint(10, 20.0), WidgetGraphPoint(20, 30.0)),
            WidgetGraphWindow.points(rows, 10, 40) { it.temperatureC })
    }

    @Test fun noEarlierReadingDoesNotInventBackwardHistory() {
        assertEquals(listOf(WidgetGraphPoint(20, 30.0)),
            WidgetGraphWindow.points(listOf(reading(20, 30.0)), 10, 40) { it.temperatureC })
    }

    @Test fun staleOnlyAndFutureSamplesDoNotProduceAVisibleLine() {
        assertEquals(emptyList<WidgetGraphPoint>(),
            WidgetGraphWindow.points(listOf(reading(0, 10.0), reading(50, 50.0)), 10, 40) { it.temperatureC })
    }

    @Test fun nullTraitsCannotReplaceAnEarlierMeasurement() {
        val rows = listOf(reading(0, 10.0), reading(5, null), reading(20, 30.0))
        assertEquals(listOf(WidgetGraphPoint(10, 20.0), WidgetGraphPoint(20, 30.0)),
            WidgetGraphWindow.points(rows, 10, 40) { it.temperatureC })
    }

    @Test fun suppliedPointsAreOrderedBeforeClipping() {
        val rows = listOf(reading(40, 40.0), reading(20, 30.0), reading(0, 10.0))
        assertEquals(listOf(WidgetGraphPoint(10, 20.0), WidgetGraphPoint(20, 30.0), WidgetGraphPoint(40, 40.0)),
            WidgetGraphWindow.points(rows, 10, 40) { it.temperatureC })
    }

    @Test fun elapsedSamplingGapsKeepTheSameJoinedSegmentsAsTheDailyChart() {
        val rows = listOf(reading(0, 0.0), reading(600, 60.0))
        assertEquals(listOf(WidgetGraphPoint(300, 30.0), WidgetGraphPoint(600, 60.0)),
            WidgetGraphWindow.points(rows, 300, 800) { it.temperatureC })
    }
}
