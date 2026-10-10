package app.fieldwatch.domain

import app.fieldwatch.domain.HeaderTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanStatusTest {
    private val now = 1_700_000_000_000L
    private val planted = "28.787120, -81.365491"

    private fun radio(
        scanning: Boolean = true,
        wifiOn: Boolean = true,
        wifiWaitingOnOs: Boolean = false,
        lastWifiScanAt: Long = now - 12_000L,
        bleOn: Boolean = true,
        bleRunning: Boolean = true,
        bleParked: Boolean = false,
        bleRetrying: Boolean = false,
        bleStarting: Boolean = false,
        bleHitsLastMin: Int = 0,
    ) = ScanRadioFacts(
        scanning, wifiOn, wifiWaitingOnOs, lastWifiScanAt,
        bleOn, bleRunning, bleParked, bleRetrying, bleStarting, bleHitsLastMin,
    )

    private fun phone(
        locationOn: Boolean = true,
        locationPermission: Boolean = true,
        gpsFixAgeMs: Long? = 12_000L,
        arrivalsOnly: Boolean = false,
        watchedOnly: Boolean = false,
        customNamesOnly: Boolean = false,
        showOnly: Boolean = false,
        movingWithYou: Boolean = false,
        showWifi: Boolean = true,
        showBle: Boolean = true,
        nameFilter: Boolean = false,
        ouiFilter: Boolean = false,
    ) = ScanPhoneFacts(
        versionName = "1.1.21",
        versionCode = 31,
        catalogVersion = 92,
        manufacturer = "samsung",
        model = "SM-A546U1",
        androidRelease = "16",
        sdkInt = 36,
        fingerprint = "samsung/a54xue/a54x:16/BP2A/123:user/release-keys",
        locationOn = locationOn,
        locationPermission = locationPermission,
        gpsFixAgeMs = gpsFixAgeMs,
        arrivalsOnly = arrivalsOnly,
        watchedOnly = watchedOnly,
        customNamesOnly = customNamesOnly,
        showOnly = showOnly,
        movingWithYou = movingWithYou,
        showWifi = showWifi,
        showBle = showBle,
        nameFilter = nameFilter,
        ouiFilter = ouiFilter,
    )

    @Test
    fun locationOffBlocksAndNamesTheSwitch() {
        val report = ScanStatus.report(radio(), phone(locationOn = false), now)
        assertEquals("Location is off. Android will not deliver scan results.", report.verdict)
        assertTrue(report.blocked)
        assertEquals("off", report.rows.first { it.first == "Location" }.second)
        assertTrue(report.checks.any { it.startsWith("Turn Location on.") })
        assertFalse(report.checks.any { it.contains("Location services") })
    }

    @Test
    fun locationPermissionMissingBlocks() {
        val report = ScanStatus.report(radio(), phone(locationPermission = false), now)
        assertEquals("Location permission is off.", report.verdict)
        assertTrue(report.blocked)
        assertTrue(report.checks.any { it.startsWith("Allow location for Fieldwatch.") })
    }

    @Test
    fun wifiRefusedAndBleParkedWhenRadiosAreOn() {
        val report = ScanStatus.report(
            radio(wifiWaitingOnOs = true, bleParked = true, bleHitsLastMin = 0),
            phone(),
            now,
        )
        assertEquals(
            "Wi-Fi scan refused. BLE scan is parked. Both radios are on.",
            report.verdict,
        )
        assertTrue(report.blocked)
        assertEquals("waiting on OS (12s ago)", report.rows.first { it.first == "Last Wi-Fi scan" }.second)
        assertEquals("parked", report.rows.first { it.first == "BLE" }.second)
        assertEquals("0", report.rows.first { it.first == "BLE results last minute" }.second)
        assertTrue(report.checks.any { it.contains("The Wi-Fi radio is on.") })
        assertTrue(report.checks.any { it.contains("The Bluetooth radio is on.") })
        assertFalse(report.checks.any { it.contains("Wi-Fi scanning") })
        assertFalse(report.checks.any { it.contains("Bluetooth scanning") })
    }

    @Test
    fun quietAirIsNotAFailure() {
        val report = ScanStatus.report(radio(bleHitsLastMin = 0, lastWifiScanAt = now - 40_000L), phone(), now)
        assertEquals("Scans are running. Nothing is on the air.", report.verdict)
        assertEquals(
            listOf("Nothing that needs to be on is off. Scans are running. Nothing is on the air right now."),
            report.checks,
        )
        assertFalse(report.blocked)
        assertEquals("All Traffic", report.rows.first { it.first == "Filters" }.second)
        assertEquals("40s ago", report.rows.first { it.first == "Last Wi-Fi scan" }.second)
        assertEquals("12s ago", report.rows.first { it.first == "GPS fix" }.second)
    }

    @Test
    fun headerColorsFollowTheRadio() {
        val on = radio()
        assertEquals(HeaderTone.HEALTHY, wifiHeader(true, true, on).first)
        assertEquals("Wi-Fi on", wifiHeader(true, true, on).second)
        assertEquals(HeaderTone.HEALTHY, bleHeader(true, true, on).first)
        assertEquals(HeaderTone.OFF, wifiHeader(true, true, radio(wifiOn = false)).first)
        assertEquals(HeaderTone.OFF, bleHeader(true, true, radio(bleOn = false)).first)
        assertEquals(HeaderTone.IDLE, wifiHeader(false, true, on).first)
        assertEquals(
            HeaderTone.PROBLEM,
            wifiHeader(true, true, radio(wifiWaitingOnOs = true)).first,
        )
        assertEquals(
            HeaderTone.PROBLEM,
            wifiHeader(true, false, on).first,
        )
        assertEquals(
            HeaderTone.HEALTHY,
            bleHeader(true, true, radio(bleRunning = false, bleParked = true)).first,
        )
        assertEquals(
            HeaderTone.PROBLEM,
            bleHeader(true, true, radio(bleRunning = false, bleRetrying = true)).first,
        )
        assertEquals(
            HeaderTone.PROBLEM,
            bleHeader(true, true, radio(bleRunning = false)).first,
        )
        assertEquals(null, gpsHeader(false, false, true, "armed", false))
        assertEquals(HeaderTone.HEALTHY, gpsHeader(true, false, true, "armed", false)?.first)
        assertEquals(HeaderTone.IDLE, gpsHeader(true, false, true, "learning 4/60 s", false)?.first)
        assertEquals(HeaderTone.IDLE, gpsHeader(true, false, true, GnssCopy.PAUSED, false)?.first)
        assertEquals(HeaderTone.IDLE, gpsHeader(true, true, true, "armed", false)?.first)
        assertEquals(HeaderTone.PROBLEM, gpsHeader(true, false, true, "armed", true)?.first)
        assertEquals("GPS paused", gpsHeader(true, false, true, GnssCopy.PAUSED, false)?.second)
    }

    @Test
    fun scanOptionsFollowTheSettingsSwitches() {
        val off = ScanStatus.report(radio(), phone().copy(wifiOsThrottled = true), now)
        assertEquals("off", off.rows.first { it.first == "Faster Wi-Fi AP scans" }.second)
        assertEquals("off", off.rows.first { it.first == "Allow background usage" }.second)
        assertEquals("off", off.rows.first { it.first == "Unrestricted battery" }.second)
        assertFalse(off.checks.any { it.contains("throttling") })
        val on = ScanStatus.report(
            radio(),
            phone().copy(wifiOsThrottled = false, backgroundUsage = true, unrestrictedBattery = true),
            now,
        )
        assertEquals("on", on.rows.first { it.first == "Faster Wi-Fi AP scans" }.second)
        assertEquals("on", on.rows.first { it.first == "Allow background usage" }.second)
        assertEquals("on", on.rows.first { it.first == "Unrestricted battery" }.second)
    }

    @Test
    fun radiosOnTheAirAreNotCalledQuiet() {
        val report = ScanStatus.report(radio(), phone(), now, radiosOnAir = 12)
        assertEquals("Scans are running.", report.verdict)
        assertFalse(report.verdict.contains("Nothing is on the air"))
        assertEquals(
            listOf("Nothing that needs to be on is off. Scans are running."),
            report.checks,
        )
        assertFalse(report.blocked)
    }

    @Test
    fun aFilterHidesTheListWithoutBlocking() {
        val flags = listOf(
            phone(arrivalsOnly = true),
            phone(watchedOnly = true),
            phone(customNamesOnly = true),
            phone(showOnly = true),
            phone(movingWithYou = true),
            phone(showWifi = false),
            phone(nameFilter = true),
            phone(ouiFilter = true),
        )
        flags.forEach { facts ->
            val report = ScanStatus.report(radio(), facts, now)
            assertEquals("Scans are running. A filter is hiding the list.", report.verdict)
            assertFalse(report.blocked)
            val filters = report.rows.first { it.first == "Filters" }.second
            assertFalse(filters == "All Traffic")
            assertTrue(report.checks.single().contains(filters))
            assertTrue(report.checks.single().contains("All Traffic"))
        }
    }

    @Test
    fun bleStillStartingIsNotBlocked() {
        val facts = radio(
            wifiOn = false,
            lastWifiScanAt = 0L,
            bleOn = true,
            bleRunning = false,
            bleStarting = true,
        )
        val report = ScanStatus.report(facts, phone(), now)
        assertEquals("Scanning is starting.", report.verdict)
        assertFalse(report.blocked)
        assertFalse(ScanStatus.isBlocked(facts, locationOn = true, locationPermission = true))
        assertEquals("starting", report.rows.first { it.first == "BLE" }.second)
        assertEquals("none", report.rows.first { it.first == "Last Wi-Fi scan" }.second)
    }

    @Test
    fun copyTextHasNoCoordinatesOrRadioNames() {
        val report = ScanStatus.report(radio(), phone(gpsFixAgeMs = null), now)
        assertEquals("none", report.rows.first { it.first == "GPS fix" }.second)
        assertTrue(report.text.startsWith("App: 1.1.21 (31)\n"))
        assertTrue(report.text.contains("Verdict: ${report.verdict}\n"))
        assertTrue(report.text.contains("Check:\n- Nothing that needs to be on is off."))
        assertFalse(report.text.contains(planted))
        assertFalse(report.text.contains("latitude", ignoreCase = true))
        assertFalse(report.text.contains("longitude", ignoreCase = true))
        assertFalse(report.text.contains("SSID", ignoreCase = true))
        val labels = report.rows.map { it.first }
        assertEquals(
            listOf(
                "App", "Catalog", "Phone", "Android", "Fingerprint",
                "Wi-Fi", "Last Wi-Fi scan", "Bluetooth", "BLE", "BLE results last minute",
                "Faster Wi-Fi AP scans", "Allow background usage", "Unrestricted battery",
                "Location", "Location permission", "GPS fix", "Filters",
            ),
            labels,
        )
        assertEquals("samsung SM-A546U1", report.rows.first { it.first == "Phone" }.second)
        assertEquals("16 (API 36)", report.rows.first { it.first == "Android" }.second)
    }

    @Test
    fun waitingWithNoPriorScanOmitsTheAge() {
        val report = ScanStatus.report(
            radio(wifiWaitingOnOs = true, lastWifiScanAt = 0L, bleOn = false, bleRunning = false),
            phone(),
            now,
        )
        assertEquals("waiting on OS", report.rows.first { it.first == "Last Wi-Fi scan" }.second)
        assertTrue(report.blocked)
    }

    @Test
    fun scanningStoppedBlocksEvenWhenRadiosLookOn() {
        val report = ScanStatus.report(radio(scanning = false), phone(), now)
        assertEquals("Scanning is not running.", report.verdict)
        assertTrue(report.blocked)
    }

    @Test
    fun olderThanAMinuteReadsInMinutes() {
        val report = ScanStatus.report(
            radio(lastWifiScanAt = now - 125_000L),
            phone(gpsFixAgeMs = 125_000L),
            now,
        )
        assertEquals("2 min ago", report.rows.first { it.first == "Last Wi-Fi scan" }.second)
        assertEquals("2 min ago", report.rows.first { it.first == "GPS fix" }.second)
    }
}
