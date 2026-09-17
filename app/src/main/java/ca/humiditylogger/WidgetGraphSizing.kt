package ca.humiditylogger

object WidgetGraphSizing {
    data class ContentSize(val widthDp: Int, val heightDp: Int)

    fun graphOnlyContentSize(widthDp: Int, heightDp: Int): ContentSize = ContentSize(
        widthDp = (widthDp - 16).coerceAtLeast(1),
        heightDp = (heightDp - 16).coerceAtLeast(1),
    )

    fun climateContentSize(widthDp: Int, heightDp: Int): ContentSize = ContentSize(
        widthDp = (widthDp - 20).coerceAtLeast(1),
        heightDp = ((heightDp - 38) / 2).coerceAtLeast(1),
    )
}
