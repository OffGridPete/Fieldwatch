package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureCandidatesTest {
    private val engine = SignatureEngine()

    @Test
    fun nameGlobHexRunBecomesQuestionMarks() {
        assertEquals("H2O-????????????", SignatureCandidates.nameGlobOf("H2O-047bcbd11400"))
        assertEquals("H2O-????????????", SignatureCandidates.nameGlobOf("H2O-0a1c8e22b400"))
        assertEquals("RG3100*", SignatureCandidates.nameGlobOf("RG3100-7A21"))
        assertEquals("RG3100*", SignatureCandidates.nameGlobOf("RG3100-8434 guest"))
        assertEquals("[fridge]*", SignatureCandidates.nameGlobOf("[fridge]_E30AJT5133207Z SJIT"))
        assertNull(SignatureCandidates.nameGlobOf("IonCannon"))
        assertNull(SignatureCandidates.nameGlobOf("Jameson"))
        assertNull(SignatureCandidates.nameGlobOf("DIRECT-roku-living"))
        assertNull(SignatureCandidates.nameGlobOf("LOB_Guest_wifi"))
    }

    @Test
    fun houseLikeNames() {
        assertTrue(SignatureCandidates.isHouseLikeName("IonCannon"))
        assertTrue(SignatureCandidates.isHouseLikeName("Jameson"))
        assertTrue(SignatureCandidates.isHouseLikeName("LOB_Guest"))
        assertTrue(SignatureCandidates.isHouseLikeName("ATT-GUEST-lobby"))
        assertFalse(SignatureCandidates.isHouseLikeName("H2O-047bcbd11400"))
        assertFalse(SignatureCandidates.isHouseLikeName("RG3100-7A21"))
        assertFalse(SignatureCandidates.isHouseLikeName("[fridge]_E30AJT5133207Z"))
    }

    @Test
    fun hexNameGlobClusterOnTwoStableAps() {
        val a = wifi("00:11:22:33:44:55", "H2O-047bcbd11400")
        val b = wifi("00:11:22:33:44:66", "H2O-0a1c8e22b400")
        val report = SignatureCandidates.analyze(listOf(a, b), emptyList(), engine)
        assertEquals(1, report.families.size)
        val hit = report.families.single()
        assertEquals("H2O", hit.proposedName)
        assertEquals(2, hit.distinctRadios)
        assertEquals(RuleKind.NAME_GLOB, hit.rules.first().kind)
        assertEquals("H2O-????????????", hit.rules.first().text)
        assertEquals(RadioKind.WIFI, hit.rules.first().radio)
        assertFalse(hit.rules.any { it.kind == RuleKind.MAC_PREFIX })
        assertTrue(hit.why.contains("not a house", ignoreCase = true))
    }

    @Test
    fun singleRadioIsNotAFamily() {
        val a = wifi("00:11:22:33:44:55", "H2O-047bcbd11400")
        val report = SignatureCandidates.analyze(listOf(a), emptyList(), engine)
        assertTrue(report.families.isEmpty())
        assertEquals(1, report.skippedOther)
    }

    @Test
    fun houseNamesAndRandomizedWithoutIdAreSkipped() {
        val house = wifi("9C:4F:5F:11:22:33", "Jameson")
        val guest = wifi("00:11:22:33:44:01", "LOB_Guest")
        val rand = wifi("CE:BE:8F:24:DC:E3", "IonCannon").copy(randomized = true)
        val unnamed = ble("F2:11:22:33:44:55", "").copy(randomized = true)
        val report = SignatureCandidates.analyze(listOf(house, guest, rand, unnamed), emptyList(), engine)
        assertTrue(report.families.isEmpty())
        assertTrue(report.skippedRandomized + report.skippedHouseLike + report.skippedOther >= 3)
    }

    @Test
    fun vendorIeClusterIgnoresProtocolIes() {
        val a = wifi("F2:11:22:33:44:01", "", ies = listOf("00:50:F2", "C8:3A:6B")).copy(randomized = true)
        val b = wifi("F2:11:22:33:44:02", "", ies = listOf("00:0F:AC", "C8:3A:6B")).copy(randomized = true)
        val proto = wifi("00:11:22:33:44:10", "Cafe", ies = listOf("00:50:F2", "00:0F:AC"))
        val proto2 = wifi("00:11:22:33:44:11", "Shop", ies = listOf("00:50:F2"))
        val report = SignatureCandidates.analyze(listOf(a, b, proto, proto2), emptyList(), engine)
        assertEquals(1, report.families.size)
        val hit = report.families.single()
        assertEquals(RuleKind.VENDOR_IE_OUI, hit.rules.first().kind)
        assertTrue(hit.rules.first().text.contains("C8:3A:6B", ignoreCase = true))
        assertEquals(2, hit.distinctRadios)
    }

    @Test
    fun chipModuleOuiIsNotAFamily() {
        val a = wifi("3C:71:BF:11:22:33", "setup1", vendor = "Espressif Inc.")
        val b = wifi("3C:71:BF:11:22:44", "setup2", vendor = "Espressif Inc.")
        val report = SignatureCandidates.analyze(listOf(a, b), emptyList(), engine)
        assertTrue(report.families.none { it.rules.any { r -> r.kind == RuleKind.OUI } })
    }

    @Test
    fun stockCatalogMatchDropsAlreadyTaggedRadios() {
        val h2o = wifi("00:11:22:33:44:55", "H2O-047bcbd11400")
        val h2o2 = wifi("00:11:22:33:44:66", "H2O-0a1c8e22b400")
        val novelA = wifi("00:AA:BB:11:22:33", "XYZMODEM-1111")
        val novelB = wifi("00:AA:BB:11:22:44", "XYZMODEM-2222")
        val stock = DefaultCatalog.fleets()
        val report = SignatureCandidates.analyze(listOf(h2o, h2o2, novelA, novelB), stock, engine)
        assertTrue(report.families.none { it.rules.any { r -> r.text.startsWith("H2O") } })
        assertEquals(1, report.families.size)
        val novel = report.families.single()
        assertTrue(novel.rules.any { it.kind == RuleKind.NAME_GLOB && it.text == "XYZMODEM*" })
    }

    @Test
    fun familyHintStrongOnManyNameGlobs() {
        val radios = (0 until 10).map { i ->
            wifi("00:11:22:33:44:%02X".format(i), "H2O-%012x".format(i.toLong()))
        }
        val device = radios.first().toSighting()
        val hint = SignatureCandidates.assessFamily(device, listOf(device), radios, emptyList())
        assertEquals(FamilyVerdict.STRONG, hint.verdict)
        assertEquals(10, hint.logCount)
        assertEquals(1, hint.liveCount)
        assertEquals(10, hint.displayCount)
        assertEquals("Strong family", hint.title)
        assertTrue(hint.body.contains("catalog pattern"))
        assertTrue(hint.ruleLabel?.startsWith("H2O-") == true)
    }

    @Test
    fun familyHintPossibleOnTwo() {
        val a = wifi("00:11:22:33:44:55", "H2O-047bcbd11400")
        val b = wifi("00:11:22:33:44:66", "H2O-0a1c8e22b400")
        val device = a.toSighting()
        val hint = SignatureCandidates.assessFamily(device, listOf(device), listOf(a, b), emptyList())
        assertEquals(FamilyVerdict.POSSIBLE, hint.verdict)
        assertEquals(2, hint.logCount)
        assertEquals("Possible family", hint.title)
    }

    @Test
    fun familyHintSingleWhenAlone() {
        val a = wifi("00:11:22:33:44:55", "H2O-047bcbd11400")
        val device = a.toSighting()
        val hint = SignatureCandidates.assessFamily(device, listOf(device), listOf(a), emptyList())
        assertEquals(FamilyVerdict.SINGLE, hint.verdict)
        assertEquals("This radio only", hint.title)
        assertEquals(0, hint.displayCount)
        assertTrue(hint.body.contains("this address", ignoreCase = true))
    }

    @Test
    fun familyHintTaggedSkipsSiblingCount() {
        val radios = (0 until 10).map { i ->
            wifi("00:11:22:33:44:%02X".format(i), "H2O-%012x".format(i.toLong()))
        }
        val fleet = DefaultCatalog.fleets().first()
        val device = radios.first().toSighting().copy(fleetIds = setOf(fleet.id))
        val hint = SignatureCandidates.assessFamily(device, listOf(device), radios, listOf(fleet))
        assertEquals(FamilyVerdict.TAGGED, hint.verdict)
        assertEquals(0, hint.displayCount)
        assertTrue(hint.body.contains(fleet.name))
        assertTrue(hint.body.contains("second signature", ignoreCase = true))
    }

    @Test
    fun familyHintIgnoresBroadCompanyCrowd() {
        val radios = (0 until 12).map { i ->
            ble("F2:11:22:33:44:%02X".format(i), "").copy(
                manufacturerId = 0x004C,
                manufacturerDataHex = "10AB",
                randomized = true,
            )
        }
        val device = radios.first().toSighting()
        val hint = SignatureCandidates.assessFamily(device, listOf(device), radios, emptyList())
        assertEquals(FamilyVerdict.SINGLE, hint.verdict)
        assertEquals(null, hint.ruleLabel)
    }

    @Test
    fun familyHintIgnoresChipOuiCrowd() {
        val radios = (0 until 12).map { i ->
            wifi("3C:71:BF:11:22:%02X".format(i), "setup$i", vendor = "Espressif Inc.")
        }
        val device = radios.first().toSighting()
        val hint = SignatureCandidates.assessFamily(device, listOf(device), radios, emptyList())
        assertEquals(FamilyVerdict.SINGLE, hint.verdict)
    }

    @Test
    fun familyHintCountsLiveWhenLogEmpty() {
        val live = (0 until 8).map { i ->
            wifi("00:11:22:33:44:%02X".format(i), "H2O-%012x".format(i.toLong())).toSighting()
        }
        val hint = SignatureCandidates.assessFamily(live.first(), live, emptyList(), emptyList())
        assertEquals(FamilyVerdict.STRONG, hint.verdict)
        assertEquals(0, hint.logCount)
        assertEquals(8, hint.liveCount)
        assertEquals(8, hint.displayCount)
        assertTrue(hint.body.contains("on the air now"))
    }

    @Test
    fun suggestFleetHasNoMacRule() {
        val a = wifi("00:11:22:33:44:55", "H2O-047bcbd11400")
        val b = wifi("00:11:22:33:44:66", "H2O-0a1c8e22b400")
        val cand = SignatureCandidates.analyze(listOf(a, b), emptyList(), engine).families.single()
        val fleet = SignatureCandidates.suggestFleet(cand)
        assertFalse(fleet.rules.any { it.kind == RuleKind.MAC_PREFIX })
        assertFalse(fleet.builtIn)
        assertEquals(SignatureClass.HOME, fleet.kind)
        assertTrue(engine.match(listOf(a.toSighting(), b.toSighting()), listOf(fleet)).values.all { "H2O" in cand.proposedName || it.contains(fleet.id) })
        val hits = engine.match(listOf(a.toSighting(), b.toSighting()), listOf(fleet))
        assertTrue(hits.getValue(a.key).contains(fleet.id))
        assertTrue(hits.getValue(b.key).contains(fleet.id))
    }

    @Test
    fun logReplayParsesOldAndNewCsv() {
        val old = """
            timestamp,iso,kind,mac,name,rssi,channel,freq,oui,vendor,fleets,mfg,uuids,flags,raw,lat,lon
            1,2026-08-31T12:00:00.000Z,WIFI,00:11:22:33:44:55,H2O-047bcbd11400,-50,1,2412,00:11:22,Acme,, ,,,,-40.0,-80.0
        """.trimIndent()
        val oldRadios = LogReplay.parse(old)
        assertEquals(1, oldRadios.size)
        assertEquals("H2O-047bcbd11400", oldRadios.single().name)
        assertTrue(oldRadios.single().vendorIeOuis.isEmpty())

        val header = listOf(
            "timestamp", "iso", "kind", "mac", "name", "rssi", "channel", "freq",
            "oui", "vendor", "fleets", "mfg", "uuids", "flags", "raw", "lat", "lon", "vendor_ie",
        )
        val newRow = listOf(
            "1", "2026-08-31T12:00:00.000Z", "WIFI", "F2:11:22:33:44:01", "Roku-living",
            "-40", "1", "2412", "F2:11:22", "", "", "", "", "RAND", "", "40.0", "-80.0",
            "C8:3A:6B|00:50:F2",
        )
        val new = header.joinToString(",") + "\n" + newRow.joinToString(",")
        val newRadios = LogReplay.parse(new)
        assertEquals(1, newRadios.size)
        assertTrue(newRadios.single().randomized)
        assertTrue(
            "ies=${newRadios.single().vendorIeOuis} rand=${newRadios.single().randomized} flags-name=${newRadios.single().name}",
            newRadios.single().vendorIeOuis.any { it.contains("C8:3A:6B", ignoreCase = true) },
        )
    }

    @Test
    fun logReplayAggregatesHitsAndMergesIes() {
        val csv = """
            timestamp,iso,kind,mac,name,rssi,channel,freq,oui,vendor,fleets,mfg,uuids,flags,raw,lat,lon,vendor_ie
            1,t,WIFI,00:11:22:33:44:55,H2O-047bcbd11400,-60,1,2412,00:11:22,,,,,,,,
            2,t,WIFI,00:11:22:33:44:55,H2O-047bcbd11400,-50,1,2412,00:11:22,,,,,,,,,C8:3A:6B
        """.trimIndent()
        val radios = LogReplay.parse(csv)
        assertEquals(1, radios.size)
        assertEquals(2, radios.single().hits)
        assertEquals(-50, radios.single().rssi)
        assertTrue(radios.single().vendorIeOuis.any { it.contains("C8:3A:6B", ignoreCase = true) })
    }

    private fun wifi(
        mac: String,
        name: String,
        vendor: String? = "Acme",
        ies: List<String> = emptyList(),
    ) = LogRadio(
        kind = RadioKind.WIFI,
        mac = MacUtil.normalize(mac),
        name = name,
        vendor = vendor,
        manufacturerId = null,
        manufacturerDataHex = "",
        serviceUuids = emptyList(),
        vendorIeOuis = ies,
        randomized = MacUtil.isRandomized(mac),
        hiddenSsid = name.isEmpty(),
        rssi = -40,
        firstSeen = 1L,
        lastSeen = 1L,
        hits = 3,
    )

    private fun ble(mac: String, name: String) = LogRadio(
        kind = RadioKind.BLE,
        mac = MacUtil.normalize(mac),
        name = name,
        vendor = null,
        manufacturerId = null,
        manufacturerDataHex = "",
        serviceUuids = emptyList(),
        vendorIeOuis = emptyList(),
        randomized = MacUtil.isRandomized(mac),
        hiddenSsid = false,
        rssi = -50,
        firstSeen = 1L,
        lastSeen = 1L,
        hits = 2,
    )

    private fun LogRadio.toSighting() = Sighting(
        key = key,
        kind = kind,
        mac = mac,
        name = name,
        rssi = rssi,
        rssiMin = rssi,
        rssiMax = rssi,
        channel = 1,
        frequencyMhz = 2412,
        vendor = vendor,
        randomized = randomized,
        hiddenSsid = hiddenSsid,
        serviceUuids = serviceUuids,
        manufacturerId = manufacturerId,
        manufacturerDataHex = manufacturerDataHex,
        rawHex = manufacturerDataHex,
        extras = "",
        firstSeen = firstSeen,
        lastSeen = lastSeen,
        hitCount = hits,
        fleetIds = emptySet(),
        rssiHistory = emptyList(),
        presence = emptyList(),
        vendorIeOuis = vendorIeOuis,
    )
}
