package ca.humiditylogger

enum class WidgetFreshness {
    DISABLED,
    WAITING,
    FRESH,
    DELAYED,
    STALE,
}

object WidgetFreshnessPolicy {
    fun evaluate(latestTimestampMs: Long?, nowMs: Long, loggingEnabled: Boolean): WidgetFreshness = when {
        !loggingEnabled -> WidgetFreshness.DISABLED
        latestTimestampMs == null -> WidgetFreshness.WAITING
        nowMs - latestTimestampMs < 30L * 60L * 1000L -> WidgetFreshness.FRESH
        nowMs - latestTimestampMs < 60L * 60L * 1000L -> WidgetFreshness.DELAYED
        else -> WidgetFreshness.STALE
    }
}
