package ca.humiditylogger

import android.content.Context

/** Persistence step for already previewed/validated CSV rows; does not schedule a sample. */
object ReadingImporter {
    /** Inserts atomically, then enforces retention; the caller owns the store and widget/UI refresh. */
    fun insert(context: Context, store: ReadingStore, readings: List<Reading>) {
        store.insertAll(readings)
        DataRetention.apply(context, store)
    }
}
