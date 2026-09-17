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
}
