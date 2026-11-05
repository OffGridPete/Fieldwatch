package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class GeoTrackTest {
    @Test
    fun aPointBesideANorthLineIsThatFarOffIt() {
        val end = northOf(28.7800, -81.3700, 40.0)
        val side = offset(end.first, end.second, 0.0, 20.0)
        val cross = Geo.crossTrackMeters(28.7800, -81.3700, end.first, end.second, side.first, side.second)
        assertEquals(20.0, abs(cross), 1.5)
    }

    @Test
    fun sideWobbleOnAStraightDriveDoesNotZigzag() {
        val path = mutableListOf<GpsSample>()
        val apex = Geo.DriveApex()
        var lat = 28.7800
        var lon = -81.3700
        Geo.extendDrivePath(path, apex, lat, lon, 1_000L)
        for (i in 1..8) {
            val east = if (i % 2 == 0) 6.0 else -6.0
            val next = offset(lat, lon, 15.0, east)
            lat = next.first
            lon = next.second
            Geo.extendDrivePath(path, apex, lat, lon, 1_000L + i * 1_000L)
        }
        assertTrue(path.size > 2)
        assertEquals(lat, path.last().lat, 1e-8)
    }

    @Test
    fun aTurnKeepsTheCorner() {
        val path = mutableListOf<GpsSample>()
        val apex = Geo.DriveApex()
        var lat = 28.7800
        val lon = -81.3700
        Geo.extendDrivePath(path, apex, lat, lon, 1_000L)
        for (i in 1..4) {
            lat = northOf(lat, lon, 20.0).first
            Geo.extendDrivePath(path, apex, lat, lon, 1_000L + i * 1_000L)
        }
        assertTrue(path.any { Geo.meters(it.lat, it.lon, lat, lon) < 1.0 })
        var eastLon = lon
        for (i in 1..4) {
            val next = offset(lat, eastLon, 0.0, 20.0)
            eastLon = next.second
            Geo.extendDrivePath(path, apex, lat, eastLon, 10_000L + i * 1_000L)
        }
        assertTrue(path.size >= 3)
        assertEquals(lat, path.last().lat, 1e-8)
        assertEquals(eastLon, path.last().lon, 1e-8)
    }

    @Test
    fun theCapDropsTheOldestCorner() {
        val path = mutableListOf<GpsSample>()
        val apex = Geo.DriveApex()
        var lat = 28.7800
        var lon = -81.3700
        var at = 1_000L
        var trimmed = false
        Geo.extendDrivePath(path, apex, lat, lon, at, cap = 4)
        repeat(6) {
            val north = northOf(lat, lon, 40.0)
            lat = north.first
            at += 1_000L
            trimmed = Geo.extendDrivePath(path, apex, lat, lon, at, cap = 4) || trimmed
            val east = offset(lat, lon, 0.0, 40.0)
            lon = east.second
            at += 1_000L
            trimmed = Geo.extendDrivePath(path, apex, lat, lon, at, cap = 4) || trimmed
        }
        assertTrue(trimmed)
        assertTrue(path.size <= 4)
        assertTrue(path.size >= 2)
    }

    @Test
    fun slowSideWobbleOnAStraightWalkDoesNotZigzag() {
        val path = mutableListOf<GpsSample>()
        val apex = Geo.DriveApex()
        var lat = 28.7800
        var lon = -81.3700
        var at = 1_000L
        Geo.extendDrivePath(path, apex, lat, lon, at)
        for (i in 1..16) {
            val east = if (i % 2 == 0) 6.0 else -6.0
            val next = offset(lat, lon, 5.0, east)
            lat = next.first
            lon = next.second
            at += 2_000L
            Geo.extendDrivePath(path, apex, lat, lon, at)
        }
        assertTrue(path.size > 2)
        assertTrue(Geo.realStops(path).isEmpty())
    }

    @Test
    fun aSlowTurnKeepsTheBend() {
        val path = mutableListOf<GpsSample>()
        val apex = Geo.DriveApex()
        val lat0 = 28.7800
        val lon0 = -81.3700
        var at = 1_000L
        fun push(northM: Double, eastM: Double) {
            val next = offset(lat0, lon0, northM, eastM)
            Geo.extendDrivePath(path, apex, next.first, next.second, at)
            at += 1_000L
        }
        var north = 0.0
        push(0.0, 0.0)
        repeat(8) {
            north += 5.0
            push(north, 0.0)
        }
        val radius = 10.0
        val steps = 8
        for (s in 1..steps) {
            val ang = Math.PI / 2.0 * s / steps
            push(north + radius * sin(ang), radius - radius * cos(ang))
        }
        var east = radius
        val endNorth = north + radius
        repeat(6) {
            east += 5.0
            push(endNorth, east)
        }
        val mid = offset(lat0, lon0, north + radius * sin(Math.PI / 4.0), radius - radius * cos(Math.PI / 4.0))
        val gap = (1 until path.size).minOf { i ->
            gapToSegment(path[i - 1], path[i], mid.first, mid.second)
        }
        assertTrue("bend was cut by $gap m", gap < 4.0)
        assertTrue(path.size >= 4)
    }

    @Test
    fun aRealStopIsMarkedAndTheDriveAfterItIsNot() {
        val path = mutableListOf<GpsSample>()
        val apex = Geo.DriveApex()
        val lat0 = 28.7800
        val lon0 = -81.3700
        var at = 1_000L
        fun push(northM: Double, eastM: Double, dt: Long = 1_000L) {
            val next = offset(lat0, lon0, northM, eastM)
            at += dt
            Geo.extendDrivePath(path, apex, next.first, next.second, at)
        }
        push(0.0, 0.0, 0L)
        var north = 0.0
        repeat(8) {
            north += 10.0
            push(north, 0.0)
        }
        repeat(10) { i ->
            val jitterN = if (i % 2 == 0) 3.0 else 0.0
            val jitterE = if (i % 2 == 0) 2.0 else -2.0
            push(north + jitterN, jitterE, 5_000L)
        }
        var east = 0.0
        repeat(8) {
            east += 12.0
            push(north, east)
        }
        val stops = Geo.realStops(path)
        assertEquals(1, stops.size)
        assertTrue(stops.single().durationMs >= Geo.STOP_MS)
        assertTrue(path.last().at > stops.single().endAt)
        assertTrue(Geo.meters(stops.single().lat, stops.single().lon, path.last().lat, path.last().lon) > 40.0)
    }

    @Test
    fun jitterOnBothSidesOfAStopStaysOneGreenStretch() {
        val lat0 = 28.7800
        val lon0 = -81.3700
        val path = ArrayList<GpsSample>()
        var at = 1_000L
        path += GpsSample(at, lat0, lon0)
        repeat(8) { i ->
            at += 8_000L
            val eastM = if (i % 2 == 0) 30.0 else -30.0
            val point = offset(lat0, lon0, 0.0, eastM)
            path += GpsSample(at, point.first, point.second)
        }
        val stops = Geo.realStops(path)
        assertEquals(1, stops.size)
        assertTrue(stops.single().durationMs >= Geo.STOP_MS)
        for (i in 1 until path.size) {
            assertTrue(
                "segment $i left the stop",
                Geo.stopCovers(stops, path[i - 1].at, path[i].at),
            )
        }
        at += 8_000L
        val left = offset(lat0, lon0, 50.0, 0.0)
        path += GpsSample(at, left.first, left.second)
        val after = Geo.realStops(path)
        assertEquals(1, after.size)
        assertFalse(Geo.stopCovers(after, path[path.lastIndex - 1].at, path.last().at))
        for (i in 1 until path.lastIndex) {
            assertTrue(Geo.stopCovers(after, path[i - 1].at, path[i].at))
        }
    }

    @Test
    fun aTurnLeavesTheStraightWhereItWasDriven() {
        val path = mutableListOf<GpsSample>()
        val apex = Geo.DriveApex()
        val lat0 = 28.7800
        val lon0 = -81.3700
        var at = 1_000L
        fun push(northM: Double, eastM: Double) {
            val next = offset(lat0, lon0, northM, eastM)
            Geo.extendDrivePath(path, apex, next.first, next.second, at)
            at += 1_000L
        }
        push(0.0, 0.0)
        var north = 0.0
        repeat(30) {
            north += 10.0
            push(north, 0.0)
        }
        val straightEnd = offset(lat0, lon0, north, 0.0)
        val radius = 15.0
        val steps = 12
        for (s in 1..steps) {
            val ang = Math.PI / 2.0 * s / steps
            push(north + radius * sin(ang), radius * (1.0 - cos(ang)))
        }
        assertTrue(path.size >= 3)
        val kept = path.dropLast(1).map { it.lat to it.lon }
        val corner = path.dropLast(1).minBy { sample ->
            Geo.meters(sample.lat, sample.lon, straightEnd.first, straightEnd.second)
        }
        assertTrue(
            "straight end moved ${Geo.meters(corner.lat, corner.lon, straightEnd.first, straightEnd.second)} m",
            Geo.meters(corner.lat, corner.lon, straightEnd.first, straightEnd.second) < 6.0,
        )
        var east = radius
        val endNorth = north + radius
        repeat(8) {
            east += 15.0
            push(endNorth, east)
        }
        var south = endNorth
        repeat(16) {
            south -= 15.0
            push(south, east)
        }
        for ((lat, lon) in kept) {
            assertTrue(path.any { it.lat == lat && it.lon == lon })
        }
        assertEquals(lat0, path.first().lat, 1e-8)
        assertEquals(lon0, path.first().lon, 1e-8)
    }

    @Test
    fun aSlowCornerFollowsTheStreet() {
        val path = mutableListOf<GpsSample>()
        val apex = Geo.DriveApex()
        val lat0 = 28.7800
        val lon0 = -81.3700
        var at = 1_000L
        fun push(northM: Double, eastM: Double) {
            val next = offset(lat0, lon0, northM, eastM)
            Geo.extendDrivePath(path, apex, next.first, next.second, at)
            at += 1_000L
        }
        push(0.0, 0.0)
        var north = 0.0
        repeat(10) {
            north += 8.0
            push(north, 0.0)
        }
        val radius = 12.0
        val arcSteps = 16
        val arc = ArrayList<Pair<Double, Double>>()
        for (s in 1..arcSteps) {
            val ang = Math.PI / 2.0 * s / arcSteps
            val point = north + radius * sin(ang) to radius * (1.0 - cos(ang))
            arc += point
            push(point.first, point.second)
        }
        var east = radius
        val endNorth = north + radius
        repeat(6) {
            east += 8.0
            push(endNorth, east)
        }
        val worst = arc.maxOf { (n, e) ->
            val atArc = offset(lat0, lon0, n, e)
            (1 until path.size).minOf { i ->
                gapToSegment(path[i - 1], path[i], atArc.first, atArc.second)
            }
        }
        assertTrue("slow corner was $worst m off the line", worst < 2.5)
        assertTrue("corner had ${path.size} points", path.size >= 8)
    }

    @Test
    fun aStraightDriveIsNotAStop() {
        val path = mutableListOf<GpsSample>()
        val apex = Geo.DriveApex()
        var lat = 28.7800
        val lon = -81.3700
        var at = 1_000L
        Geo.extendDrivePath(path, apex, lat, lon, at)
        repeat(20) {
            lat = northOf(lat, lon, 50.0).first
            at += 2_000L
            Geo.extendDrivePath(path, apex, lat, lon, at)
        }
        assertTrue(path.size > 2)
        assertTrue(Geo.realStops(path).isEmpty())
    }

    @Test
    fun onlyATightGpsFixJoinsTheDrive() {
        assertTrue(Geo.acceptDriveFix(gps = true, accuracyM = 8f))
        assertTrue(Geo.acceptDriveFix(gps = true, accuracyM = null))
        assertFalse(Geo.acceptDriveFix(gps = true, accuracyM = 40f))
        assertFalse(Geo.acceptDriveFix(gps = false, accuracyM = 8f))
    }

    private fun gapToSegment(a: GpsSample, b: GpsSample, lat: Double, lon: Double): Double {
        val cosLat = cos(Math.toRadians(a.lat)).coerceAtLeast(0.2)
        fun east(sampleLon: Double) = (sampleLon - a.lon) * 111_320.0 * cosLat
        fun north(sampleLat: Double) = (sampleLat - a.lat) * 111_320.0
        val bx = east(b.lon)
        val by = north(b.lat)
        val px = east(lon)
        val py = north(lat)
        val len2 = bx * bx + by * by
        val t = if (len2 < 1.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        return hypot(px - t * bx, py - t * by)
    }

    @Test
    fun aShortMoveAndATurnInPlaceDoNotSetCourse() {
        val course = Geo.TravelCourse()
        course.note(36.0, -115.0, 0f, 10f, 5f)
        assertTrue(course.degrees.isNaN())
        val step = offset(36.0, -115.0, 0.0, 2.0)
        course.note(step.first, step.second, 0.2f, 180f, 5f)
        assertTrue(course.degrees.isNaN())
    }

    @Test
    fun aFourMeterGpsMoveSetsTheCourse() {
        val course = Geo.TravelCourse()
        course.note(36.0, -115.0, null, null, null)
        val step = offset(36.0, -115.0, 0.0, 5.0)
        course.note(step.first, step.second, null, null, null)
        assertEquals(90.0, course.degrees, 2.0)
    }

    @Test
    fun stoppingHoldsTheLastCourse() {
        val course = Geo.TravelCourse()
        course.note(36.0, -115.0, 1.4f, 45f, null)
        assertEquals(45.0, course.degrees, 0.01)
        course.note(36.0, -115.0, 0f, 200f, 5f)
        assertEquals(45.0, course.degrees, 0.01)
        val jitter = offset(36.0, -115.0, 1.0, 0.0)
        course.note(jitter.first, jitter.second, 0.2f, 180f, 5f)
        assertEquals(45.0, course.degrees, 0.01)
    }

    @Test
    fun aWideBearingIsIgnoredUntilTheStep() {
        val course = Geo.TravelCourse()
        course.note(36.0, -115.0, 2f, 10f, 80f)
        assertTrue(course.degrees.isNaN())
        val step = offset(36.0, -115.0, 5.0, 0.0)
        course.note(step.first, step.second, 0.3f, 10f, 80f)
        assertEquals(0.0, course.degrees, 2.0)
    }

    @Test
    fun runningLengthMatchesTheMeasuredPath() {
        val path = mutableListOf<GpsSample>()
        val apex = Geo.DriveApex()
        var length = 0.0
        var lat = 28.78
        var lon = -81.37
        var at = 1_000L
        fun step(north: Double, east: Double, dt: Long) {
            val next = offset(lat, lon, north, east)
            lat = next.first
            lon = next.second
            at += dt
            val before = path.size
            val frozen = if (path.size >= 2) path[path.lastIndex - 1] else null
            val tip = path.lastOrNull()
            val trimmed = Geo.extendDrivePath(path, apex, lat, lon, at)
            length = if (trimmed) {
                Geo.pathLengthM(path)
            } else {
                Geo.advanceDriveLength(length, before, frozen, tip, path)
            }
            assertEquals(Geo.pathLengthM(path), length, 0.02)
        }
        repeat(6) { step(1.2, 0.0, 1_000L) }
        repeat(8) { step(6.0, 1.0, 1_000L) }
        repeat(12) { step(0.4, -0.2, 4_000L) }
        assertTrue(path.zipWithNext().any { it.second.at - it.first.at >= Geo.STOP_MS })
        repeat(4) { step(8.0, 0.0, 1_000L) }
        val capped = ArrayList<GpsSample>()
        val capApex = Geo.DriveApex()
        var capLen = 0.0
        var clat = 28.78
        var clon = -81.37
        var cat = 1_000L
        var sawTrim = false
        repeat(12) {
            val next = offset(clat, clon, 10.0, 3.0)
            clat = next.first
            clon = next.second
            cat += 1_000L
            val before = capped.size
            val frozen = if (capped.size >= 2) capped[capped.lastIndex - 1] else null
            val tip = capped.lastOrNull()
            val trimmed = Geo.extendDrivePath(capped, capApex, clat, clon, cat, cap = 6)
            if (trimmed) sawTrim = true
            capLen = if (trimmed) {
                Geo.pathLengthM(capped)
            } else {
                Geo.advanceDriveLength(capLen, before, frozen, tip, capped)
            }
            assertEquals(Geo.pathLengthM(capped), capLen, 0.02)
        }
        assertTrue(sawTrim)
        assertTrue(capped.size <= 6)
    }

    private fun northOf(lat: Double, lon: Double, meters: Double): Pair<Double, Double> =
        offset(lat, lon, meters, 0.0)

    private fun offset(lat: Double, lon: Double, northM: Double, eastM: Double): Pair<Double, Double> {
        val dLat = northM / 111_320.0
        val dLon = eastM / (111_320.0 * kotlin.math.cos(Math.toRadians(lat)).coerceAtLeast(0.2))
        return (lat + dLat) to (lon + dLon)
    }
}
