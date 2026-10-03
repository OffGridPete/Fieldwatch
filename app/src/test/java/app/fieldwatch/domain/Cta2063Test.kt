package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Cta2063Test {
    @Test
    fun longestPrefixNamesTheDeclaredModel() {
        assertEquals("Freefly Alta X Gen2", Cta2063.label("18179132000209"))
        assertEquals("Freefly Astro", Cta2063.label("18179131000018"))
        assertEquals("Freefly Astro", Cta2063.label("18179130000000"))
        assertEquals("Freefly Astro", Cta2063.label("18179133000001"))
        assertEquals("Freefly Alta X", Cta2063.label("18179200000001"))
        assertEquals("Freefly Alta X broadcast kit", Cta2063.label("18179300000001"))
        assertEquals("Freefly", Cta2063.label("18179400000001"))
        assertEquals("BRINC Lemur 2", Cta2063.label("1914CL2D230001"))
        assertEquals("BRINC Responder", Cta2063.label("1914CR1D230001"))
        assertEquals("BRINC", Cta2063.label("1914ZZ0001"))
        assertEquals("Teal 2", Cta2063.label("1839FTD5020001"))
        assertEquals("Teal", Cta2063.label("1839ABC0001"))
        assertNull(Cta2063.label("1596F33ABCDEF"))
        assertNull(Cta2063.label("1581F3YTDJ1D0031Z530"))
        assertEquals("Freefly Alta X Gen2", Cta2063.label("  18179132000209  "))
        assertEquals("Freefly Alta X Gen2", Cta2063.label("18179132000209".lowercase()))
        assertEquals("BRINC Lemur 2", Cta2063.label("1914cl2d230001"))
        assertNull(Cta2063.label("XX18179132000209"))
        assertNull(Cta2063.label(""))
        assertNull(Cta2063.label("   "))
    }
}
