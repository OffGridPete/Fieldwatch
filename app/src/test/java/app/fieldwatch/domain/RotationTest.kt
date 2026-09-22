package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RotationTest {

    private fun sighting(
        kind: RadioKind = RadioKind.BLE,
        mac: String = "C2:11:22:33:44:55",
        randomized: Boolean = true,
        name: String = "",
        mfgId: Int? = 0x004C,
        mfgData: String = "1219AABB",
        uuids: List<String> = emptyList(),
        appearance: Int? = null,
    ) = Sighting(
        key = "${kind.name}:$mac",
        kind = kind,
        mac = mac,
        name = name,
        rssi = -60,
        rssiMin = -60,
        rssiMax = -60,
        channel = 0,
        frequencyMhz = 0,
        vendor = null,
        randomized = randomized,
        hiddenSsid = false,
        serviceUuids = uuids,
        manufacturerId = mfgId,
        manufacturerDataHex = mfgData,
        rawHex = "",
        extras = "",
        firstSeen = 0L,
        lastSeen = 0L,
        hitCount = 1,
        fleetIds = emptySet(),
        rssiHistory = emptyList(),
        presence = emptyList(),
        facts = RadioFacts(appearance = appearance),
    )

    @Test
    fun wifiNeverFingerprints() {
        assertNull(Rotation.fingerprint(sighting(kind = RadioKind.WIFI)))
    }

    @Test
    fun stableBleMacNeverFingerprints() {
        assertNull(Rotation.fingerprint(sighting(randomized = false)))
    }

    @Test
    fun featurelessRadioHasNoFingerprint() {
        assertNull(
            Rotation.fingerprint(
                sighting(mfgId = null, mfgData = "", uuids = emptyList(), name = ""),
            ),
        )
    }

    @Test
    fun sameStructureSameFingerprint() {
        val a = sighting(mac = "C2:11:22:33:44:55", mfgData = "1219AABBCCDD")
        val b = sighting(mac = "C6:AA:BB:CC:DD:EE", mfgData = "1219AABBEEFF")
        // Rotating payload tail differs — stable prefix still matches.
        assertEquals(Rotation.fingerprint(a), Rotation.fingerprint(b))
    }

    @Test
    fun differentPrefixDiffers() {
        val a = sighting(mfgData = "1219AABB")
        val b = sighting(mfgData = "0F8201CC")
        assertNotEquals(Rotation.fingerprint(a), Rotation.fingerprint(b))
    }

    @Test
    fun nameParticipatesInFingerprint() {
        val a = sighting(name = "Tracker")
        val b = sighting(name = "")
        assertNotEquals(Rotation.fingerprint(a), Rotation.fingerprint(b))
    }

    @Test
    fun uuidOnlyRadioFingerprints() {
        val a = sighting(mfgId = null, mfgData = "", uuids = listOf("FE2C", "180F"))
        val b = sighting(mfgId = null, mfgData = "", uuids = listOf("180F", "FE2C"))
        assertEquals(Rotation.fingerprint(a), Rotation.fingerprint(b))
    }
}
