package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GnssInterferenceTest {
    @Test
    fun theQuietMinuteIsNotTheBaseline() {
        val det = GnssDetector()
        val wild = feed(det, 60, 0L, agc = 20.0, cn0 = 15.0, sv = 3)
        assertFalse(wild.armed)
        assertTrue(wild.lives.isEmpty())
        assertTrue(wild.statusLine.startsWith("settling"))
        val learned = feed(det, 60, 60_000L, agc = 2.0, cn0 = 40.0, sv = 11)
        assertTrue(learned.armed)
        assertTrue(learned.lives.isEmpty())
        val snap = feed(det, 30, 120_000L, agc = -8.0, cn0 = 32.0, sv = 11)
        val hit = snap.lives.single { it.kind == GnssKind.INTERFERENCE }
        assertEquals(GnssConfidence.MEDIUM, hit.confidence)
    }

    @Test
    fun aDropDuringTheQuietMinuteStaysQuiet() {
        val det = GnssDetector()
        feed(det, 30, 0L, agc = 2.0, cn0 = 40.0, sv = 11)
        val snap = feed(det, 20, 30_000L, agc = -20.0, cn0 = 10.0, sv = 0)
        assertFalse(snap.armed)
        assertTrue(snap.lives.isEmpty())
        assertTrue(snap.marks.isEmpty())
    }

    @Test
    fun cleanRunStaysQuiet() {
        val snap = feed(quiet(), 80, 0L, agc = 2.0, cn0 = 40.0, sv = 11)
        assertTrue(snap.lives.isEmpty())
        assertTrue(snap.marks.isEmpty())
        assertTrue(snap.armed)
    }

    @Test
    fun agcAndSignalDropIsMediumInterference() {
        val det = quiet()
        feed(det, 60, 0L, agc = 2.0, cn0 = 40.0, sv = 11)
        val snap = feed(det, 30, 60_000L, agc = -8.0, cn0 = 32.0, sv = 11)
        val hit = snap.lives.single { it.kind == GnssKind.INTERFERENCE }
        assertEquals(GnssConfidence.MEDIUM, hit.confidence)
        assertTrue(hit.detail.contains("GPS L1"))
        assertFalse(hit.detail.contains("jammer", ignoreCase = true))
    }

    @Test
    fun lossOfSatellitesOnEventAgcIsHigh() {
        val det = quiet()
        feed(det, 60, 0L, agc = 2.0, cn0 = 40.0, sv = 11)
        val snap = feed(det, 30, 60_000L, agc = -10.0, cn0 = 30.0, sv = 0)
        val hit = snap.lives.single { it.kind == GnssKind.INTERFERENCE }
        assertEquals(GnssConfidence.HIGH, hit.confidence)
    }

    @Test
    fun sharedAgcCountsOnce() {
        val det = quiet()
        feed(det, 60, 0L, agc = 2.0, cn0 = 40.0, sv = 11, galAgc = 2.0)
        val snap = feed(det, 30, 60_000L, agc = -8.0, cn0 = 32.0, sv = 11, galAgc = -8.0)
        val hit = snap.lives.single { it.kind == GnssKind.INTERFERENCE }
        assertEquals(GnssConfidence.MEDIUM, hit.confidence)
    }

    @Test
    fun twoIndependentBandsAreHigh() {
        val det = quiet()
        feed(det, 60, 0L, agc = 2.0, cn0 = 40.0, sv = 11, galAgc = 12.0, galCn0 = 40.0)
        val snap = feed(det, 30, 60_000L, agc = -8.0, cn0 = 32.0, sv = 11, galAgc = 2.0, galCn0 = 32.0)
        val hit = snap.lives.single { it.kind == GnssKind.INTERFERENCE }
        assertEquals(GnssConfidence.HIGH, hit.confidence)
    }

    @Test
    fun aSuddenLossOfMeasurementsIsAPause() {
        val det = quiet()
        feed(det, 70, 0L, agc = 2.0, cn0 = 40.0, sv = 11)
        val paused = det.onEpoch(GnssEpoch(70_000L, 70_000L, 0, emptyMap()))
        assertEquals(GnssCopy.PAUSED, paused.statusLine)
        assertTrue(paused.lives.isEmpty())
        assertTrue(paused.marks.isEmpty())
        val back = feed(det, 20, 80_000L, agc = 2.0, cn0 = 40.0, sv = 11)
        assertTrue(back.lives.isEmpty())
        assertTrue(back.statusLine.startsWith("armed"))
    }

    @Test
    fun engineStoppedDoesNotAlertAndDoesNotMoveTheBaseline() {
        val det = quiet()
        feed(det, 70, 0L, agc = 2.0, cn0 = 40.0, sv = 11)
        val stopped = feed(
            det,
            3,
            70_000L,
            agc = -30.0,
            cn0 = 8.0,
            sv = 11,
            side = GnssSide(engineStopped = true),
        )
        assertEquals(GnssCopy.PAUSED, stopped.statusLine)
        assertTrue(stopped.lives.isEmpty())
        assertTrue(stopped.marks.isEmpty())
        val warming = feed(det, 5, 73_000L, agc = -12.0, cn0 = 30.0, sv = 11)
        assertEquals(GnssCopy.PAUSED, warming.statusLine)
        assertTrue(warming.lives.isEmpty())
        val hit = feed(det, 20, 78_000L, agc = -12.0, cn0 = 30.0, sv = 11)
        val live = hit.lives.single { it.kind == GnssKind.INTERFERENCE }
        assertEquals(GnssConfidence.MEDIUM, live.confidence)
    }

    @Test
    fun missingAgcIsNotAveragedAsZero() {
        val det = quiet()
        feed(det, 70, 0L, agc = 2.0, cn0 = 40.0, sv = 11)
        val snap = feed(det, 20, 70_000L, agc = null, cn0 = 40.0, sv = 11)
        assertTrue(snap.lives.none { it.kind == GnssKind.INTERFERENCE })
        assertFalse(snap.statusLine == GnssCopy.PAUSED)
    }

    @Test
    fun signalDropWithoutAgcDropStaysQuiet() {
        val det = quiet()
        feed(det, 60, 0L, agc = 2.0, cn0 = 40.0, sv = 11)
        val snap = feed(det, 30, 60_000L, agc = 2.0, cn0 = 28.0, sv = 4)
        assertTrue(snap.lives.none { it.kind == GnssKind.INTERFERENCE })
    }

    @Test
    fun nonsenseAgcIsIgnored() {
        val det = quiet()
        feed(det, 60, 0L, agc = 2.0, cn0 = 40.0, sv = 11)
        val snap = feed(det, 30, 60_000L, agc = -90.0, cn0 = 32.0, sv = 11)
        assertTrue(snap.lives.none { it.kind == GnssKind.INTERFERENCE })
    }

    @Test
    fun alreadyPresentDoesNotAlert() {
        val snap = feed(quiet(), 90, 0L, agc = -12.0, cn0 = 28.0, sv = 4)
        assertTrue(snap.lives.isEmpty())
    }

    @Test
    fun calibrationFloorHoldsAMediumDropThatTheUserBarWouldAllow() {
        val open = quiet(GnssConfig(sensitivity = GnssSensitivity.MEDIUM))
        feed(open, 60, 0L, agc = 2.0, cn0 = 40.0, sv = 11)
        val hit = feed(open, 30, 60_000L, agc = -8.0, cn0 = 32.0, sv = 11)
        assertEquals(GnssConfidence.MEDIUM, hit.lives.single { it.kind == GnssKind.INTERFERENCE }.confidence)

        val floored = quiet(GnssConfig(sensitivity = GnssSensitivity.MEDIUM, agcFloorDb = 12.0))
        feed(floored, 60, 0L, agc = 2.0, cn0 = 40.0, sv = 11)
        val quiet = feed(floored, 30, 60_000L, agc = -8.0, cn0 = 32.0, sv = 11)
        assertTrue(quiet.lives.none { it.kind == GnssKind.INTERFERENCE })
    }

    @Test
    fun moreSensitiveFiresOnASmallerDrop() {
        val normal = quiet(GnssConfig(sensitivity = GnssSensitivity.MEDIUM))
        feed(normal, 60, 0L, agc = 2.0, cn0 = 40.0, sv = 11)
        val quiet = feed(normal, 30, 60_000L, agc = -2.0, cn0 = 36.0, sv = 11)
        assertTrue(quiet.lives.none { it.kind == GnssKind.INTERFERENCE })

        val more = quiet(GnssConfig(sensitivity = GnssSensitivity.HIGH))
        feed(more, 60, 0L, agc = 2.0, cn0 = 40.0, sv = 11)
        val hit = feed(more, 30, 60_000L, agc = -2.0, cn0 = 36.0, sv = 11)
        assertEquals(GnssConfidence.MEDIUM, hit.lives.single { it.kind == GnssKind.INTERFERENCE }.confidence)
    }

    @Test
    fun invertedAgcTreatsARiseAsADrop() {
        val det = quiet(GnssConfig(invertAgc = true))
        feed(det, 60, 0L, agc = 2.0, cn0 = 40.0, sv = 11)
        val snap = feed(det, 30, 60_000L, agc = 12.0, cn0 = 32.0, sv = 11)
        assertEquals(GnssConfidence.MEDIUM, snap.lives.single { it.kind == GnssKind.INTERFERENCE }.confidence)
    }

    @Test
    fun gainDownSignalUpIsSpoofing() {
        val det = quiet(GnssConfig(spoofChecks = true))
        feed(det, 60, 0L, agc = 2.0, cn0 = 40.0, sv = 11)
        val snap = feed(det, 50, 60_000L, agc = -8.0, cn0 = 42.0, sv = 11)
        val spoof = snap.lives.single { it.kind == GnssKind.SPOOFING }
        assertTrue(spoof.confidence >= GnssConfidence.MEDIUM)
        assertTrue(spoof.detail.contains("Gain is down"))
    }

    @Test
    fun oneMismatchDoesNotWarn() {
        for (side in listOf(
            GnssSide(timeDisagree = true),
            GnssSide(positionDisagree = true),
            GnssSide(impossibleHop = true),
        )) {
            val det = quiet(GnssConfig(spoofChecks = true))
            feed(det, 60, 0L, agc = 2.0, cn0 = 40.0, sv = 11)
            val snap = feed(det, 30, 60_000L, agc = 2.0, cn0 = 40.0, sv = 11, side = side)
            assertTrue(snap.lives.none { it.kind == GnssKind.SPOOFING })
            assertTrue(snap.marks.none { it.kind == "spoofing" })
        }
    }

    @Test
    fun twoMismatchesWarnWithoutAGainDrop() {
        val det = quiet(GnssConfig(spoofChecks = true))
        feed(det, 60, 0L, agc = 2.0, cn0 = 40.0, sv = 11)
        val snap = feed(
            det, 5, 60_000L, agc = 2.0, cn0 = 40.0, sv = 11,
            side = GnssSide(timeDisagree = true, positionDisagree = true),
        )
        val spoof = snap.lives.single { it.kind == GnssKind.SPOOFING }
        assertEquals(GnssConfidence.MEDIUM, spoof.confidence)
        assertTrue(spoof.detail.contains("GPS time and network time disagree."))
        assertTrue(spoof.detail.contains("GPS position and network position disagree."))
        assertFalse(spoof.detail.contains("Gain is down"))
    }

    @Test
    fun positionMismatchRaisesTheGainPattern() {
        val det = quiet(GnssConfig(spoofChecks = true))
        feed(det, 60, 0L, agc = 2.0, cn0 = 40.0, sv = 11)
        val snap = feed(
            det, 20, 60_000L, agc = -8.0, cn0 = 40.0, sv = 11,
            side = GnssSide(positionDisagree = true),
        )
        val spoof = snap.lives.single { it.kind == GnssKind.SPOOFING }
        assertTrue(spoof.confidence >= GnssConfidence.MEDIUM)
        assertTrue(spoof.detail.contains("GPS position and network position disagree."))
    }

    @Test
    fun coarseOrBriefNetworkFixDoesNotCount() {
        assertFalse(GnssPositionWitness.rawDisagree(1.5, 100.0, 500.0, 1_000L, 1_000L))
        assertFalse(GnssPositionWitness.rawDisagree(1.5, 40.0, 140.0, 1_000L, 1_000L))
        assertTrue(GnssPositionWitness.rawDisagree(1.5, 40.0, 142.0, 1_000L, 1_000L))
        assertFalse(GnssPositionWitness.rawDisagree(1.5, 40.0, 500.0, 11_000L, 1_000L))
        assertFalse(GnssPositionWitness.rawDisagree(0.0, 40.0, 500.0, 1_000L, 1_000L))
        val started = GnssPositionWitness.hold(true, 0L, 1_000L)
        assertFalse(started.on)
        assertFalse(GnssPositionWitness.hold(true, started.sinceMs, 1_000L + 14_000L).on)
        assertTrue(GnssPositionWitness.hold(true, started.sinceMs, 1_000L + 15_000L).on)
        assertEquals(0L, GnssPositionWitness.hold(false, started.sinceMs, 2_000L).sinceMs)
    }

    @Test
    fun timeCheckWithGainDropReachesHighThenDemotesWhileInterferenceIsUp() {
        val det = quiet(GnssConfig(spoofChecks = true))
        feed(det, 60, 0L, agc = 2.0, cn0 = 40.0, sv = 11)
        feed(det, 20, 60_000L, agc = -8.0, cn0 = 42.0, sv = 11)
        val snap = feed(
            det, 15, 80_000L, agc = -8.0, cn0 = 42.0, sv = 11,
            side = GnssSide(timeDisagree = true),
        )
        val spoof = snap.lives.single { it.kind == GnssKind.SPOOFING }
        assertEquals(GnssConfidence.MEDIUM, spoof.confidence)
        assertTrue(spoof.detail.contains("side effect"))
    }

    @Test
    fun mockLocationIsItsOwnMessage() {
        val det = quiet()
        val snap = feed(det, 5, 0L, agc = 2.0, cn0 = 40.0, sv = 11, side = GnssSide(mock = true))
        val mock = snap.lives.single { it.kind == GnssKind.MOCK }
        assertTrue(mock.detail.contains("mock location"))
        assertTrue(snap.lives.none { it.kind == GnssKind.SPOOFING })
    }

    @Test
    fun noAgcFallsBackToLowOnly() {
        val det = quiet()
        feed(det, 60, 0L, agc = null, cn0 = 40.0, sv = 10)
        val snap = feed(det, 30, 60_000L, agc = null, cn0 = 30.0, sv = 4)
        val hit = snap.lives.single { it.kind == GnssKind.INTERFERENCE }
        assertEquals(GnssConfidence.LOW, hit.confidence)
        assertTrue(snap.statusLine.contains("low confidence") || hit.confidence == GnssConfidence.LOW)
    }

    @Test
    fun recoveryEndsTheEpisode() {
        val det = quiet()
        feed(det, 60, 0L, agc = 2.0, cn0 = 40.0, sv = 11)
        feed(det, 30, 60_000L, agc = -10.0, cn0 = 30.0, sv = 11)
        val snap = feed(det, 100, 90_000L, agc = 2.0, cn0 = 40.0, sv = 11)
        assertTrue(snap.lives.none { it.kind == GnssKind.INTERFERENCE })
        assertTrue(snap.marks.any { it.kind == "interference" && it.endedAt > 0L })
    }

    @Test
    fun popupSaysWhyBeforeTheNumbers() {
        val det = quiet()
        feed(det, 60, 0L, agc = 2.0, cn0 = 40.0, sv = 11)
        val snap = feed(det, 30, 60_000L, agc = -8.0, cn0 = 32.0, sv = 11)
        val open = GnssAlerts().onLives(snap.lives, GnssConfidence.LOW, 1L).notice!!
        assertTrue(open.showDialog)
        assertEquals("Possible GPS interference (Medium)", open.title)
        assertTrue(open.body.startsWith("This phone's GPS got weaker."))
        assertTrue(open.body.contains("turned its gain down"))
        assertFalse(open.body.contains("AGC"))
        assertTrue(open.details.contains("AGC"))
        assertTrue(open.body.contains("router"))
        assertFalse(open.body.contains("jammer", ignoreCase = true))
        assertFalse(open.body.contains("spoofer", ignoreCase = true))
        assertTrue(open.line.contains("GNSS"))
    }

    @Test
    fun spoofPopupNamesThePatternBeforeTheNumbers() {
        val det = quiet(GnssConfig(spoofChecks = true))
        feed(det, 60, 0L, agc = 2.0, cn0 = 40.0, sv = 11)
        val snap = feed(
            det, 20, 60_000L, agc = -8.0, cn0 = 40.0, sv = 11,
            side = GnssSide(positionDisagree = true),
        )
        val spoof = snap.lives.single { it.kind == GnssKind.SPOOFING }
        val open = GnssAlerts().onLives(listOf(spoof), GnssConfidence.LOW, 1L).notice!!
        assertTrue(open.title.startsWith("Possible GPS spoofing"))
        assertTrue(open.body.startsWith("The receiver turned its gain down, and the satellite signals stayed up."))
        assertTrue(open.body.contains("GPS position does not match the network position."))
        assertFalse(open.body.contains("AGC"))
        assertTrue(open.details.contains("AGC"))
        assertFalse(open.body.contains("spoofer", ignoreCase = true))
    }

    @Test
    fun popupFollowsTheFloorAndDismissLeavesTheLine() {
        val alerts = GnssAlerts()
        val live = GnssLive(
            kind = GnssKind.INTERFERENCE,
            confidence = GnssConfidence.MEDIUM,
            startedWall = 1_000L,
            detail = "GPS L1: AGC -6.0 dB, signal -4.0 dB, 11 satellites (baseline 11)",
            adapted = false,
            rose = true,
        )
        assertNull(alerts.onLives(listOf(live), GnssConfidence.HIGH, 2_000L).notice)
        val open = alerts.onLives(listOf(live.copy(rose = false)), GnssConfidence.MEDIUM, 3_000L)
        assertNotNull(open.notice)
        assertTrue(open.notice!!.showDialog)
        assertTrue(open.notice.body.contains("Wi-Fi router"))
        assertFalse(open.notice.body.contains("jammer", ignoreCase = true))
        assertEquals("Possible G P S interference", open.cue!!.phrase)
        alerts.dismiss()
        val held = alerts.onLives(listOf(live.copy(rose = false)), GnssConfidence.MEDIUM, 4_000L)
        assertFalse(held.notice!!.showDialog)
        assertTrue(held.notice.line.contains("Medium"))
        assertNull(held.cue)
        val ended = alerts.onLives(emptyList(), GnssConfidence.MEDIUM, 5_000L)
        assertTrue(ended.notice!!.line.contains("ended"))
    }

    @Test
    fun debriefIncludesTheHitAndLeavesCoordinatesOut() {
        val now = 1_700_000_000_000L
        val mark = GnssMark(
            at = now - 60_000L,
            kind = "interference",
            confidence = "Medium",
            detail = "GPS L1: AGC -9.0 dB, signal -7.0 dB, 8 satellites (baseline 12)",
        )
        val doc = DebriefReport.document(
            devices = emptyList(),
            fleets = emptyList(),
            settings = AppSettings(tagLocation = false),
            operatorPath = emptyList(),
            now = now,
            window = DebriefWindow(now - 15 * 60_000L, now, "lot"),
            gnss = listOf(mark),
        )
        val section = doc.sections.single { it.title == "GNSS" }
        assertTrue(section.body.contains("Possible GNSS interference"))
        assertTrue(section.body.contains("Wi-Fi router"))
        assertFalse(section.body.contains("latitude", ignoreCase = true))
        assertFalse(section.body.contains("longitude", ignoreCase = true))
        assertTrue(section.alert)
        assertNull(doc.pathFigure)
        assertFalse(section.body.contains("path map"))
    }

    @Test
    fun debriefMarksThePathWithoutCoordinates() {
        val now = 1_700_000_000_000L
        val mark = GnssMark(
            at = now - 60_000L,
            endedAt = now - 30_000L,
            kind = "interference",
            confidence = "High",
            detail = "GPS L1: AGC -8.0 dB, signal -6.0 dB, 7 satellites (baseline 11)",
        )
        val path = listOf(
            GpsSample(now - 90_000L, 28.7800, -81.3700),
            GpsSample(now - 50_000L, 28.7810, -81.3710),
        )
        val doc = DebriefReport.document(
            devices = emptyList(),
            fleets = emptyList(),
            settings = AppSettings(tagLocation = true),
            operatorPath = path,
            now = now,
            window = DebriefWindow(now - 15 * 60_000L, now, "lot"),
            gnss = listOf(mark),
        )
        val section = doc.sections.single { it.title == "GNSS" }
        assertTrue(section.body.contains("Possible GNSS interference"))
        assertTrue(section.body.contains("path map marks where this phone was"))
        assertFalse(section.body.contains("28.78"))
        assertFalse(section.body.contains("jammer", ignoreCase = true))
        val figure = doc.pathFigure!!
        val pin = figure.gnss.single()
        assertEquals(28.7810, pin.lat, 0.00001)
        assertTrue(pin.keyLine().contains("High"))
        assertTrue(pin.keyLine().contains("AGC -8.0 dB"))
        assertFalse(pin.keyLine().contains("28.781"))
        assertTrue(figure.caption.contains("red diamond"))
        assertFalse(figure.caption.contains("28.781"))
    }

    private fun quiet(config: GnssConfig = GnssConfig()) = GnssDetector(config.copy(settleMs = 0))

    private fun feed(
        det: GnssDetector,
        n: Int,
        start: Long,
        agc: Double?,
        cn0: Double,
        sv: Int,
        galAgc: Double? = null,
        galCn0: Double? = null,
        side: GnssSide = GnssSide(),
    ): GnssSnapshot {
        var last: GnssSnapshot? = null
        for (i in 0 until n) {
            last = det.onEpoch(epoch(start + i * 1_000L, agc, cn0, sv, galAgc, galCn0), side)
        }
        return last!!
    }

    private fun epoch(
        t: Long,
        agc: Double?,
        cn0: Double,
        sv: Int,
        galAgc: Double?,
        galCn0: Double?,
    ): GnssEpoch {
        val bands = LinkedHashMap<BandKey, BandEpoch>()
        bands[BandKey(GnssConstellation.GPS, GnssBand.L1)] = BandEpoch(
            agcDb = agc,
            agcFromEventLevel = agc != null,
            cn0Top3 = cn0,
            svTracked = sv,
        )
        if (galAgc != null || galCn0 != null) {
            bands[BandKey(GnssConstellation.GALILEO, GnssBand.L1)] = BandEpoch(
                agcDb = galAgc,
                agcFromEventLevel = galAgc != null,
                cn0Top3 = galCn0 ?: cn0,
                svTracked = sv,
            )
        }
        return GnssEpoch(t, t, 0, bands)
    }
}
