package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RfAnomalyDetectorTest {

    private fun mockEphemeralBle(
        index: Int,
        companyId: Int? = null,
        serviceUuid: String? = null,
        firstSeen: Long = 10_000L,
    ): Sighting {
        val mac = "02:00:00:00:00:%02X".format(index)
        return Sighting(
            key = "BLE:$mac",
            kind = RadioKind.BLE,
            mac = mac,
            name = "",
            rssi = -65,
            rssiMin = -65,
            rssiMax = -65,
            channel = 37,
            frequencyMhz = 2402,
            vendor = null,
            randomized = true,
            hiddenSsid = false,
            serviceUuids = serviceUuid?.let { listOf(it) } ?: emptyList(),
            manufacturerId = companyId,
            manufacturerDataHex = companyId?.let { "0707010F2051981100" } ?: "",
            rawHex = "",
            extras = "",
            firstSeen = firstSeen,
            lastSeen = firstSeen + 100,
            hitCount = 1,
            fleetIds = emptySet(),
            rssiHistory = emptyList(),
            presence = emptyList(),
            facts = RadioFacts(
                mfgRecords = companyId?.let { listOf(MfgRecord(it, "0707010F2051981100")) } ?: emptyList(),
                serviceData = serviceUuid?.let { listOf(ServiceDataRecord(it, "010203")) } ?: emptyList(),
            ),
        )
    }

    @Test
    fun quietEnvironmentReturnsNoSpamAlert() {
        val normalSightings = listOf(
            mockEphemeralBle(1, companyId = 0x004C),
            mockEphemeralBle(2, companyId = 0x004C),
        )
        val result = RfAnomalyDetector.assessBleSpam(normalSightings, now = 10_500L)
        assertFalse(result.isSpamAttackActive)
        assertEquals(2, result.burstCount)
    }

    @Test
    fun highRateApplePairingFloodTriggersThreatAlert() {
        // Generate 20 ephemeral devices in a 2-second burst with Apple Continuity (0x004C)
        val spamBurst = (1..20).map { i ->
            mockEphemeralBle(i, companyId = 0x004C, firstSeen = 9_500L)
        }

        val result = RfAnomalyDetector.assessBleSpam(spamBurst, now = 10_000L, windowMs = 5_000L)
        assertTrue(result.isSpamAttackActive)
        assertEquals(20, result.burstCount)
        assertTrue(result.primaryVector?.contains("Apple") == true)
        assertTrue(result.details?.contains("Flipper Zero") == true)
    }

    @Test
    fun fastPairFloodIdentifiesGoogleVector() {
        // Generate 16 ephemeral FastPair devices (0xFE2C)
        val fastPairBurst = (1..16).map { i ->
            mockEphemeralBle(i, serviceUuid = "FE2C", firstSeen = 9_800L)
        }

        val result = RfAnomalyDetector.assessBleSpam(fastPairBurst, now = 10_000L, windowMs = 5_000L)
        assertTrue(result.isSpamAttackActive)
        assertTrue(result.primaryVector?.contains("FastPair") == true)
    }
}
