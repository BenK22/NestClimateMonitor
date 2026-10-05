package ca.humiditylogger

/** Converts launcher bounds to positive graph content dimensions after layout padding/reserved rows. */
object WidgetGraphSizing {
    /** Logical content dimensions in dp, before bitmap density conversion. */
    data class ContentSize(val widthDp: Int, val heightDp: Int)

    /** Reserves the graph-only layout's padding on both axes. */
    fun graphOnlyContentSize(widthDp: Int, heightDp: Int): ContentSize = ContentSize(
        widthDp = (widthDp - 16).coerceAtLeast(1),
        heightDp = (heightDp - 16).coerceAtLeast(1),
    )

    /** Reserves climate tiles, status and padding before assigning graph height. */
    fun climateContentSize(widthDp: Int, heightDp: Int): ContentSize = ContentSize(
        widthDp = (widthDp - 20).coerceAtLeast(1),
        heightDp = ((heightDp - 38) / 2).coerceAtLeast(1),
    )
}
