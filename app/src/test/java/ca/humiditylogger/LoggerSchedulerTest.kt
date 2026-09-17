package ca.humiditylogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggerSchedulerTest {
    @Test
    fun errorHistorySurvivesLaterEntriesAndKeepsTheNewestTwenty() {
        var serialized: String? = null
        repeat(25) { index ->
            serialized = LoggerScheduler.appendErrorHistory(
                serialized,
                timestampMs = (index + 1).toLong(),
                message = "Failure $index",
            )
        }

        val errors = LoggerScheduler.decodeErrorHistory(serialized)

        assertEquals(20, errors.size)
        assertEquals(6L, errors.first().timestampMs)
        assertEquals(25L, errors.last().timestampMs)
        assertEquals("Failure 24", errors.last().message)
    }

    @Test
    fun malformedHistoryIsIgnored() {
        assertTrue(LoggerScheduler.decodeErrorHistory("not json").isEmpty())
    }

    @Test
    fun errorMessagesAreBoundedSingleLineAndRedactCredentials() {
        val sanitized = LoggerScheduler.sanitizeErrorMessage(
            "OAuth failed\nrefresh_token=secret-value client_secret: another-secret",
        )

        assertFalse(sanitized.contains("secret-value"))
        assertFalse(sanitized.contains("another-secret"))
        assertFalse(sanitized.contains('\n'))
        assertTrue(sanitized.contains("refresh_token=[redacted]"))
        assertTrue(sanitized.contains("client_secret=[redacted]"))
    }
}
