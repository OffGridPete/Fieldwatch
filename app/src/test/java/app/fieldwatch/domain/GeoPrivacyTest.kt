package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoPrivacyTest {
    @Test
    fun screenCoordMasksWhenDemo() {
        assertNull(Geo.screenCoord(null, -122.41941, false))
        assertNull(Geo.screenCoord(37.77492, null, true))
        assertEquals("masked", Geo.screenCoord(37.77492, -122.41941, true))
        assertEquals("37.77492, -122.41941", Geo.screenCoord(37.77492, -122.41941, false))
    }

    @Test
    fun redactCoordsInReplacesSitReportPins() {
        val raw = "Stay  37.77492, -122.41941  (operator phone)\nTransit 37.7750,-122.4200"
        val masked = Geo.redactCoordsIn(raw, true)
        assertTrue(masked.contains("masked"))
        assertFalse(masked.contains("37.77492"))
        assertFalse(masked.contains("-122.41941"))
        assertEquals(raw, Geo.redactCoordsIn(raw, false))
    }

    @Test
    fun redactCoordsInLeavesPathMetersAlone() {
        val path = "GPS path so far 123 m. Keep moving (~50 m). Span 45 m."
        assertEquals(path, Geo.redactCoordsIn(path, true))
    }

    @Test
    fun movingWithYouIgnoresWifiAccessPoints() {
        val now = 1_000_000L
        val path = listOf(
            GpsSample(now - 60_000, 37.7700, -122.4200),
            GpsSample(now - 30_000, 37.7710, -122.4200),
            GpsSample(now, 37.7720, -122.4200),
        )
        val ctx = CoTravel.Ctx.of(path)
        assertTrue(ctx.ready)
        val trail = listOf(
            GpsSample(now - 50_000, 37.7702, -122.4200, rssi = -60),
            GpsSample(now - 20_000, 37.7712, -122.4200, rssi = -58),
            GpsSample(now - 1_000, 37.7719, -122.4200, rssi = -55),
        )
        val wifi = radio(RadioKind.WIFI, trail, now)
        val ble = radio(RadioKind.BLE, trail, now)
        assertFalse(CoTravel.withYou(wifi, ctx, now))
        assertTrue(CoTravel.withYou(ble, ctx, now))
    }

    private fun radio(kind: RadioKind, trail: List<GpsSample>, now: Long): Sighting {
        val last = trail.last()
        return Sighting(
            key = "${kind.name}:AA:BB:CC:DD:EE:01",
            kind = kind,
            mac = "AA:BB:CC:DD:EE:01",
            name = if (kind == RadioKind.WIFI) "House" else "Tag",
            rssi = last.rssi,
            rssiMin = -70,
            rssiMax = -50,
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
            firstSeen = trail.first().at,
            lastSeen = last.at,
            hitCount = trail.size,
            fleetIds = emptySet(),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = trail,
        ).also { require(now - it.lastSeen < CoTravel.HEARD_MS) }
    }
}
