package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SignatureListSortTest {
    private val fleets = listOf(
        fleet("fleet-axon", "Axon", SignatureClass.LAW_ENFORCEMENT),
        fleet("fleet-tesla", "Tesla", SignatureClass.VEHICLE),
        fleet("fleet-airlink", "AirLink", SignatureClass.LAW_ENFORCEMENT),
        fleet("fleet-amazon", "Amazon", SignatureClass.HOME),
    )

    @Test
    fun nameSortIsAlphabetical() {
        assertEquals(
            listOf("AirLink", "Amazon", "Axon", "Tesla"),
            fleets.sortedForCatalog(SignatureListSort.NAME).map { it.name },
        )
    }

    @Test
    fun classSortGroupsByClassLabelThenName() {
        assertEquals(
            listOf("Amazon", "AirLink", "Axon", "Tesla"),
            fleets.sortedForCatalog(SignatureListSort.CLASS).map { it.name },
        )
        val groups = fleets.groupedByClass()
        assertEquals(
            listOf("Home IoT", "Public safety", "Vehicle"),
            groups.map { it.first.label() },
        )
        assertEquals(listOf("AirLink", "Axon"), groups[1].second.map { it.name })
    }

    @Test
    fun labels() {
        assertEquals("Name A–Z", SignatureListSort.NAME.label())
        assertEquals("Class A–Z", SignatureListSort.CLASS.label())
    }

    @Test
    fun speechLabelIsShortAndSpeaksSlashes() {
        assertEquals("finder tags", SignatureClass.FINDER.speechLabel())
        assertEquals("audio", SignatureClass.AUDIO.speechLabel())
        assertEquals("public safety", SignatureClass.LAW_ENFORCEMENT.speechLabel())
        assertEquals("I S P routers", SignatureClass.ISP.speechLabel())
        assertEquals("phones", SignatureClass.PHONE.speechLabel())
        assertEquals("health", SignatureClass.HEALTH.speechLabel())
        assertEquals("Health", SignatureClass.HEALTH.label())
    }

    @Test
    fun spokenWatchClassUsesFirstMatch() {
        val axon = fleet("fleet-axon", "Axon", SignatureClass.LAW_ENFORCEMENT)
        val device = sampleDevice(linkedSetOf("fleet-axon"))
        assertEquals("public safety", spokenWatchClass(device, listOf(axon)))
        assertEquals("unmatched", spokenWatchClass(device.copy(fleetIds = emptySet()), listOf(axon)))
    }

    @Test
    fun spokenWatchPhraseClassSignatureOrBoth() {
        val air = fleet("fleet-airtag", "Apple AirTags", SignatureClass.FINDER)
        val device = sampleDevice(linkedSetOf("fleet-airtag"))
        val fleets = listOf(air)
        assertEquals("finder tags", spokenWatchPhrase(device, fleets, AlertVoiceWhat.CLASS))
        assertEquals("Apple AirTags", spokenWatchPhrase(device, fleets, AlertVoiceWhat.SIGNATURE))
        assertEquals("finder tags, Apple AirTags", spokenWatchPhrase(device, fleets, AlertVoiceWhat.BOTH))
        assertEquals("unmatched", spokenWatchPhrase(device.copy(fleetIds = emptySet()), fleets, AlertVoiceWhat.BOTH))
    }

    @Test
    fun spokenWatchPhrasePrefersWatchedSignatureOnDualChip() {
        val axon = fleet("fleet-axon", "Axon", SignatureClass.LAW_ENFORCEMENT)
        val air = fleet("fleet-airtag", "Apple AirTags", SignatureClass.FINDER)
        val device = sampleDevice(linkedSetOf("fleet-axon", "fleet-airtag"))
        val target = WatchTarget(id = "w-air", fleetId = "fleet-airtag", label = "Apple AirTags")
        val fleets = listOf(axon, air)
        assertEquals("finder tags", spokenWatchPhrase(device, fleets, AlertVoiceWhat.CLASS, target))
        assertEquals("Apple AirTags", spokenWatchPhrase(device, fleets, AlertVoiceWhat.SIGNATURE, target))
        assertEquals(
            "public safety, Axon",
            spokenWatchPhrase(device, fleets, AlertVoiceWhat.BOTH),
        )
    }

    @Test
    fun spokenWatchPhraseUsesRadioBookmarkName() {
        val air = fleet("fleet-airtag", "Apple AirTags", SignatureClass.FINDER)
        val device = sampleDevice(linkedSetOf("fleet-airtag"))
        val radio = WatchTarget(
            id = "w-radio",
            deviceKey = device.key,
            label = "Peter's Mesh Node",
        )
        assertEquals(
            "Peter's Mesh Node",
            spokenWatchPhrase(device, listOf(air), AlertVoiceWhat.BOTH, radio),
        )
        val unmatched = device.copy(fleetIds = emptySet())
        assertEquals(
            "Peter's Mesh Node",
            spokenWatchPhrase(unmatched, listOf(air), AlertVoiceWhat.CLASS, radio),
        )
    }

    @Test
    fun speakableWatchNameStripsSlashes() {
        assertEquals("Ray-Ban Meta glasses", speakableWatchName("Ray-Ban / Meta glasses"))
        assertEquals("Marauder Deauther", speakableWatchName("Marauder / Deauther"))
    }

    @Test
    fun testWatchPhraseMatchesFinderExample() {
        assertEquals("finder tags", testWatchPhrase(AlertVoiceWhat.CLASS))
        assertEquals("Apple AirTags", testWatchPhrase(AlertVoiceWhat.SIGNATURE))
        assertEquals("finder tags, Apple AirTags", testWatchPhrase(AlertVoiceWhat.BOTH))
    }

    private fun sampleDevice(fleetIds: Set<String>) = Sighting(
        key = "BLE:AA:BB:CC:DD:EE:01",
        kind = RadioKind.BLE,
        mac = "AA:BB:CC:DD:EE:01",
        name = "Axon",
        rssi = -40,
        rssiMin = -40,
        rssiMax = -40,
        channel = 0,
        frequencyMhz = 0,
        vendor = null,
        randomized = false,
        hiddenSsid = false,
        serviceUuids = emptyList(),
        manufacturerId = null,
        manufacturerDataHex = "",
        rawHex = "",
        extras = "",
        firstSeen = 1L,
        lastSeen = 1L,
        hitCount = 1,
        fleetIds = fleetIds,
        rssiHistory = emptyList(),
        presence = emptyList(),
    )

    private fun fleet(id: String, name: String, kind: SignatureClass) = Fleet(
        id = id,
        name = name,
        kind = kind,
    )
}
