package ca.humiditylogger

import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs widget rendering (database reads plus bitmap drawing) off the main thread.
 *
 * Provider broadcasts and UI-triggered refreshes both land on the main thread; rendering there
 * froze the app. Renders are serialized so bursts of requests cannot draw concurrently.
 */
internal object WidgetUpdates {
    private const val TAG = "WidgetUpdates"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val renderLock = Mutex()

    /** Fire-and-forget re-render of every climate and graph widget, for UI callers. */
    fun requestUpdateAll(context: Context) {
        val appContext = context.applicationContext
        launch { ClimateWidgetProvider.updateAll(appContext) }
    }

    /** Runs [block] in the background, keeping the broadcast alive until it completes. */
    fun BroadcastReceiver.renderAsync(block: () -> Unit) {
        val pending = goAsync()
        launch(onDone = pending::finish, block = block)
    }

    /** Runs [block] on the serialized background scope, logging failures, then calls [onDone]. */
    fun launch(onDone: () -> Unit = {}, block: () -> Unit) {
        scope.launch {
            try {
                renderLock.withLock { block() }
            } catch (error: Exception) {
                Log.w(TAG, "Widget render failed", error)
            } finally {
                onDone()
            }
        }
    }
}
