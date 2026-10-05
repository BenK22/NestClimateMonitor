package ca.humiditylogger

import kotlinx.coroutines.CancellationException

/**
 * Captures failures as [Result] while preserving coroutine cancellation.
 *
 * Use instead of plain `runCatching` around cancellable work: turning cancellation into an
 * ordinary failure can continue database/UI work after its lifecycle or worker has ended.
 */
internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Throwable) {
    Result.failure(error)
}
