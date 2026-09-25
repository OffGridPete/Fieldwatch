package app.fieldwatch.domain

import org.junit.Assert.assertEquals
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
        assertEquals(1, dots.size)
        assertTrue(dots[0].extraAttention)
        assertNotNull(SitPathPlot.layout(
            SitPathPlot.Model(
                samples = listOf(
                    GpsSample(1L, 28.780, -81.370),
                    GpsSample(2L, 28.781, -81.371),
                ),
                dots = dots,
                lengthM = 120.0,
                spanM = 100.0,
                title = "plaza",
            ),
            300f, 200f,
        ))
    }
}
