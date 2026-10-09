package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingFloodTest {
    @Test
    fun ninePopupsStayQuiet() {
        val flood = PairingFlood()
        repeat(9) { flood.consider(proximity(it), 0L) }
        assertNull(flood.notice.value)
    }

    @Test
    fun tenClusteredPopupsOpenTheDialog() {
        val flood = PairingFlood()
        repeat(10) { flood.consider(proximity(it), 0L) }
        val notice = flood.notice.value!!
        assertTrue(notice.showDialog)
        assertEquals("Pairing flood", notice.title())
        assertEquals(10, notice.popupCount)
        assertEquals(0, notice.nameCount)
        assertEquals(listOf("Apple proximity pairing"), notice.families)
        assertEquals(-48, notice.medianRssi)
        assertEquals("Pairing flood · 10 new addresses · about -48 dBm", notice.line())
        assertTrue(notice.body().contains("10 new addresses sent pairing advertisements"))
        assertTrue(notice.body().contains("They are about the same loudness, about -48 dBm."))
        assertTrue(notice.body().contains("such as in a store, or one radio changing its address on every packet"))
        assertTrue(notice.body().contains("can do the second"))
        assertTrue(notice.body().contains("Continue leaves them on Live for about the next 15 minutes. Hide these takes this burst, and later bursts in that time, off Live."))
        assertFalse(notice.body().contains("one nearby radio"))
        flood.consider(proximity(10), 1_000L)
        val more = flood.notice.value!!
        assertTrue(more.showDialog)
        assertEquals(11, more.popupCount)
    }

    @Test
    fun mixedPopupFamiliesShareOneCounter() {
        val flood = PairingFlood()
        flood.consider(proximity(0), 0L)
        flood.consider(proximity(1), 0L)
        flood.consider(nearbyAction(2), 0L)
        flood.consider(nearbyAction(3), 0L)
        flood.consider(fastPair(4), 0L)
        repeat(4) { flood.consider(proximity(10 + it), 0L) }
        assertNull(flood.notice.value)
        flood.consider(swift(5), 0L)
        val notice = flood.notice.value!!
        assertEquals(10, notice.popupCount)
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
        repeat(10) { flood.consider(proximity(it), 0L) }
        assertTrue(flood.notice.value!!.showDialog)
        flood.tick(PairingFlood.WINDOW_MS)
        assertTrue(flood.notice.value!!.showDialog)
        flood.tick(PairingFlood.WINDOW_MS + 1)
        assertNull(flood.notice.value)
    }

    @Test
    fun nearbyInfoAndFindMyAreNotPopups() {
        val flood = PairingFlood()
        repeat(12) { flood.consider(apple(it, "100100"), 0L) }
        repeat(12) { flood.consider(apple(20 + it, "120100"), 0L) }
        repeat(12) { flood.consider(apple(40 + it, "020100"), 0L) }
        assertNull(flood.notice.value)
    }

    @Test
    fun findMyThatContainsAProximityShapeStaysQuiet() {
        val flood = PairingFlood()
        val findMy = "12026E0007110654D03727C0AFB609F0BD82E4C265C651"
        repeat(12) { flood.consider(apple(it, findMy), 0L) }
        assertNull(flood.notice.value)
        repeat(10) { flood.consider(apple(40 + it, "120200000F0101"), 0L) }
        val action = flood.notice.value!!
        assertEquals(10, action.popupCount)
        assertEquals(listOf("Apple Nearby Action"), action.families)
        val leading = PairingFlood()
        repeat(10) { leading.consider(apple(it, "07010012020000"), 0L) }
        val notice = leading.notice.value!!
        assertEquals(10, notice.popupCount)
        assertEquals(listOf("Apple proximity pairing"), notice.families)
    }

    @Test
    fun aLongFastPairPayloadIsNotThePairingFrame() {
        val flood = PairingFlood()
        repeat(12) { flood.consider(fastPair(it, payload = "01020304050607"), 0L) }
        assertNull(flood.notice.value)
    }

    @Test
    fun spreadOutRadiosDoNotTrip() {
        val flood = PairingFlood()
        val rssi = intArrayOf(-30, -50, -70, -90, -110, -40, -35, -55, -75, -95)
        rssi.forEachIndexed { i, dbm -> flood.consider(proximity(i, rssi = dbm), 0L) }
        assertNull(flood.notice.value)
    }

    @Test
    fun aWeakPairingClusterStillOpens() {
        val flood = PairingFlood()
        repeat(11) { flood.consider(swift(it, rssi = -92), 0L) }
        val notice = flood.notice.value!!
        assertEquals("Pairing flood", notice.title())
        assertEquals(11, notice.popupCount)
        assertEquals(-92, notice.medianRssi)
        assertEquals(listOf("Swift Pair"), notice.families)
        assertEquals(1, flood.bursts().size)
    }

    @Test
    fun aTightClusterStillCountsWhenAFewAreFar() {
        val flood = PairingFlood()
        val rssi = intArrayOf(-48, -50, -46, -52, -49, -47, -48, -49, -50, -47, -20, -90)
        rssi.forEachIndexed { i, dbm -> flood.consider(proximity(i, rssi = dbm), 0L) }
        val notice = flood.notice.value!!
        assertEquals(10, notice.popupCount)
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
        assertTrue(notice.body().contains("such as tags in a store, or one radio changing its name and address on every packet"))
        assertTrue(notice.body().contains("Continue leaves them on Live for about the next 15 minutes."))
        assertFalse(notice.body().contains("pairing advertisements"))
    }

    @Test
    fun factoryNamedAddressesStayOutOfANameFlood() {
        val flood = PairingFlood()
        repeat(20) {
            flood.consider(
                named(it, mac = "88:0F:62:10:20:%02X".format(it), name = "IL%03X".format(it)),
                0L,
            )
        }
        assertNull(flood.notice.value)
        repeat(14) { flood.consider(named(100 + it), 0L) }
        assertNull(flood.notice.value)
        flood.consider(named(114), 0L)
        val notice = flood.notice.value!!
        assertEquals("Name flood", notice.title())
        assertEquals(0, notice.popupCount)
        assertEquals(15, notice.nameCount)
    }

    @Test
    fun aFactoryAddressStillCountsAsAPairingPopup() {
        val flood = PairingFlood()
        repeat(9) { flood.consider(proximity(it, mac = "00:00:00:00:00:%02X".format(it)), 0L) }
        assertNull(flood.notice.value)
        flood.consider(proximity(9, mac = "00:00:00:00:00:09"), 0L)
        val notice = flood.notice.value!!
        assertEquals("Pairing flood", notice.title())
        assertEquals(10, notice.popupCount)
        assertEquals(0, notice.nameCount)
    }

    @Test
    fun popupAndNameBurstsBothShow() {
        val flood = PairingFlood()
        repeat(10) { flood.consider(proximity(it, rssi = -40), 0L) }
        repeat(15) { flood.consider(named(50 + it, rssi = -70), 0L) }
        val notice = flood.notice.value!!
        assertEquals(10, notice.popupCount)
        assertEquals(15, notice.nameCount)
        assertEquals(-40, notice.medianRssi)
        assertEquals("Pairing flood · 10 new addresses · 15 named · about -40 dBm", notice.line())
        assertTrue(notice.body().contains("10 new addresses sent pairing advertisements"))
        assertTrue(notice.body().contains("15 more each advertised a Bluetooth name"))
    }

    @Test
    fun aFewMeasuredPopupsDoNotBorrowUnmeasuredOnes() {
        val flood = PairingFlood()
        repeat(9) { flood.consider(proximity(it, rssi = -45), 0L) }
        repeat(4) { flood.consider(proximity(20 + it, rssi = 127), 0L) }
        assertNull(flood.notice.value)
    }

    @Test
    fun aNamedPopupDoesNotAlsoCountAsAName() {
        val flood = PairingFlood()
        repeat(10) { flood.consider(proximity(it, name = "Buds $it"), 0L) }
        val notice = flood.notice.value!!
        assertEquals(10, notice.popupCount)
        assertEquals(0, notice.nameCount)
        assertFalse(notice.body().contains("each advertised a Bluetooth name"))
    }

    @Test
    fun dismissKeepsOneLineUntilTheBurstGoesQuiet() {
        val flood = PairingFlood()
        repeat(10) { flood.consider(proximity(it), 1_000L) }
        assertTrue(flood.notice.value!!.showDialog)
        flood.dismiss()
        val line = flood.notice.value!!
        assertFalse(line.showDialog)
        assertTrue(line.line().startsWith("Pairing flood"))
        repeat(10) { flood.consider(proximity(100 + it), 5_000L) }
        assertFalse(flood.notice.value!!.showDialog)
        assertEquals(20, flood.notice.value!!.popupCount)
        flood.tick(1_000L + PairingFlood.WINDOW_MS + 1)
        val mid = flood.notice.value!!
        assertFalse(mid.showDialog)
        assertEquals(10, mid.popupCount)
        flood.tick(5_000L + PairingFlood.WINDOW_MS + 1)
        assertNull(flood.notice.value)
        repeat(10) { flood.consider(proximity(200 + it), 16_000L) }
        val held = flood.notice.value!!
        assertFalse(held.showDialog)
        assertTrue(held.line().startsWith("Pairing flood"))
    }

    @Test
    fun aFifteenMinuteHoldAsksAgainOnTheNextBurst() {
        val flood = PairingFlood()
        val answered = 1_000L
        repeat(10) { flood.consider(proximity(it), answered) }
        flood.dismiss()
        flood.tick(answered + PairingFlood.WINDOW_MS + 1)
        assertNull(flood.notice.value)
        val stillHeld = answered + PairingFlood.HOLD_MS - 2_000L
        repeat(10) { flood.consider(proximity(100 + it), stillHeld) }
        assertFalse(flood.notice.value!!.showDialog)
        flood.tick(answered + PairingFlood.HOLD_MS)
        assertNotNull(flood.notice.value)
        assertFalse(flood.notice.value!!.showDialog)
        flood.tick(stillHeld + PairingFlood.WINDOW_MS + 1)
        assertNull(flood.notice.value)
        repeat(10) { flood.consider(proximity(200 + it), stillHeld + PairingFlood.WINDOW_MS + 2) }
        assertTrue(flood.notice.value!!.showDialog)
    }

    @Test
    fun aFifteenMinuteHoldCarriesIntoASit() {
        val flood = PairingFlood()
        repeat(10) { flood.consider(proximity(it), 1_000L) }
        flood.dismiss()
        flood.tick(1_000L + PairingFlood.WINDOW_MS + 1)
        flood.setSitOpen(true)
        repeat(10) { flood.consider(proximity(100 + it), 1_000L + PairingFlood.HOLD_MS + 1_000L) }
        val carried = flood.notice.value!!
        assertFalse(carried.showDialog)
        assertTrue(carried.duringSit)
        assertTrue(carried.body().contains("for the rest of this sit"))
        flood.tick(1_000L + PairingFlood.HOLD_MS + 1_000L + PairingFlood.WINDOW_MS + 1)
        flood.setSitOpen(false)
        repeat(10) { flood.consider(proximity(200 + it), 1_000L + PairingFlood.HOLD_MS + 20_000L) }
        assertTrue(flood.notice.value!!.showDialog)
        assertFalse(flood.notice.value!!.duringSit)
    }

    @Test
    fun hideHoldsForFifteenMinutesThenAsksAgain() {
        val flood = PairingFlood()
        repeat(10) { flood.consider(proximity(it), 1_000L) }
        flood.setHideBurst(true)
        flood.dismiss()
        flood.tick(1_000L + PairingFlood.WINDOW_MS + 1)
        repeat(10) { flood.consider(proximity(100 + it), 20_000L) }
        assertFalse(flood.notice.value!!.showDialog)
        assertTrue(flood.hide.value.episodeOn)
        assertEquals(20, flood.hide.value.keys.size)
        flood.tick(20_000L + PairingFlood.WINDOW_MS + 1)
        repeat(10) { flood.consider(proximity(200 + it), 1_000L + PairingFlood.HOLD_MS) }
        assertTrue(flood.notice.value!!.showDialog)
        assertFalse(flood.hide.value.keys.contains(burstKey(200)))
    }

    @Test
    fun theDialogDoesNotNameTheTool() {
        val flood = PairingFlood()
        repeat(10) { flood.consider(proximity(it), 0L) }
        val body = flood.notice.value!!.body()
        assertTrue(body.contains("does not name the tool"))
        assertTrue(body.contains("Flipper Zero"))
        assertTrue(body.contains("Marauder"))
        assertTrue(body.contains("Bruce"))
        assertTrue(body.contains("one radio changing its address on every packet"))
        assertTrue(body.contains("can do the second"))
        assertFalse(body.contains("identified", ignoreCase = true))
    }

    @Test
    fun missingRssiFallsBackToTheRawCount() {
        val flood = PairingFlood()
        repeat(10) { flood.consider(proximity(it, rssi = 127), 0L) }
        val notice = flood.notice.value!!
        assertNull(notice.medianRssi)
        assertFalse(notice.line().contains("dBm"))
        assertFalse(notice.body().contains("one nearby radio"))
        assertEquals(10, notice.popupCount)
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
        repeat(7) { layouts.consider(proximity(10 + it), 0L) }
        assertNull(layouts.notice.value)
        layouts.consider(proximity(20), 0L)
        val notice = layouts.notice.value!!
        assertEquals(10, notice.popupCount)
        assertEquals(listOf("Apple proximity pairing", "Fast Pair"), notice.families)
        val flood = PairingFlood()
        repeat(100) { flood.consider(proximity(it), 0L) }
        assertEquals(96, flood.notice.value!!.popupCount)
    }

    @Test
    fun aCrossingKeepsOneBurstUntilTheWindowClears() {
        val flood = PairingFlood()
        repeat(10) { flood.consider(proximity(it), 1_000L) }
        val opened = flood.bursts().single()
        assertEquals(1_000L, opened.at)
        assertEquals(10, opened.popupCount)
        assertEquals(listOf("Apple proximity pairing"), opened.families)
        assertEquals(-48, opened.medianRssi)
        flood.consider(proximity(10), 2_000L)
        val peaked = flood.bursts().single()
        assertEquals(1_000L, peaked.at)
        assertEquals(11, peaked.popupCount)
        assertEquals(
            "12:00 UTC. Pairing flood. 11 new addresses: Apple proximity pairing · about -48 dBm. " +
                "11 addresses from this burst are left out of the counts and lists below.",
            peaked.reportLine("12:00"),
        )
        assertEquals((0..10).map { burstKey(it) }.toSet(), peaked.keys.toSet())
        flood.tick(2_000L + PairingFlood.WINDOW_MS + 1)
        assertNull(flood.notice.value)
        assertEquals(11, flood.bursts().single().popupCount)
        assertEquals((0..10).map { burstKey(it) }.toSet(), flood.bursts().single().keys.toSet())
        repeat(10) { flood.consider(proximity(100 + it), 20_000L) }
        val bursts = flood.bursts()
        assertEquals(2, bursts.size)
        assertEquals(1_000L, bursts[0].at)
        assertEquals(11, bursts[0].popupCount)
        assertEquals((0..10).map { burstKey(it) }.toSet(), bursts[0].keys.toSet())
        assertEquals(20_000L, bursts[1].at)
        assertEquals(10, bursts[1].popupCount)
        assertEquals((100..109).map { burstKey(it) }.toSet(), bursts[1].keys.toSet())
    }

    @Test
    fun hideThisBurstDropsOnlyTheAddressesItCounted() {
        val flood = PairingFlood()
        repeat(9) { flood.consider(proximity(it), 1_000L) }
        flood.setHideBurst(true)
        assertFalse(flood.hide.value.episodeOn)
        assertTrue(flood.hide.value.keys.isEmpty())
        flood.consider(proximity(9), 1_000L)
        val counted = (0..9).map { burstKey(it) }.toSet()
        assertFalse(flood.hide.value.episodeOn)
        assertTrue(flood.hide.value.keys.isEmpty())
        flood.setHideBurst(true)
        assertTrue(flood.hide.value.episodeOn)
        assertEquals(counted, flood.hide.value.keys)
        flood.consider(proximity(10), 2_000L)
        assertTrue(burstKey(10) in flood.hide.value.keys)
        flood.consider(proximity(11, rssi = -100), 2_000L)
        assertFalse(burstKey(11) in flood.hide.value.keys)
        assertEquals((0..10).map { burstKey(it) }.toSet(), flood.bursts().single().keys.toSet())
        flood.setHideBurst(false)
        assertFalse(flood.hide.value.episodeOn)
        assertTrue(flood.hide.value.keys.isEmpty())
        flood.consider(proximity(12), 2_500L)
        assertFalse(burstKey(12) in flood.hide.value.keys)
    }

    @Test
    fun aHiddenBurstOutlastsTheLineAndTheNextBurstStartsVisible() {
        val flood = PairingFlood()
        repeat(10) { flood.consider(proximity(it), 1_000L) }
        flood.setHideBurst(true)
        val first = (0..9).map { burstKey(it) }.toSet()
        flood.tick(1_000L + PairingFlood.WINDOW_MS + 1)
        assertNull(flood.notice.value)
        assertFalse(flood.hide.value.episodeOn)
        assertEquals(first, flood.hide.value.keys)
        repeat(10) { flood.consider(proximity(100 + it), 20_000L) }
        assertFalse(flood.hide.value.episodeOn)
        val second = (100..109).map { burstKey(it) }.toSet()
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
        repeat(10) { flood.consider(proximity(it), 1_000L) }
        flood.setHideBurst(true)
        val keys = (0..9).map { burstKey(it) }.toSet()
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
        repeat(9) { flood.consider(swift(it, payload = beacon), 0L) }
        assertNull(flood.notice.value)
        flood.consider(swift(9, payload = beacon), 0L)
        val notice = flood.notice.value!!
        assertEquals(10, notice.popupCount)
        assertEquals(0, notice.nameCount)
        assertEquals(listOf("Swift Pair"), notice.families)
        flood.setHideBurst(true)
        assertEquals((0..9).map { burstKey(it) }.toSet(), flood.hide.value.keys)

        val quiet = PairingFlood()
        listOf(
            "0300", "030000", "030380", "00", "0209",
            "0109", "01092022B0018CE2", "010F2022",
        ).forEachIndexed { group, payload ->
            repeat(12) { quiet.consider(swift(group * 16 + it, payload = payload), 0L) }
        }
        assertNull(quiet.notice.value)

        val dual = PairingFlood()
        repeat(4) { dual.consider(swift(it, payload = "030080"), 0L) }
        repeat(3) { dual.consider(swift(10 + it, payload = "030180"), 0L) }
        repeat(2) { dual.consider(swift(20 + it, payload = "030280"), 0L) }
        assertNull(dual.notice.value)
        dual.consider(swift(30, payload = "030080"), 0L)
        val mixed = dual.notice.value!!
        assertEquals(10, mixed.popupCount)
        assertEquals(listOf("Swift Pair"), mixed.families)
    }

    @Test
    fun samsungEasySetupCountsAndAFinderServiceDoesNot() {
        val flood = PairingFlood()
        val buds = "42098102141503210109EE7A01"
        val watch = "010002000101FF0000431A"
        repeat(5) { flood.consider(samsung(it, payload = buds), 0L) }
        repeat(4) { flood.consider(samsung(10 + it, payload = watch), 0L) }
        assertNull(flood.notice.value)
        flood.consider(samsung(20, payload = watch), 0L)
        val notice = flood.notice.value!!
        assertEquals(10, notice.popupCount)
        assertEquals(0, notice.nameCount)
        assertEquals(listOf("Samsung Easy Setup"), notice.families)
        flood.setHideBurst(true)
        val keys = (0..4).map { burstKey(it) }.toSet() +
            (10..13).map { burstKey(it) }.toSet() +
            burstKey(20)
        assertEquals(keys, flood.hide.value.keys)

        val quiet = PairingFlood()
        listOf("420981021415032101", "010002000101FF0000", "00").forEachIndexed { group, payload ->
            repeat(12) { quiet.consider(samsung(group * 16 + it, payload = payload), 0L) }
        }
        repeat(12) {
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
        repeat(5) { flood.consider(typo(it, payload = packet), 0L) }
        repeat(4) { flood.consider(typo(10 + it, payload = otherMode), 0L) }
        assertNull(flood.notice.value)
        flood.consider(typo(20, payload = "6DB643CE97FE427C"), 0L)
        val notice = flood.notice.value!!
        assertEquals(10, notice.popupCount)
        assertEquals(0, notice.nameCount)
        assertEquals(listOf("LoveSpouse"), notice.families)
        flood.setHideBurst(true)
        val keys = (0..4).map { burstKey(it) }.toSet() +
            (10..13).map { burstKey(it) }.toSet() +
            burstKey(20)
        assertEquals(keys, flood.hide.value.keys)

        val quiet = PairingFlood()
        listOf("6DB643CE97FE42", "00", "6DB643CE97FE427B000100").forEachIndexed { group, payload ->
            repeat(12) { quiet.consider(typo(group * 16 + it, payload = payload), 0L) }
        }
        repeat(12) {
            quiet.consider(obs(80 + it, serviceUuid = "AE8F", serviceHex = "01020304"), 0L)
            quiet.consider(obs(100 + it, mfgId = 0x00E0, mfgHex = packet), 0L)
        }
        assertNull(quiet.notice.value)
    }

    @Test
    fun aNameBurstLineOmitsPairingFamilies() {
        val line = FloodBurst(0L, popupCount = 0, nameCount = 16, medianRssi = -55).reportLine("09:15")
        assertEquals("09:15 UTC. Name flood. 16 new addresses · about -55 dBm.", line)
        val below = FloodBurst(0L, popupCount = 9, nameCount = 16, medianRssi = -55).reportLine("09:15")
        assertEquals("09:15 UTC. Name flood. 16 new addresses · about -55 dBm.", below)
        val kept = FloodBurst(
            0L,
            popupCount = 7,
            nameCount = 2,
            families = listOf("Swift Pair"),
            medianRssi = -92,
        ).reportLine("10:41")
        assertEquals("10:41 UTC. Pairing flood. 7 new addresses: Swift Pair · about -92 dBm.", kept)
    }

    @Test
    fun continueDuringASitHoldsUntilTheSitEnds() {
        val flood = PairingFlood()
        flood.setSitOpen(true)
        repeat(10) { flood.consider(proximity(it), 1_000L) }
        val first = flood.notice.value!!
        assertTrue(first.showDialog)
        assertTrue(first.duringSit)
        assertTrue(first.body().contains("for the rest of this sit"))
        flood.dismiss()
        assertFalse(flood.notice.value!!.showDialog)
        flood.tick(1_000L + PairingFlood.WINDOW_MS + 1)
        assertNull(flood.notice.value)
        repeat(10) { flood.consider(proximity(100 + it), 16_000L) }
        val later = flood.notice.value!!
        assertFalse(later.showDialog)
        assertTrue(later.line().startsWith("Pairing flood"))
        assertFalse(flood.hide.value.episodeOn)
        assertEquals(2, flood.bursts().size)
        flood.tick(16_000L + PairingFlood.WINDOW_MS + 1)
        assertNull(flood.notice.value)
        repeat(15) { flood.consider(named(200 + it), 40_000L) }
        val namedLater = flood.notice.value!!
        assertFalse(namedLater.showDialog)
        assertEquals("Name flood", namedLater.title())
        assertEquals(3, flood.bursts().size)
        flood.tick(40_000L + PairingFlood.WINDOW_MS + 1)
        flood.setSitOpen(false)
        repeat(15) { flood.consider(named(300 + it), 60_000L) }
        val asked = flood.notice.value!!
        assertTrue(asked.showDialog)
        assertEquals("Name flood", asked.title())
        assertFalse(asked.duringSit)
    }

    @Test
    fun hideDuringASitHidesLaterBurstsUntilTheSwitchGoesOff() {
        val flood = PairingFlood()
        flood.setSitOpen(true)
        repeat(10) { flood.consider(proximity(it), 1_000L) }
        flood.setHideBurst(true)
        flood.dismiss()
        assertTrue(flood.hide.value.episodeOn)
        assertEquals(10, flood.hide.value.keys.size)
        flood.tick(1_000L + PairingFlood.WINDOW_MS + 1)
        assertNull(flood.notice.value)
        assertFalse(flood.hide.value.episodeOn)
        assertEquals(10, flood.hide.value.keys.size)
        repeat(10) { flood.consider(proximity(100 + it), 16_000L) }
        assertFalse(flood.notice.value!!.showDialog)
        assertTrue(flood.hide.value.episodeOn)
        assertEquals(20, flood.hide.value.keys.size)
        assertEquals(2, flood.bursts().size)
        flood.setHideBurst(false)
        assertFalse(flood.hide.value.episodeOn)
        flood.tick(16_000L + PairingFlood.WINDOW_MS + 1)
        repeat(10) { flood.consider(proximity(200 + it), 40_000L) }
        assertFalse(flood.notice.value!!.showDialog)
        assertFalse(flood.hide.value.episodeOn)
        assertFalse(flood.hide.value.keys.contains(burstKey(200)))
    }

    @Test
    fun aWeakBurstWhileDrivingStaysQuiet() {
        val flood = PairingFlood()
        repeat(16) { flood.consider(named(it, rssi = -97), 0L, speedMps = 20.0) }
        assertNull(flood.notice.value)
        assertTrue(flood.bursts().isEmpty())
        val pairing = PairingFlood()
        repeat(12) { pairing.consider(proximity(it, rssi = -96), 0L, speedMps = 12.0) }
        assertNull(pairing.notice.value)
    }

    @Test
    fun theSameWeakBurstWarnsOnFoot() {
        val flood = PairingFlood()
        repeat(16) { flood.consider(named(it, rssi = -97), 0L, speedMps = 1.2) }
        assertEquals("Name flood", flood.notice.value!!.title())
        assertEquals(-97, flood.notice.value!!.medianRssi)
    }

    @Test
    fun aLoudBurstWhileDrivingStillWarns() {
        val flood = PairingFlood()
        repeat(10) { flood.consider(proximity(it, rssi = -55), 0L, speedMps = 20.0) }
        assertEquals("Pairing flood", flood.notice.value!!.title())
        assertEquals(-55, flood.notice.value!!.medianRssi)
    }

    @Test
    fun unknownSpeedDoesNotHideAWeakBurst() {
        val flood = PairingFlood()
        repeat(16) { flood.consider(named(it, rssi = -97), 0L, speedMps = null) }
        assertEquals("Name flood", flood.notice.value!!.title())
    }

    @Test
    fun slowingDownLetsAWeakBurstWarn() {
        val flood = PairingFlood()
        repeat(16) { flood.consider(named(it, rssi = -97), 1_000L, speedMps = 20.0) }
        assertNull(flood.notice.value)
        flood.tick(2_000L, speedMps = 0.0)
        assertEquals("Name flood", flood.notice.value!!.title())
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

    private fun proximity(n: Int, rssi: Int = -48, name: String = "", mac: String? = null) =
        apple(n, "070100", rssi, name, mac)

    private fun nearbyAction(n: Int, rssi: Int = -48) = apple(n, "0F0100", rssi)

    private fun apple(n: Int, hex: String, rssi: Int = -48, name: String = "", mac: String? = null) =
        obs(n, rssi, name, mfgId = 0x004C, mfgHex = hex, mac = mac)

    private fun fastPair(n: Int, rssi: Int = -48, payload: String = "000006") =
        obs(n, rssi, serviceUuid = "FE2C", serviceHex = payload)

    private fun swift(n: Int, rssi: Int = -48, payload: String = "030080") =
        obs(n, rssi, mfgId = 0x0006, mfgHex = payload)

    private fun samsung(n: Int, rssi: Int = -48, payload: String) =
        obs(n, rssi, mfgId = 0x0075, mfgHex = payload)

    private fun typo(n: Int, rssi: Int = -31, payload: String) =
        obs(n, rssi, mfgId = 0x00FF, mfgHex = payload)

    private fun named(n: Int, rssi: Int = -55, mac: String? = null, name: String = "Radio $n") =
        obs(n, rssi, name = name, mac = mac)

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
        mac: String? = null,
    ): Observation {
        val mfg = if (mfgId != null) listOf(MfgRecord(mfgId, mfgHex)) else emptyList()
        val service = if (serviceUuid != null) listOf(ServiceDataRecord(serviceUuid, serviceHex)) else emptyList()
        return Observation(
            kind = kind,
            mac = mac ?: "02:00:00:00:00:%02X".format(n),
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
