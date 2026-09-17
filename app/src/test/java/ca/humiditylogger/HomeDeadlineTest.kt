package ca.humiditylogger

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeDeadlineTest {
    @Test
    fun deadlineReturnsCompletedResult() = runBlocking {
        assertEquals("done", withTimeoutOrNull(100) { "done" })
    }

    @Test
    fun deadlineBoundsAStalledOperation() = runBlocking {
        val result = withTimeoutOrNull(10) {
            delay(1_000)
            "late"
        }
        assertNull(result)
    }
}
