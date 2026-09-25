package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioBookmarksTest {
    private val radio = WatchTarget(id = "r1", deviceKey = "BLE:AA:BB:CC:DD:EE:FF", label = "van tag")
    private val fleet = WatchTarget(id = "f1", fleetId = "fleet-airtag", label = "Apple AirTags")

    @Test
    fun radiosOnlyDropsSignatureWatches() {
        val list = listOf(fleet, radio)
        assertEquals(listOf(radio), RadioBookmarks.radios(list))
        assertEquals(listOf(fleet), RadioBookmarks.withoutRadios(list))
    }

    @Test
    fun clearRadiosKeepsSignatures() {
        assertEquals(listOf(fleet), RadioBookmarks.withoutRadios(listOf(fleet, radio)))
    }

    @Test
    fun renameOnlyTouchesThatRadio() {
        val next = RadioBookmarks.rename(listOf(fleet, radio), "r1", "  porch cam  ")
        assertEquals("porch cam", next.single { it.id == "r1" }.label)
        assertEquals("Apple AirTags", next.single { it.id == "f1" }.label)
    }

    @Test
    fun removeOneLeavesSignatures() {
        val next = RadioBookmarks.remove(listOf(fleet, radio), "r1")
        assertEquals(listOf(fleet), next)
    }

    @Test
    fun upsertNameWithoutAlertThenToggleKeepsName() {
        val named = RadioBookmarks.upsertName(listOf(fleet), "BLE:AA:BB:CC:DD:EE:FF", "Peter's Mesh Node")
        val row = named.single { it.deviceKey != null }
        assertEquals("Peter's Mesh Node", row.label)
        assertEquals(false, row.alert)
        val watched = RadioBookmarks.toggleAlert(named, row.deviceKey!!, "ignored")
        assertEquals(true, watched.single { it.deviceKey != null }.alert)
        assertEquals("Peter's Mesh Node", watched.single { it.deviceKey != null }.label)
        val quiet = RadioBookmarks.toggleAlert(watched, row.deviceKey!!, "ignored")
        assertEquals(false, quiet.single { it.deviceKey != null }.alert)
        assertEquals("Peter's Mesh Node", quiet.single { it.deviceKey != null }.label)
        assertEquals(1, RadioBookmarks.radios(quiet).size)
        assertEquals(fleet, quiet.single { it.fleetId != null })
    }

    @Test
    fun labelsMapsDeviceKeyToCustomName() {
        val named = radio.copy(label = "porch cam")
        val blank = radio.copy(id = "r2", deviceKey = "WIFI:00:11:22:33:44:55", label = "  ")
        val map = RadioBookmarks.labels(listOf(fleet, named, blank))
        assertEquals("porch cam", map["BLE:AA:BB:CC:DD:EE:FF"])
        assertFalse(map.containsKey("WIFI:00:11:22:33:44:55"))
        val device = ble(name = "Meshtastic_3480")
        assertEquals("porch cam", device.reportName(map))
        assertEquals("Meshtastic_3480", device.reportName(emptyMap()))
    }

    @Test
    fun namedKeysSkipsSignaturesAndBlankLabels() {
        val blank = radio.copy(id = "r2", deviceKey = "WIFI:00:11:22:33:44:55", label = "  ")
        assertEquals(
            setOf("BLE:AA:BB:CC:DD:EE:FF"),
            RadioBookmarks.namedKeys(listOf(fleet, radio, blank)),
        )
    }

    @Test
    fun watchedOnlySetsAreBookmarksAndAlertRadios() {
        val quiet = radio.copy(alert = false)
        val alerted = radio.copy(id = "r2", deviceKey = "BLE:11:22:33:44:55:66", alert = true)
        val list = listOf(fleet, quiet, alerted)
        assertEquals(setOf("fleet-airtag"), RadioBookmarks.watchedFleetIds(list))
        assertEquals(setOf("BLE:11:22:33:44:55:66"), RadioBookmarks.alertDeviceKeys(list))
    }

    @Test
    fun parseKey() {
        assertEquals(RadioKind.BLE to "AA:BB:CC:DD:EE:FF", RadioBookmarks.parseKey("BLE:AA:BB:CC:DD:EE:FF"))
        assertEquals(RadioKind.WIFI to "00:11:22:33:44:55", RadioBookmarks.parseKey("WIFI:00:11:22:33:44:55"))
        assertNull(RadioBookmarks.parseKey("nope"))
    }

    @Test
    fun listLineUsesWatchNameForNameLines() {
        val named = ble(name = "Meshtastic_3480")
        assertEquals("Peter's Mesh Node", named.listLineText(ListLine.NAME_AND_TYPE, watchName = "Peter's Mesh Node"))
        assertEquals("Peter's Mesh Node", named.listLineText(ListLine.ADVERTISED_NAME, watchName = "Peter's Mesh Node"))
        assertEquals(named.mac, named.listLineText(ListLine.MAC, watchName = "Peter's Mesh Node"))
        assertEquals("Meshtastic_3480", named.listLineText(ListLine.NAME_AND_TYPE))
    }

    @Test
    fun suggestLabelPrefersAdvertisedName() {
        val named = ble(name = "Tile")
        assertEquals("Tile", RadioBookmarks.suggestLabel(named))
        val unnamed = ble(name = "")
        assertEquals("unnamed LE", RadioBookmarks.suggestLabel(unnamed))
    }

    @Test
    fun upsertNotesCreatesQuietNamedRadioAndClips() {
        assertEquals("a".repeat(280), RadioBookmarks.clipNotes("a".repeat(300)))
        assertEquals("lot B", RadioBookmarks.clipNotes("  lot B  "))
        assertEquals(listOf(fleet), RadioBookmarks.upsertNotes(listOf(fleet), "WIFI:00:11:22:33:44:55", "  ", "van"))
        val created = RadioBookmarks.upsertNotes(
            listOf(fleet),
            "WIFI:00:11:22:33:44:55",
            "  fleet van  ",
            "PS-CRADLEPOINT",
        )
        val row = created.single { it.deviceKey != null }
        assertEquals("fleet van", row.observerNotes)
        assertEquals("PS-CRADLEPOINT", row.label)
        assertEquals(false, row.alert)
        assertEquals("fleet van", RadioBookmarks.notes(created)["WIFI:00:11:22:33:44:55"])
        val named = RadioBookmarks.upsertName(listOf(fleet), "WIFI:00:11:22:33:44:55", "van")
        val noted = RadioBookmarks.upsertNotes(named, "WIFI:00:11:22:33:44:55", "lot B", "ignored")
        val kept = noted.single { it.deviceKey != null }
        assertEquals("van", kept.label)
        assertEquals("lot B", kept.observerNotes)
        assertEquals(false, kept.alert)
        val edited = RadioBookmarks.updateNamedRadio(listOf(radio), "r1", "porch", "north lot")
        assertEquals("porch", edited.single { it.id == "r1" }.label)
        assertEquals("north lot", edited.single { it.id == "r1" }.observerNotes)
        assertEquals("Apple AirTags", RadioBookmarks.updateNamedRadio(listOf(fleet), "f1", "x", "y").single().label)
    }

    @Test
    fun wifiLocalBitBssidCanTakeACustomName() {
        val ap = wifi(mac = "02:0A:F5:86:56:DD", randomized = true)
        assertTrue(RadioBookmarks.canSetCustomName(ap))
        assertTrue(RadioBookmarks.customNameHint(ap).contains("BSSID"))
        val burned = wifi(mac = "00:0A:F5:86:56:DD", randomized = false)
        assertTrue(RadioBookmarks.canSetCustomName(burned))
        val rotatingBle = ble(name = "tag")
        assertFalse(RadioBookmarks.canSetCustomName(rotatingBle))
    }

    private fun wifi(mac: String, randomized: Boolean) = Sighting(
        key = "WIFI:$mac",
        kind = RadioKind.WIFI,
        mac = mac,
        name = "PS-CRADLEPOINT",
        rssi = -50,
        rssiMin = -50,
        rssiMax = -50,
        channel = 1,
        frequencyMhz = 2412,
        vendor = null,
        randomized = randomized,
        hiddenSsid = false,
        serviceUuids = emptyList(),
        manufacturerId = null,
        manufacturerDataHex = "",
        rawHex = "",
        extras = "",
        firstSeen = 1L,
        lastSeen = 1L,
        hitCount = 1,
        fleetIds = emptySet(),
        rssiHistory = emptyList(),
        presence = emptyList(),
    )

    private fun ble(name: String) = Sighting(
        key = "BLE:AA:BB:CC:DD:EE:FF",
        kind = RadioKind.BLE,
        mac = "AA:BB:CC:DD:EE:FF",
        name = name,
        rssi = -50,
        rssiMin = -50,
        rssiMax = -50,
        channel = 0,
        frequencyMhz = 0,
        vendor = null,
        randomized = true,
        hiddenSsid = false,
        serviceUuids = emptyList(),
        manufacturerId = null,
        manufacturerDataHex = "",
        rawHex = "",
        extras = "",
        firstSeen = 1L,
        lastSeen = 1L,
        hitCount = 1,
        fleetIds = emptySet(),
        rssiHistory = emptyList(),
        presence = emptyList(),
    )
}
