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
        assertFalse(text.contains("Full Wi-Fi inventory"))
        assertFalse(text.contains("## Persistence (15 min)"))
        assertFalse(text.contains("## Channel utilization"))
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
