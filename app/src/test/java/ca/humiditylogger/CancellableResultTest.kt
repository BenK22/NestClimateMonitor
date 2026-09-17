package ca.humiditylogger

import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CancellableResultTest {
    @Test(expected = CancellationException::class)
    fun cancellationIsRethrown() {
        runCatchingCancellable<Unit> { throw CancellationException("stopped") }
    }

    @Test
    fun ordinaryFailuresRemainResults() {
        val result = runCatchingCancellable<Unit> { throw IOException("offline") }
        assertTrue(result.isFailure)
        assertEquals("offline", result.exceptionOrNull()?.message)
    }
}
