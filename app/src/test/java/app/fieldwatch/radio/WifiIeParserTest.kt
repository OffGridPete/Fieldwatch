package app.fieldwatch.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiIeParserTest {
    private fun ie(id: Int, vararg data: Int) =
        WifiIeParser.Ie(id, data.map { it.toByte() }.toByteArray())

    private val wpa2CcmpPsk = ie(
        48,
        0x01, 0x00, // version 1
        0x00, 0x0F, 0xAC, 0x04, // group CCMP
        0x01, 0x00, // 1 pairwise
        0x00, 0x0F, 0xAC, 0x04, // CCMP
        0x01, 0x00, // 1 AKM
        0x00, 0x0F, 0xAC, 0x02, // PSK
    )

    @Test
    fun rsnWpa2PskCcmp() {
        val p = WifiIeParser.parseIes(listOf(wpa2CcmpPsk), "[WPA2-PSK-CCMP][ESS]")
        assertEquals("RSN PSK CCMP (group CCMP)", p.security)
    }

    @Test
    fun rsnWpa3Sae() {
        val ie = ie(
            48,
            0x01, 0x00,
            0x00, 0x0F, 0xAC, 0x04,
            0x01, 0x00,
            0x00, 0x0F, 0xAC, 0x04,
            0x01, 0x00,
            0x00, 0x0F, 0xAC, 0x08, // SAE
        )
        assertEquals("RSN SAE CCMP (group CCMP)", WifiIeParser.parseIes(listOf(ie), "").security)
    }

    @Test
    fun rsnTransitionModeListsBothAkms() {
        val ie = ie(
            48,
            0x01, 0x00,
            0x00, 0x0F, 0xAC, 0x04,
            0x01, 0x00,
            0x00, 0x0F, 0xAC, 0x04,
            0x02, 0x00,
            0x00, 0x0F, 0xAC, 0x02, // PSK
            0x00, 0x0F, 0xAC, 0x08, // SAE
        )
        assertEquals("RSN PSK/SAE CCMP (group CCMP)", WifiIeParser.parseIes(listOf(ie), "").security)
    }

    @Test
    fun wpa1VendorIeSummaryAndOui() {
        val wpa = ie(
            221,
            0x00, 0x50, 0xF2, 0x01, // Microsoft OUI, WPA type
            0x01, 0x00,
            0x00, 0x50, 0xF2, 0x02, // group TKIP
            0x01, 0x00,
            0x00, 0x50, 0xF2, 0x02, // pairwise TKIP
            0x01, 0x00,
            0x00, 0x50, 0xF2, 0x02, // akm
        )
        val p = WifiIeParser.parseIes(listOf(wpa), "")
        assertEquals("WPA TKIP", p.security)
        assertTrue(p.vendorIes.any { it.oui == "00:50:F2" && it.type == 1 })
    }

    @Test
    fun vendorIeExtractsOuiTypeAndPayload() {
        val p = WifiIeParser.parseIes(
            listOf(ie(221, 0x00, 0x17, 0xF2, 0x0A, 0x01, 0x02)),
            "",
        )
        assertEquals(1, p.vendorIes.size)
        assertEquals("00:17:F2", p.vendorIes[0].oui)
        assertEquals(0x0A, p.vendorIes[0].type)
        assertEquals("0102", p.vendorIes[0].dataHex)
    }

    @Test
    fun ratesDecodeBasicFlagAndHalfMbps() {
        val p = WifiIeParser.parseIes(
            listOf(ie(1, 0x82, 0x84, 0x8B, 0x96, 0x0C, 0x12, 0x18, 0x24)),
            "",
        )
        assertEquals("1* 2* 5.5* 11* 6 9 12 18", p.rates)
    }

    @Test
    fun dsParameterSetsChannel() {
        val p = WifiIeParser.parseIes(listOf(ie(3, 0x06)), "")
        assertEquals(6, p.channelFromDs)
    }

    @Test
    fun capabilitiesFallbackWhenNoSecurityIe() {
        assertEquals(
            "[WPA2-PSK-CCMP][ESS]",
            WifiIeParser.parseIes(emptyList(), "[WPA2-PSK-CCMP][ESS]").security,
        )
        // A truncated RSN body must not shadow the capability string.
        val short = ie(48, 0x01, 0x00, 0x00, 0x0F)
        assertEquals(
            "[WPA2-PSK-CCMP][ESS]",
            WifiIeParser.parseIes(listOf(short), "[WPA2-PSK-CCMP][ESS]").security,
        )
    }

    @Test
    fun noIesNoCapabilitiesGivesNullSecurity() {
        assertNull(WifiIeParser.parseIes(emptyList(), null).security)
        assertNull(WifiIeParser.parseIes(emptyList(), "").security)
    }
}
