package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingFloodTest {
    @Test
    fun fivePopupsStayQuiet() {
        val flood = PairingFlood()
        repeat(5) { flood.consider(proximity(it), 0L) }
        assertNull(flood.notice.value)
    }

    @Test
    fun sixClusteredPopupsOpenTheDialog() {
        val flood = PairingFlood()
        repeat(6) { flood.consider(proximity(it), 0L) }
        val notice = flood.notice.value!!
        assertTrue(notice.showDialog)
        assertEquals("Pairing flood", notice.title())
        assertEquals(6, notice.popupCount)
        assertEquals(0, notice.nameCount)
        assertEquals(listOf("Apple proximity pairing"), notice.families)
        assertEquals(-48, notice.medianRssi)
        assertEquals("Pairing flood · 6 new addresses · about -48 dBm", notice.line())
        assertTrue(notice.body().contains("6 new addresses sent pairing advertisements"))
        assertTrue(notice.body().contains("which reads as one nearby radio"))
        flood.consider(proximity(6), 1_000L)
        val more = flood.notice.value!!
        assertTrue(more.showDialog)
        assertEquals(7, more.popupCount)
    }

    @Test
    fun mixedPopupFamiliesShareOneCounter() {
        val flood = PairingFlood()
        flood.consider(proximity(0), 0L)
        flood.consider(proximity(1), 0L)
        flood.consider(nearbyAction(2), 0L)
        flood.consider(nearbyAction(3), 0L)
        flood.consider(fastPair(4), 0L)
        assertNull(flood.notice.value)
        flood.consider(swift(5), 0L)
        val notice = flood.notice.value!!
        assertEquals(6, notice.popupCount)
        assertEquals(0, notice.nameCount)
        assertEquals(
            listOf(
                "Apple proximity pairing",
                "Apple Nearby Action",
                "Fast Pair",
                "Swift Pair",
            ),
            notice.families,
        )
    }

    @Test
    fun theSameAddressIsCountedOnce() {
        val flood = PairingFlood()
        repeat(8) { flood.consider(proximity(1), it * 100L) }
        assertNull(flood.notice.value)
    }

    @Test
    fun entriesLeaveTheWindowAfterTenSeconds() {
        val flood = PairingFlood()
        repeat(6) { flood.consider(proximity(it), 0L) }
        assertTrue(flood.notice.value!!.showDialog)
        flood.tick(PairingFlood.WINDOW_MS)
        assertTrue(flood.notice.value!!.showDialog)
        flood.tick(PairingFlood.WINDOW_MS + 1)
        assertNull(flood.notice.value)
    }

    @Test
    fun nearbyInfoAndFindMyAreNotPopups() {
        val flood = PairingFlood()
        repeat(6) { flood.consider(apple(it, "100100"), 0L) }
        repeat(6) { flood.consider(apple(10 + it, "120100"), 0L) }
        repeat(6) { flood.consider(apple(20 + it, "020100"), 0L) }
        assertNull(flood.notice.value)
    }

    @Test
    fun aLongFastPairPayloadIsNotThePairingFrame() {
        val flood = PairingFlood()
        repeat(6) { flood.consider(fastPair(it, payload = "01020304050607"), 0L) }
        assertNull(flood.notice.value)
    }

    @Test
    fun spreadOutRadiosDoNotTrip() {
        val flood = PairingFlood()
        val rssi = intArrayOf(-30, -50, -70, -90, -110, -40)
        rssi.forEachIndexed { i, dbm -> flood.consider(proximity(i, rssi = dbm), 0L) }
        assertNull(flood.notice.value)
    }

    @Test
    fun aTightClusterStillCountsWhenAFewAreFar() {
        val flood = PairingFlood()
        val rssi = intArrayOf(-48, -50, -46, -52, -49, -47, -20, -90)
        rssi.forEachIndexed { i, dbm -> flood.consider(proximity(i, rssi = dbm), 0L) }
        val notice = flood.notice.value!!
        assertEquals(6, notice.popupCount)
        assertEquals(-48, notice.medianRssi)
    }

    @Test
    fun fifteenNewNamesOpenTheDialog() {
        val flood = PairingFlood()
        repeat(14) { flood.consider(named(it), 0L) }
        assertNull(flood.notice.value)
        flood.consider(named(14), 0L)
        val notice = flood.notice.value!!
        assertTrue(notice.showDialog)
        assertEquals("Name flood", notice.title())
        assertEquals(0, notice.popupCount)
        assertEquals(15, notice.nameCount)
        assertEquals("Name flood · 15 new addresses · about -55 dBm", notice.line())
        assertTrue(notice.body().contains("15 new addresses each advertised a Bluetooth name"))
        assertTrue(notice.body().contains("advertising a new name and changing the address every packet"))
        assertFalse(notice.body().contains("pairing advertisements"))
    }

    @Test
    fun popupAndNameBurstsBothShow() {
        val flood = PairingFlood()
        repeat(6) { flood.consider(proximity(it, rssi = -40), 0L) }
        repeat(15) { flood.consider(named(50 + it, rssi = -70), 0L) }
        val notice = flood.notice.value!!
        assertEquals(6, notice.popupCount)
        assertEquals(15, notice.nameCount)
        assertEquals(-40, notice.medianRssi)
        assertEquals("Pairing flood · 6 new addresses · 15 named · about -40 dBm", notice.line())
        assertTrue(notice.body().contains("6 new addresses sent pairing advertisements"))
        assertTrue(notice.body().contains("15 more each advertised a Bluetooth name"))
    }

    @Test
    fun aFewMeasuredPopupsDoNotBorrowUnmeasuredOnes() {
        val flood = PairingFlood()
        repeat(4) { flood.consider(proximity(it, rssi = -45), 0L) }
        repeat(2) { flood.consider(proximity(10 + it, rssi = 127), 0L) }
        assertNull(flood.notice.value)
    }

    @Test
    fun aNamedPopupDoesNotAlsoCountAsAName() {
        val flood = PairingFlood()
        repeat(6) { flood.consider(proximity(it, name = "Buds $it"), 0L) }
        val notice = flood.notice.value!!
        assertEquals(6, notice.popupCount)
        assertEquals(0, notice.nameCount)
        assertFalse(notice.body().contains("each advertised a Bluetooth name"))
    }

    @Test
    fun dismissKeepsOneLineUntilTheBurstGoesQuiet() {
        val flood = PairingFlood()
        repeat(6) { flood.consider(proximity(it), 1_000L) }
        assertTrue(flood.notice.value!!.showDialog)
        flood.dismiss()
        val line = flood.notice.value!!
        assertFalse(line.showDialog)
        assertTrue(line.line().startsWith("Pairing flood"))
        repeat(6) { flood.consider(proximity(100 + it), 5_000L) }
        assertFalse(flood.notice.value!!.showDialog)
        assertEquals(12, flood.notice.value!!.popupCount)
        flood.tick(1_000L + PairingFlood.WINDOW_MS + 1)
        val mid = flood.notice.value!!
        assertFalse(mid.showDialog)
        assertEquals(6, mid.popupCount)
        flood.tick(5_000L + PairingFlood.WINDOW_MS + 1)
        assertNull(flood.notice.value)
        repeat(6) { flood.consider(proximity(200 + it), 16_000L) }
        assertTrue(flood.notice.value!!.showDialog)
    }

    @Test
    fun theDialogDoesNotNameTheTool() {
        val flood = PairingFlood()
        repeat(6) { flood.consider(proximity(it), 0L) }
        val body = flood.notice.value!!.body()
        assertTrue(body.contains("does not name the tool"))
        assertTrue(body.contains("Flipper Zero"))
        assertTrue(body.contains("Marauder"))
        assertTrue(body.contains("Bruce"))
        assertTrue(body.contains("advertising a pairing request and changing the address every packet"))
        assertFalse(body.contains("identified", ignoreCase = true))
    }

    @Test
    fun missingRssiFallsBackToTheRawCount() {
        val flood = PairingFlood()
        repeat(6) { flood.consider(proximity(it, rssi = 127), 0L) }
        val notice = flood.notice.value!!
        assertNull(notice.medianRssi)
        assertFalse(notice.line().contains("dBm"))
        assertFalse(notice.body().contains("one nearby radio"))
        assertEquals(6, notice.popupCount)
    }

    @Test
    fun knownPopupLayoutsAndAFullWindow() {
        val layouts = PairingFlood()
        layouts.consider(obs(0, mfgId = 0x004C, mfgHex = "070100", factsOn = false), 0L)
        layouts.consider(
            obs(
                1,
                serviceUuid = "0000fe2c-0000-1000-8000-00805f9b34fb",
                serviceHex = "AABBCC",
            ),
            0L,
        )
        layouts.consider(obs(2, mfgId = 0x0006, mfgHex = "01"), 0L)
        layouts.consider(swift(3, payload = "0209"), 0L)
        repeat(3) { layouts.consider(proximity(10 + it), 0L) }
        assertNull(layouts.notice.value)
        layouts.consider(proximity(20), 0L)
        val notice = layouts.notice.value!!
        assertEquals(6, notice.popupCount)
        assertEquals(listOf("Apple proximity pairing", "Fast Pair"), notice.families)
        val flood = PairingFlood()
        repeat(100) { flood.consider(proximity(it), 0L) }
        assertEquals(96, flood.notice.value!!.popupCount)
    }

    @Test
    fun aCrossingKeepsOneBurstUntilTheWindowClears() {
        val flood = PairingFlood()
        repeat(6) { flood.consider(proximity(it), 1_000L) }
        val opened = flood.bursts().single()
        assertEquals(1_000L, opened.at)
        assertEquals(6, opened.popupCount)
        assertEquals(listOf("Apple proximity pairing"), opened.families)
        assertEquals(-48, opened.medianRssi)
        flood.consider(proximity(6), 2_000L)
        val peaked = flood.bursts().single()
        assertEquals(1_000L, peaked.at)
        assertEquals(7, peaked.popupCount)
        assertEquals(
            "12:00 UTC. Pairing flood. 7 new addresses: Apple proximity pairing · about -48 dBm. " +
                "7 addresses from this burst are left out of the counts and lists below.",
            peaked.reportLine("12:00"),
        )
        assertEquals((0..6).map { burstKey(it) }.toSet(), peaked.keys.toSet())
        flood.tick(2_000L + PairingFlood.WINDOW_MS + 1)
        assertNull(flood.notice.value)
        assertEquals(7, flood.bursts().single().popupCount)
        assertEquals((0..6).map { burstKey(it) }.toSet(), flood.bursts().single().keys.toSet())
        repeat(6) { flood.consider(proximity(100 + it), 20_000L) }
        val bursts = flood.bursts()
        assertEquals(2, bursts.size)
        assertEquals(1_000L, bursts[0].at)
        assertEquals(7, bursts[0].popupCount)
        assertEquals((0..6).map { burstKey(it) }.toSet(), bursts[0].keys.toSet())
        assertEquals(20_000L, bursts[1].at)
        assertEquals(6, bursts[1].popupCount)
        assertEquals((100..105).map { burstKey(it) }.toSet(), bursts[1].keys.toSet())
    }

    @Test
    fun hideThisBurstDropsOnlyTheAddressesItCounted() {
        val flood = PairingFlood()
        repeat(5) { flood.consider(proximity(it), 1_000L) }
        flood.setHideBurst(true)
        assertFalse(flood.hide.value.episodeOn)
        assertTrue(flood.hide.value.keys.isEmpty())
        flood.consider(proximity(5), 1_000L)
        val counted = (0..5).map { burstKey(it) }.toSet()
        assertFalse(flood.hide.value.episodeOn)
        assertTrue(flood.hide.value.keys.isEmpty())
        flood.setHideBurst(true)
        assertTrue(flood.hide.value.episodeOn)
        assertEquals(counted, flood.hide.value.keys)
        flood.consider(proximity(6), 2_000L)
        assertTrue(burstKey(6) in flood.hide.value.keys)
        flood.consider(proximity(7, rssi = -100), 2_000L)
        assertFalse(burstKey(7) in flood.hide.value.keys)
        assertEquals((0..6).map { burstKey(it) }.toSet(), flood.bursts().single().keys.toSet())
        flood.setHideBurst(false)
        assertFalse(flood.hide.value.episodeOn)
        assertTrue(flood.hide.value.keys.isEmpty())
        flood.consider(proximity(8), 2_500L)
        assertFalse(burstKey(8) in flood.hide.value.keys)
    }

    @Test
    fun aHiddenBurstOutlastsTheLineAndTheNextBurstStartsVisible() {
        val flood = PairingFlood()
        repeat(6) { flood.consider(proximity(it), 1_000L) }
        flood.setHideBurst(true)
        val first = (0..5).map { burstKey(it) }.toSet()
        flood.tick(1_000L + PairingFlood.WINDOW_MS + 1)
        assertNull(flood.notice.value)
        assertFalse(flood.hide.value.episodeOn)
        assertEquals(first, flood.hide.value.keys)
        repeat(6) { flood.consider(proximity(100 + it), 20_000L) }
        assertFalse(flood.hide.value.episodeOn)
        val second = (100..105).map { burstKey(it) }.toSet()
        assertTrue(flood.hide.value.keys.none { it in second })
        assertEquals(first, flood.hide.value.keys)
        flood.setHideBurst(false)
        assertEquals(first, flood.hide.value.keys)
        flood.clearHidden()
        assertTrue(flood.hide.value.keys.isEmpty())
    }

    @Test
    fun pruneDropsAHiddenAddressAfterItLeavesTheLiveMap() {
        val flood = PairingFlood()
        repeat(6) { flood.consider(proximity(it), 1_000L) }
        flood.setHideBurst(true)
        val keys = (0..5).map { burstKey(it) }.toSet()
        flood.prune(emptySet())
        assertEquals(keys, flood.hide.value.keys)
        flood.prune(keys)
        assertEquals(keys, flood.hide.value.keys)
        val staying = setOf(burstKey(0))
        flood.prune(staying)
        assertEquals(staying, flood.hide.value.keys)
    }

    @Test
    fun swiftPairBeaconCountsAndLooseMicrosoftDataDoesNot() {
        val flood = PairingFlood()
        val beacon = "0300804C6170746F70"
        repeat(5) { flood.consider(swift(it, payload = beacon), 0L) }
        assertNull(flood.notice.value)
        flood.consider(swift(5, payload = beacon), 0L)
        val notice = flood.notice.value!!
        assertEquals(6, notice.popupCount)
        assertEquals(0, notice.nameCount)
        assertEquals(listOf("Swift Pair"), notice.families)
        flood.setHideBurst(true)
        assertEquals((0..5).map { burstKey(it) }.toSet(), flood.hide.value.keys)

        val quiet = PairingFlood()
        listOf("0300", "030000", "030380", "00", "0209").forEachIndexed { group, payload ->
            repeat(8) { quiet.consider(swift(group * 10 + it, payload = payload), 0L) }
        }
        assertNull(quiet.notice.value)

        val dual = PairingFlood()
        repeat(2) { dual.consider(swift(it, payload = "030080"), 0L) }
        repeat(2) { dual.consider(swift(10 + it, payload = "030180"), 0L) }
        dual.consider(swift(20, payload = "030280"), 0L)
        assertNull(dual.notice.value)
        dual.consider(swift(21, payload = "030080"), 0L)
        val mixed = dual.notice.value!!
        assertEquals(6, mixed.popupCount)
        assertEquals(listOf("Swift Pair"), mixed.families)
    }

    @Test
    fun samsungEasySetupCountsAndAFinderServiceDoesNot() {
        val flood = PairingFlood()
        val buds = "42098102141503210109EE7A01"
        val watch = "010002000101FF0000431A"
        repeat(3) { flood.consider(samsung(it, payload = buds), 0L) }
        repeat(2) { flood.consider(samsung(10 + it, payload = watch), 0L) }
        assertNull(flood.notice.value)
        flood.consider(samsung(20, payload = watch), 0L)
        val notice = flood.notice.value!!
        assertEquals(6, notice.popupCount)
        assertEquals(0, notice.nameCount)
        assertEquals(listOf("Samsung Easy Setup"), notice.families)
        flood.setHideBurst(true)
        val keys = (0..2).map { burstKey(it) }.toSet() +
            (10..11).map { burstKey(it) }.toSet() +
            burstKey(20)
        assertEquals(keys, flood.hide.value.keys)

        val quiet = PairingFlood()
        listOf("420981021415032101", "010002000101FF0000", "00").forEachIndexed { group, payload ->
            repeat(8) { quiet.consider(samsung(group * 10 + it, payload = payload), 0L) }
        }
        repeat(8) {
            quiet.consider(obs(80 + it, serviceUuid = "FD5A", serviceHex = "01020304"), 0L)
            quiet.consider(obs(100 + it, serviceUuid = "FD59", serviceHex = "01020304"), 0L)
        }
        assertNull(quiet.notice.value)
    }

    @Test
    fun loveSpousePrefixCountsAndAShortCompanyBlobDoesNot() {
        val flood = PairingFlood()
        val packet = "6DB643CE97FE427C000100"
        val otherMode = "6DB643CE97FE427C010203"
        repeat(3) { flood.consider(typo(it, payload = packet), 0L) }
        repeat(2) { flood.consider(typo(10 + it, payload = otherMode), 0L) }
        assertNull(flood.notice.value)
        flood.consider(typo(20, payload = "6DB643CE97FE427C"), 0L)
        val notice = flood.notice.value!!
        assertEquals(6, notice.popupCount)
        assertEquals(0, notice.nameCount)
        assertEquals(listOf("LoveSpouse"), notice.families)
        flood.setHideBurst(true)
        val keys = (0..2).map { burstKey(it) }.toSet() +
            (10..11).map { burstKey(it) }.toSet() +
            burstKey(20)
        assertEquals(keys, flood.hide.value.keys)

        val quiet = PairingFlood()
        listOf("6DB643CE97FE42", "00", "6DB643CE97FE427B000100").forEachIndexed { group, payload ->
            repeat(8) { quiet.consider(typo(group * 10 + it, payload = payload), 0L) }
        }
        repeat(8) {
            quiet.consider(obs(80 + it, serviceUuid = "AE8F", serviceHex = "01020304"), 0L)
            quiet.consider(obs(100 + it, mfgId = 0x00E0, mfgHex = packet), 0L)
        }
        assertNull(quiet.notice.value)
    }

    @Test
    fun aNameBurstLineOmitsPairingFamilies() {
        val line = FloodBurst(0L, popupCount = 0, nameCount = 16, medianRssi = -55).reportLine("09:15")
        assertEquals("09:15 UTC. Name flood. 16 new addresses · about -55 dBm.", line)
    }

    @Test
    fun wifiNamesAreIgnored() {
        val flood = PairingFlood()
        repeat(20) {
            flood.consider(obs(it, name = "Radio $it", kind = RadioKind.WIFI), 0L)
        }
        assertNull(flood.notice.value)
    }

    private fun burstKey(n: Int) = "BLE:02:00:00:00:00:%02X".format(n)

    private fun proximity(n: Int, rssi: Int = -48, name: String = "") =
        apple(n, "070100", rssi, name)

    private fun nearbyAction(n: Int, rssi: Int = -48) = apple(n, "0F0100", rssi)

    private fun apple(n: Int, hex: String, rssi: Int = -48, name: String = "") =
        obs(n, rssi, name, mfgId = 0x004C, mfgHex = hex)

    private fun fastPair(n: Int, rssi: Int = -48, payload: String = "000006") =
        obs(n, rssi, serviceUuid = "FE2C", serviceHex = payload)

    private fun swift(n: Int, rssi: Int = -48, payload: String = "0109") =
        obs(n, rssi, mfgId = 0x0006, mfgHex = payload)

    private fun samsung(n: Int, rssi: Int = -48, payload: String) =
        obs(n, rssi, mfgId = 0x0075, mfgHex = payload)

    private fun typo(n: Int, rssi: Int = -31, payload: String) =
        obs(n, rssi, mfgId = 0x00FF, mfgHex = payload)

    private fun named(n: Int, rssi: Int = -55) = obs(n, rssi, name = "Radio $n")

    private fun obs(
        n: Int,
        rssi: Int = -48,
        name: String = "",
        kind: RadioKind = RadioKind.BLE,
        mfgId: Int? = null,
        mfgHex: String = "",
        serviceUuid: String? = null,
        serviceHex: String = "",
        factsOn: Boolean = true,
    ): Observation {
        val mfg = if (mfgId != null) listOf(MfgRecord(mfgId, mfgHex)) else emptyList()
        val service = if (serviceUuid != null) listOf(ServiceDataRecord(serviceUuid, serviceHex)) else emptyList()
        return Observation(
            kind = kind,
            mac = "02:00:00:00:00:%02X".format(n),
            name = name,
            rssi = rssi,
            channel = 0,
            frequencyMhz = 0,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = mfgId,
            manufacturerDataHex = mfgHex,
            rawHex = "",
            extras = "",
            at = 0L,
            facts = if (factsOn) RadioFacts(mfgRecords = mfg, serviceData = service) else RadioFacts.Empty,
        )
    }
}
