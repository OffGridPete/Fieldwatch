package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GnssCalibrationTest {
    @Test
    fun steadyOpenSkyIsGoodAndKeepsTodaysBars() {
        val saved = grade(steady(cn0 = 40.0, sv = 20)) as GnssCalOutcome.Saved
        assertEquals(GnssCalGrade.GOOD, saved.profile.grade)
        assertEquals(0.0, saved.profile.cn0FloorDb, 0.01)
        assertEquals(0.0, saved.profile.agcFloorDb, 0.01)
        assertFalse(saved.profile.ignoreStopped)
        assertFalse(saved.profile.suppressAlerts)
        assertEquals(
            "Calibration complete. This phone is Good. " +
                "It should work with a steady view of the sky, away from other electronics.",
            saved.profile.summary(),
        )
        val rows = GnssCalibration.diagRows(saved.profile).joinToString(" ") { it.first + " " + it.second }
        assertFalse(rows.contains("latitude", ignoreCase = true))
        assertFalse(rows.contains("longitude", ignoreCase = true))
        assertTrue(rows.contains("Good"))
    }

    @Test
    fun weakerSteadyPhoneIsFairAndIgnoresMeasurementGaps() {
        val saved = grade(steady(cn0 = 27.0, sv = 16)) as GnssCalOutcome.Saved
        assertEquals(GnssCalGrade.FAIR, saved.profile.grade)
        assertTrue(saved.profile.ignoreStopped)
        assertFalse(saved.profile.suppressAlerts)
        assertEquals(
            "Calibration complete. This phone is Fair. Usable. " +
                "Small drops will be hard to separate from this phone's own GPS. " +
                "Medium or Low will be steadier than High.",
            saved.profile.summary(),
        )
        val raised = GnssSensitivity.MEDIUM.thresholds().floored(saved.profile.agcFloorDb, saved.profile.cn0FloorDb)
        assertEquals(5.0, raised.agcDropDb, 0.01)
        assertEquals(3.0, raised.cn0DropDb, 0.01)
    }

    @Test
    fun wildSignalIsPoorAndSuppressesThePopup() {
        val epochs = (0 until 55).map { i ->
            val cn0 = if (i % 2 == 0) 30.0 else 40.0
            epoch(i * 1_000L, cn0 = cn0, sv = 20)
        }
        val saved = grade(attempt(epochs)) as GnssCalOutcome.Saved
        assertEquals(GnssCalGrade.POOR, saved.profile.grade)
        assertTrue(saved.profile.suppressAlerts)
        assertTrue(saved.profile.ignoreStopped)
        assertEquals(8.0, saved.profile.cn0FloorDb, 0.01)
        assertEquals(
            "Calibration complete. This phone is Poor. " +
                "This phone's GPS is too coarse for an interference alert. " +
                "It can still tag a path. The popup stays off.",
            saved.profile.summary(),
        )
    }

    @Test
    fun movingPhoneIsNotSaved() {
        val fixes = stillFixes().mapIndexed { i, fix ->
            if (i < 8) fix else fix.copy(lat = fix.lat + 0.002)
        }
        val outcome = grade(attempt(steadyEpochs(40.0, 20), fixes))
        assertTrue(outcome is GnssCalOutcome.Rejected)
        assertEquals(GnssCalibration.MOVING, (outcome as GnssCalOutcome.Rejected).reason)
    }

    @Test
    fun shortRunAndWeakSkyAreRejected() {
        val short = grade(attempt((0 until 10).map { epoch(it * 1_000L, 40.0, 20) }))
        assertEquals(GnssCalibration.FEW_READINGS, (short as GnssCalOutcome.Rejected).reason)
        val indoor = grade(steady(cn0 = 40.0, sv = 4))
        assertEquals(GnssCalibration.FEW_SV, (indoor as GnssCalOutcome.Rejected).reason)
        val weak = grade(steady(cn0 = 12.0, sv = 20))
        assertEquals(GnssCalibration.WEAK, (weak as GnssCalOutcome.Rejected).reason)
        val noFix = grade(attempt(steadyEpochs(40.0, 20), emptyList()))
        assertEquals(GnssCalibration.NO_FIX, (noFix as GnssCalOutcome.Rejected).reason)
    }

    @Test
    fun diagnosticsSaysNotRunUntilAProfileExists() {
        assertEquals("not run", GnssCalibration.diagRows(null).single().second)
    }

    @Test
    fun settingsExportLeavesThePhoneProfileOut() {
        val encoded = SettingsExchange.encode(
            SettingsExchange.pack(
                settings = AppSettings(gnssMonitor = true),
                filter = FilterState(),
                presets = emptyList(),
                watchlist = emptyList(),
                hiddenPresetIds = emptySet(),
                appVersion = "1.1.22",
                exportedAt = "2026-10-08T00:00:00Z",
            ),
        )
        assertFalse(encoded.contains("calibration"))
        assertFalse(encoded.contains("suppressAlerts"))
        assertFalse(encoded.contains("cn0FloorDb"))
    }

    private fun grade(attempt: GnssCalAttempt): GnssCalOutcome =
        GnssCalibration.grade(attempt, 1_700_000_000_000L)

    private fun steady(cn0: Double, sv: Int): GnssCalAttempt =
        attempt(steadyEpochs(cn0, sv), stillFixes())

    private fun steadyEpochs(cn0: Double, sv: Int): List<GnssEpoch> =
        (0 until 55).map { epoch(it * 1_000L, cn0, sv) }

    private fun attempt(epochs: List<GnssEpoch>, fixes: List<GnssCalFix> = stillFixes()): GnssCalAttempt =
        GnssCalAttempt(epochs, fixes, 0L, 60_000L)

    private fun epoch(t: Long, cn0: Double, sv: Int): GnssEpoch = GnssEpoch(
        elapsedMs = t,
        wallMs = t,
        clockDiscontinuity = 0,
        bands = mapOf(
            BandKey(GnssConstellation.GPS, GnssBand.L1) to BandEpoch(
                agcDb = 2.0,
                agcFromEventLevel = true,
                cn0Top3 = cn0,
                svTracked = sv,
            ),
        ),
    )

    private fun stillFixes(): List<GnssCalFix> =
        (0 until 20).map { i ->
            GnssCalFix(
                elapsedMs = i * 3_000L,
                accM = 5f,
                speedMps = 0f,
                lat = 28.78723,
                lon = -81.37300,
            )
        }
}
