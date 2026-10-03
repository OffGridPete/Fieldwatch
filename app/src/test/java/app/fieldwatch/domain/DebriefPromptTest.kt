package app.fieldwatch.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebriefPromptTest {
    private val axon = Fleet(
        id = "fleet-axon",
        name = "Axon",
        kind = SignatureClass.LAW_ENFORCEMENT,
        attentionNote = "Body-worn, in-car, dock, or TASER.",
    )

    @Test
    fun addendumOmitsFullInventoriesAndKeepsRates() {
        val now = 15 * 60_000L
        val cam = radio("WIFI:AA:AA:AA:AA:AA:01", "cam", now, fleetIds = setOf("fleet-axon"))
        val ble = radio("BLE:BB:BB:BB:BB:BB:02", "tag", now, kind = RadioKind.BLE, rand = true)
        val text = DebriefPrompt.build(
            devices = listOf(cam, ble),
            fleets = listOf(axon),
            settings = AppSettings(),
            now = now,
        )
        assertTrue(text.contains("Do not rewrite that report"))
        assertTrue(text.contains("RAND BLE"))
        assertTrue(text.contains("5 min:"))
        assertTrue(text.contains("15 min:"))
        assertTrue(text.contains("Extra attention:"))
        assertTrue(text.contains("Observer notes:"))
        assertTrue(text.contains("Axon"))
        assertTrue(text.contains("Takeaway:"))
        assertTrue(text.contains("decoded live value"))
        assertTrue(text.contains("A flood note is a burst of new addresses, not a follower."))
        assertTrue(text.contains("Flood if any"))
        assertFalse(text.contains("Full Wi-Fi inventory"))
        assertFalse(text.contains("## Persistence (15 min)"))
        assertFalse(text.contains("## Channel utilization"))
    }

    @Test
    fun onboardPasteIncludesAFloodLine() {
        val now = 15 * 60_000L
        val text = DebriefPrompt.build(
            devices = emptyList(),
            fleets = emptyList(),
            settings = AppSettings(),
            now = now,
            floods = listOf(
                FloodBurst(
                    at = now - 60_000L,
                    popupCount = 8,
                    nameCount = 0,
                    families = listOf("Apple proximity pairing"),
                    medianRssi = -48,
                ),
            ),
        )
        assertTrue(text.contains("FLOOD"))
        assertTrue(text.contains(FloodBurst.INTRO))
        assertTrue(text.contains("8 new addresses"))
    }

    @Test
    fun workingCountsLeaveOutFloodAddresses() {
        val now = 15 * 60_000L
        val real = radio("BLE:AC:23:3F:11:22:33", "Checkout beacon", now, kind = RadioKind.BLE)
        val flood = radio("BLE:02:00:00:00:00:01", "spam", now, kind = RadioKind.BLE, rand = true)
        val text = DebriefPrompt.build(
            devices = listOf(real, flood),
            fleets = emptyList(),
            settings = AppSettings(),
            now = now,
            floods = listOf(
                FloodBurst(
                    at = now - 60_000L,
                    popupCount = 6,
                    nameCount = 0,
                    keys = listOf(flood.key),
                ),
            ),
        )
        assertTrue(text.contains("15 min: Wi-Fi 0  BLE 1"))
        assertTrue(text.contains("1 address from this burst is left out of the counts and lists below."))
        assertFalse(text.contains("02:00:00:00:00:01"))
    }
}

private fun radio(
    key: String,
    name: String,
    now: Long,
    kind: RadioKind = RadioKind.WIFI,
    fleetIds: Set<String> = emptySet(),
    rand: Boolean = false,
) = Sighting(
    key = key,
    kind = kind,
    mac = key.substringAfter(':'),
    name = name,
    rssi = -60,
    rssiMin = -70,
    rssiMax = -50,
    channel = 6,
    frequencyMhz = 2437,
    vendor = null,
    randomized = rand,
    hiddenSsid = false,
    serviceUuids = emptyList(),
    manufacturerId = null,
    manufacturerDataHex = "",
    rawHex = "",
    extras = "",
    firstSeen = now - 60_000L,
    lastSeen = now,
    hitCount = 4,
    fleetIds = fleetIds,
    rssiHistory = emptyList(),
    presence = emptyList(),
)
