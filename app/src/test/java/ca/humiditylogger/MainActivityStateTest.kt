package ca.humiditylogger

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityStateTest {
    @Test
    fun selectedHistoryDaySurvivesRecreation() {
        val today = LocalDate.of(2026, 9, 17)
        assertEquals(
            LocalDate.of(2026, 9, 15),
            restoreSelectedHistoryDay("2026-09-15", today),
        )
        assertEquals(today, restoreSelectedHistoryDay(null, today))
        assertEquals(today, restoreSelectedHistoryDay("not-a-date", today))
    }

    @Test
    fun chartInspectionIsClearedWhenTheSelectedReadingDisappears() {
        val oldDay = listOf(Reading(1_000L, "Inside", "id", 20.0, 40.0))
        val newDay = listOf(Reading(90_000_000L, "Inside", "id", 21.0, 41.0))

        assertEquals(1_000L, retainChartSelection(1_000L, oldDay))
        assertEquals(null, retainChartSelection(1_000L, newDay))
    }
}
