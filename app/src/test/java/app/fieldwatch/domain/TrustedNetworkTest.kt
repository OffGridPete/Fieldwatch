package app.fieldwatch.domain

import app.fieldwatch.data.TrustedNetworkStore
import app.fieldwatch.radio.WifiIeParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Synthetic scan snapshots for trusted networks.
 * A field check on a phone is separate and is not this file.
 */
class TrustedNetworkTest {
    @Test
    fun openOrWepAlertsAndWpa2AlreadyInTheSetDoesNot() {
        val psk = rsn(0x02)
        assertTrue(psk.startsWith("RSN"))
        assertTrue(psk.contains("PSK"))
        val wpa3 = TrustedSecurity.parse(SAE)
        val both = TrustedSecurity.parse(psk) + wpa3
        assertNull(TrustedSecurity.downgrade(TrustedSecurity.parse(psk), both))
        assertTrue(TrustedSecurity.parse(OPEN).any { it.auth == TrustedSecurity.AUTH_OPEN })
        assertTrue(TrustedSecurity.parse("[WPA2-PSK-CCMP][ESS]").none { it.auth == TrustedSecurity.AUTH_OPEN })
        assertTrue(TrustedSecurity.parse("WPA2-PSK OPEN").none { it.auth == TrustedSecurity.AUTH_OPEN })
        val mixed = setOf(
            TrustedSecurity.Mark(0, TrustedSecurity.AUTH_OPEN),
            TrustedSecurity.Mark(3, TrustedSecurity.AUTH_PERSONAL),
        )
        assertNull(TrustedSecurity.downgrade(mixed, both))

        val home = placed()
        val open = twice(home, hear(mac = HOME, security = OPEN, lat = LAT, lon = LON))
        assertEquals(listOf(TrustedSecurity.REASON_OPEN), open.alert!!.reasons)
        val wep = twice(home, hear(mac = HOME, security = WEP, lat = LAT, lon = LON))
        assertEquals(listOf(TrustedSecurity.REASON_WEAKER), wep.alert!!.reasons)

        var learned = TrustedNets.hear(home, hear(mac = HOME, security = SAE, lat = LAT, lon = LON), watching(), T0).book
        val still = twice(learned, hear(mac = HOME, security = psk, lat = LAT, lon = LON))
        assertNull(still.alert)

        val blank = twice(home, hear(mac = HOME, security = null, lat = LAT + 0.02, lon = LON))
        assertNull(blank.alert)
        assertEquals(1, blank.book.net().places.size)
        assertTrue(blank.book.net().aps.single().security.none { it.auth == TrustedSecurity.AUTH_OPEN })
        assertEquals(
            TrustedSecurity.REASON_WEAKER,
            TrustedSecurity.downgrade(TrustedSecurity.parse(SAE), TrustedSecurity.parse(rsn(0x01))),
        )
        val enterprise = TrustedNets.trust(TrustedBook(), "Home", listOf(hear(security = rsn(0x01))), T0)
        val saeHit = twice(enterprise, hear(mac = HOME, security = SAE, lat = LAT, lon = LON))
        assertEquals(listOf(TrustedSecurity.REASON_WEAKER), saeHit.alert!!.reasons)
        val pskHome = placed()
        val pskAgain = twice(pskHome, hear(mac = HOME, security = psk, lat = LAT, lon = LON))
        assertNull(pskAgain.alert)
    }

    @Test
    fun unknownFactoryOuiAlertsAndADifferentMakerIsNotLearned() {
        val home = placed()
        val stranger = "11:22:33:44:55:66"
        val hit = twice(home, hear(mac = stranger, lat = LAT, lon = LON))
        assertEquals(listOf(TrustedSecurity.REASON_UNKNOWN), hit.alert!!.reasons)
        assertTrue(hit.alert.offerLearn)
        assertEquals(1, hit.book.net().aps.size)
        val third = TrustedNets.hear(hit.book, hear(mac = stranger, lat = LAT, lon = LON), watching(), T0 + 5_000)
        assertEquals(1, third.book.net().aps.size)

        val local = TrustedNets.hear(
            home,
            hear(mac = "02:AA:BB:CC:DD:11", lat = LAT, lon = LON),
            watching(),
            T0,
        )
        assertNull(local.alert)
        val added = local.book.net().aps.single { it.bssid == "02:AA:BB:CC:DD:11" }
        assertNull(added.oui)
        assertNull(added.vendor)

        val tool = "00:13:37:AA:BB:CC"
        val note = twice(home, hear(mac = tool, lat = LAT, lon = LON))
        assertNull(note.alert)
        assertTrue(note.book.net().aps.none { it.bssid == tool })
        val toolOpen = twice(home, hear(mac = tool, security = OPEN, lat = LAT, lon = LON))
        assertEquals(listOf(TrustedSecurity.REASON_OPEN), toolOpen.alert!!.reasons)
        assertTrue(toolOpen.alert.ouiNote)
        assertEquals(
            "Possible evil twin of \"Home\": open security",
            TrustedNets.sentence("Home", toolOpen.alert.reasons),
        )
    }

    @Test
    fun awayPlacesWindowAndTravel() {
        val home = placed()
        val aged = home.copy(nets = home.nets.map { it.copy(trustedAt = T0 - 10L * DAY) })
        val far = twice(aged, hear(mac = HOME, lat = LAT + 0.02, lon = LON), now = T0)
        assertEquals(listOf(TrustedSecurity.REASON_AWAY), far.alert!!.reasons)
        assertFalse(far.alert.offerLearn)
        assertTrue(far.alert.offerRemember)
        assertEquals(1, far.book.net().places.size)

        val coarse = twice(aged, hear(mac = HOME, lat = LAT + 0.02, lon = LON, accuracy = 100f), now = T0)
        assertNull(coarse.alert)
        assertEquals(1, coarse.book.net().places.size)

        val trip = TrustedNets.hear(home, hear(mac = HOME, lat = LAT + 0.02, lon = LON), watching(), T0 + 2_000)
        assertNull(trip.alert)
        assertEquals(2, trip.book.net().places.size)

        val bare = TrustedNets.trust(TrustedBook(), "Home", listOf(hear()), T0 - 10L * DAY)
        val nowhere = twice(bare, hear(mac = HOME, lat = LAT + 0.02, lon = LON))
        assertNull(nowhere.alert)
        assertTrue(nowhere.book.net().places.isEmpty())

        var growing = TrustedNets.trust(TrustedBook(), "Home", listOf(hear()), T0)
        for (i in 0 until 9) {
            val step = TrustedNets.hear(
                growing,
                hear(mac = HOME, lat = LAT + i * 0.02, lon = LON),
                watching(),
                T0 + i * 1_000,
            )
            assertNull(step.alert)
            growing = step.book
        }
        val places = growing.net().places
        assertEquals(8, places.size)
        assertTrue(places.none { abs(it.lat - LAT) < 1e-9 })
        assertTrue(places.any { abs(it.lat - (LAT + 8 * 0.02)) < 1e-9 })

        val moving = TrustedNets.setTravels(home, "Home", true)
        val during = TrustedNets.hear(moving, hear(mac = HOME, lat = LAT + 0.02, lon = LON), watching(), T0)
        assertNull(during.alert)
        assertEquals(1, during.book.net().places.size)
        val later = twice(
            during.book.copy(nets = during.book.nets.map { it.copy(trustedAt = T0 - 10L * DAY) }),
            hear(mac = HOME, lat = LAT + 0.02, lon = LON),
        )
        assertNull(later.alert)
        assertEquals(1, later.book.net().places.size)
        assertFalse(later.book.net().episodes.any { it.open && TrustedSecurity.REASON_AWAY in it.reasons })
        val stillWeaker = twice(later.book, hear(mac = HOME, security = OPEN, lat = LAT, lon = LON))
        assertEquals(listOf(TrustedSecurity.REASON_OPEN), stillWeaker.alert!!.reasons)

        val between = TrustedNets.hear(home, hear(mac = HOME, lat = LAT + 0.004, lon = LON), watching(), T0)
        assertNull(between.alert)
        assertEquals(1, between.book.net().places.size)
        assertEquals(LAT, between.book.net().places.single().lat, 1e-9)
    }

    @Test
    fun oneHourLearnsASiblingAtHomeAndThenStops() {
        val router = "00:11:22:33:44:01"
        val sib = "00:11:22:33:44:02"
        val trusted = TrustedNets.trust(TrustedBook(), "Home", listOf(hear(mac = router)), T0)
        val home = TrustedNets.hear(trusted, hear(mac = router, lat = LAT, lon = LON), watching(), T0).book
        val hour = watching().copy(learnDays = 0)
        val during = TrustedNets.hear(home, hear(mac = sib, lat = LAT, lon = LON), hour, T0 + HOUR - 1)
        assertNull(during.alert)
        assertTrue(during.book.net().aps.any { it.bssid == sib })
        val after = TrustedNets.hear(home, hear(mac = sib, lat = LAT, lon = LON), hour, T0 + HOUR)
        assertNull(after.alert)
        assertFalse(after.book.net().aps.any { it.bssid == sib })
    }

    @Test
    fun aSiblingNearAUsualPlaceIsLearnedQuietly() {
        val router = "00:11:22:33:44:01"
        val sib = "00:11:22:33:44:02"
        val trusted = TrustedNets.trust(TrustedBook(), "Home", listOf(hear(mac = router)), T0)
        val home = TrustedNets.hear(trusted, hear(mac = router, lat = LAT, lon = LON), watching(), T0).book
        val step = TrustedNets.hear(home, hear(mac = sib, lat = LAT, lon = LON), watching(), T0)
        assertNull(step.alert)
        assertTrue(step.book.net().aps.any { it.bssid == sib })
        val quiet = TrustedNets.hear(home, hear(mac = sib, rssi = -90, lat = LAT, lon = LON), watching(), T0)
        assertNull(quiet.alert)
        assertTrue(quiet.book.net().aps.any { it.bssid == sib })
    }

    @Test
    fun twoFreshScansAlertAndAQuietOrCachedHearDoesNotCount() {
        val home = placed()
        val suspect = hear(mac = OTHER, security = OPEN, lat = LAT, lon = LON)
        val once = TrustedNets.hear(home, suspect, watching(), T0)
        assertNull(once.alert)
        val cached = TrustedNets.hear(once.book, suspect.copy(fresh = false), watching(), T0 + 1_000)
        assertNull(cached.alert)
        val second = TrustedNets.hear(cached.book, suspect, watching(), T0 + 2_000)
        assertNotNull(second.alert)

        val quiet = twice(home, suspect.copy(rssi = -86))
        assertNull(quiet.alert)
        assertFalse(quiet.book.net().pending.containsKey(OTHER))
        val floor = twice(home, suspect.copy(rssi = -85))
        assertNotNull(floor.alert)
    }

    @Test
    fun onePinPerSuspectMovesOnlyWhenLouder() {
        val home = placed()
        val loud = hear(mac = OTHER, security = OPEN, rssi = -40, lat = LAT, lon = LON)
        val hit = twice(home, loud)
        assertNotNull(hit.alert)
        val pinsOn = watching(takPin = true)
        val (pinned, xml) = TrustedNets.pinCalls(hit.book, pinsOn, true, T0 + 1_000)
        assertEquals(1, xml.size)
        assertTrue(xml[0].contains("FW-TWIN-112233445566"))
        assertTrue(xml[0].contains("a-n-G"))
        assertTrue(xml[0].contains("name=\"Green\""))
        assertTrue(xml[0].contains("Where this phone was. Not the access point."))
        assertFalse(xml[0].contains("FIELDWATCH-SELF"))
        assertEquals(LAT, pinned.net().pins.single().lat, 1e-6)

        val weaker = TrustedNets.hear(
            pinned,
            loud.copy(rssi = -70, lat = LAT + 0.05),
            pinsOn,
            T0 + 2_000,
        )
        val (held, skipped) = TrustedNets.pinCalls(weaker.book, pinsOn, true, T0 + 2_000)
        assertTrue(skipped.isEmpty())
        assertEquals(LAT, held.net().pins.single().lat, 1e-6)

        val (refreshed, again) = TrustedNets.pinCalls(weaker.book, pinsOn, true, T0 + 13_000)
        assertEquals(1, again.size)
        assertEquals(LAT, refreshed.net().pins.single().lat, 1e-6)

        val movedLat = LAT + 0.05
        val louder = TrustedNets.hear(refreshed, loud.copy(rssi = -20, lat = movedLat), pinsOn, T0 + 14_000)
        val (moved, sent) = TrustedNets.pinCalls(louder.book, pinsOn, true, T0 + 14_000)
        assertEquals(movedLat, moved.net().pins.single().lat, 1e-6)
        assertTrue(sent[0].contains("a-n-G"))

        val other = hear(mac = "11:22:33:44:55:77", security = OPEN, rssi = -40, lat = LAT, lon = LON)
        val extra = twice(moved, other, pinsOn, T0 + 20_000)
        val (both, _) = TrustedNets.pinCalls(extra.book, pinsOn, true, T0 + 21_000)
        assertEquals(
            setOf("FW-TWIN-112233445566", "FW-TWIN-112233445577"),
            both.net().pins.map { it.uid }.toSet(),
        )
        assertTrue(TrustedNets.twinCallsign("H".repeat(40)).length <= 32)

        val noFix = twice(home, hear(mac = OTHER, security = OPEN))
        assertNotNull(noFix.alert)
        assertTrue(TrustedNets.pinCalls(noFix.book, pinsOn, true, T0).second.isEmpty())

        val (paused, nothing) = TrustedNets.pinCalls(pinned, pinsOn, false, T0 + 30_000)
        assertTrue(nothing.isEmpty())
        assertEquals(pinned.net().pins, paused.net().pins)
        val (masterOff, noPin) = TrustedNets.pinCalls(pinned, watching(enabled = false, takPin = true), true, T0 + 30_000)
        assertTrue(noPin.isEmpty())
        assertEquals(1, masterOff.net().pins.size)
    }

    @Test
    fun anUntrustedNameProducesNoAlertAndNoPin() {
        val step = TrustedNets.hear(TrustedBook(), hear(ssid = "Cafe", mac = OTHER, security = OPEN), watching(), T0)
        assertNull(step.alert)
        assertTrue(step.book.nets.isEmpty())
        assertTrue(TrustedNets.pinCalls(step.book, watching(takPin = true), true, T0).second.isEmpty())
    }

    @Test
    fun learnAddsTheSecurityJustSeenAndIsNotOfferedForAwayOnly() {
        val home = placed()
        val hit = twice(home, hear(mac = OTHER, security = OPEN, lat = LAT, lon = LON))
        assertTrue(hit.alert!!.offerLearn)
        assertTrue(hit.alert.offerIgnore)
        assertFalse(hit.alert.offerRemember)
        val learned = TrustedNets.learnAp(hit.book, "Home", OTHER, T0 + 2_000)
        val ap = learned.net().aps.single { it.bssid == OTHER }
        assertTrue(ap.security.any { it.auth == TrustedSecurity.AUTH_OPEN })
        assertTrue(learned.net().episodes.none { it.open })

        val aged = home.copy(nets = home.nets.map { it.copy(trustedAt = T0 - 10L * DAY) })
        val away = twice(aged, hear(mac = HOME, lat = LAT + 0.02, lon = LON))
        assertFalse(away.alert!!.offerLearn)
        assertTrue(away.alert.offerRemember)
        val remembered = TrustedNets.remember(away.book, "Home", HOME, T0 + 5_000)
        assertEquals(2, remembered.net().places.size)
        assertTrue(remembered.net().episodes.none { it.open })
        val traveling = TrustedNets.setTravels(aged, "Home", true)
        val awayTrip = twice(traveling, hear(mac = HOME, lat = LAT + 0.02, lon = LON))
        assertNull(awayTrip.alert)
    }

    @Test
    fun ignoreStaysQuietAndDismissWaitsTenMinutes() {
        val home = placed()
        val before = home.net().aps.single().security
        val hit = twice(home, hear(mac = OTHER, security = OPEN, lat = LAT, lon = LON))
        val ignored = TrustedNets.ignore(hit.book, "Home", OTHER)
        assertEquals(listOf(OTHER), ignored.net().ignored)
        assertEquals(before, ignored.net().aps.single().security)
        assertEquals(1, ignored.net().aps.size)
        val later = twice(ignored, hear(mac = OTHER, security = OPEN, lat = LAT, lon = LON), now = T0 + 20_000)
        assertNull(later.alert)

        val t = T0 + 5_000
        val dismissed = TrustedNets.dismiss(hit.book, "Home", OTHER, t)
        val quiet = TrustedNets.hear(
            dismissed,
            hear(mac = OTHER, security = OPEN, rssi = -90, fresh = false, lat = LAT, lon = LON),
            watching(),
            t + 60_000,
        )
        assertNull(quiet.alert)
        val held = quiet.book.net().episodes.single { it.bssid == OTHER && it.dismissed }
        assertEquals(t + 60_000, held.lastHeard)
        val still = TrustedNets.hear(
            quiet.book,
            hear(mac = OTHER, security = OPEN, lat = LAT, lon = LON),
            watching(),
            held.lastHeard + TrustedNets.ABSENT_MS - 1,
        )
        assertNull(still.alert)
        val one = TrustedNets.hear(
            quiet.book,
            hear(mac = OTHER, security = OPEN, lat = LAT, lon = LON),
            watching(),
            held.lastHeard + TrustedNets.ABSENT_MS,
        )
        assertNull(one.alert)
        val two = TrustedNets.hear(
            one.book,
            hear(mac = OTHER, security = OPEN, lat = LAT, lon = LON),
            watching(),
            held.lastHeard + TrustedNets.ABSENT_MS + 1_000,
        )
        assertNotNull(two.alert)
    }

    @Test
    fun theTakPinSwitchDefaultsOff() {
        assertFalse(AppSettings().trustedTak)
        assertFalse(TrustedOpt().takPin)
        val hit = twice(placed(), hear(mac = OTHER, security = OPEN, lat = LAT, lon = LON))
        assertNotNull(hit.alert)
        val (book, xml) = TrustedNets.pinCalls(hit.book, watching(), true, T0)
        assertTrue(xml.isEmpty())
        assertTrue(book.net().pins.isEmpty())
        val (pinned, _) = TrustedNets.pinCalls(hit.book, watching(takPin = true), true, T0)
        val (cleared, tombs) = TrustedNets.pinCalls(pinned, watching(), true, T0 + 1_000)
        assertTrue(tombs.single().contains("a-u-G"))
        assertTrue(cleared.net().pins.isEmpty())
    }

    @Test
    fun forgetDeletesTheNameAndRemovingOneAccessPointDoesNot() {
        val both = TrustedNets.trust(
            TrustedBook(),
            "Home",
            listOf(hear(mac = HOME), hear(mac = SIB)),
            T0,
        )
        val left = TrustedNets.removeAp(both, "Home", HOME)
        assertEquals("Home", left.net().ssid)
        assertEquals(listOf(SIB), left.net().aps.map { it.bssid })
        val gone = TrustedNets.forget(left, "Home")
        assertTrue(gone.nets.isEmpty())
        assertNull(TrustedNets.hear(gone, hear(mac = OTHER, security = OPEN), watching(), T0).alert)
    }

    @Test
    fun forgetTombstonesPinsAndABadImportDoesNotWipe() {
        val dir = java.nio.file.Files.createTempDirectory("trusted").toFile()
        try {
            val store = TrustedNetworkStore(File(dir, "trusted-networks.json"), CoroutineScope(Dispatchers.Unconfined))
            val opt = watching(takPin = true)
            store.trust("Home", listOf(hear(lat = LAT, lon = LON)), T0)
            val suspect = hear(mac = OTHER, security = OPEN, lat = LAT, lon = LON)
            store.hear(suspect, opt, T0 + 1_000)
            val alert = store.hear(suspect, opt, T0 + 2_000)
            assertNotNull(alert)
            val plan = store.planCot(opt, true, T0 + 2_000)
            assertTrue(plan.xml.any { it.contains("FW-TWIN-112233445566") })
            store.commitCot(plan)
            store.forget("Home")
            assertTrue(store.snapshot().nets.isEmpty())
            val tombs = store.planCot(opt, true, T0 + 3_000)
            assertTrue(tombs.xml.any { it.contains("a-u-G") })

            store.trust("Home", listOf(hear(lat = 40.123456, lon = LON)), T0)
            val kept = store.hear(hear(mac = HOME, lat = 40.123456, lon = LON), opt, T0 + 10)
            assertEquals(1, store.snapshot().net().places.size)
            val exported = store.exportText()
            assertFalse(exported.contains("40.123456"))
            assertTrue(exported.contains("Home"))
            assertTrue(TrustedExchange.parse(exported)!!.net().places.isEmpty())
            assertFalse(store.importText("nope"))
            assertEquals(1, store.snapshot().nets.size)
            assertFalse(store.importText("""{"format":"fieldwatch-settings","keepScreenOn":true}"""))
            assertEquals(1, store.snapshot().nets.size)
            assertEquals(40.123456, store.snapshot().net().places.single().lat, 1e-9)
            val incoming = TrustedExchange.parse(exported)!!.let { book ->
                book.copy(nets = book.nets.map { net ->
                    net.copy(
                        ignored = listOf("02:00:00:00:00:09"),
                        travels = true,
                        aps = net.aps + hear(mac = SIB).let { pick ->
                            net.aps.single().copy(bssid = SIB, oui = "AA:BB:CC")
                        },
                    )
                })
            }
            val merged = TrustedNets.mergeImport(store.snapshot(), incoming)
            assertEquals(1, merged.net().places.size)
            assertTrue(merged.net().travels)
            assertTrue(merged.net().ignored.contains("02:00:00:00:00:09"))
            assertTrue(merged.net().aps.any { it.bssid == SIB })
            assertEquals(store.snapshot().nets.size, TrustedNets.mergeImport(store.snapshot(), TrustedBook(listOf(TrustedNet(ssid = " ", trustedAt = 1)))).nets.size)
            assertNull(kept)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun anOldSettingsFileStillDecodes() {
        val missing = SettingsExchange.json.decodeFromString(
            AppSettings.serializer(),
            """{"keepScreenOn":true}""",
        )
        assertTrue(missing.keepScreenOn)
        assertFalse(missing.trustedWatch)
        assertTrue(missing.trustedDowngrade)
        assertTrue(missing.trustedVendor)
        assertTrue(missing.trustedAway)
        assertEquals(1_000, missing.trustedAwayMeters)
        assertEquals(-85, missing.trustedFloorDbm)
        assertTrue(missing.trustedLearn)
        assertEquals(3, missing.trustedLearnDays)
        assertTrue(missing.trustedBeep)
        assertTrue(missing.trustedVoice)
        assertFalse(missing.trustedShade)
        assertEquals(GnssAlertFloor.MEDIUM, missing.trustedAlertFloor)
        assertFalse(missing.trustedTak)
        assertEquals(TakGnssHold.HOUR, missing.trustedTakHold)
    }

    @Test
    fun theSitReportIncludesTheBlockWithoutCoordinates() {
        val now = T0
        val window = DebriefWindow(now - 60_000, now, "sit")
        val path = listOf(GpsSample(now - 1_000, LAT, LON))
        val plain = DebriefReport.document(
            devices = emptyList(),
            fleets = emptyList(),
            settings = AppSettings(),
            operatorPath = path,
            now = now,
            window = window,
        )
        assertTrue(plain.sections.none { it.title == "Trusted networks" })
        val line = TrustedSitLine("Home", "House", HOME, listOf(TrustedSecurity.REASON_OPEN), now - 1_000, now)
        val doc = DebriefReport.document(
            devices = emptyList(),
            fleets = emptyList(),
            settings = AppSettings(),
            operatorPath = path,
            now = now,
            window = window,
            trusted = listOf(line),
        )
        val section = doc.sections.single { it.title == "Trusted networks" }
        assertTrue(section.body.contains("Home"))
        assertTrue(section.body.contains("House"))
        assertTrue(section.body.contains("open security"))
        assertTrue(section.body.contains("AA:BB:CC:DD:EE:01"))
        assertFalse(section.body.contains("40"))
        assertFalse(section.body.contains("-75"))
        val early = line.copy(at = window.startAt - 5_000, until = window.startAt - 1)
        val missed = DebriefReport.document(
            devices = emptyList(),
            fleets = emptyList(),
            settings = AppSettings(),
            operatorPath = path,
            now = now,
            window = window,
            trusted = listOf(early),
        )
        assertTrue(missed.sections.none { it.title == "Trusted networks" })
        val overlap = line.copy(at = window.startAt - 5_000, until = window.startAt + 1_000)
        val kept = DebriefReport.document(
            devices = emptyList(),
            fleets = emptyList(),
            settings = AppSettings(),
            operatorPath = path,
            now = now,
            window = window,
            trusted = listOf(overlap),
        )
        assertEquals(1, kept.sections.count { it.title == "Trusted networks" })
        val masked = DebriefReport.document(
            devices = emptyList(),
            fleets = emptyList(),
            settings = AppSettings(demoMode = true),
            operatorPath = path,
            now = now,
            window = window,
            trusted = listOf(line),
        ).sections.single { it.title == "Trusted networks" }
        assertTrue(masked.body.contains("AA:BB:CC:**:**:**"))
        assertFalse(masked.body.contains("DD:EE:01"))
        val prompt = DebriefPrompt.build(
            devices = emptyList(),
            fleets = emptyList(),
            settings = AppSettings(),
            now = now,
            operatorPath = path,
            window = window,
            trusted = listOf(line),
        )
        assertTrue(prompt.contains("open security"))
        assertTrue(prompt.contains("Home"))
    }

    @Test
    fun alsoTrustListsOnlyTheSameOuiAndSecurity() {
        val checked = listOf(hear(mac = "00:11:22:33:44:01"))
        val visible = listOf(
            hear(mac = "00:11:22:33:44:01"),
            hear(ssid = "Home-5", mac = "00:11:22:66:77:88"),
            hear(ssid = "Home Guest", mac = OTHER),
            hear(ssid = "Homf", mac = "00:11:22:99:88:77", security = OPEN),
        )
        assertEquals(listOf("Home-5"), TrustedNets.alsoNames(visible, checked, "Home"))
        assertTrue(TrustedNets.severalMakers(listOf(hear(mac = "00:11:22:33:44:01"), hear(mac = OTHER))))
        assertFalse(TrustedNets.canTrust("", false))
        assertFalse(TrustedNets.canTrust("Home", true))
        assertFalse(TrustedNets.canTrust("<unknown ssid>", false))
        assertTrue(TrustedNets.canTrust("Home", false))
    }

    @Test
    fun masterOffStillLearnsAndDoesNotAlert() {
        val home = placed()
        val off = watching(enabled = false)
        val learned = TrustedNets.hear(home, hear(mac = SIB, lat = LAT, lon = LON), off, T0)
        assertNull(learned.alert)
        assertTrue(learned.book.net().aps.any { it.bssid == SIB })
        val hit = twice(learned.book, hear(mac = OTHER, security = OPEN, lat = LAT, lon = LON), off)
        assertNull(hit.alert)
    }

    private fun rsn(akm: Int): String {
        val ie = WifiIeParser.Ie(
            48,
            byteArrayOf(
                0x01, 0x00,
                0x00, 0x0F, 0xAC.toByte(), 0x04,
                0x01, 0x00,
                0x00, 0x0F, 0xAC.toByte(), 0x04,
                0x01, 0x00,
                0x00, 0x0F, 0xAC.toByte(), akm.toByte(),
            ),
        )
        return WifiIeParser.parseIes(listOf(ie), "").security.orEmpty()
    }

    private fun placed(): TrustedBook {
        val trusted = TrustedNets.trust(TrustedBook(), "Home", listOf(hear()), T0)
        val step = TrustedNets.hear(trusted, hear(mac = HOME, lat = LAT, lon = LON), watching(), T0)
        assertNull(step.alert)
        assertEquals(1, step.book.net().places.size)
        return step.book
    }

    private fun twice(
        book: TrustedBook,
        hear: TwinHear,
        opt: TrustedOpt = watching(),
        now: Long = T0,
    ): TrustedStep {
        val first = TrustedNets.hear(book, hear, opt, now)
        assertNull(first.alert)
        return TrustedNets.hear(first.book, hear, opt, now + 1_000)
    }

    private fun watching(enabled: Boolean = true, takPin: Boolean = false) =
        TrustedOpt(enabled = enabled, takPin = takPin)

    private fun hear(
        ssid: String = "Home",
        mac: String = HOME,
        security: String? = WPA2,
        rssi: Int = -50,
        fresh: Boolean = true,
        lat: Double? = null,
        lon: Double? = null,
        accuracy: Float? = if (lat != null) 10f else null,
        vendor: String? = "Acme",
    ) = TwinHear(
        ssid = ssid,
        bssid = mac,
        rssi = rssi,
        fresh = fresh,
        securityText = security,
        channel = 1,
        frequencyMhz = 2412,
        vendor = vendor,
        lat = lat,
        lon = lon,
        accuracyM = accuracy,
        locationPaused = false,
    )

    private fun TrustedBook.net() = nets.single()

    private companion object {
        const val WPA2 = "RSN PSK CCMP (group CCMP)"
        const val SAE = "RSN SAE CCMP (group CCMP)"
        const val OPEN = "[ESS]"
        const val WEP = "[WEP]"
        const val HOME = "AA:BB:CC:DD:EE:01"
        const val SIB = "AA:BB:CC:DD:EE:02"
        const val OTHER = "11:22:33:44:55:66"
        const val LAT = 40.0
        const val LON = -75.0
        const val T0 = 1_700_000_000_000L
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L
    }
}
