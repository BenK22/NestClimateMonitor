package ca.humiditylogger

import kotlin.math.min
import kotlin.math.abs
import kotlin.math.roundToInt

/** Converts launcher bounds to positive graph content dimensions after layout padding/reserved rows. */
object WidgetGraphSizing {
    /** Logical content dimensions in dp, before bitmap density conversion. */
    data class ContentSize(val widthDp: Int, val heightDp: Int)

    /** Bitmap dimensions in pixels; a uniform cap preserves the content's aspect ratio. */
    data class PixelSize(val width: Int, val height: Int)

    /** Bounds bitmap memory without independently squashing one axis of a large widget. */
    fun pixelSize(widthDp: Int, heightDp: Int, density: Float): PixelSize {
        val width = (widthDp.coerceAtLeast(1) * density).coerceAtLeast(1f)
        val height = (heightDp.coerceAtLeast(1) * density).coerceAtLeast(1f)
        val scale = min(1f, min(1200f / width, 750f / height))
        return PixelSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
    }

    /** Legacy launcher bounds describe two orientations, not a min-width/min-height rectangle. */
    fun orientationSize(minWidth: Int, minHeight: Int, maxWidth: Int, maxHeight: Int, landscape: Boolean): ContentSize =
        if (landscape) ContentSize(maxWidth.coerceAtLeast(minWidth), minHeight)
        else ContentSize(minWidth, maxHeight.coerceAtLeast(minHeight))

    /** Selects one reported size near current-orientation bounds instead of a host-selected map. */
    fun closestSize(available: List<ContentSize>, estimate: ContentSize): ContentSize =
        available.minByOrNull {
            abs(it.widthDp.toLong() - estimate.widthDp) + abs(it.heightDp.toLong() - estimate.heightDp)
        } ?: estimate

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
