package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class RadarPlotTest {
    private val maxR = 1000f

    @Test
    fun loudSitsNearCenter() {
        val r30 = RadarPlot.radius(-30, maxR)
        val r100 = RadarPlot.radius(-100, maxR)
        assertEquals(120f, r30, 0.5f)
        assertEquals(1000f, r100, 0.5f)
        assertTrue(r30 < RadarPlot.radius(-40, maxR))
        assertTrue(RadarPlot.radius(-60, maxR) < r100)
    }

    @Test
    fun zoomSpreadsInnerRadiosAndPushesWeakOffDisc() {
        val inner = RadarPlot.radius(-40, maxR, zoom = 1f)
        val innerZ = RadarPlot.radius(-40, maxR, zoom = 2f)
        assertEquals(inner * 2f, innerZ, 0.5f)
        assertTrue(RadarPlot.onDisc(-100, maxR, zoom = 1f))
        assertFalse(RadarPlot.onDisc(-100, maxR, zoom = 2f))
        assertTrue(RadarPlot.onDisc(-40, maxR, zoom = 2f))
    }

    @Test
    fun zoomIsClamped() {
        assertEquals(RadarPlot.radius(-50, maxR, 1f), RadarPlot.radius(-50, maxR, 0.2f), 0.01f)
        assertEquals(RadarPlot.radius(-50, maxR, 4f), RadarPlot.radius(-50, maxR, 9f), 0.01f)
    }

    @Test
    fun geographicBearingCalculatesCardinalDirections() {
        // Due North
        val north = RadarPlot.initialBearing(0.0, 0.0, 1.0, 0.0)
        assertEquals(0.0, north, 0.1)
        assertEquals(-PI / 2.0, RadarPlot.bearingToScreenAngle(north), 0.01)

        // Due East
        val east = RadarPlot.initialBearing(0.0, 0.0, 0.0, 1.0)
        assertEquals(90.0, east, 0.1)
        assertEquals(0.0, RadarPlot.bearingToScreenAngle(east), 0.01)

        // Due South
        val south = RadarPlot.initialBearing(1.0, 0.0, 0.0, 0.0)
        assertEquals(180.0, south, 0.1)
        assertEquals(PI / 2.0, RadarPlot.bearingToScreenAngle(south), 0.01)

        // Due West
        val west = RadarPlot.initialBearing(0.0, 1.0, 0.0, 0.0)
        assertEquals(270.0, west, 0.1)
    }

    @Test
    fun trueBearingAppliedWhenPayloadCoordinatesExist() {
        val droneSighting = Sighting(
            key = "BLE:11:22:33:44:55:66",
            kind = RadioKind.BLE,
            mac = "11:22:33:44:55:66",
            name = "DJI Mavic",
            rssi = -60,
            rssiMin = -60,
            rssiMax = -60,
            channel = 0,
            frequencyMhz = 0,
            vendor = null,
            randomized = false,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = 1L,
            lastSeen = 1L,
            hitCount = 1,
            fleetIds = emptySet(),
            rssiHistory = emptyList(),
            presence = emptyList(),
            latitude = 18.4861,
            longitude = -69.9312,
            payloadLat = 18.4961, // North of phone
            payloadLon = -69.9312,
        )

        assertTrue(RadarPlot.hasTrueBearing(droneSighting))
        val angle = RadarPlot.contactAngle(droneSighting)
        // North corresponds to -PI/2 on screen
        assertEquals(-PI / 2.0, angle, 0.05)
    }

    @Test
    fun fallbackToMacHashWhenNoCoordinates() {
        val radio = Sighting(
            key = "BLE:AA:BB:CC:DD:EE:FF",
            kind = RadioKind.BLE,
            mac = "AA:BB:CC:DD:EE:FF",
            name = "Beacon",
            rssi = -70,
            rssiMin = -70,
            rssiMax = -70,
            channel = 0,
            frequencyMhz = 0,
            vendor = null,
            randomized = false,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = 1L,
            lastSeen = 1L,
            hitCount = 1,
            fleetIds = emptySet(),
            rssiHistory = emptyList(),
            presence = emptyList(),
        )

        assertFalse(RadarPlot.hasTrueBearing(radio))
        assertEquals(RadarPlot.macAngle("AA:BB:CC:DD:EE:FF"), RadarPlot.contactAngle(radio), 0.001)
    }
}
