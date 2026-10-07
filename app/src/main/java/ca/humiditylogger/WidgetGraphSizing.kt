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

    /** Selects a reported size near one orientation's legacy bounds. */
    fun closestSize(available: List<ContentSize>, estimate: ContentSize): ContentSize =
        available.minByOrNull {
            abs(it.widthDp.toLong() - estimate.widthDp) + abs(it.heightDp.toLong() - estimate.heightDp)
        } ?: estimate

    /**
     * Responsive minimum size that selects the wide (or equal-width tall) bitmap variant.
     *
     * The midpoint distinguishes orientations without requiring an exact reported size to fit.
     * Host padding/rounding differences then cannot fall back to the wrong smallest-area bitmap.
     * The other variant uses a 1 x 1 baseline; identical sizes need only one RemoteViews layout.
     */
    fun orientationBreakpoint(portrait: ContentSize, landscape: ContentSize): ContentSize =
        if (landscape.widthDp > portrait.widthDp) {
            ContentSize(((portrait.widthDp.toLong() + landscape.widthDp) / 2 + 1).toInt(), 1)
        } else {
            ContentSize(1, ((portrait.heightDp.toLong() + landscape.heightDp) / 2 + 1).toInt())
        }

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
