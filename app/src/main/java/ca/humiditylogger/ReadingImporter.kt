package ca.humiditylogger

import android.content.Context

object ReadingImporter {
    fun insert(context: Context, store: ReadingStore, readings: List<Reading>) {
        store.insertAll(readings)
        DataRetention.apply(context, store)
    }
}
