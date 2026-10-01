package app.fieldwatch.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Polar radius and angular placement on Classic radar.
 * Stronger RSSI sits closer to YOU.
 * [zoom] > 1 stretches the plot so loud radios spread out and weak ones leave the disc.
 *
 * For devices broadcasting georeferenced coordinates (e.g. ASTM F3411 Remote ID drones),
 * contactAngle computes the true forward geographic azimuth / bearing relative to North.
 * Radios without coordinates fall back to a stable deterministic hash angle from their MAC.
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

    /**
     * Deterministic fallback angle based on MAC address in radians.
     */
    fun macAngle(mac: String): Double {
        val hash = mac.hashCode()
        return ((hash ushr 1) % 360) * Math.PI / 180.0
    }

    /**
     * Calculates the true forward azimuth / geographic initial bearing in degrees [0, 360)
     * from (fromLat, fromLon) to (toLat, toLon) using spherical navigation.
     */
    fun initialBearing(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val phi1 = Math.toRadians(fromLat)
        val phi2 = Math.toRadians(toLat)
        val deltaLambda = Math.toRadians(toLon - fromLon)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val theta = atan2(y, x)
        return (Math.toDegrees(theta) + 360.0) % 360.0
    }

    /**
     * Converts a geographic bearing (0 deg = North) to polar screen angle in radians
     * for a canvas where +X is East (right) and +Y is South (down):
     * North (0 deg)   -> -PI/2 (top)
     * East (90 deg)   -> 0 (right)
     * South (180 deg) -> +PI/2 (bottom)
     * West (270 deg)  -> PI (left)
     */
    fun bearingToScreenAngle(bearingDeg: Double): Double {
        var deg = (bearingDeg - 90.0) % 360.0
        if (deg > 180.0) deg -= 360.0
        if (deg < -180.0) deg += 360.0
        return Math.toRadians(deg)
    }

    /**
     * Determines whether the sighting has a valid true geographic bearing calculation.
     */
    fun hasTrueBearing(
        device: Sighting,
        refLat: Double? = device.latitude,
        refLon: Double? = device.longitude,
    ): Boolean {
        val targetLat = device.payloadLat
        val targetLon = device.payloadLon
        return targetLat != null && targetLon != null && refLat != null && refLon != null &&
            PayloadLocation.validCoord(targetLat, targetLon) && PayloadLocation.validCoord(refLat, refLon)
    }

    /**
     * Returns the angular placement in radians for drawing on the radar canvas.
     */
    fun contactAngle(
        device: Sighting,
        refLat: Double? = device.latitude,
        refLon: Double? = device.longitude,
    ): Double {
        if (hasTrueBearing(device, refLat, refLon)) {
            val bearing = initialBearing(refLat!!, refLon!!, device.payloadLat!!, device.payloadLon!!)
            return bearingToScreenAngle(bearing)
        }
        return macAngle(device.mac)
    }

    /**
     * Returns the contact's polar position in degrees [0, 360) for sweep fade / phosphor timing.
     */
    fun contactDegrees(
        device: Sighting,
        refLat: Double? = device.latitude,
        refLon: Double? = device.longitude,
    ): Float {
        if (hasTrueBearing(device, refLat, refLon)) {
            val bearing = initialBearing(refLat!!, refLon!!, device.payloadLat!!, device.payloadLon!!)
            return bearing.toFloat()
        }
        return ((device.mac.hashCode() ushr 1) % 360).toFloat()
    }
}
