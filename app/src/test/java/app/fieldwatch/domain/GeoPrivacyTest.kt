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
}
