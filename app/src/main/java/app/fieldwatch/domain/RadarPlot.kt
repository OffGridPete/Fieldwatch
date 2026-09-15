package app.fieldwatch.domain

/**
 * Polar radius on Classic radar. Stronger RSSI sits closer to YOU.
 * [zoom] > 1 stretches the plot so loud radios spread out and weak ones
 * leave the disc. Angle is not part of this mapping.
 */
object RadarPlot {
    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 4f

    fun clampZoom(zoom: Float): Float = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

    fun radius(rssi: Int, maxR: Float, zoom: Float = 1f): Float {
        val t = ((-30 - rssi).toFloat() / 70f).coerceIn(0f, 1f)
        return maxR * (0.12f + t * 0.88f) * clampZoom(zoom)
    }

    fun onDisc(rssi: Int, maxR: Float, zoom: Float): Boolean =
        radius(rssi, maxR, zoom) <= maxR + 0.5f
}
