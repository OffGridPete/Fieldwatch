package app.fieldwatch.domain

import kotlinx.serialization.Serializable

/**
 * One quiet cue that actually fired. The name is already masked for privacy mode.
 * Stored on the sit because a later report cannot rebuild the scanner check.
 */
@Serializable
data class QuietMark(
    val at: Long,
    val lastHeard: Long,
    val key: String,
    val name: String,
    val rssi: Int,
) {
    fun reportLine(): String = LanguageText.text(
        "report.quiet.line",
        mapOf(
            "clock" to FloodBurst.clock(at),
            "name" to name,
            "time" to FloodBurst.clock(lastHeard),
        ),
    )
}

/** A heard-to-gone edge the scan loop may cue. ViewModel refreshes only stash these. */
data class QuietEdge(
    val key: String,
    val kind: RadioKind,
    val mac: String,
    val name: String,
    val rssi: Int,
    val lastSeen: Long,
    val spanStart: Long,
)

object QuietAlert {
    const val MIN_HEARD_MS = 120_000L
    /** One or two scan ticks. A parked restart is longer than this and does not cue. */
    const val BLE_LATE_MS = 6_000L
    /** Covers a Battery saver Wi-Fi interval minus the shortest gone wait. */
    const val WIFI_LATE_MS = 45_000L

    fun heardLongEnough(spanStart: Long, lastSeen: Long): Boolean =
        lastSeen - spanStart >= MIN_HEARD_MS

    /**
     * [late] is how far past the gone wait this pass is.
     * A catch-up after a hold is later than the cap and does not cue.
     */
    fun onTime(kind: RadioKind, lastSeen: Long, now: Long, lingerMs: Long): Boolean {
        val late = now - lastSeen - lingerMs
        if (late < 0L) return false
        val cap = when (kind) {
            RadioKind.BLE -> BLE_LATE_MS
            RadioKind.WIFI -> WIFI_LATE_MS
        }
        return late <= cap
    }

    /** Mute wins. Mine does not. A fleet row is not a named radio. */
    fun armed(row: WatchTarget): Boolean =
        row.deviceKey != null && row.alertOnGone && !row.mute

    fun cueName(label: String, fallback: String, mac: String, demoMode: Boolean): String =
        MacUtil.redactMacIn(label.trim().ifEmpty { fallback }, mac, demoMode)

    fun rssiText(rssi: Int): String = if (Rssi.measured(rssi)) rssi.toString() else "—"

    /** Shade card clock. The debrief line puts UTC on the event clock instead. */
    fun heardClock(lastSeen: Long): String = FloodBurst.clock(lastSeen) + " UTC"
}
