package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SitPathPlotTest {
    @Test
    fun northIsUpAndEastIsRight() {
        val south = GpsSample(1L, 28.7800, -81.3700)
        val north = GpsSample(2L, 28.7810, -81.3700)
        val east = GpsSample(3L, 28.7810, -81.3690)
        val model = SitPathPlot.Model(
            samples = listOf(south, north, east),
            dots = emptyList(),
            lengthM = 200.0,
            spanM = 150.0,
            title = "test",
        )
        val layout = SitPathPlot.layout(model, 400f, 300f)!!
        assertEquals(3, layout.path.size)
        assertTrue(layout.path[1].y < layout.path[0].y)
        assertTrue(layout.path[2].x > layout.path[1].x)
    }

    @Test
    fun tallPathCentersHorizontally() {
        val south = GpsSample(1L, 28.7800, -81.3700)
        val north = GpsSample(2L, 28.7850, -81.3700)
        val model = SitPathPlot.Model(
            samples = listOf(south, north),
            dots = emptyList(),
            lengthM = 550.0,
            spanM = 550.0,
            title = "tall",
        )
        val layout = SitPathPlot.layout(model, 400f, 300f)!!
        val mid = 200f
        layout.path.forEach { pt ->
            assertTrue(kotlin.math.abs(pt.x - mid) < 20f)
        }
    }

    @Test
    fun scaleBarIsANiceMeterValue() {
        assertEquals(50.0, SitPathPlot.niceMeters(47.0), 0.01)
        assertEquals(100.0, SitPathPlot.niceMeters(80.0), 0.01)
        assertEquals(20.0, SitPathPlot.niceMeters(18.0), 0.01)
    }

    @Test
    fun nearbyDotsBecomeOneStackedCluster() {
        val a = SitPathPlot.Dot("a", 0.0, 0.0, "A", extraAttention = true, named = false)
        val b = SitPathPlot.Dot("b", 0.0, 0.0, "B", extraAttention = true, named = false)
        val c = SitPathPlot.Dot("c", 0.0, 0.0, "C", extraAttention = false, named = true)
        val stacked = SitPathPlot.clusters(
            listOf(
                a to SitPathPlot.Pt(10f, 10f),
                b to SitPathPlot.Pt(18f, 12f),
                c to SitPathPlot.Pt(200f, 180f),
            ),
            threshPx = 28f,
        )
        assertEquals(2, stacked.size)
        val pile = stacked.first { it.stacked }
        assertEquals(2, pile.members.size)
        assertTrue(stacked.any { !it.stacked && it.members.single().dot.key == "c" })
    }

    @Test
    fun extraAttentionDotsComeFirstAndNeedAFix() {
        val axon = Fleet(
            id = "fleet-axon",
            name = "Axon",
            kind = SignatureClass.LAW_ENFORCEMENT,
            attentionNote = "Body-worn.",
        )
        val tagged = Sighting(
            key = "WIFI:AA:AA:AA:AA:AA:01",
            kind = RadioKind.WIFI,
            mac = "AA:AA:AA:AA:AA:01",
            name = "cam",
            rssi = -50,
            rssiMin = -50,
            rssiMax = -50,
            channel = 6,
            frequencyMhz = 2437,
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
            fleetIds = setOf("fleet-axon"),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(GpsSample(1L, 28.78, -81.37)),
        )
        val dots = SitPathPlot.dotsFrom(listOf(tagged), listOf(axon), namedKeys = emptySet())
        assertEquals(1, dots.points.size)
        assertTrue(dots.points[0].extraAttention)
        assertNotNull(SitPathPlot.layout(
            SitPathPlot.Model(
                samples = listOf(
                    GpsSample(1L, 28.780, -81.370),
                    GpsSample(2L, 28.781, -81.371),
                ),
                dots = dots.points,
                lengthM = 120.0,
                spanM = 100.0,
                title = "plaza",
            ),
            300f, 200f,
        ))
    }

    @Test
    fun sameRadioPlotsOnceAtLoudestHear() {
        val axon = Fleet(
            id = "fleet-axon",
            name = "Axon",
            kind = SignatureClass.LAW_ENFORCEMENT,
            attentionNote = "Body-worn.",
        )
        val tagged = Sighting(
            key = "WIFI:AA:AA:AA:AA:AA:01",
            kind = RadioKind.WIFI,
            mac = "AA:AA:AA:AA:AA:01",
            name = "cam",
            rssi = -40,
            rssiMin = -80,
            rssiMax = -40,
            channel = 6,
            frequencyMhz = 2437,
            vendor = null,
            randomized = false,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = 1L,
            lastSeen = 3L,
            hitCount = 3,
            fleetIds = setOf("fleet-axon"),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(
                GpsSample(1L, 28.7800, -81.3700, -80),
                GpsSample(2L, 28.7810, -81.3710, -42),
                GpsSample(3L, 28.7820, -81.3720, -70),
            ),
        )
        val dots = SitPathPlot.dotsFrom(listOf(tagged, tagged), listOf(axon), namedKeys = emptySet())
        assertEquals(1, dots.points.size)
        assertEquals(28.7810, dots.points[0].lat, 0.00001)
        assertEquals(-81.3710, dots.points[0].lon, 0.00001)
    }

    @Test
    fun extraAttentionAlongTheWalkIsStillANumberedStop() {
        val axon = Fleet(
            id = "fleet-axon",
            name = "Axon",
            kind = SignatureClass.LAW_ENFORCEMENT,
            attentionNote = "Body-worn.",
        )
        val path = listOf(
            GpsSample(0L, 28.7800, -81.3700),
            GpsSample(60_000L, 28.7850, -81.3700),
        )
        val tagged = Sighting(
            key = "WIFI:AA:AA:AA:AA:AA:01",
            kind = RadioKind.WIFI,
            mac = "AA:AA:AA:AA:AA:01",
            name = "van",
            rssi = -50,
            rssiMin = -60,
            rssiMax = -40,
            channel = 6,
            frequencyMhz = 2437,
            vendor = null,
            randomized = false,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = 0L,
            lastSeen = 60_000L,
            hitCount = 8,
            fleetIds = setOf("fleet-axon"),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(
                GpsSample(0L, 28.7800, -81.3700, -55),
                GpsSample(15_000L, 28.7812, -81.3700, -48),
                GpsSample(30_000L, 28.7825, -81.3700, -40),
                GpsSample(45_000L, 28.7837, -81.3700, -46),
                GpsSample(60_000L, 28.7850, -81.3700, -52),
            ),
        )
        val dots = SitPathPlot.dotsFrom(listOf(tagged), listOf(axon), namedKeys = emptySet())
        assertEquals(1, dots.points.size)
        assertEquals("van", dots.points[0].label)
        assertTrue(dots.points[0].extraAttention)
    }

    @Test
    fun bookmarkedRadioIsANumberedStop() {
        val axon = Fleet(
            id = "fleet-axon",
            name = "Axon",
            kind = SignatureClass.LAW_ENFORCEMENT,
            attentionNote = "Body-worn.",
        )
        val path = (0..20).map { i ->
            GpsSample(i * 60_000L, 28.7800 + i * 0.01, -81.3700)
        }
        val tagged = Sighting(
            key = "BLE:AA:AA:AA:AA:AA:02",
            kind = RadioKind.BLE,
            mac = "AA:AA:AA:AA:AA:02",
            name = "Cybertruck",
            rssi = -55,
            rssiMin = -70,
            rssiMax = -40,
            channel = 0,
            frequencyMhz = 2402,
            vendor = "Tesla, Inc.",
            randomized = false,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = 0L,
            lastSeen = 20 * 60_000L,
            hitCount = 80,
            fleetIds = emptySet(),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(0, 5, 10, 15, 20).map { i ->
                GpsSample(i * 60_000L, 28.7800 + i * 0.01, -81.3700, -50)
            },
        )
        val dots = SitPathPlot.dotsFrom(
            listOf(tagged), listOf(axon), namedKeys = setOf(tagged.key),
            bookmarkedKeys = setOf(tagged.key),
        )
        assertEquals(1, dots.points.size)
        assertEquals("Cybertruck", dots.points[0].label)
        assertTrue(dots.points[0].named)
    }

    @Test
    fun extraAttentionLeaveAndReturnIsAStop() {
        val axon = Fleet(
            id = "fleet-axon",
            name = "Axon",
            kind = SignatureClass.LAW_ENFORCEMENT,
            attentionNote = "Body-worn.",
        )
        val path = (0..20).map { i ->
            GpsSample(i * 60_000L, 28.7800 + i * 0.01, -81.3700)
        }
        val tagged = Sighting(
            key = "WIFI:AA:AA:AA:AA:AA:03",
            kind = RadioKind.WIFI,
            mac = "AA:AA:AA:AA:AA:03",
            name = "home AP",
            rssi = -40,
            rssiMin = -50,
            rssiMax = -35,
            channel = 6,
            frequencyMhz = 2437,
            vendor = null,
            randomized = false,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = 0L,
            lastSeen = 20 * 60_000L,
            hitCount = 12,
            fleetIds = setOf("fleet-axon"),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(
                GpsSample(0L, 28.7800, -81.3700, -40),
                GpsSample(20 * 60_000L, 28.7800, -81.3700, -42),
            ),
        )
        val dots = SitPathPlot.dotsFrom(listOf(tagged), listOf(axon), namedKeys = emptySet())
        assertEquals(1, dots.points.size)
        assertEquals("home AP", dots.points[0].label)
        assertTrue(dots.points[0].extraAttention)
    }

    @Test
    fun namedWithoutBookmarkIsNotPlotted() {
        val path = listOf(
            GpsSample(0L, 28.7800, -81.3700),
            GpsSample(60_000L, 28.7850, -81.3700),
        )
        val tagged = Sighting(
            key = "BLE:AA:AA:AA:AA:AA:05",
            kind = RadioKind.BLE,
            mac = "AA:AA:AA:AA:AA:05",
            name = "tsTPMS",
            rssi = -55,
            rssiMin = -60,
            rssiMax = -50,
            channel = 0,
            frequencyMhz = 2402,
            vendor = null,
            randomized = false,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = 0L,
            lastSeen = 60_000L,
            hitCount = 8,
            fleetIds = emptySet(),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(GpsSample(0L, 28.7800, -81.3700, -55)),
        )
        val dots = SitPathPlot.dotsFrom(
            listOf(tagged),
            fleets = emptyList(),
            namedKeys = setOf(tagged.key),
            observerNotes = mapOf(tagged.key to "Saw this at the gate."),
        )
        assertTrue(dots.points.isEmpty())
    }

    @Test
    fun observerNotesPlotAsNumberedBlueStop() {
        val path = listOf(
            GpsSample(0L, 28.7800, -81.3700),
            GpsSample(60_000L, 28.7850, -81.3700),
        )
        val tagged = Sighting(
            key = "BLE:AA:AA:AA:AA:AA:04",
            kind = RadioKind.BLE,
            mac = "AA:AA:AA:AA:AA:04",
            name = "iBeacon",
            rssi = -55,
            rssiMin = -60,
            rssiMax = -50,
            channel = 0,
            frequencyMhz = 2402,
            vendor = null,
            randomized = false,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = 0L,
            lastSeen = 60_000L,
            hitCount = 8,
            fleetIds = emptySet(),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(
                GpsSample(0L, 28.7800, -81.3700, -55),
                GpsSample(30_000L, 28.7825, -81.3700, -50),
                GpsSample(60_000L, 28.7850, -81.3700, -58),
            ),
        )
        val dots = SitPathPlot.dotsFrom(
            listOf(tagged),
            fleets = emptyList(),
            namedKeys = emptySet(),
            observerNotes = mapOf(tagged.key to "Mall door, same as last week."),
            bookmarkedKeys = setOf(tagged.key),
        )
        assertEquals(1, dots.points.size)
        assertTrue(dots.points[0].named)
        assertFalse(dots.points[0].extraAttention)
        assertEquals("Mall door, same as last week.", dots.points[0].observerNotes)
    }

    @Test
    fun despikeDropsOutAndBackJump() {
        val a = GpsSample(0L, 28.7800, -81.3700)
        val spike = GpsSample(2_000L, 28.7950, -81.3700)
        val c = GpsSample(4_000L, 28.7802, -81.3700)
        val cleaned = Geo.despikePath(listOf(a, spike, c))
        assertEquals(2, cleaned.size)
        assertEquals(a.at, cleaned[0].at)
        assertEquals(c.at, cleaned[1].at)
    }

    @Test
    fun despikeKeepsHighwayHop() {
        val a = GpsSample(0L, 28.7800, -81.3700)
        val b = GpsSample(10_000L, 28.7827, -81.3700)
        val cleaned = Geo.despikePath(listOf(a, b))
        assertEquals(2, cleaned.size)
    }

    @Test
    fun capSpreadKeepsFirstAndLast() {
        val samples = (0 until 100).map { i ->
            GpsSample(i * 1_000L, 28.78 + i * 0.001, -81.37)
        }
        val kept = Geo.capSpread(samples, 40)
        assertEquals(40, kept.size)
        assertEquals(samples.first().at, kept.first().at)
        assertEquals(samples.last().at, kept.last().at)
        assertTrue(kept.any { it.at in 20_000L..30_000L })
    }
}
