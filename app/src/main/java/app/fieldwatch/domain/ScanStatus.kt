package app.fieldwatch.domain

/**
 * Settings → Diagnostics. Phone and scan facts only.
 * No network names, MACs, or GPS coordinates.
 */
enum class HeaderTone { OFF, IDLE, HEALTHY, PROBLEM }

/** Wi-Fi icon in the list header. Green is healthy, including the wait between scans. */
fun wifiHeader(scanning: Boolean, locationOn: Boolean, radio: ScanRadioFacts): Pair<HeaderTone, String> {
    if (!radio.wifiOn) return HeaderTone.OFF to "Wi-Fi off"
    if (!scanning) return HeaderTone.IDLE to "Wi-Fi idle"
    if (!locationOn) return HeaderTone.PROBLEM to "Wi-Fi, Location is off"
    if (radio.wifiWaitingOnOs) return HeaderTone.PROBLEM to "Wi-Fi waiting on Android"
    return HeaderTone.HEALTHY to "Wi-Fi on"
}

/**
 * GPS icon in the list header. Null when the check is off.
 * Green is armed and quiet. A hit is red. A pause, a Poor phone, and the warmup stay muted.
 */
fun gpsHeader(
    monitorOn: Boolean,
    suppressAlerts: Boolean,
    scanning: Boolean,
    statusLine: String,
    alertShowing: Boolean,
): Pair<HeaderTone, String>? {
    if (!monitorOn) return null
    if (alertShowing) return HeaderTone.PROBLEM to "GPS alert"
    if (statusLine == GnssCopy.PAUSED) return HeaderTone.IDLE to "GPS paused"
    if (!scanning || suppressAlerts) return HeaderTone.IDLE to "GPS on"
    val ready = statusLine == "armed" || statusLine.startsWith("AGC not available")
    return if (ready) HeaderTone.HEALTHY to "GPS on" else HeaderTone.IDLE to "GPS on"
}

/** Bluetooth icon in the list header. A planned rest stays healthy. */
fun bleHeader(scanning: Boolean, locationOn: Boolean, radio: ScanRadioFacts): Pair<HeaderTone, String> {
    if (!radio.bleOn) return HeaderTone.OFF to "Bluetooth off"
    if (!scanning) return HeaderTone.IDLE to "Bluetooth idle"
    if (!locationOn) return HeaderTone.PROBLEM to "Bluetooth, Location is off"
    val stuck = !radio.bleRunning && !radio.bleParked && !radio.bleRetrying && !radio.bleStarting
    if (radio.bleRetrying || stuck) return HeaderTone.PROBLEM to "Bluetooth waiting on Android"
    return HeaderTone.HEALTHY to "Bluetooth on"
}

data class ScanRadioFacts(
    val scanning: Boolean = false,
    val wifiOn: Boolean = false,
    val wifiWaitingOnOs: Boolean = false,
    val lastWifiScanAt: Long = 0L,
    val bleOn: Boolean = false,
    val bleRunning: Boolean = false,
    val bleParked: Boolean = false,
    val bleRetrying: Boolean = false,
    val bleStarting: Boolean = false,
    val bleHitsLastMin: Int = 0,
)

data class ScanPhoneFacts(
    val versionName: String,
    val versionCode: Int,
    val catalogVersion: Int,
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val fingerprint: String,
    val locationOn: Boolean,
    val locationPermission: Boolean,
    /** Milliseconds since the last GPS fix. Null when this phone has none. */
    val gpsFixAgeMs: Long?,
    val arrivalsOnly: Boolean = false,
    val watchedOnly: Boolean = false,
    val customNamesOnly: Boolean = false,
    val showOnly: Boolean = false,
    val movingWithYou: Boolean = false,
    val showWifi: Boolean = true,
    val showBle: Boolean = true,
    val nameFilter: Boolean = false,
    val ouiFilter: Boolean = false,
    /** Settings switch. Fast scans run only when this is on and the OS is not throttling. */
    val wifiFastScan: Boolean = false,
    val wifiOsThrottled: Boolean = false,
    val backgroundUsage: Boolean = false,
    val unrestrictedBattery: Boolean = false,
)

data class ScanStatusReport(
    val rows: List<Pair<String, String>>,
    val verdict: String,
    /** Plain steps for the person holding the phone. Empty never happens. */
    val checks: List<String>,
    val text: String,
    val blocked: Boolean,
)

object ScanStatus {
    fun report(
        radio: ScanRadioFacts,
        phone: ScanPhoneFacts,
        now: Long,
        radiosOnAir: Int = 0,
        gnssRows: List<Pair<String, String>> = emptyList(),
    ): ScanStatusReport {
        val rows = listOf(
            "App" to "${phone.versionName} (${phone.versionCode})",
            "Catalog" to phone.catalogVersion.toString(),
            "Phone" to "${phone.manufacturer} ${phone.model}".trim(),
            "Android" to "${phone.androidRelease} (API ${phone.sdkInt})",
            "Fingerprint" to phone.fingerprint,
            "Wi-Fi" to if (radio.wifiOn) "on" else "off",
            "Last Wi-Fi scan" to lastWifi(radio, now),
            "Bluetooth" to if (radio.bleOn) "on" else "off",
            "BLE" to bleState(radio),
            "BLE results last minute" to radio.bleHitsLastMin.coerceAtLeast(0).toString(),
            "Faster Wi-Fi AP scans" to fasterWifi(phone),
            "Allow background usage" to onOff(phone.backgroundUsage),
            "Unrestricted battery" to onOff(phone.unrestrictedBattery),
            "Location" to if (phone.locationOn) "on" else "off",
            "Location permission" to if (phone.locationPermission) "granted" else "missing",
            "GPS fix" to ageLabel(phone.gpsFixAgeMs),
            "Filters" to filtersLabel(phone),
        ) + gnssRows
        val verdict = verdict(radio, phone, radiosOnAir)
        val checks = checks(radio, phone, radiosOnAir)
        val blocked = isBlocked(radio, phone.locationOn, phone.locationPermission)
        val text = buildString {
            rows.forEach { (k, v) -> append(k).append(": ").append(v).append('\n') }
            append("Verdict: ").append(verdict).append('\n')
            append("Check:\n")
            checks.forEach { append("- ").append(it).append('\n') }
        }
        return ScanStatusReport(rows, verdict, checks, text, blocked)
    }

    fun isBlocked(radio: ScanRadioFacts, locationOn: Boolean, locationPermission: Boolean): Boolean {
        if (!radio.scanning) return true
        if (!locationOn || !locationPermission) return true
        if (radio.bleStarting && !radio.bleRunning) return false
        return !wifiUsable(radio) && !bleUsable(radio)
    }

    /** Appended to an empty Live list while a scan is blocked. */
    const val EMPTY_POINTER = "Settings → Diagnostics."

    private fun wifiUsable(radio: ScanRadioFacts): Boolean =
        radio.wifiOn && !radio.wifiWaitingOnOs

    private fun bleUsable(radio: ScanRadioFacts): Boolean =
        radio.bleOn && radio.bleRunning && !radio.bleParked && !radio.bleRetrying

    private fun verdict(radio: ScanRadioFacts, phone: ScanPhoneFacts, radiosOnAir: Int): String {
        val wifiOk = wifiUsable(radio)
        val bleOk = bleUsable(radio)
        return when {
            !radio.scanning -> "Scanning is not running."
            !phone.locationOn -> "Location is off. Android will not deliver scan results."
            !phone.locationPermission -> "Location permission is off."
            !radio.wifiOn && !radio.bleOn -> "Wi-Fi and Bluetooth are off."
            radio.bleStarting && !radio.bleRunning && !radio.wifiWaitingOnOs -> "Scanning is starting."
            !wifiOk && !bleOk && radio.wifiWaitingOnOs && radio.bleParked ->
                "Wi-Fi scan refused. BLE scan is parked. Both radios are on."
            !wifiOk && !bleOk && radio.bleParked ->
                "BLE scan is parked. Bluetooth is on."
            !wifiOk && !bleOk && radio.bleRetrying -> "BLE scan is retrying."
            !wifiOk && !bleOk && radio.wifiWaitingOnOs ->
                "Wi-Fi scan refused. Wi-Fi is on."
            !wifiOk && !bleOk -> "Wi-Fi and Bluetooth scans are not running."
            radio.wifiWaitingOnOs && bleOk -> "Wi-Fi scan refused. BLE is running."
            radio.bleParked && wifiOk -> "BLE scan is parked. Wi-Fi is running."
            !radio.wifiOn && bleOk -> "Wi-Fi is off. BLE is running."
            !radio.bleOn && wifiOk -> "Bluetooth is off. Wi-Fi is running."
            filterHiding(phone) -> "Scans are running. A filter is hiding the list."
            radiosOnAir > 0 -> "Scans are running."
            else -> "Scans are running. Nothing is on the air."
        }
    }

    /**
     * What to turn on, in the order a person can fix it.
     * A radio that is off is named only when the other radio is still hearing.
     * Background usage, unrestricted battery, and a GPS fix are not required
     * while Fieldwatch is open, so they stay in the fact rows.
     */
    private fun checks(radio: ScanRadioFacts, phone: ScanPhoneFacts, radiosOnAir: Int): List<String> {
        val lines = mutableListOf<String>()
        if (!radio.scanning) {
            lines += "Scanning is not running. Open Live. If Fieldwatch asks for permission, allow Location, Nearby devices, and Notifications."
        }
        if (!phone.locationOn) {
            lines += "Turn Location on. Android will not return Wi-Fi or Bluetooth results while Location is off."
        }
        if (!phone.locationPermission) {
            lines += "Allow location for Fieldwatch. Phone Settings, Apps, Fieldwatch, Permissions, Location."
        }
        val wifiOk = wifiUsable(radio)
        val bleOk = bleUsable(radio)
        val locationReady = phone.locationOn && phone.locationPermission
        if (!radio.wifiOn && !radio.bleOn) {
            lines += "Turn Wi-Fi or Bluetooth on. Fieldwatch hears access points and Bluetooth advertisements."
        } else {
            if (!radio.wifiOn && bleOk) {
                lines += "Wi-Fi is off, so access points will not appear. Bluetooth is running."
            }
            if (!radio.bleOn && wifiOk) {
                lines += "Bluetooth is off, so Bluetooth radios will not appear. Wi-Fi is running."
            }
            if (locationReady && radio.wifiOn && radio.wifiWaitingOnOs) {
                lines += "Wi-Fi scans are being refused. The Wi-Fi radio is on. Toggle Wi-Fi off and on, then reopen Fieldwatch."
            }
            if (locationReady && radio.bleOn && radio.bleParked) {
                lines += "Bluetooth scans are parked. The Bluetooth radio is on. Fieldwatch will try again."
            }
            if (radio.bleOn && radio.bleRetrying) {
                lines += "Bluetooth scan is retrying. Toggle Bluetooth off and on, then reopen Fieldwatch."
            }
            if (radio.bleOn && radio.bleStarting && !radio.bleRunning) {
                lines += "Bluetooth is still starting. Wait a few seconds."
            }
            if (radio.bleOn && !radio.bleRunning && !radio.bleParked && !radio.bleRetrying && !radio.bleStarting) {
                lines += "Bluetooth is on, and the scan is not running. Toggle Bluetooth off and on, then reopen Fieldwatch."
            }
        }
        if (filterHiding(phone)) {
            lines += "A filter is hiding radios. Filters is ${filtersLabel(phone)}. Set Filters to All Traffic to see everything the phone hears."
        }
        if (lines.isEmpty()) {
            lines += if (radiosOnAir > 0) {
                "Nothing that needs to be on is off. Scans are running."
            } else {
                "Nothing that needs to be on is off. Scans are running. Nothing is on the air right now."
            }
        }
        return lines
    }

    private fun filterHiding(phone: ScanPhoneFacts): Boolean =
        phone.arrivalsOnly || phone.watchedOnly || phone.customNamesOnly || phone.showOnly ||
            phone.movingWithYou || !phone.showWifi || !phone.showBle || phone.nameFilter || phone.ouiFilter

    private fun filtersLabel(phone: ScanPhoneFacts): String {
        val parts = buildList {
            if (phone.arrivalsOnly) add("Arrivals only")
            if (phone.watchedOnly) add("Watched only")
            if (phone.customNamesOnly) add("Named radios only")
            if (phone.showOnly) add("Show only")
            if (phone.movingWithYou) add("Moving with you")
            if (!phone.showWifi) add("Wi-Fi hidden")
            if (!phone.showBle) add("BLE hidden")
            if (phone.nameFilter) add("Name filter on")
            if (phone.ouiFilter) add("OUI filter on")
        }
        return if (parts.isEmpty()) "All Traffic" else parts.joinToString(", ")
    }

    private fun onOff(on: Boolean): String = if (on) "on" else "off"

    private fun fasterWifi(phone: ScanPhoneFacts): String =
        if (phone.wifiOsThrottled) "off" else "on"

    private fun lastWifi(radio: ScanRadioFacts, now: Long): String {
        val age = ageLabel(if (radio.lastWifiScanAt > 0L) now - radio.lastWifiScanAt else null)
        return when {
            radio.wifiWaitingOnOs && radio.lastWifiScanAt > 0L -> "waiting on OS ($age)"
            radio.wifiWaitingOnOs -> "waiting on OS"
            else -> age
        }
    }

    private fun bleState(radio: ScanRadioFacts): String = when {
        !radio.bleOn -> "off"
        radio.bleParked -> "parked"
        radio.bleRetrying -> "retrying"
        radio.bleStarting && !radio.bleRunning -> "starting"
        radio.bleRunning -> "running"
        else -> "not running"
    }

    private fun ageLabel(ageMs: Long?): String {
        if (ageMs == null || ageMs < 0L) return "none"
        val sec = (ageMs / 1000L).toInt()
        return if (sec < 60) "${sec}s ago" else "${sec / 60} min ago"
    }
}
