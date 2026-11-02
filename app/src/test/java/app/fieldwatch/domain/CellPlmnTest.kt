package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CellPlmnTest {
    @Test
    fun countryAndOperatorFollowTheNetworkCode() {
        LanguageText.clear()
        assertEquals(241, CellPlmnData.countryIso.size)
        assertEquals(1762, CellPlmnData.operator.size)
        assertEquals("United States", CellPlmn.country("310-410"))
        assertEquals("AT&T", CellPlmn.operator("310-410"))
        assertEquals("United States", CellPlmn.country("310-999"))
        assertNull(CellPlmn.operator("310-999"))
        assertEquals("United Kingdom", CellPlmn.country("234-15"))
        assertNull(CellPlmn.operator("234-15"))
        assertEquals("Canada", CellPlmn.country("302-220"))
        assertNull(CellPlmn.operator("302-220"))
        assertEquals("Verizon Wireless", CellPlmn.operator("311-480"))
        assertEquals("Test Network, Used by GSM test equipment", CellPlmn.operator("001-01"))
        assertNull(CellPlmn.country("001-01"))
        assertNull(CellPlmn.country("999-99"))
        assertNull(CellPlmn.operator("999-99"))
        assertNull(CellPlmn.country("310410"))
        assertNull(CellPlmn.operator("310410"))
        assertNull(CellPlmn.country(""))
        assertNull(CellPlmn.operator("nope"))
    }
}
