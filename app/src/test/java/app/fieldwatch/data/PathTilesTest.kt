package app.fieldwatch.data

import org.junit.Assert.assertTrue
import org.junit.Test

class PathTilesTest {
    @Test
    fun zoomTightensForAShortSpanAndOpensForADrive() {
        assertTrue(PathTiles.zoomFor(80.0, 28.5) >= 15)
        assertTrue(PathTiles.zoomFor(12_000.0, 28.5) in 8..14)
    }

    @Test
    fun expandToPlotFillsAWidePlotForATallPath() {
        val box = PathTiles.expandToPlot(28.780, 28.790, -81.370, -81.3695)
        val latSpan = box[1] - box[0]
        val lonSpan = box[3] - box[2]
        val yM = latSpan * 110_540.0
        val xM = lonSpan * 111_320.0 * kotlin.math.cos(Math.toRadians(28.785))
        assertTrue(xM / yM in 1.5..1.9)
    }
}
