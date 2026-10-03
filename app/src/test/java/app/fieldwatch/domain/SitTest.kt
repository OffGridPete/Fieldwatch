package app.fieldwatch.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SitTest {
    private val axon = Fleet(
        id = "fleet-axon",
        name = "Axon",
        kind = SignatureClass.LAW_ENFORCEMENT,
        attentionNote = "Body-worn, in-car, dock, or TASER.",
    )
    private val remote = Fleet(
        id = "fleet-remote-id",
        name = "Remote ID",
        kind = SignatureClass.DRONE,
    )
    private val fleets = listOf(axon, remote)

    @Test
    fun blankNameBecomesTimestamp() {
        val name = Sit.resolveName("  ", 0L, java.util.Locale.US)
        assertTrue(name.isNotBlank())
        assertFalse(name.isBlank())
        assertEquals("parking lot", Sit.resolveName(" parking lot ", 0L))
        assertEquals(Sit.NAME_MAX, Sit.clipName("x".repeat(80)).length)
    }

    @Test
    fun dropWarningNamesOldestWhenAtCap() {
        assertNull(Sit.dropWarning(emptyList()))
        val nine = (1..9).map { i ->
            SitSummary(id = "$i", name = "s$i", startAt = i.toLong())
        }
        assertNull(Sit.dropWarning(nine))
        val ten = listOf(SitSummary("new", "newest", 20L)) +
            nine +
            listOf(SitSummary("old", "oldest", 1L))
        val warn = Sit.dropWarning(ten)
        assertNotNull(warn)
        assertTrue(warn!!.contains("oldest"))
        assertTrue(warn.contains("10"))
    }

    @Test
    fun startCopiesCurrentlyHeardAndSkipsGone() {
        val cam = radio("BLE:AA:AA:AA:AA:AA:01", fleetIds = setOf("fleet-axon"), firstSeen = 1_000L)
        val gone = radio("BLE:AA:AA:AA:AA:AA:02", gone = true, lastSeen = 1_000L)
        val session = SitSession.start(
            name = "lot",
            now = 10_000L,
            heard = listOf(cam, gone),
            fleets = fleets,
            watchDeviceKeys = emptySet(),
            watchedFleetIds = emptySet(),
        )
        assertEquals("lot", session.summary.name)
        assertEquals(1, session.radioCount)
        val row = session.sightings().single()
        assertEquals(cam.key, row.key)
        assertEquals(1_000L, row.firstSeen)
        assertTrue(row.attentionNotes(fleets).isNotEmpty())
    }

    @Test
    fun arrivedAfterStartVsAlreadyHere() {
        val here = radio("BLE:AA:AA:AA:AA:AA:01", firstSeen = 1_000L, lastSeen = 10_000L)
        val session = SitSession.start(
            name = "walk",
            now = 10_000L,
            heard = listOf(here),
            fleets = fleets,
            watchDeviceKeys = emptySet(),
            watchedFleetIds = emptySet(),
        )
        val later = radio("BLE:BB:BB:BB:BB:BB:02", firstSeen = 11_000L, lastSeen = 11_000L)
        session.ingest(later, fleets, emptySet(), emptySet())
        val already = session.sightings().first { it.key == here.key }
        val arrived = session.sightings().first { it.key == later.key }
        assertTrue(already.firstSeen < session.summary.startAt)
        assertTrue(arrived.firstSeen >= session.summary.startAt)
    }

    @Test
    fun unnamedBleEvictsBeforeExtraAttention() {
        val session = SitSession.start(
            name = "plaza",
            now = 1L,
            heard = emptyList(),
            fleets = fleets,
            watchDeviceKeys = emptySet(),
            watchedFleetIds = emptySet(),
        )
        val cam = radio("BLE:CC:CC:CC:CC:CC:01", fleetIds = setOf("fleet-axon"), lastSeen = 1L)
        session.ingest(cam, fleets, emptySet(), emptySet())
        repeat(Sit.RADIO_CAP) { i ->
            val mac = "AA:AA:AA:AA:%02X:%02X".format(i / 256, i % 256)
            session.ingest(
                radio("BLE:$mac", mac = mac, lastSeen = i.toLong() + 2, fleetIds = emptySet()),
                fleets,
                emptySet(),
                emptySet(),
            )
        }
        assertTrue(session.radioCount <= Sit.RADIO_CAP)
        assertTrue(session.sightings().any { it.key == cam.key })
        assertTrue(session.atCap)
    }

    @Test
    fun tightMemoryRefusesNewUnpinnedKeepsPinned() {
        val session = SitSession.start(
            name = "tight",
            now = 1L,
            heard = emptyList(),
            fleets = fleets,
            watchDeviceKeys = emptySet(),
            watchedFleetIds = emptySet(),
        )
        val clutter = radio("BLE:AA:AA:AA:AA:AA:01", lastSeen = 1L)
        val later = radio("BLE:AA:AA:AA:AA:AA:01", lastSeen = 5L, firstSeen = 1L)
        val other = radio("BLE:BB:BB:BB:BB:BB:02", lastSeen = 2L)
        val cam = radio("BLE:CC:CC:CC:CC:CC:03", fleetIds = setOf("fleet-axon"), lastSeen = 3L)
        assertTrue(session.ingest(clutter, fleets, emptySet(), emptySet(), tight = false))
        assertTrue(session.ingest(later, fleets, emptySet(), emptySet(), tight = true))
        assertFalse(session.ingest(other, fleets, emptySet(), emptySet(), tight = true))
        assertTrue(session.ingest(cam, fleets, emptySet(), emptySet(), tight = true))
        val keys = session.sightings().map { it.key }.toSet()
        assertTrue(clutter.key in keys)
        assertEquals(5L, session.sightings().first { it.key == clutter.key }.lastSeen)
        assertFalse(other.key in keys)
        assertTrue(cam.key in keys)
    }

    @Test
    fun payloadAndWatchlistArePinned() {
        val session = SitSession.start(
            name = "air",
            now = 1L,
            heard = emptyList(),
            fleets = fleets,
            watchDeviceKeys = setOf("BLE:WATCH"),
            watchedFleetIds = emptySet(),
        )
        val drone = radio(
            "BLE:DD:DD:DD:DD:DD:01",
            fleetIds = setOf("fleet-remote-id"),
            lastSeen = 1L,
            payloadLat = 40.0,
            payloadLon = -74.0,
        )
        val named = radio("BLE:WATCH", lastSeen = 1L)
        session.ingest(drone, fleets, setOf("BLE:WATCH"), emptySet())
        session.ingest(named, fleets, setOf("BLE:WATCH"), emptySet())
        repeat(Sit.RADIO_CAP) { i ->
            val mac = "BB:BB:BB:BB:%02X:%02X".format(i / 256, i % 256)
            session.ingest(
                radio("BLE:$mac", mac = mac, lastSeen = i.toLong() + 2),
                fleets,
                setOf("BLE:WATCH"),
                emptySet(),
            )
        }
        val keys = session.sightings().map { it.key }.toSet()
        assertTrue(drone.key in keys)
        assertTrue(named.key in keys)
    }

    @Test
    fun debriefWindowUsesSitBounds() {
        val cam = radio("BLE:AA:AA:AA:AA:AA:01", fleetIds = setOf("fleet-axon"), firstSeen = 5_000L, lastSeen = 20_000L)
        val window = DebriefWindow(10_000L, 30_000L, "lot")
        val doc = DebriefReport.document(
            devices = listOf(cam),
            fleets = fleets,
            settings = AppSettings(tagLocation = false),
            operatorPath = emptyList(),
            now = 40_000L,
            window = window,
        )
        assertTrue(doc.heading.contains("lot"))
        assertTrue(doc.windowLine.contains("sit lot"))
        assertTrue(doc.disclaimer.contains("named sit"))
        assertFalse(doc.windowLine.contains("last 15 minutes"))
    }

    @Test
    fun debriefListsObserverNotesSection() {
        val now = System.currentTimeMillis()
        val van = radio("WIFI:AA:AA:AA:AA:AA:01", firstSeen = now - 60_000L, lastSeen = now)
        val other = radio("WIFI:AA:AA:AA:AA:AA:02", firstSeen = now - 60_000L, lastSeen = now)
        val doc = DebriefReport.document(
            devices = listOf(van, other),
            fleets = fleets,
            settings = AppSettings(tagLocation = false),
            operatorPath = emptyList(),
            now = now,
            customNames = mapOf(van.key to "fleet van"),
            observerNotes = mapOf(van.key to "lot B north"),
        )
        val titles = doc.sections.map { it.title }
        assertEquals("Where you were", titles[1])
        assertEquals("Observer notes", titles[2])
        val section = doc.sections.single { it.title == "Observer notes" }
        assertTrue(section.body.contains("fleet van"))
        assertTrue(section.body.contains("lot B north"))
        assertTrue(section.body.contains("AA:AA:AA:AA:AA:01"))
        assertFalse(section.body.contains("AA:AA:AA:AA:AA:02"))
        assertFalse(doc.toPlainText().contains("Observer: lot B north"))
    }

    @Test
    fun debriefNotesAFloodWithoutRaisingTracking() {
        val now = 1_700_000_000_000L
        val burst = FloodBurst(
            at = now - 60_000L,
            popupCount = 8,
            nameCount = 0,
            families = listOf("Apple proximity pairing"),
            medianRssi = -48,
        )
        val outside = burst.copy(at = now - 2 * 60 * 60_000L, popupCount = 99)
        val doc = DebriefReport.document(
            devices = emptyList(),
            fleets = fleets,
            settings = AppSettings(tagLocation = false),
            operatorPath = emptyList(),
            now = now,
            window = DebriefWindow(now - 15 * 60_000L, now, "lot"),
            floods = listOf(outside, burst),
        )
        val text = doc.toPlainText()
        val section = doc.sections.single { it.title == "Flood" }
        assertTrue(text.contains("FLOOD"))
        assertTrue(section.body.contains(FloodBurst.INTRO))
        assertTrue(section.body.contains("Pairing flood"))
        assertTrue(section.body.contains("8 new addresses: Apple proximity pairing"))
        assertTrue(section.body.contains("about -48 dBm"))
        assertFalse(section.body.contains("99 new"))
        assertFalse(section.alert)
        assertFalse(doc.trackingAlert)
        assertTrue(doc.sections.none { it.title == "Possible trackers with you" })
        assertTrue(doc.sections.none { it.title == "Possible tail" })
        val titles = doc.sections.map { it.title }
        assertTrue(titles.indexOf("Flood") > titles.indexOf("Anomalies"))
        assertTrue(titles.indexOf("Flood") < titles.indexOf("Privacy"))
    }

    @Test
    fun debriefUsesTheWifiFloodIntroAndLeavesThoseAddressesOut() {
        val now = 1_700_000_000_000L
        val window = DebriefWindow(now - 15 * 60_000L, now, "lobby")
        val real = radio(
            "WIFI:AC:23:3F:11:22:33",
            kind = RadioKind.WIFI,
            firstSeen = now - 60_000L,
            lastSeen = now,
            randomized = false,
            name = "FrontDesk",
        )
        val floodA = radio(
            "WIFI:02:00:00:00:00:11",
            kind = RadioKind.WIFI,
            firstSeen = now - 30_000L,
            lastSeen = now - 30_000L,
            name = "SpamOne",
        )
        val burst = FloodBurst(
            at = now - 30_000L,
            popupCount = 15,
            nameCount = 0,
            medianRssi = -34,
            keys = listOf(floodA.key),
            wifi = true,
        )
        val doc = DebriefReport.document(
            devices = listOf(real, floodA),
            fleets = fleets,
            settings = AppSettings(tagLocation = false),
            operatorPath = emptyList(),
            now = now,
            window = window,
            customNames = mapOf(real.key to "FrontDesk"),
            floods = listOf(burst),
        )
        val section = doc.sections.single { it.title == "Flood" }
        assertTrue(section.body.contains(FloodBurst.WIFI_INTRO))
        assertFalse(section.body.contains(FloodBurst.INTRO))
        assertTrue(section.body.contains("Wi-Fi beacon flood"))
        assertTrue(section.body.contains("15 new names"))
        assertTrue(section.body.contains("1 address from this burst is left out of the counts and lists below."))
        assertEquals("1", doc.meta.first { it.first == "Radios" }.second)
        assertFalse(doc.toPlainText().contains("02:00:00:00:00:11"))
        assertTrue(doc.toPlainText().contains("FrontDesk"))
    }

    @Test
    fun noteFloodsKeepsWifiBesideBluetoothAtTheSameTime() {
        val session = SitSession.start(
            name = "lot",
            now = 1_000L,
            heard = emptyList(),
            fleets = fleets,
            watchDeviceKeys = emptySet(),
            watchedFleetIds = emptySet(),
        )
        val ble = FloodBurst(at = 5_000L, popupCount = 6, nameCount = 0)
        val wifi = FloodBurst(
            at = 5_000L,
            popupCount = 15,
            nameCount = 0,
            keys = listOf("WIFI:02:00:00:00:00:01"),
            wifi = true,
        )
        session.noteFloods(listOf(ble, wifi))
        session.noteFloods(listOf(wifi.copy(popupCount = 20)))
        val kept = session.snapshot().floods
        assertEquals(2, kept.size)
        assertEquals(6, kept.single { !it.wifi }.popupCount)
        assertEquals(20, kept.single { it.wifi }.popupCount)
        assertEquals(listOf("WIFI:02:00:00:00:00:01"), kept.single { it.wifi }.keys)
    }

    @Test
    fun debriefLeavesCountedFloodAddressesOutOfTheCounts() {
        val now = 1_700_000_000_000L
        val window = DebriefWindow(now - 15 * 60_000L, now, "lot")
        val real = radio(
            "BLE:AC:23:3F:11:22:33",
            firstSeen = now - 60_000L,
            lastSeen = now,
            randomized = false,
            name = "Checkout beacon",
        )
        val floodA = radio(
            "BLE:02:00:00:00:00:01",
            fleetIds = setOf("fleet-axon"),
            firstSeen = now - 30_000L,
            lastSeen = now - 30_000L,
        ).copy(facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FE2C", "AABBCC"))))
        val floodB = radio(
            "BLE:02:00:00:00:00:02",
            fleetIds = setOf("fleet-axon"),
            firstSeen = now - 30_000L,
            lastSeen = now - 30_000L,
        ).copy(facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FE2C", "CCDDEE"))))
        val burst = FloodBurst(
            at = now - 30_000L,
            popupCount = 8,
            nameCount = 0,
            families = listOf("Fast Pair"),
            medianRssi = -47,
            keys = listOf(floodA.key, floodB.key),
        )
        val earlier = FloodBurst(
            at = now - 2 * 60 * 60_000L,
            popupCount = 6,
            nameCount = 0,
            keys = listOf(real.key),
        )
        val doc = DebriefReport.document(
            devices = listOf(real, floodA, floodB),
            fleets = fleets,
            settings = AppSettings(tagLocation = false),
            operatorPath = emptyList(),
            now = now,
            window = window,
            customNames = mapOf(real.key to "Checkout beacon"),
            floods = listOf(earlier, burst),
        )
        val text = doc.toPlainText()
        assertEquals("1", doc.meta.first { it.first == "Radios" }.second)
        assertTrue(text.contains("Heard 1 advertiser(s)"))
        assertTrue(text.contains("First seen in this window: 1"))
        assertTrue(text.contains("Checkout beacon"))
        assertTrue(text.contains("2 addresses from this burst are left out of the counts and lists below."))
        assertTrue(doc.sections.single { it.title == "Signature hits" }.body.contains("None in this window."))
        assertFalse(text.contains("Google Fast Pair in pairing mode"))
        assertFalse(text.contains("02:00:00:00:00:01"))
        assertFalse(text.contains("02:00:00:00:00:02"))
        assertFalse(text.contains("Axon"))
    }

    @Test
    fun oldSitJsonWithoutFloodsDecodesEmptyAndANotedBurstRoundTrips() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val legacy = json.decodeFromString<SitFile>(
            """{"format":"fieldwatch-sit","formatVersion":1,"summary":{"id":"a","name":"lot","startAt":1000}}""",
        )
        assertTrue(legacy.floods.isEmpty())
        val session = SitSession.start(
            name = "lot",
            now = 1_000L,
            heard = emptyList(),
            fleets = fleets,
            watchDeviceKeys = emptySet(),
            watchedFleetIds = emptySet(),
        )
        val before = FloodBurst(500L, popupCount = 6, nameCount = 0, families = listOf("Fast Pair"))
        val during = FloodBurst(
            at = 5_000L,
            popupCount = 6,
            nameCount = 0,
            families = listOf("Apple proximity pairing"),
            medianRssi = -48,
            keys = listOf("BLE:02:00:00:00:00:01", "BLE:02:00:00:00:00:02"),
        )
        session.noteFloods(listOf(before, during))
        val louder = during.copy(popupCount = 9)
        session.noteFloods(listOf(louder))
        val kept = session.snapshot().floods
        assertEquals(1, kept.size)
        assertEquals(5_000L, kept.single().at)
        assertEquals(9, kept.single().popupCount)
        assertEquals(listOf("BLE:02:00:00:00:00:01", "BLE:02:00:00:00:00:02"), kept.single().keys)
        session.noteFloods((1..41).map { FloodBurst(at = 1_000L + it, popupCount = 6, nameCount = 0) })
        val capped = session.snapshot().floods
        assertEquals(40, capped.size)
        assertEquals(1_000L + 2, capped.first().at)
        val back = json.decodeFromString<SitFile>(json.encodeToString(session.snapshot()))
        assertEquals(capped, back.floods)
        session.end(50_000L, fleets)
        session.noteFloods(listOf(FloodBurst(60_000L, popupCount = 6, nameCount = 0)))
        assertEquals(40, session.snapshot().floods.size)
        assertTrue(session.snapshot().floods.none { it.at == 60_000L })
    }

    @Test
    fun debriefOmitsUnmatchedRandomBleFromListsKeepsCounts() {
        val now = System.currentTimeMillis()
        val rand = radio(
            "BLE:AA:AA:AA:11:22:33",
            firstSeen = now - 14 * 60_000L,
            lastSeen = now,
            rssi = -40,
        )
        val named = radio(
            "BLE:BB:BB:BB:11:22:33",
            fleetIds = setOf("fleet-axon"),
            firstSeen = now - 60_000L,
            lastSeen = now,
            rssi = -55,
        )
        val text = DebriefReport.document(
            devices = listOf(rand, named),
            fleets = fleets,
            settings = AppSettings(tagLocation = false),
            operatorPath = emptyList(),
            now = now,
        ).toPlainText()
        assertTrue(text.contains("2 BLE advertisers"))
        assertTrue(text.contains("1 with randomized addresses") || text.contains("randomized"))
        assertTrue(text.contains("Unmatched rotating BLE omitted"))
        assertTrue(text.contains("Public safety  1"))
        assertTrue(text.contains("Unmatched  1"))
        assertFalse(text.contains("Notable BLE:"))
        assertFalse(text.contains("AA:AA:AA:11:22:33"))
        assertTrue(text.contains("BB:BB:BB:11:22:33"))
        val unmatchedOnly = DebriefReport.document(
            devices = listOf(rand, named),
            fleets = fleets,
            settings = AppSettings(tagLocation = false, debriefShowUnmatchedRandomBle = true),
            operatorPath = emptyList(),
            now = now,
        ).toPlainText()
        assertFalse(unmatchedOnly.contains("AA:AA:AA:11:22:33"))
        val shown = DebriefReport.document(
            devices = listOf(rand, named),
            fleets = fleets,
            settings = AppSettings(
                tagLocation = false,
                debriefShowUnmatchedRandomBle = true,
                debriefShowAllRadios = true,
            ),
            operatorPath = emptyList(),
            now = now,
        ).toPlainText()
        assertTrue(shown.contains("Notable BLE:"))
        assertTrue(shown.contains("AA:AA:AA:11:22:33"))
    }

    @Test
    fun rollingDebriefUnchangedWithoutSit() {
        val cam = radio("BLE:AA:AA:AA:AA:AA:01", firstSeen = 1L, lastSeen = System.currentTimeMillis())
        val doc = DebriefReport.document(
            devices = listOf(cam),
            fleets = fleets,
            settings = AppSettings(tagLocation = false),
            operatorPath = emptyList(),
        )
        assertEquals("FIELDWATCH FIELD DEBRIEF", doc.heading)
        assertTrue(doc.windowLine.contains("last 15 minutes"))
    }

    @Test
    fun sitKeepsLiveDecodeAndReplacesItWhenTheNextHearHasOne() {
        val separated = LiveDecodeChip("separated", emphasis = true, note = "day")
        val near = LiveDecodeChip("near owner", emphasis = false, note = "joined")
        val session = SitSession.start(
            name = "walk",
            now = 1_000L,
            heard = listOf(radio("BLE:AA:AA:AA:AA:AA:09").copy(liveDecode = listOf(separated))),
            fleets = fleets,
            watchDeviceKeys = emptySet(),
            watchedFleetIds = emptySet(),
        )
        assertEquals("separated", session.sightings().single().liveDecode.single().text)
        session.ingest(
            radio("BLE:AA:AA:AA:AA:AA:09", lastSeen = 2_000L, rssi = -40),
            fleets,
            emptySet(),
            emptySet(),
        )
        assertEquals("day", session.sightings().single().liveDecode.single().note)
        session.ingest(
            radio("BLE:AA:AA:AA:AA:AA:09", lastSeen = 3_000L).copy(liveDecode = listOf(near)),
            fleets,
            emptySet(),
            emptySet(),
        )
        val kept = session.sightings().single().liveDecode.single()
        assertEquals("near owner", kept.text)
        assertEquals("joined", kept.note)
    }

    @Test
    fun debriefQuotesCatalogSentenceAndKeepsRotationLineWithoutOne() {
        val now = 120_000L
        val path = listOf(
            GpsSample(0L, 28.0, -81.0, -50),
            GpsSample(60_000L, 28.0012, -81.0, -50),
        )
        val dult = listOf(Fleet(id = "fleet-dult", name = "DULT tracker", kind = SignatureClass.FINDER))
        val day = "Separated from its owner. The address can hold still for about a day."
        val separated = radio(
            "BLE:11:22:33:44:55:66",
            fleetIds = setOf("fleet-dult"),
            firstSeen = 0L,
            lastSeen = now,
            rssi = -50,
            randomized = false,
        ).copy(
            gpsTrail = path,
            liveDecode = listOf(LiveDecodeChip("separated", emphasis = true, note = day)),
        )
        val plain = radio(
            "BLE:11:22:33:44:55:77",
            fleetIds = setOf("fleet-dult"),
            firstSeen = 0L,
            lastSeen = now,
            rssi = -50,
            randomized = false,
        ).copy(gpsTrail = path)
        val text = DebriefReport.document(
            devices = listOf(separated, plain),
            fleets = dult,
            settings = AppSettings(tagLocation = true),
            operatorPath = path,
            now = now,
            window = DebriefWindow(0L, now, "walk"),
        ).toPlainText()
        assertTrue(text.contains("11:22:33:44:55:66 (Separated)"))
        assertTrue(text.contains("Account for it. $day"))
        assertTrue(text.contains("Account for it. Find My / iPhone addresses rotate; this MAC is this session."))
        assertFalse(text.contains("$day Find My"))
    }

    @Test
    fun markedMineLeavesTheUnmarkedTagInTheCallout() {
        val now = 120_000L
        val path = listOf(
            GpsSample(0L, 28.0, -81.0, -50),
            GpsSample(60_000L, 28.0012, -81.0, -50),
            GpsSample(now, 28.0018, -81.0, -50),
        )
        val dult = listOf(
            Fleet(
                id = "fleet-dult",
                name = "DULT tracker",
                kind = SignatureClass.FINDER,
                attentionNote = "A separated tag.",
            ),
        )
        fun tag(mac: String) = radio(
            "BLE:$mac",
            fleetIds = setOf("fleet-dult"),
            firstSeen = 0L,
            lastSeen = now,
            rssi = -50,
            randomized = false,
            name = "tag",
        ).copy(gpsTrail = path)
        val mineTag = tag("11:22:33:44:55:66")
        val other = tag("11:22:33:44:55:77")
        val quiet = radio(
            "BLE:AA:BB:CC:DD:EE:01",
            firstSeen = 0L,
            lastSeen = now,
            rssi = -80,
            randomized = false,
        )
        val doc = DebriefReport.document(
            devices = listOf(mineTag, other, quiet),
            fleets = dult,
            settings = AppSettings(tagLocation = true),
            operatorPath = path,
            now = now,
            window = DebriefWindow(0L, now, "walk"),
            customNames = mapOf(mineTag.key to "bag tag"),
            observerNotes = mapOf(quiet.key to "north lot"),
            mineKeys = setOf(mineTag.key, quiet.key),
        )
        val titles = doc.sections.map { it.title }
        assertEquals("Observer notes", titles[2])
        val mineAt = titles.indexOf("Marked mine")
        assertTrue(mineAt > titles.indexOf("Observer notes"))
        assertTrue(mineAt < titles.indexOf("Tracking assessment"))
        val mine = doc.sections.single { it.title == "Marked mine" }
        assertFalse(mine.alert)
        assertTrue(mine.body.contains("11:22:33:44:55:66"))
        assertTrue(mine.body.contains("bag tag"))
        assertTrue(mine.body.contains("Marked mine. With you the whole sit."))
        assertTrue(mine.body.contains("AA:BB:CC:DD:EE:01"))
        assertTrue(mine.body.contains("\n    Marked mine."))
        assertFalse(mine.body.contains("11:22:33:44:55:77"))
        val trackers = doc.sections.single { it.title == "Possible trackers with you" }
        assertTrue(trackers.body.contains("11:22:33:44:55:77"))
        assertFalse(trackers.body.contains("11:22:33:44:55:66"))
        assertTrue(doc.trackingAlert)
        assertTrue(doc.toPlainText().contains("Account for it."))
        val exec = doc.sections.single { it.title == "Executive summary" }.body
        assertTrue(exec.contains("2 marked mine"))
        assertFalse(exec.contains("11:22:33:44:55:66"))
        val extra = doc.sections.single { it.title == "Extra attention" }.body
        assertTrue(extra.contains("11:22:33:44:55:66"))
        assertTrue(extra.contains("Marked mine"))
        assertTrue(extra.contains("A separated tag."))
        val hit = doc.extraAttention.single { it.radioLabel.contains("11:22:33:44:55:66") }
        assertTrue(hit.radioLabel.contains("Marked mine"))
        assertEquals("A separated tag.", hit.note)
    }

    @Test
    fun markedMineAloneClearsTheTrackingBanner() {
        val now = 120_000L
        val path = listOf(
            GpsSample(0L, 28.0, -81.0, -50),
            GpsSample(60_000L, 28.0012, -81.0, -50),
            GpsSample(now, 28.0018, -81.0, -50),
        )
        val dult = listOf(Fleet(id = "fleet-dult", name = "DULT tracker", kind = SignatureClass.FINDER))
        val mineTag = radio(
            "BLE:11:22:33:44:55:66",
            fleetIds = setOf("fleet-dult"),
            firstSeen = 0L,
            lastSeen = now,
            rssi = -50,
            randomized = false,
        ).copy(gpsTrail = path)
        val doc = DebriefReport.document(
            devices = listOf(mineTag),
            fleets = dult,
            settings = AppSettings(tagLocation = true),
            operatorPath = path,
            now = now,
            window = DebriefWindow(0L, now, "walk"),
            customNames = mapOf(mineTag.key to "bag tag"),
            mineKeys = setOf(mineTag.key),
        )
        assertFalse(doc.trackingAlert)
        assertTrue(doc.sections.none { it.title == "Possible trackers with you" })
        assertTrue(doc.sections.none { it.title == "Possible tail" })
        assertFalse(doc.takeaway.contains("11:22:33:44:55:66"))
        assertTrue(doc.sections.single { it.title == "Marked mine" }.body.contains("With you the whole sit."))
        assertTrue(doc.sections.single { it.title == "Executive summary" }.body.contains("1 marked mine"))
    }

    @Test
    fun markedMineTailUsesTheArrivalLine() {
        val now = 120_000L
        val path = listOf(
            GpsSample(0L, 28.0, -81.0, -60),
            GpsSample(40_000L, 28.0008, -81.0, -60),
            GpsSample(80_000L, 28.0016, -81.0, -60),
            GpsSample(now, 28.0024, -81.0, -60),
        )
        val dult = listOf(Fleet(id = "fleet-dult", name = "DULT tracker", kind = SignatureClass.FINDER))
        val tail = radio(
            "BLE:11:22:33:44:55:88",
            fleetIds = setOf("fleet-dult"),
            firstSeen = 20_000L,
            lastSeen = now,
            rssi = -60,
            randomized = false,
        ).copy(gpsTrail = path.drop(1))
        val doc = DebriefReport.document(
            devices = listOf(tail),
            fleets = dult,
            settings = AppSettings(tagLocation = true),
            operatorPath = path,
            now = now,
            window = DebriefWindow(0L, now, "walk"),
            mineKeys = setOf(tail.key),
        )
        assertFalse(doc.trackingAlert)
        assertTrue(doc.sections.none { it.title == "Possible tail" })
        val body = doc.sections.single { it.title == "Marked mine" }.body
        assertTrue(body.contains("First heard after the sit started and stayed with the path."))
    }

    @Test
    fun endFreezesSummary() {
        val session = SitSession.start(
            name = "lot",
            now = 1_000L,
            heard = listOf(radio("BLE:AA:AA:AA:AA:AA:01")),
            fleets = fleets,
            watchDeviceKeys = emptySet(),
            watchedFleetIds = emptySet(),
        )
        val file = session.end(5_000L, fleets)
        assertEquals(5_000L, file.summary.endAt)
        assertFalse(file.summary.open)
        assertEquals(1, file.summary.radioCount)
        assertFalse(session.open)
    }

    @Test
    fun pathSubsamplesShortMoves() {
        val session = SitSession.start(
            name = "walk",
            now = 1_000L,
            heard = emptyList(),
            fleets = fleets,
            watchDeviceKeys = emptySet(),
            watchedFleetIds = emptySet(),
        )
        session.recordPath(40.0, -74.0, 1_000L)
        session.recordPath(40.00001, -74.0, 2_000L)
        assertEquals(1, session.operatorPath.size)
        session.recordPath(40.001, -74.0, 8_000L)
        assertEquals(2, session.operatorPath.size)
    }

    private fun radio(
        key: String,
        mac: String = key.substringAfter(':'),
        fleetIds: Set<String> = emptySet(),
        firstSeen: Long = 1L,
        lastSeen: Long = 1L,
        gone: Boolean = false,
        payloadLat: Double? = null,
        payloadLon: Double? = null,
        randomized: Boolean = true,
        rssi: Int = -60,
        name: String = "",
        kind: RadioKind = RadioKind.BLE,
    ) = Sighting(
        key = key,
        kind = kind,
        mac = mac,
        name = name,
        rssi = rssi,
        rssiMin = rssi,
        rssiMax = rssi,
        channel = 0,
        frequencyMhz = 2402,
        vendor = null,
        randomized = randomized,
        hiddenSsid = false,
        serviceUuids = emptyList(),
        manufacturerId = null,
        manufacturerDataHex = "",
        rawHex = "",
        extras = "",
        firstSeen = firstSeen,
        lastSeen = lastSeen,
        hitCount = 1,
        fleetIds = fleetIds,
        rssiHistory = emptyList(),
        presence = emptyList(),
        gone = gone,
        payloadLat = payloadLat,
        payloadLon = payloadLon,
    )
}
