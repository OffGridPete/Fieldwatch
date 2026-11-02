package app.fieldwatch.domain

/**
 * Settings → Diagnostics. Phone and scan facts only.
 * No network names, MACs, or GPS coordinates.
 */
enum class HeaderTone { OFF, IDLE, HEALTHY, PROBLEM }

/** Wi-Fi icon in the list header. Green is healthy, including the wait between scans. */
fun wifiHeader(scanning: Boolean, locationOn: Boolean, radio: ScanRadioFacts): Pair<HeaderTone, String> {
    if (!radio.wifiOn) return HeaderTone.OFF to LanguageText.text("header.wifi.off")
    if (!scanning) return HeaderTone.IDLE to LanguageText.text("header.wifi.idle")
    if (!locationOn) return HeaderTone.PROBLEM to LanguageText.text("header.wifi.location")
    if (radio.wifiWaitingOnOs) return HeaderTone.PROBLEM to LanguageText.text("header.wifi.wait")
    return HeaderTone.HEALTHY to LanguageText.text("header.wifi.on")
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
    if (alertShowing) return HeaderTone.PROBLEM to LanguageText.text("header.gps.alert")
    if (statusLine == GnssCopy.PAUSED) return HeaderTone.IDLE to LanguageText.text("header.gps.paused")
    if (!scanning || suppressAlerts) return HeaderTone.IDLE to LanguageText.text("header.gps.on")
    val ready = statusLine == "armed" || statusLine.startsWith("AGC not available")
    return if (ready) HeaderTone.HEALTHY to LanguageText.text("header.gps.on") else HeaderTone.IDLE to LanguageText.text("header.gps.on")
}

/** Bluetooth icon in the list header. A planned rest stays healthy. */
fun bleHeader(scanning: Boolean, locationOn: Boolean, radio: ScanRadioFacts): Pair<HeaderTone, String> {
    if (!radio.bleOn) return HeaderTone.OFF to LanguageText.text("header.ble.off")
    if (!scanning) return HeaderTone.IDLE to LanguageText.text("header.ble.idle")
    if (!locationOn) return HeaderTone.PROBLEM to LanguageText.text("header.ble.location")
    val stuck = !radio.bleRunning && !radio.bleParked && !radio.bleRetrying && !radio.bleStarting
    if (radio.bleRetrying || stuck) return HeaderTone.PROBLEM to LanguageText.text("header.ble.wait")
    return HeaderTone.HEALTHY to LanguageText.text("header.ble.on")
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
    val seenAtPlaces: Boolean = false,
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

/** TAK rows on Diagnostics. The host address is not included. */
data class TakDiagFacts(
    val enabled: Boolean = false,
    val preset: TakUdpPreset = TakUdpPreset.THIS_PHONE,
    val port: Int = TakDefaults.PORT,
    val open: Boolean = false,
    val birth: TakBirth? = null,
    val lastSendOk: Boolean? = null,
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
        tak: TakDiagFacts = TakDiagFacts(),
        cellRows: List<Pair<String, String>> = emptyList(),
    ): ScanStatusReport {
        val rows = listOf(
            LanguageText.text("ui.app.label") to "${phone.versionName} (${phone.versionCode})",
            LanguageText.text("ui.catalog.label") to phone.catalogVersion.toString(),
            LanguageText.text("ui.phone.label.2") to "${phone.manufacturer} ${phone.model}".trim(),
            LanguageText.text("ui.android.label") to "${phone.androidRelease} (API ${phone.sdkInt})",
            LanguageText.text("ui.fingerprint.label") to phone.fingerprint,
            LanguageText.text("radio.wifi") to if (radio.wifiOn) LanguageText.text("diag.on") else LanguageText.text("diag.off"),
            LanguageText.text("ui.last.wi.fi.scan") to lastWifi(radio, now),
            LanguageText.text("ui.bluetooth.label") to if (radio.bleOn) LanguageText.text("diag.on") else LanguageText.text("diag.off"),
            LanguageText.text("radio.ble") to bleState(radio),
            LanguageText.text("ui.ble.results.last.minute") to radio.bleHitsLastMin.coerceAtLeast(0).toString(),
            LanguageText.text("ui.faster.wi.fi.ap") to fasterWifi(phone),
            LanguageText.text("ui.allow.background.usage") to onOff(phone.backgroundUsage),
            LanguageText.text("ui.unrestricted.battery") to onOff(phone.unrestrictedBattery),
            LanguageText.text("settings.topic.location") to if (phone.locationOn) LanguageText.text("diag.on") else LanguageText.text("diag.off"),
            LanguageText.text("ui.location.permission") to if (phone.locationPermission) LanguageText.text("diag.granted") else LanguageText.text("diag.missing"),
            LanguageText.text("ui.gps.fix") to ageLabel(phone.gpsFixAgeMs),
            LanguageText.text("tab.filters") to filtersLabel(phone),
        ) + takRows(tak) + gnssRows + cellRows
        val verdict = verdict(radio, phone, radiosOnAir)
        val checks = checks(radio, phone, radiosOnAir)
        val blocked = isBlocked(radio, phone.locationOn, phone.locationPermission)
        val text = buildString {
            rows.forEach { (k, v) -> append(k).append(": ").append(v).append('\n') }
            append((LanguageText.text("ui.verdict.label") + " ")).append(verdict).append('\n')
            append((LanguageText.text("ui.check.label") + "\n"))
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
    val EMPTY_POINTER get() = LanguageText.text("ui.settings.diagnostics")

    private fun takRows(tak: TakDiagFacts): List<Pair<String, String>> {
        val port = tak.port.coerceIn(1, 65_535).toString()
        val dest = when (tak.preset) {
            TakUdpPreset.THIS_PHONE -> LanguageText.text("diag.tak.dest.phone", mapOf("port" to port))
            TakUdpPreset.LAN_MULTICAST -> LanguageText.text("diag.tak.dest.lan", mapOf("port" to port))
            TakUdpPreset.CUSTOM -> LanguageText.text("diag.tak.dest.other", mapOf("port" to port))
        }
        val feed = if (tak.enabled) {
            LanguageText.text("diag.on") + " · " + dest
        } else {
            LanguageText.text("diag.off")
        }
        val birth = tak.birth
        val loop = when {
            !tak.open || birth == null -> LanguageText.text("diag.tak.loopback.closed")
            samePhoneReachesAtak(birth) -> LanguageText.text("diag.tak.same.reach")
            else -> LanguageText.text("diag.tak.same.wifi")
        }
        val opened = when (tak.birth) {
            null -> LanguageText.text("diag.tak.loopback.closed")
            TakBirth.NONE -> LanguageText.text("diag.tak.opened.none")
            TakBirth.CELLULAR -> LanguageText.text("diag.tak.opened.cellular")
            TakBirth.WIFI -> LanguageText.text("diag.tak.opened.wifi")
            TakBirth.ETHERNET -> LanguageText.text("diag.tak.opened.ethernet")
        }
        val send = when (tak.lastSendOk) {
            null -> LanguageText.text("diag.tak.send.none")
            true -> LanguageText.text("diag.tak.send.ok")
            false -> LanguageText.text("diag.tak.send.fail")
        }
        return listOf(
            LanguageText.text("diag.tak") to feed,
            LanguageText.text("diag.tak.loopback") to loop,
            LanguageText.text("diag.tak.opened") to opened,
            LanguageText.text("diag.tak.send") to send,
        )
    }

    private fun wifiUsable(radio: ScanRadioFacts): Boolean =
        radio.wifiOn && !radio.wifiWaitingOnOs

    private fun bleUsable(radio: ScanRadioFacts): Boolean =
        radio.bleOn && radio.bleRunning && !radio.bleParked && !radio.bleRetrying

    private fun verdict(radio: ScanRadioFacts, phone: ScanPhoneFacts, radiosOnAir: Int): String {
        val wifiOk = wifiUsable(radio)
        val bleOk = bleUsable(radio)
        return when {
            !radio.scanning -> LanguageText.text("ui.scanning.not.running")
            !phone.locationOn -> LanguageText.text("ui.location.off.android.will.2")
            !phone.locationPermission -> LanguageText.text("ui.location.permission.off")
            !radio.wifiOn && !radio.bleOn -> LanguageText.text("ui.wi.fi.bluetooth.off")
            radio.bleStarting && !radio.bleRunning && !radio.wifiWaitingOnOs -> LanguageText.text("ui.scanning.starting")
            !wifiOk && !bleOk && radio.wifiWaitingOnOs && radio.bleParked ->
                LanguageText.text("ui.wi.fi.scan.refused")
            !wifiOk && !bleOk && radio.bleParked ->
                LanguageText.text("ui.ble.scan.parked.bluetooth")
            !wifiOk && !bleOk && radio.bleRetrying -> LanguageText.text("ui.ble.scan.retrying")
            !wifiOk && !bleOk && radio.wifiWaitingOnOs ->
                LanguageText.text("ui.wi.fi.scan.refused.2")
            !wifiOk && !bleOk -> LanguageText.text("ui.wi.fi.bluetooth.scans")
            radio.wifiWaitingOnOs && bleOk -> LanguageText.text("ui.wi.fi.scan.refused.3")
            radio.bleParked && wifiOk -> LanguageText.text("ui.ble.scan.parked.wi")
            !radio.wifiOn && bleOk -> LanguageText.text("ui.wi.fi.off.ble")
            !radio.bleOn && wifiOk -> LanguageText.text("ui.bluetooth.off.wi.fi")
            filterHiding(phone) -> LanguageText.text("ui.scans.running.filter.hiding")
            radiosOnAir > 0 -> LanguageText.text("ui.scans.running")
            else -> LanguageText.text("ui.scans.running.nothing.air")
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
            lines += LanguageText.text("ui.scanning.not.running.open")
        }
        if (!phone.locationOn) {
            lines += LanguageText.text("ui.turn.location.android.will")
        }
        if (!phone.locationPermission) {
            lines += LanguageText.text("ui.allow.location.fieldwatch.phone")
        }
        val wifiOk = wifiUsable(radio)
        val bleOk = bleUsable(radio)
        val locationReady = phone.locationOn && phone.locationPermission
        if (!radio.wifiOn && !radio.bleOn) {
            lines += LanguageText.text("ui.turn.wi.fi.bluetooth")
        } else {
            if (!radio.wifiOn && bleOk) {
                lines += LanguageText.text("ui.wi.fi.off.so")
            }
            if (!radio.bleOn && wifiOk) {
                lines += LanguageText.text("ui.bluetooth.off.so.bluetooth")
            }
            if (locationReady && radio.wifiOn && radio.wifiWaitingOnOs) {
                lines += LanguageText.text("ui.wi.fi.scans.being")
            }
            if (locationReady && radio.bleOn && radio.bleParked) {
                lines += LanguageText.text("ui.bluetooth.scans.parked.bluetooth")
            }
            if (radio.bleOn && radio.bleRetrying) {
                lines += LanguageText.text("ui.bluetooth.scan.retrying.toggle")
            }
            if (radio.bleOn && radio.bleStarting && !radio.bleRunning) {
                lines += LanguageText.text("ui.bluetooth.still.starting.wait")
            }
            if (radio.bleOn && !radio.bleRunning && !radio.bleParked && !radio.bleRetrying && !radio.bleStarting) {
                lines += LanguageText.text("ui.bluetooth.scan.not.running")
            }
        }
        if (filterHiding(phone)) {
            lines += LanguageText.text("diag.filter.hiding", mapOf("filters" to filtersLabel(phone)))
        }
        if (lines.isEmpty()) {
            lines += if (radiosOnAir > 0) {
                LanguageText.text("ui.nothing.needs.off.scans")
            } else {
                LanguageText.text("ui.nothing.needs.off.scans.2")
            }
        }
        return lines
    }

    private fun filterHiding(phone: ScanPhoneFacts): Boolean =
        phone.arrivalsOnly || phone.watchedOnly || phone.customNamesOnly || phone.showOnly ||
            phone.movingWithYou || phone.seenAtPlaces || !phone.showWifi || !phone.showBle ||
            phone.nameFilter || phone.ouiFilter

    private fun filtersLabel(phone: ScanPhoneFacts): String {
        val parts = buildList {
            if (phone.arrivalsOnly) add(LanguageText.text("ui.arrivals.only"))
            if (phone.watchedOnly) add(LanguageText.text("ui.keep.watched.only"))
            if (phone.customNamesOnly) add(LanguageText.text("ui.keep.named.only"))
            if (phone.showOnly) add(LanguageText.text("ui.show.only"))
            if (phone.movingWithYou) add(LanguageText.text("ui.moving.label"))
            if (phone.seenAtPlaces) add(LanguageText.text("ui.seen.more.than.one"))
            if (!phone.showWifi) add(LanguageText.text("ui.wi.fi.hidden"))
            if (!phone.showBle) add(LanguageText.text("ui.ble.hidden"))
            if (phone.nameFilter) add(LanguageText.text("ui.name.filter"))
            if (phone.ouiFilter) add(LanguageText.text("ui.oui.filter"))
        }
        return if (parts.isEmpty()) LanguageText.text("ui.all.traffic") else parts.joinToString(", ")
    }

    private fun onOff(on: Boolean): String =
        if (on) LanguageText.text("diag.on") else LanguageText.text("diag.off")

    private fun fasterWifi(phone: ScanPhoneFacts): String =
        if (phone.wifiOsThrottled) LanguageText.text("diag.off") else LanguageText.text("diag.on")

    private fun lastWifi(radio: ScanRadioFacts, now: Long): String {
        val age = ageLabel(if (radio.lastWifiScanAt > 0L) now - radio.lastWifiScanAt else null)
        return when {
            radio.wifiWaitingOnOs && radio.lastWifiScanAt > 0L ->
                LanguageText.text("diag.waiting.age", mapOf("age" to age))
            radio.wifiWaitingOnOs -> LanguageText.text("ui.waiting.os")
            else -> age
        }
    }

    private fun bleState(radio: ScanRadioFacts): String = when {
        !radio.bleOn -> LanguageText.text("diag.off")
        radio.bleParked -> LanguageText.text("diag.parked")
        radio.bleRetrying -> LanguageText.text("diag.retrying")
        radio.bleStarting && !radio.bleRunning -> LanguageText.text("diag.starting")
        radio.bleRunning -> LanguageText.text("diag.running")
        else -> LanguageText.text("ui.not.running")
    }

    private fun ageLabel(ageMs: Long?): String {
        if (ageMs == null || ageMs < 0L) return LanguageText.text("diag.none")
        val sec = (ageMs / 1000L).toInt()
        return if (sec < 60) {
            LanguageText.text("diag.seconds.ago", mapOf("n" to sec.toString()))
        } else {
            LanguageText.text("diag.minutes.ago", mapOf("n" to (sec / 60).toString()))
        }
    }
}
