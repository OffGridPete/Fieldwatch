package app.fieldwatch.domain

/**
 * Offline map geometry. Radios and the operator path are projected to a
 * meter grid around a reference latitude (equirectangular — fine at field
 * scales), fitted into the canvas, then zoomed/panned by the user.
 * Pure math so unit tests pin the projection.
 */
object MapPlot {

    /** Meters per degree of latitude (mean earth). Longitude is scaled by cos(refLat). */
    const val DEG_M = 111_320.0

    /** A point in meters, relative to an arbitrary origin. */
    data class MPoint(val x: Double, val y: Double)

    /** Fit view: meter-space center + base pixels-per-meter for the canvas. */
    data class View(val cx: Double, val cy: Double, val scale: Float)

    fun toMeters(lat: Double, lon: Double, refLat: Double): MPoint =
        MPoint(
            x = lon * DEG_M * kotlin.math.cos(Math.toRadians(refLat)),
            y = lat * DEG_M,
        )

    /** Tight meter bounds over the points, padded by [pad] of the larger span. */
    fun fit(points: List<MPoint>, w: Float, h: Float, pad: Double = 0.12): View? {
        if (points.isEmpty() || w <= 0f || h <= 0f) return null
        var minX = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        points.forEach {
            minX = minOf(minX, it.x); maxX = maxOf(maxX, it.x)
            minY = minOf(minY, it.y); maxY = maxOf(maxY, it.y)
        }
        val spanX = (maxX - minX).coerceAtLeast(1.0)
        val spanY = (maxY - minY).coerceAtLeast(1.0)
        val padX = spanX * pad
        val padY = spanY * pad
        val bw = spanX + padX * 2
        val bh = spanY + padY * 2
        return View(
            cx = (minX + maxX) / 2,
            cy = (minY + maxY) / 2,
            scale = minOf(w / bw, h / bh).toFloat(),
        )
    }

    /** Project a meter point to canvas px. North is up; zoom multiplies, pan is px. */
    fun project(
        p: MPoint,
        view: View,
        w: Float,
        h: Float,
        zoom: Float = 1f,
        panX: Float = 0f,
        panY: Float = 0f,
    ): Pair<Float, Float> {
        val s = view.scale * zoom
        return (w / 2f + ((p.x - view.cx) * s).toFloat() + panX) to
            (h / 2f - ((p.y - view.cy) * s).toFloat() + panY)
    }

    fun metersPerPixel(view: View, zoom: Float): Double =
        if (view.scale * zoom <= 0f) Double.MAX_VALUE else 1.0 / (view.scale * zoom)

    private val BAR_STEPS = intArrayOf(
        1, 2, 5, 10, 20, 50, 100, 200, 500, 1_000, 2_000, 5_000, 10_000, 20_000, 50_000,
    )

    /** Largest round distance that still fits ~90 px, and its pixel width. */
    fun scaleBar(metersPerPixel: Double, targetPx: Float = 90f): Pair<Int, Float>? {
        if (metersPerPixel <= 0.0 || metersPerPixel.isInfinite()) return null
        val maxMeters = metersPerPixel * targetPx
        val meters = BAR_STEPS.lastOrNull { it <= maxMeters } ?: return null
        return meters to (meters / metersPerPixel).toFloat()
    }

    fun barLabel(meters: Int): String =
        if (meters >= 1000) "${meters / 1000} km" else "$meters m"
}
