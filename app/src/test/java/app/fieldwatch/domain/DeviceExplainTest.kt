package app.fieldwatch.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceExplainTest {
    @Test
    fun ravenDirectSsidIsAcousticSensorNotPhoneOrTv() {
        val device = wifi("00:0A:F5:86:56:DD", "DIRECT-rR-Raven-607")
        val guess = DeviceExplain.guess(device, listOf("Raven / ShotSpotter", "Unknown Signature"))
        assertTrue(guess.headline, guess.headline.contains("Raven", ignoreCase = true))
        assertTrue(guess.headline, guess.headline.contains("ShotSpotter", ignoreCase = true))
        assertFalse(guess.headline, guess.headline.contains("phone or TV", ignoreCase = true))
        assertTrue(guess.because, guess.because.contains("Raven / ShotSpotter"))
        assertTrue(guess.because, guess.because.contains("DIRECT-"))
    }

    @Test
    fun genericDirectSsidWithoutProductFamilyStillLooksLikeWifiDirect() {
        val device = wifi("02:11:22:33:44:55", "DIRECT-xy-LivingRoom")
        val guess = DeviceExplain.guess(device, emptyList())
        assertTrue(guess.headline, guess.headline.contains("Wi-Fi Direct", ignoreCase = true))
    }

    @Test
    fun wifiLocalBitBssidIsNotARotatingPrivacyMac() {
        val ap = wifi("02:0A:F5:86:56:DD", "PS-CRADLEPOINT").copy(randomized = true)
        val text = DeviceExplain.addressExplain(ap)
        assertTrue(text, text.contains("Locally administered BSSID"))
        assertFalse(text, text.contains("privacy", ignoreCase = true))
        assertFalse(text, text.contains("can change", ignoreCase = true))
    }

    @Test
    fun unknownSignatureNameIsNotAProductGuess() {
        val device = wifi("02:11:22:33:44:56", "DIRECT-ab-TV")
        val guess = DeviceExplain.guess(device, listOf("Unknown Signature"))
        assertFalse(guess.headline, guess.headline.contains("Unknown", ignoreCase = true))
        assertTrue(guess.headline, guess.headline.contains("Wi-Fi Direct", ignoreCase = true))
    }

    private fun wifi(mac: String, name: String) = Sighting(
        key = "WIFI:$mac",
        kind = RadioKind.WIFI,
        mac = mac,
        name = name,
        rssi = -86,
        rssiMin = -86,
        rssiMax = -76,
        channel = 1,
        frequencyMhz = 2412,
        vendor = "Airgo Networks, Inc.",
        randomized = false,
        hiddenSsid = false,
        serviceUuids = emptyList(),
        manufacturerId = null,
        manufacturerDataHex = "",
        rawHex = "",
        extras = "",
        firstSeen = 1L,
        lastSeen = 1L,
        hitCount = 7,
        fleetIds = emptySet(),
        rssiHistory = emptyList(),
        presence = emptyList(),
    )
}
