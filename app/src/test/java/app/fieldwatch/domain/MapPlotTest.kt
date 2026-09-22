package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos

class MapPlotTest {

    private fun m(lat: Double, lon: Double, ref: Double = 0.0) =
        MapPlot.toMeters(lat, lon, ref)

    @Test
    fun `toMeters scales longitude by cos of reference latitude`() {
        val p = m(lat = 10.0, lon = 1.0, ref = 60.0)
        assertEquals(MapPlot.DEG_M * 10.0, p.y, 0.001)
        assertEquals(MapPlot.DEG_M * cos(Math.toRadians(60.0)), p.x, 0.001)
    }

    @Test
    fun `fit returns null on empty points or empty canvas`() {
        assertNull(MapPlot.fit(emptyList(), 100f, 100f))
        assertNull(MapPlot.fit(listOf(m(1.0, 1.0)), 0f, 100f))
        assertNull(MapPlot.fit(listOf(m(1.0, 1.0)), 100f, 0f))
    }

    @Test
    fun `fit centers on the midpoint of the span`() {
        val view = MapPlot.fit(listOf(m(0.0, 0.0), m(0.0, 2.0)), 400f, 200f)!!
        assertEquals(MapPlot.DEG_M, view.cx, 0.001)
        assertEquals(0.0, view.cy, 0.001)
    }

    @Test
    fun `fit scale fills the limiting canvas dimension with padding`() {
        // 1° of longitude at the equator = 111_320 m wide, 1 m tall → width limits.
        val view = MapPlot.fit(listOf(m(0.0, 0.0), m(0.0, 1.0)), 1240f, 1240f)!!
        val padded = MapPlot.DEG_M * 1.24
        assertEquals(1240f / padded.toFloat(), view.scale, 1e-6f)
    }

    @Test
    fun `project maps the view center to canvas center`() {
        val view = MapPlot.fit(listOf(m(0.0, 0.0), m(1.0, 1.0)), 400f, 300f)!!
        val (x, y) = MapPlot.project(m(0.5, 0.5), view, 400f, 300f)
        assertEquals(200f, x, 0.5f)
        assertEquals(150f, y, 0.5f)
    }

    @Test
    fun `project keeps north up and east right`() {
        val view = MapPlot.fit(listOf(m(0.0, 0.0), m(1.0, 1.0)), 400f, 300f)!!
        val (_, northY) = MapPlot.project(m(1.0, 0.5), view, 400f, 300f)
        val (eastX, southY) = MapPlot.project(m(0.0, 1.0), view, 400f, 300f)
        assertTrue(northY < southY) // smaller y = higher on screen
        val (centerX, _) = MapPlot.project(m(0.5, 0.5), view, 400f, 300f)
        assertTrue(eastX > centerX)
    }

    @Test
    fun `zoom doubles meters-per-pixel inverse and pan shifts px`() {
        val view = MapPlot.fit(listOf(m(0.0, 0.0), m(1.0, 0.0)), 400f, 400f)!!
        val base = MapPlot.metersPerPixel(view, 1f)
        assertEquals(base / 2, MapPlot.metersPerPixel(view, 2f), 1e-9)
        val (x0, _) = MapPlot.project(m(0.5, 0.0), view, 400f, 400f)
        val (x1, _) = MapPlot.project(m(0.5, 0.0), view, 400f, 400f, panX = 30f)
        assertEquals(30f, x1 - x0, 0.001f)
    }

    @Test
    fun `a single point still fits and projects to canvas center`() {
        val view = MapPlot.fit(listOf(m(45.0, 5.0)), 400f, 300f)!!
        val (x, y) = MapPlot.project(m(45.0, 5.0), view, 400f, 300f)
        assertEquals(200f, x, 0.5f)
        assertEquals(150f, y, 0.5f)
    }

    @Test
    fun `scaleBar picks the largest round step under the target width`() {
        // 1 m/px → 90 px target → 50 m bar of 50 px.
        val (meters, px) = MapPlot.scaleBar(1.0, 90f)!!
        assertEquals(50, meters)
        assertEquals(50f, px, 0.01f)
        // 20 m/px → 90 px covers 1800 m → 1000 m bar of 50 px.
        val (m2, px2) = MapPlot.scaleBar(20.0, 90f)!!
        assertEquals(1000, m2)
        assertEquals(50f, px2, 0.01f)
    }

    @Test
    fun `scaleBar returns null when even 1 m is too wide`() {
        assertNull(MapPlot.scaleBar(0.0))
        assertNull(MapPlot.scaleBar(Double.POSITIVE_INFINITY))
        assertNull(MapPlot.scaleBar(0.001, 0.0005f))
    }

    @Test
    fun `barLabel switches to km at one thousand meters`() {
        assertEquals("500 m", MapPlot.barLabel(500))
        assertEquals("1 km", MapPlot.barLabel(1000))
        assertEquals("50 km", MapPlot.barLabel(50_000))
    }

    @Test
    fun `projection stays finite at antimeridian and poles`() {
        val pts = listOf(m(89.0, 179.0, ref = 45.0), m(-89.0, -179.0, ref = 45.0))
        val view = MapPlot.fit(pts, 400f, 300f)!!
        pts.forEach {
            val (x, y) = MapPlot.project(it, view, 400f, 300f)
            assertTrue(abs(x) < 1e6 && abs(y) < 1e6)
        }
    }
}
