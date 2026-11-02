package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CellBandTest {
    @Test
    fun lteRangesDoNotOverlap() {
        assertTrue(CellBand.lteRangesAreDisjoint())
    }

    @Test
    fun aChannelThatMapsToOneBandShowsTheDownlink() {
        assertEquals("12 · 731.5 MHz", CellBand.label(CellGen.LTE, 5035))
        assertEquals("2 · 1960 MHz", CellBand.label(CellGen.LTE, 900))
        assertEquals("12 · 729 MHz", CellBand.label(CellGen.LTE, 5010))
        assertNull(CellBand.label(CellGen.LTE, 5009))
        assertEquals("13 · 746 MHz", CellBand.label(CellGen.LTE, 5180))
        assertEquals("66 · 2155 MHz", CellBand.label(CellGen.LTE, 66886))
        assertEquals("850 · 883.6 MHz", CellBand.label(CellGen.GSM, 200))
        assertEquals("1800 · 1822.8 MHz or 1900 · 1947.8 MHz", CellBand.label(CellGen.GSM, 600))
        assertEquals("1800 · 1865 MHz", CellBand.label(CellGen.GSM, 811))
        assertEquals("1 · 2140 MHz", CellBand.label(CellGen.WCDMA, 10700))
        assertEquals("2 · 1960 MHz", CellBand.label(CellGen.WCDMA, 9800))
        assertEquals("n71 · 634.5 MHz", CellBand.label(CellGen.NR, 126900))
        assertEquals("n2 or n25 · 1960 MHz", CellBand.label(CellGen.NR, 392000))
        assertEquals("n1 or n66 · 2140 MHz", CellBand.label(CellGen.NR, 428000))
        assertEquals("n5, n18, or n26 · 870 MHz", CellBand.label(CellGen.NR, 174000))
        assertNull(CellBand.label(CellGen.TDSCDMA, 10054))
    }
}
