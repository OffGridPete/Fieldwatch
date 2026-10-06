package app.fieldwatch.domain

/**
 * Settings → Diagnostics. Phone and scan facts only.
 * No network names, MACs, or GPS coordinates.
 */
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
    val text: String,
    val blocked: Boolean,
)

object ScanStatus {
    fun report(
        radio: ScanRadioFacts,
        phone: ScanPhoneFacts,
        now: Long,
        radiosOnAir: Int = 0,
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
        )
        val verdict = verdict(radio, phone, radiosOnAir)
        val blocked = isBlocked(radio, phone.locationOn, phone.locationPermission)
        val text = buildString {
            rows.forEach { (k, v) -> append(k).append(": ").append(v).append('\n') }
            append("Verdict: ").append(verdict)
        }
        return ScanStatusReport(rows, verdict, text, blocked)
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
                "Wi-Fi scan refused. BLE scan is parked. Check Location, then Location services, then Wi-Fi scanning and Bluetooth scanning."
            !wifiOk && !bleOk && radio.bleParked ->
                "BLE scan is parked. Check Location, then Location services, then Bluetooth scanning."
            !wifiOk && !bleOk && radio.bleRetrying -> "BLE scan is retrying."
            !wifiOk && !bleOk && radio.wifiWaitingOnOs ->
                "Wi-Fi scan refused. Check Location, then Location services, then Wi-Fi scanning."
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

    private fun fasterWifi(phone: ScanPhoneFacts): String = when {
        !phone.wifiFastScan -> "off"
        phone.wifiOsThrottled -> "on, OS still throttling"
        else -> "on"
    }

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
