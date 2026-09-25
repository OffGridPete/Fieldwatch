package app.fieldwatch.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Paste-ready addendum prompt for Reports → AI Export.
 * Onboard Debrief is verbatim. Working data is rates, RSSI bands, Extra attention,
 * and finder-tag rows for a tracking stress-test — not a second inventory.
 */
object DebriefPrompt {
    const val WINDOW_SHORT_MS = 5 * 60_000L
    const val WINDOW_MS = 15 * 60_000L
    private const val MAX_CHARS = 90_000

    fun build(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        settings: AppSettings,
        now: Long = System.currentTimeMillis(),
        operatorPath: List<GpsSample> = emptyList(),
        places: DebriefPlaces = DebriefPlaces.Off,
        window: DebriefWindow? = null,
        customNames: Map<String, String> = emptyMap(),
        observerNotes: Map<String, String> = emptyMap(),
    ): String {
        val names = fleets.associate { it.id to it.name }
        val win = window ?: DebriefWindow(now - WINDOW_MS, now)
        val windowStart = win.startAt
        val windowEnd = win.endAt
        val in15 = devices.filter { it.lastSeen >= windowStart || it.firstSeen >= windowStart }
        val shortStart = maxOf(windowStart, windowEnd - WINDOW_SHORT_MS)
        val in5 = in15.filter { it.lastSeen >= shortStart || it.firstSeen >= shortStart }
        val wifi = in15.filter { it.kind == RadioKind.WIFI }
        val ble = in15.filter { it.kind == RadioKind.BLE }
        val signed = in15.filter { it.fleetIds.isNotEmpty() }
        val randomized = ble.count { it.randomized }
        val arrived = in15.filter { it.firstSeen >= windowStart }
        val departed = in15.filter { it.gone || it.lastSeen < windowEnd - 45_000L }
        val persistent = in15.filter { dwellMs(it, windowStart, windowEnd) >= win.durationMs * 2 / 3 }
        val path = operatorPath.filter { it.at in windowStart..windowEnd }
        val pathSpan = Geo.spanM(path)
        val pathLen = Geo.pathLengthM(path)
        val extraHits = in15.flatMap { d ->
            d.attentionNotes(fleets).map { (sig, note) -> Triple(d, sig, note) }
        }
        val finders = in15.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.FINDER }
        val sigFamilies = signed.groupBy { d ->
            d.fleetIds.joinToString("+") { names[it] ?: it }
        }.mapValues { it.value.size }.toList().sortedByDescending { it.second }
        val bleRssi = ble.map { it.rssi }
        val wifiRssi = wifi.map { it.rssi }
        val onboard = DebriefReport.build(devices, fleets, settings, operatorPath, now, places, win, customNames, observerNotes)
        val iso = utc(windowEnd)
        val start = utc(windowStart)

        val body = buildString {
            append(experimentalDisclaimerMarkdown())
            appendLine()
            appendLine("You are a field RF analyst for the operator who collected this sit. Fieldwatch is a stock-Android, receive-only Wi-Fi access-point + BLE-advertiser listener.")
            appendLine()
            appendLine("The **onboard Debrief** (verbatim below) already tabulated the sit: counts, Where you were, tracking callouts, inventories, Extra attention, takeaway. **Do not rewrite that report. Do not reprint inventories or stay lists.** Your job is an addendum the phone cannot write: rates, competing hypotheses, and a stress-test of the onboard tracking callouts.")
            appendLine()
            appendLine("Constraints you must respect:")
            appendLine("- Hear-only. Wi-Fi rows are access points only. BLE rows are advertisers. Kind + MAC. BLE rotation is a new row and will not stitch.")
            appendLine("- Signature / OUI / company matches are hypotheses, not identity, not a person or vehicle.")
            appendLine("- GPS stamps (if present) are this phone at hear-time, not the other radio. Do not place a camera or tag at the GPS pin.")
            appendLine("- Place names (if present) are system reverse-geocode of those stamps.")
            appendLine("- RSSI is loudness at the phone, not meters.")
            appendLine("- Live RAM cap is about 400 radios; unnamed BLE evicts after ~3 min. A named sit keeps more. This is not a complete capture.")
            appendLine("- Do not claim a tracker is following unless the onboard GPS co-travel section supports it. A radio with you the whole sit is not automatically yours — it may be planted. Do not dismiss it. Do not invent a tail the onboard test did not flag. Do not treat a retail beacon as a Find My tail.")
            appendLine("- Do not give safety advice. Do not tell the operator they are safe or in danger.")
            appendLine("- Treat this paste as operationally sensitive.")
            appendLine()
            appendLine("## Your output (required — this is the addendum the operator reads)")
            appendLine("Write complete sentences. Headings as below. Short bullets only for Extra attention and tracking rows from the working table. No markdown tables. No code fences. No dump of the onboard inventories.")
            appendLine()
            appendLine("1. **Disclaimer** — Repeat the experimental-use disclaimer first.")
            appendLine("2. **What the onboard Debrief already established** — 3–5 sentences. Counts, distance, tracking callouts, Extra attention hits, Observer notes if any. Do not reprint inventories.")
            appendLine("3. **What the numbers add** — 5- vs 15-minute counts, RSSI bands, RAND BLE percent, arrivals per minute, persistent vs gone, signature-family mix. Say street vs dwelling vs retail vs vehicle, and 5-minute vs 15-minute change (denser, quieter, stable). Confidence. If GPS ran, path length/span from the working table — do not pin a radio to a stay.")
            appendLine("4. **Extra attention and tracking callouts** — Full identifiers from the working table (complete MAC, name, RSSI min/max, signatures, dwell). Stress-test onboard Possible trackers with you / Possible tail / Retail beacons / Wearables. Agree, qualify, or say the data are too thin. Pattern match, not identity. If none, say none.")
            appendLine("5. **What another sit or Hunt would shrink** — Concrete in-app next steps only (Hunt on one Extra attention row, a longer GPS path, Compare sits, Filters). No safety advice. No “call the police.”")
            appendLine()
            appendLine("**Takeaway (required, last line).** One sentence starting with `Takeaway:` that adds *one number the onboard takeaway does not already say* (a rate, RAND percent, 5- vs 15-minute change, path span). Not a moral. Not a threat level.")
            appendLine()
            appendLine("## Collection context")
            appendLine("- Tool: Fieldwatch (app.fieldwatch), receive-only, no association / injection / cloud.")
            appendLine(
                if (win.sitName != null) {
                    "- Window: sit **${win.sitName}** ($start → $iso UTC), with a 5-minute recent slice."
                } else {
                    "- Window: last **15 minutes** ($start → $iso UTC), with a **5-minute** recent slice."
                },
            )
            appendLine("- Scan intensity: ${settings.intensity.name.lowercase()}. Stale after ${settings.staleSec}s.")
            appendLine("- Location tags: ${if (settings.tagLocation) "on" else "off"}. Online place names: ${if (settings.onlineLookup) "on" else "off"}.")
            appendLine()
            appendLine("## Onboard Debrief (verbatim — already shown to the operator; do not rewrite)")
            appendLine()
            appendLine(onboard.trimEnd())
            appendLine()
            appendLine("## Working data (for the addendum — do not copy rosters into the answer)")
            appendLine()
            appendLine(
                "15 min: Wi-Fi ${wifi.size}  BLE ${ble.size}  signed ${signed.size}  hidden SSIDs ${wifi.count { it.hiddenSsid }}  " +
                    "RAND BLE $randomized/${ble.size} (${pct(randomized, ble.size)}%)  " +
                    "first-seen ${arrived.size} (${perMin(arrived.size)}/min)  persistent ${persistent.size}  gone/quiet ${departed.size}",
            )
            appendLine(
                "5 min: Wi-Fi ${in5.count { it.kind == RadioKind.WIFI }}  BLE ${in5.count { it.kind == RadioKind.BLE }}  " +
                    "signed ${in5.count { it.fleetIds.isNotEmpty() }}  first-seen ${in5.count { it.firstSeen >= shortStart }}",
            )
            appendLine(
                "BLE RSSI (n=${ble.size}): ≥−50 ${bandGe(bleRssi, -50)}  −51..−70 ${band(bleRssi, -70, -51)}  " +
                    "−71..−85 ${band(bleRssi, -85, -71)}  <−85 ${bandLt(bleRssi, -85)}",
            )
            appendLine(
                "Wi-Fi RSSI (n=${wifi.size}): ≥−50 ${bandGe(wifiRssi, -50)}  −51..−70 ${band(wifiRssi, -70, -51)}  " +
                    "−71..−85 ${band(wifiRssi, -85, -71)}  <−85 ${bandLt(wifiRssi, -85)}",
            )
            if (sigFamilies.isEmpty()) {
                appendLine("Signature families: none.")
            } else {
                appendLine("Signature families (count): " + sigFamilies.take(12).joinToString { "${it.first}=${it.second}" })
            }
            appendLine(
                "GPS path: tagging ${if (settings.tagLocation) "on" else "off"}  " +
                    "fixes ${path.size}  length ${pathLen.toInt()} m  span ${pathSpan.toInt()} m  " +
                    "places ${if (places.attempted) places.note else "off"}",
            )
            appendLine()
            appendLine("Extra attention:")
            if (extraHits.isEmpty()) {
                appendLine("- None.")
            } else {
                extraHits.forEach { (d, sig, note) ->
                    append("- ").append(row(d, names, now, windowStart, customNames, observerNotes))
                    append(" | ").append(sig).append(": ").append(note)
                    appendLine()
                }
            }
            appendLine()
            appendLine("Observer notes:")
            val observed = in15.mapNotNull { d ->
                val note = observerNotes[d.key]?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                d to note
            }
            if (observed.isEmpty()) {
                appendLine("- None.")
            } else {
                observed.sortedByDescending { it.first.rssi }.forEach { (d, note) ->
                    append("- ").append(row(d, names, now, windowStart, customNames, emptyMap()))
                    appendLine()
                    appendLine("  $note")
                }
            }
            appendLine()
            appendLine("Finder-tag-like radios (for stress-test of onboard tracking; not a tail list):")
            if (finders.isEmpty()) {
                appendLine("- None.")
            } else {
                finders.sortedByDescending { it.rssi }.take(20).forEach { d ->
                    append("- ").append(row(d, names, now, windowStart, customNames, observerNotes))
                    append(" rssiMin=").append(d.rssiMin).append(" rssiMax=").append(d.rssiMax)
                    appendLine()
                }
            }
            appendLine()
            appendLine("## End of working data")
            appendLine("Write the addendum now, following **Your output** at the top. Do not rewrite the onboard Debrief.")
        }
        return if (body.length <= MAX_CHARS) body
        else body.take(MAX_CHARS) + "\n\n[truncated for share-sheet size]\n"
    }

    fun experimentalDisclaimerMarkdown(): String = FieldwatchDisclaimer.experimentalMarkdown()

    private fun row(
        d: Sighting,
        names: Map<String, String>,
        now: Long,
        windowStart: Long,
        customNames: Map<String, String> = emptyMap(),
        observerNotes: Map<String, String> = emptyMap(),
    ): String = buildString {
        append(if (d.kind == RadioKind.WIFI) "WIFI" else "BLE")
        append(" ").append(d.mac)
        val label = d.reportName(customNames).trim()
        if (label.isNotEmpty() && !label.equals(d.mac, ignoreCase = true)) {
            append("  ").append(label.take(32))
        }
        observerNotes[d.key]?.let { append("  Observer: ").append(it.take(80)) }
        append(" rssi=").append(d.rssi).append("dBm")
        if (d.randomized) append(" RAND")
        if (d.fleetIds.isNotEmpty()) {
            append(" sig=").append(d.fleetIds.joinToString("+") { names[it] ?: it })
        }
        append(" dwell=").append(fmtDur(dwellMs(d, windowStart, now)))
    }

    private fun dwellMs(d: Sighting, from: Long, to: Long): Long {
        var sum = 0L
        val spans = d.presence.ifEmpty { listOf(PresenceSpan(d.firstSeen, if (d.gone) d.lastSeen else null)) }
        for (span in spans) {
            val a = maxOf(span.start, from)
            val b = minOf(span.end ?: to, to)
            if (b > a) sum += b - a
        }
        return sum
    }

    private fun utc(ms: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(ms))
    }

    private fun fmtDur(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val m = s / 60
        val r = s % 60
        return if (m >= 60) "${m / 60}h${m % 60}m" else if (m > 0) "${m}m${r}s" else "${r}s"
    }

    private fun pct(n: Int, d: Int): Int = if (d <= 0) 0 else (n * 100) / d

    private fun perMin(n: Int): String {
        val rate = n / 15.0
        return if (rate >= 10) rate.toInt().toString() else "%.1f".format(Locale.US, rate)
    }

    private fun band(list: List<Int>, lo: Int, hi: Int) = list.count { it in lo..hi }
    private fun bandGe(list: List<Int>, lo: Int) = list.count { it >= lo }
    private fun bandLt(list: List<Int>, hi: Int) = list.count { it < hi }
}
