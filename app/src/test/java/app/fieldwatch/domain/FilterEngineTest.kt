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
    fun seenAtPlacesKeepsOnlyListedKeys() {
        val filter = FilterState(seenAtPlaces = true)
        assertTrue(engine.pass(labeled, filter, placeKeys = setOf(labeled.key)))
        assertFalse(engine.pass(other, filter, placeKeys = setOf(labeled.key)))
        assertTrue(engine.pass(other, FilterState()))
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
    fun hideMineDropsMarkedKeysAndKeepsTheRest() {
        val filter = FilterState(hideMine = true)
        val mine = setOf(labeled.key)
        assertFalse(engine.pass(labeled, filter, mineKeys = mine))
        assertTrue(engine.pass(other, filter, mineKeys = mine))
        assertTrue(engine.pass(labeled, FilterState(), mineKeys = mine))
    }

    @Test
    fun hideRandomizedBleDropsLocalBitBluetoothAndKeepsWifi() {
        val hide = FilterState(hideRandomizedBle = true)
        val stable = labeled.copy(randomized = false)
        val wifi = labeled.copy(
            key = "WIFI:AA:BB:CC:DD:EE:FF",
            kind = RadioKind.WIFI,
            randomized = true,
        )
        // AirTag C5:5F:72:12:DF:24. Android calls it random. The local bit is clear.
        val airtag = labeled.copy(
            key = "BLE:C5:5F:72:12:DF:24",
            mac = "C5:5F:72:12:DF:24",
            randomized = false,
            facts = RadioFacts(addressType = "Random"),
        )
        assertFalse(MacUtil.isRandomized(airtag.mac))
        assertFalse(engine.pass(labeled, hide))
        assertFalse(engine.pass(airtag, hide))
        assertTrue(engine.pass(airtag.copy(facts = RadioFacts(addressType = "Public")), hide))
        assertTrue(engine.pass(stable, hide))
        assertTrue(engine.pass(wifi, hide))
        assertTrue(engine.pass(wifi.copy(facts = RadioFacts(addressType = "Random")), hide))
        assertTrue(engine.pass(labeled, FilterState()))
        assertTrue(engine.pass(airtag, FilterState()))
        assertTrue(hide.whoStaysOn())
        assertFalse(hide.clearWhoStays().hideRandomizedBle)
    }

    @Test
    fun hideRandomizedBleDropsAnExposureNotificationAddress() {
        val hide = FilterState(hideRandomizedBle = true)
        val payload = "00".repeat(20)
        val local = labeled.copy(
            serviceUuids = listOf("FD6F"),
            facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FD6F", payload))),
        )
        val random = local.copy(
            key = "BLE:C5:5F:72:12:DF:24",
            mac = "C5:5F:72:12:DF:24",
            randomized = false,
            facts = RadioFacts(
                addressType = "Random",
                serviceData = listOf(ServiceDataRecord("FD6F", payload)),
            ),
        )
        val stock = DefaultCatalog.fleets()
        val matcher = SignatureEngine()
        assertTrue(
            "fleet-exposure-notification" in matcher.match(listOf(local), stock).getValue(local.key),
        )
        assertTrue(
            "fleet-exposure-notification" in matcher.match(listOf(random), stock).getValue(random.key),
        )
        assertFalse(engine.pass(local, hide))
        assertFalse(engine.pass(random, hide))
        assertTrue(engine.pass(local, FilterState()))
        assertTrue(engine.pass(random, FilterState()))
    }

    @Test
    fun hideRandomizedBleStaysAndInOrLogic() {
        val filter = FilterState(
            hideRandomizedBle = true,
            logic = FilterLogic.OR,
            nameQuery = "anything",
        )
        val airtag = labeled.copy(
            name = "anything",
            randomized = false,
            facts = RadioFacts(addressType = "Random"),
        )
        assertFalse(engine.pass(labeled.copy(name = "anything"), filter))
        assertFalse(engine.pass(airtag, filter))
        assertTrue(
            engine.pass(
                labeled.copy(key = "WIFI:AA:BB:CC:DD:EE:FF", kind = RadioKind.WIFI, name = "anything"),
                filter,
            ),
        )
    }

    @Test
    fun hideMineStaysAndInOrLogic() {
        val filter = FilterState(
            hideMine = true,
            logic = FilterLogic.OR,
            nameQuery = "anything",
        )
        val named = labeled.copy(name = "anything")
        assertFalse(engine.pass(named, filter, mineKeys = setOf(labeled.key)))
        assertTrue(engine.pass(other.copy(name = "anything"), filter, mineKeys = setOf(labeled.key)))
    }

    @Test
    fun sectionSwitchFollowsItsOptions() {
        val fresh = FilterState()
        assertFalse(fresh.whoStaysOn())
        assertFalse(fresh.signaturesOn())
        assertFalse(fresh.fineOn())
        assertFalse(fresh.useClassFilter)
        val picked = FilterState(hideMine = true, includeSignatures = true, rssiMin = -70)
        assertTrue(picked.whoStaysOn())
        assertTrue(picked.signaturesOn())
        assertTrue(picked.fineOn())
        assertFalse(picked.clearWhoStays().hideMine)
        assertFalse(picked.clearSelectedSignatures().includeSignatures)
        assertTrue(picked.clearSelectedSignatures().includeFleetIds.isEmpty())
        assertEquals(-100, picked.clearFine().rssiMin)
        assertFalse(picked.clearClasses().useClassFilter)
    }

    @Test
    fun classHideNeedsTheSectionOn() {
        val filter = FilterState(
            useClassFilter = false,
            excludeClasses = true,
            classes = setOf(SignatureClass.SURVEILLANCE),
        )
        val flock = signed.copy(fleetIds = setOf("fleet-flock"))
        val byClass = mapOf("fleet-flock" to SignatureClass.SURVEILLANCE)
        assertTrue(engine.pass(flock, filter, classByFleetId = byClass))
    }

    @Test
    fun selectedSignaturesAreOneList() {
        val show = FilterState(
            includeSignatures = true,
            includeFleetIds = setOf("fleet-airtag"),
        )
        assertTrue(engine.pass(signed, show))
        assertFalse(engine.pass(other, show))

        val hide = FilterState(
            excludeSignatures = true,
            fleetIds = setOf("fleet-airtag"),
        )
        assertFalse(engine.pass(signed, hide))
        assertTrue(engine.pass(other, hide))

        val both = FilterState(
            includeSignatures = true,
            excludeSignatures = true,
            includeFleetIds = setOf("fleet-airtag"),
            fleetIds = setOf("fleet-airtag"),
        )
        assertTrue(both.signatureShowOn())
        assertFalse(both.signatureHideOn())
        assertTrue(engine.pass(signed, both))
        assertFalse(engine.pass(other, both))

        val orBoth = both.copy(logic = FilterLogic.OR, nameQuery = "nope")
        assertTrue(engine.pass(signed, orBoth))
        assertFalse(engine.pass(other, orBoth))
    }

    @Test
    fun signatureChipsShareOnePickList() {
        val off = FilterState()
        val shown = off.toggleSignaturePick("fleet-airtag", checked = true)
        assertTrue(shown.signatureShowOn())
        assertFalse(shown.signatureHideOn())
        assertEquals(setOf("fleet-airtag"), shown.includeFleetIds)
        assertEquals(setOf("fleet-airtag"), shown.fleetIds)

        val hidden = shown.toggleSignatureHide()
        assertFalse(hidden.signatureShowOn())
        assertTrue(hidden.signatureHideOn())
        assertEquals(setOf("fleet-airtag"), hidden.selectedSignatureIds())
        assertFalse(engine.pass(signed, hidden))

        val kept = hidden.toggleSignatureHide()
        assertFalse(kept.signaturesOn())
        assertEquals(setOf("fleet-airtag"), kept.selectedSignatureIds())
        assertTrue(engine.pass(signed, kept))

        val again = kept.toggleSignatureShow()
        assertTrue(again.signatureShowOn())
        assertTrue(engine.pass(signed, again))
        assertFalse(engine.pass(other, again))

        val old = FilterState(
            includeSignatures = true,
            excludeSignatures = true,
            includeFleetIds = setOf("fleet-airtag"),
            fleetIds = setOf("fleet-tile"),
        )
        assertEquals(setOf("fleet-airtag"), old.selectedSignatureIds())
        val unified = old.toggleSignatureHide()
        assertTrue(unified.signatureHideOn())
        assertEquals(setOf("fleet-airtag"), unified.includeFleetIds)
        assertEquals(setOf("fleet-airtag"), unified.fleetIds)
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
