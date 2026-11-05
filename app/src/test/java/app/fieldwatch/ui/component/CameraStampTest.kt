package app.fieldwatch.ui.component

import app.fieldwatch.domain.FlockMark
import app.fieldwatch.domain.GpsSample
import app.fieldwatch.domain.SensorKind
import app.fieldwatch.domain.SitPathPlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraStampTest {
    @Test
    fun closeWedgesOfOneKindAreOnePath() {
        val lay = layout(halfM = 80f)
        assertTrue(flockWedges(lay.metersPerPx))
        val marks = List(12) { i ->
            FlockMark(
                lat = 28.7801 + i * 0.00005,
                lon = -81.3700,
                count = 1,
                facings = listOf(90),
                flock = true,
                kind = SensorKind.PLATE,
            )
        }
        val plan = planFlockMarks(lay, marks, night = false, bold = true)
        assertTrue(plan.shapes.size < 12)
        val fills = plan.shapes.filter { it.strokeWidth == 0f && it.wedges > 0 }
        assertEquals(1, fills.size)
        assertEquals(12, fills.single().wedges)
        val lines = plan.shapes.filter { it.strokeWidth > 0f && it.wedges > 0 }
        assertEquals(1, lines.size)
        assertEquals(12, lines.single().wedges)
        assertTrue(plan.labels.isEmpty())
    }

    @Test
    fun aWidePictureStaysDots() {
        val lay = layout(halfM = 8_000f)
        assertFalse(flockWedges(lay.metersPerPx))
        val marks = List(12) { i ->
            FlockMark(
                lat = 28.7801 + i * 0.0002,
                lon = -81.3700,
                count = 1,
                facings = listOf(90),
                flock = true,
                kind = SensorKind.PLATE,
            )
        }
        val plan = planFlockMarks(lay, marks, night = false, bold = true)
        assertEquals(1, plan.shapes.size)
        assertEquals(0, plan.shapes.single().wedges)
        assertEquals(12, plan.shapes.single().circles)
        assertTrue(plan.labels.isEmpty())
    }

    private fun layout(halfM: Float): SitPathPlot.Layout {
        val model = SitPathPlot.Model(
            samples = listOf(
                GpsSample(1L, 28.7800, -81.3700),
                GpsSample(2L, 28.7802, -81.3700),
            ),
            dots = emptyList(),
            lengthM = 20.0,
            spanM = 20.0,
            title = "stamp",
        )
        return SitPathPlot.layout(
            model,
            400f,
            300f,
            viewport = SitPathPlot.Viewport(28.7801, -81.3700, halfM, halfM),
        )!!
    }
}
