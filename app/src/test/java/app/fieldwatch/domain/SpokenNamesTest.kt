package app.fieldwatch.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenNamesTest {
    private val compex = Fleet(id = "fleet-compex", name = "Compex", kind = SignatureClass.LAW_ENFORCEMENT)
    private val axon = Fleet(id = "fleet-axon", name = "Axon", kind = SignatureClass.LAW_ENFORCEMENT)
    private val fire = listOf(SpokenName("sn-1", "fleet-compex", "FDEMS", "Fire Department"))

    @Test
    fun oneNameCoversEveryAddressWithThatSsid() {
        val one = radio("WIFI:11:22:33:44:55:01", "11:22:33:44:55:01", "FDEMS", linkedSetOf("fleet-compex"))
        val two = radio("WIFI:11:22:33:44:55:02", "11:22:33:44:55:02", "FDEMS", linkedSetOf("fleet-compex"))
        val fleets = listOf(compex)
        assertEquals("public safety, Fire Department", spokenWatchPhrase(one, fleets, AlertVoiceWhat.BOTH, spokenNames = fire))
        assertEquals("public safety, Fire Department", spokenWatchPhrase(two, fleets, AlertVoiceWhat.BOTH, spokenNames = fire))
        assertEquals("Fire Department", spokenWatchPhrase(one, fleets, AlertVoiceWhat.SIGNATURE, spokenNames = fire))
        assertEquals("public safety", spokenWatchPhrase(one, fleets, AlertVoiceWhat.CLASS, spokenNames = fire))
    }

    @Test
    fun aDifferentSsidStaysTheSignature() {
        val guest = radio("WIFI:11:22:33:44:55:01", "11:22:33:44:55:01", "FDEMS-Guest", linkedSetOf("fleet-compex"))
        val lower = guest.copy(name = "fdems")
        val blank = guest.copy(name = " ")
        val macName = guest.copy(name = guest.mac)
        val fleets = listOf(compex)
        assertEquals("public safety, Compex", spokenWatchPhrase(guest, fleets, AlertVoiceWhat.BOTH, spokenNames = fire))
        assertEquals("Fire Department", spokenWatchPhrase(lower, fleets, AlertVoiceWhat.SIGNATURE, spokenNames = fire))
        assertEquals("Compex", spokenWatchPhrase(blank, fleets, AlertVoiceWhat.SIGNATURE, spokenNames = fire))
        assertEquals("Compex", spokenWatchPhrase(macName, fleets, AlertVoiceWhat.SIGNATURE, spokenNames = fire))
        assertNull(SpokenNames.find(fire, "fleet-compex", "FDEMS-Guest"))
        assertEquals("Fire Department", SpokenNames.find(fire, "fleet-compex", "fdems")?.label)
    }

    @Test
    fun spokenNameFollowsTheSignatureThatIsSaid() {
        val device = radio("WIFI:11:22:33:44:55:01", "11:22:33:44:55:01", "FDEMS", linkedSetOf("fleet-axon", "fleet-compex"))
        val fleets = listOf(axon, compex)
        assertEquals("public safety, Axon", spokenWatchPhrase(device, fleets, AlertVoiceWhat.BOTH, spokenNames = fire))
        val watched = WatchTarget(id = "w", fleetId = "fleet-compex", label = "Compex")
        assertEquals(
            "public safety, Fire Department",
            spokenWatchPhrase(device, fleets, AlertVoiceWhat.BOTH, watched, fire),
        )
    }

    @Test
    fun anIndividualCustomNameReplacesTheWholeAnnouncement() {
        val device = radio("WIFI:11:22:33:44:55:01", "11:22:33:44:55:01", "FDEMS", linkedSetOf("fleet-compex"))
        val fleetWatch = WatchTarget(id = "w-fleet", fleetId = "fleet-compex", label = "Compex")
        val named = WatchTarget(id = "w-radio", deviceKey = device.key, label = "Engine 4")
        assertEquals(
            "Engine 4",
            spokenWatchPhrase(device, listOf(compex), AlertVoiceWhat.BOTH, fleetWatch, fire, listOf(named)),
        )
        assertEquals(
            "Engine 4",
            spokenWatchPhrase(device, listOf(compex), AlertVoiceWhat.CLASS, fleetWatch, fire, listOf(named)),
        )
        val macLabel = named.copy(label = device.mac)
        assertEquals(
            "public safety, Fire Department",
            spokenWatchPhrase(device, listOf(compex), AlertVoiceWhat.BOTH, fleetWatch, fire, listOf(macLabel)),
        )
    }

    @Test
    fun saveUpdatesTheSameSsidAndKeepsAnother() {
        val once = SpokenNames.save(emptyList(), "fleet-compex", "FDEMS", "Fire Department")
        val renamed = SpokenNames.save(once, "fleet-compex", " fdems ", "Fire Dept")
        assertEquals(1, renamed.size)
        assertEquals("Fire Dept", renamed.single().label)
        val both = SpokenNames.save(renamed, "fleet-compex", "FDEMS-Guest", "Fire Department")
        assertEquals(2, both.size)
        assertEquals(both, SpokenNames.save(both, "fleet-compex", "   ", "Nope"))
        assertEquals(both, SpokenNames.save(both, "fleet-compex", "FDEMS", "   "))
        val left = SpokenNames.remove(both, "fleet-compex", "FDEMS")
        assertEquals(listOf("FDEMS-Guest"), left.map { it.networkName })
    }

    @Test
    fun preferredFleetIsOneThatAlreadyHasAName() {
        val ids = listOf("fleet-axon", "fleet-compex")
        assertEquals("fleet-compex", SpokenNames.preferredFleet(ids, fire, "FDEMS"))
        assertEquals("fleet-axon", SpokenNames.preferredFleet(ids, fire, "Other"))
        assertEquals("fleet-compex", SpokenNames.voiceFleet(linkedSetOf("fleet-axon", "fleet-compex"), listOf(
            WatchTarget(id = "w", fleetId = "fleet-compex", label = "Compex"),
        )))
    }

    @Test
    fun oldSettingsAndABackupKeepTheList() {
        val json = Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString(AppSettings.serializer(), "{}")
        assertTrue(old.spokenNames.isEmpty())
        val named = fire.single()
        val pack = SettingsExchange.pack(
            settings = AppSettings(spokenNames = fire),
            filter = FilterState(),
            presets = emptyList(),
            watchlist = emptyList(),
            hiddenPresetIds = emptySet(),
            appVersion = "1.1.37",
            exportedAt = "2026-11-01T00:00:00Z",
        )
        val encoded = SettingsExchange.encode(pack)
        assertTrue(encoded.contains("Fire Department"))
        assertTrue(encoded.contains("FDEMS"))
        val (next, _) = SettingsExchange.apply(PersistedConfig(), SettingsExchange.parse(encoded))
        assertEquals(named, next.settings.spokenNames.single())
    }

    private fun radio(key: String, mac: String, name: String, fleetIds: Set<String>) = Sighting(
        key = key,
        kind = RadioKind.WIFI,
        mac = mac,
        name = name,
        rssi = -40,
        rssiMin = -40,
        rssiMax = -40,
        channel = 6,
        frequencyMhz = 2437,
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
}
