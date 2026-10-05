package ca.humiditylogger

/** Presentation state of the selected indoor reading; outdoor freshness cannot substitute for it. */
enum class WidgetFreshness {
    DISABLED,
    WAITING,
    FRESH,
    DELAYED,
    STALE,
}

/** Pure freshness thresholds shared by widget presentation and regression tests. */
object WidgetFreshnessPolicy {
    /** Disabled takes precedence; missing is waiting, under 30 minutes fresh, under 60 delayed. */
    fun evaluate(latestTimestampMs: Long?, nowMs: Long, loggingEnabled: Boolean): WidgetFreshness = when {
        !loggingEnabled -> WidgetFreshness.DISABLED
        latestTimestampMs == null -> WidgetFreshness.WAITING
        nowMs - latestTimestampMs < 30L * 60L * 1000L -> WidgetFreshness.FRESH
        nowMs - latestTimestampMs < 60L * 60L * 1000L -> WidgetFreshness.DELAYED
        else -> WidgetFreshness.STALE
    }
}
