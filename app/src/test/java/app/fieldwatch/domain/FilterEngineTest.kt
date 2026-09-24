package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {
    private val engine = FilterEngine()
    private val labeled = ble("BLE:AA:BB:CC:DD:EE:FF")
    private val other = ble("BLE:11:22:33:44:55:66")
    private val signed = labeled.copy(fleetIds = setOf("fleet-airtag"))

    @Test
    fun stockPresetsMatchShortSetWithWatchedOnly() {
        val presets = engine.defaultPresets()
        assertEquals(
            listOf("all", "wifi", "ble", "strong", "with-you", "watched"),
            presets.map { it.id },
        )
        val watched = presets.single { it.id == "watched" }
        assertEquals("Watched only", watched.name)
        assertTrue(watched.filter.watchedOnly)
        assertTrue(watched.filter.showWifi)
        assertTrue(watched.filter.showBle)
        assertEquals(-100, watched.filter.rssiMin)
        assertTrue(watched.isBuiltIn())
        assertTrue(FilterPreset("trackers", "Trackers", FilterState()).isBuiltIn())
    }

    @Test
    fun customNamesOnlyKeepsLabeledKeys() {
        val filter = FilterState(customNamesOnly = true)
        val keys = setOf(labeled.key)
        assertTrue(engine.pass(labeled, filter, namedRadioKeys = keys))
        assertFalse(engine.pass(other, filter, namedRadioKeys = keys))
        assertFalse(engine.pass(labeled, filter, namedRadioKeys = emptySet()))
    }

    @Test
    fun customNamesOnlyOffDoesNotHide() {
        assertTrue(engine.pass(other, FilterState(), namedRadioKeys = setOf(labeled.key)))
    }

    @Test
    fun hideFastPairAccountKeyDropsPlazaChipsKeepsPairingAndDual() {
        val hide = FilterState(hideFastPairAccountKey = true)
        val account = labeled.copy(fleetIds = setOf("fleet-fast-pair"), fastPairPairing = false)
        val pairing = labeled.copy(fleetIds = setOf("fleet-fast-pair"), fastPairPairing = true)
        val dual = labeled.copy(
            fleetIds = setOf("fleet-fast-pair", "fleet-google"),
            fastPairPairing = false,
        )
        assertFalse(engine.pass(account, hide))
        assertTrue(engine.pass(pairing, hide))
        assertTrue(engine.pass(dual, hide))
        assertTrue(engine.pass(account, FilterState()))
    }

    @Test
    fun watchedOnlyKeepsBookmarkedSignature() {
        val filter = FilterState(watchedOnly = true)
        assertTrue(engine.pass(signed, filter, watchedFleetIds = setOf("fleet-airtag")))
        assertFalse(engine.pass(signed, filter, watchedFleetIds = emptySet()))
        assertFalse(engine.pass(other, filter, watchedFleetIds = setOf("fleet-airtag")))
    }

    @Test
    fun watchedOnlyKeepsAlertNamedRadioNotLabelOnly() {
        val filter = FilterState(watchedOnly = true)
        assertTrue(engine.pass(labeled, filter, alertDeviceKeys = setOf(labeled.key)))
        assertFalse(engine.pass(labeled, filter, namedRadioKeys = setOf(labeled.key)))
    }

    @Test
    fun watchedOnlyAndHideSurveillanceStillHidesCameras() {
        val filter = FilterState(
            watchedOnly = true,
            useClassFilter = true,
            excludeClasses = true,
            classes = setOf(SignatureClass.SURVEILLANCE),
        )
        val flock = signed.copy(fleetIds = setOf("fleet-flock"))
        val axon = other.copy(fleetIds = setOf("fleet-axon"))
        val byClass = mapOf(
            "fleet-flock" to SignatureClass.SURVEILLANCE,
            "fleet-axon" to SignatureClass.LAW_ENFORCEMENT,
        )
        val watched = setOf("fleet-flock", "fleet-axon")
        assertFalse(engine.pass(flock, filter, classByFleetId = byClass, watchedFleetIds = watched))
        assertTrue(engine.pass(axon, filter, classByFleetId = byClass, watchedFleetIds = watched))
    }

    @Test
    fun watchedOnlyStaysAndInOrLogic() {
        val filter = FilterState(
            watchedOnly = true,
            logic = FilterLogic.OR,
            nameQuery = "anything",
        )
        val namedUnwatched = labeled.copy(name = "anything")
        assertFalse(engine.pass(namedUnwatched, filter, watchedFleetIds = setOf("fleet-airtag")))
        assertTrue(engine.pass(signed.copy(name = "anything"), filter, watchedFleetIds = setOf("fleet-airtag")))
    }

    @Test
    fun signaturesOnlyAndCustomNamesStack() {
        val both = FilterState(namedOnly = true, customNamesOnly = true)
        val keys = setOf(labeled.key, other.key)
        assertTrue(engine.pass(signed, both, namedRadioKeys = keys))
        assertFalse(engine.pass(labeled, both, namedRadioKeys = keys))
        assertFalse(engine.pass(signed.copy(key = other.key), both, namedRadioKeys = setOf(labeled.key)))
    }

    private fun ble(key: String) = Sighting(
        key = key,
        kind = RadioKind.BLE,
        mac = key.substringAfter(':'),
        name = "",
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
