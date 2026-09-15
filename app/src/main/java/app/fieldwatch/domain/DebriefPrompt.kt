package app.fieldwatch.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Builds a paste-ready analyst prompt from the last 5 and 15 minutes of
 * in-memory sightings. One-tap share; no network.
 */
object DebriefPrompt {
    const val WINDOW_SHORT_MS = 5 * 60_000L
    const val WINDOW_MS = 15 * 60_000L
    private const val COLOCATE_MS = 10_000L
    private const val MAX_CHARS = 90_000

    fun build(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        settings: AppSettings,
        now: Long = System.currentTimeMillis(),
        operatorPath: List<GpsSample> = emptyList(),
        places: DebriefPlaces = DebriefPlaces.Off,
    ): String {
        val names = fleets.associate { it.id to it.name }
        val windowStart = now - WINDOW_MS
        val in15 = devices.filter { it.lastSeen >= windowStart || it.firstSeen >= windowStart }
            .sortedByDescending { it.rssi }
        val in5 = in15.filter { it.lastSeen >= now - WINDOW_SHORT_MS || it.firstSeen >= now - WINDOW_SHORT_MS }
        val wifi = in15.filter { it.kind == RadioKind.WIFI }
        val ble = in15.filter { it.kind == RadioKind.BLE }
        val named = in15.filter { it.fleetIds.isNotEmpty() }
        val hidden = wifi.filter { it.hiddenSsid }
        val randomized = ble.count { it.randomized }
        val arrived = in15.filter { it.firstSeen >= windowStart }
        val departed = in15.filter { it.gone || it.lastSeen < now - 45_000L }
        val persistent = in15.filter { dwellMs(it, windowStart, now) >= WINDOW_MS * 2 / 3 }
        val iso = utc(now)
        val start = utc(windowStart)
        val mesh = wifi.filter { isMesh(it) }
        val colocated = in15.filter { colocatedWithSit(it, windowStart, now) }
        val top5 = in15.distinctBy { it.key }.sortedByDescending { it.rssi }.take(5)
        val onboard = DebriefReport.build(devices, fleets, settings, operatorPath, now, places)
        val sigFamilies = named.groupBy { d ->
            d.fleetIds.joinToString("+") { names[it] ?: it }
        }.mapValues { it.value.size }.toList().sortedByDescending { it.second }
        val bleRssi = ble.map { it.rssi }
        val wifiRssi = wifi.map { it.rssi }
        fun band(list: List<Int>, lo: Int, hi: Int) = list.count { it in lo..hi }
        fun bandGe(list: List<Int>, lo: Int) = list.count { it >= lo }
        fun bandLt(list: List<Int>, hi: Int) = list.count { it < hi }

        val body = buildString {
            append(experimentalDisclaimerMarkdown())
            appendLine()
            appendLine("You are a **field RF analyst** for the operator who collected this sit. Fieldwatch is a stock-Android, receive-only Wi-Fi access-point + BLE-advertiser listener.")
            appendLine()
            appendLine("The **onboard Debrief** (included verbatim below) already tabulated the sit: counts, Where you were, tracking callouts, inventories, Extra attention, takeaway. **Do not rewrite that report.** Do not copy its section list. Your job is an **addendum the phone cannot write**: statistical depth, competing hypotheses, and what the onboard heuristics could not resolve.")
            appendLine()
            appendLine("**Working data** after the onboard Debrief is for calculation. Do not paste rosters into the answer. **Exception:** every radio in Possible trackers with you / Possible tail / Retail beacons with you / Wearables with you, and every Extra attention hit, must appear in the addendum with full identifiers (complete MAC, name, RSSI min/max, signatures, dwell, GPS-trail span if present). Do not list radios the operator only passed. Do not hide a follow MAC behind “a tracker-like BLE.”")
            appendLine()
            appendLine("Constraints you must respect:")
            appendLine("- Wi-Fi rows are **access points only** (beacons / hotspots / mesh / soft-APs). Associated clients are invisible.")
            appendLine("- BLE rows are advertisers. Randomized MACs are not stable identities and will not stitch across rotations.")
            appendLine("- Signature / OUI / company matches are **hypotheses**, not proof of a person, vehicle, or serial.")
            appendLine("- GPS stamps (if present) are the **operator phone** at hear-time, not the other radio’s location. Do not place a camera or tag at the GPS pin.")
            appendLine("- Place names (if present) are system reverse-geocode of those stamps. Offline: a note, no streets.")
            appendLine("- Do not claim a tracker is following unless the GPS co-travel section supports it. A radio with you the whole sit is **not automatically yours** — it may be planted. Do not dismiss it.")
            appendLine("- Do not invent radios that are not in the working data. If the sit is thin, say so and lower confidence.")
            appendLine("- RSSI is loudness at the phone, not meters. Structures (walls, metal, people, floors) change RSSI without a change in range.")
            appendLine("- Treat this paste as operationally sensitive (neighbor MACs, SSIDs, GPS, street names).")
            appendLine("- Do not give safety advice. Do not tell the operator they are safe or in danger.")
            appendLine()
            appendLine("## Your output (required — this is the addendum the operator reads)")
            appendLine("Write complete sentences. Headings as below. Short bullets only for findings, never for raw inventories. No markdown tables in the answer. No code fences. No dump of the working data or a rewrite of the onboard Debrief.")
            appendLine()
            appendLine("1. **Disclaimer** — Repeat the experimental-use disclaimer first.")
            appendLine("2. **What the onboard Debrief already established** — 3–5 sentences. Counts, distance, tracking callouts, Extra attention hits. Do not reprint inventories or stay lists.")
            appendLine("3. **Statistical picture** — Use the 5- vs 15-minute counts, RSSI bands, channel table, signature-family mix, turnover (first-seen vs persistent vs gone), and randomized-BLE fraction. Give numbers (counts, percents, rates per minute). Say what that mix usually means (street vs dwelling vs retail vs vehicle) and how confident you are. Note 5-minute vs 15-minute change: field getting denser, quieter, or stable.")
            appendLine("4. **Stays and path** — If GPS tagging ran: which stay or transit was RF-busiest (AP/BLE/signature density, not a guessed neighborhood). Compare stays with numbers. Do not pin a camera or tag to the GPS pin.")
            appendLine("5. **Tracking addendum** — Stress-test the onboard Possible trackers with you / Possible tail (finder tags) and the separate Retail beacons with you / Wearables with you lists using RSSI min/max, trail span, and dwell. Agree, qualify, or say the data are too thin — do not invent a tail the onboard test did not flag. Do not treat a retail beacon as a Find My tail. Do not roster radios the operator only passed. Do not dismiss whole-sit finder tags as “yours.” Find My rotation will not stitch.")
            appendLine("6. **Extra attention and other notables** — Depth on Extra attention hits and any high-confidence signature that is not street noise (payload, dwell, which stays heard them, competing product class). Pattern match, not identity, not a skimmer detector.")
            appendLine("7. **What the onboard report could not resolve** — Stock Android limits (no Wi-Fi stations, no Classic Bluetooth, no cellular, live cap 400, unnamed BLE eviction, OS Wi-Fi throttle). What a dedicated sniffer would still be needed for. Uncertainties that more walking / Hunt / a second sit would shrink.")
            appendLine("8. **Fieldwatch next steps** — Concrete in-app actions only (Filters, signatures, Pause + detail + Hunt, GPS tagging, a longer path). No safety advice. No “call the police.”")
            appendLine()
            appendLine("**Takeaway (required, last line).** One sentence starting with `Takeaway:` that adds *one insight the onboard takeaway does not already say* (a rate, a mix, a stay that was busier, a qualifier on a tracking callout), plus distance or “no GPS path.” Not a moral. Not a threat level.")
            appendLine()
            appendLine("## Collection context")
            appendLine("- Tool: Fieldwatch (app.fieldwatch), receive-only, no association / injection / cloud.")
            appendLine("- Window: last **15 minutes** ($start → $iso UTC), with a **5-minute** recent slice.")
            appendLine("- Scan intensity: ${settings.intensity.name.lowercase()}. Stale after ${settings.staleSec}s. Brief hold ${settings.decaySec}s.")
            appendLine("- Location tags on rows: ${if (settings.tagLocation) "on" else "off"}.")
            appendLine("- Online place names: ${if (settings.onlineLookup) "on" else "off"}.")
            appendLine("- In-memory live cap is 400 radios; unnamed BLE evicts after ~3 min. This is not a complete promiscuous capture.")
            appendLine()
            appendLine("## Onboard Debrief (verbatim — already shown to the operator; do not rewrite)")
            appendLine()
            appendLine(onboard.trimEnd())
            appendLine()
            appendLine("## Working data (for statistics — do not copy into the answer)")
            appendLine()
            append(DebriefReport.gpsAnalystMarkdown(devices, fleets, settings, operatorPath, now, places).trimEnd())
            appendLine()
            appendLine()
            appendLine("## Counts")
            table(
                this,
                listOf("slice", "Wi-Fi APs", "BLE ads", "signature hits", "hidden SSIDs", "randomized BLE", "new in slice", "persistent (~whole window)"),
                listOf(
                    listOf("15 min", wifi.size, ble.size, named.size, hidden.size, randomized, arrived.size, persistent.size).map { it.toString() },
                    listOf(
                        "5 min",
                        in5.count { it.kind == RadioKind.WIFI }.toString(),
                        in5.count { it.kind == RadioKind.BLE }.toString(),
                        in5.count { it.fleetIds.isNotEmpty() }.toString(),
                        in5.count { it.hiddenSsid }.toString(),
                        in5.count { it.kind == RadioKind.BLE && it.randomized }.toString(),
                        in5.count { it.firstSeen >= now - WINDOW_SHORT_MS }.toString(),
                        "—",
                    ),
                ),
            )
            appendLine()
            appendLine("## Precomputed statistics (15 min unless noted)")
            val bleNamed = named.count { it.kind == RadioKind.BLE }
            val wifiNamed = named.count { it.kind == RadioKind.WIFI }
            appendLine("- Randomized BLE: $randomized / ${ble.size} (" + pct(randomized, ble.size) + "%). Signature-matched: Wi-Fi $wifiNamed, BLE $bleNamed.")
            appendLine("- Turnover: ${arrived.size} first-seen in the 15 min window (" + perMin(arrived.size) + "/min), ${persistent.size} sat ≥2/3 of the window, ${departed.size} quiet or gone by the end.")
            appendLine(
                "- BLE RSSI bands (n=${ble.size}): ≥−50 ${bandGe(bleRssi, -50)}  " +
                    "−51..−70 ${band(bleRssi, -70, -51)}  " +
                    "−71..−85 ${band(bleRssi, -85, -71)}  " +
                    "<−85 ${bandLt(bleRssi, -85)}",
            )
            appendLine(
                "- Wi-Fi RSSI bands (n=${wifi.size}): ≥−50 ${bandGe(wifiRssi, -50)}  " +
                    "−51..−70 ${band(wifiRssi, -70, -51)}  " +
                    "−71..−85 ${band(wifiRssi, -85, -71)}  " +
                    "<−85 ${bandLt(wifiRssi, -85)}",
            )
            if (sigFamilies.isEmpty()) {
                appendLine("- Signature families: none.")
            } else {
                appendLine("- Signature families (count): " + sigFamilies.joinToString { "${it.first}=${it.second}" })
            }
            appendLine()
            appendLine("## Channel utilization (Wi-Fi APs, 15 min)")
            val byCh = wifi.groupBy { it.channel }.toSortedMap()
            if (byCh.isEmpty()) {
                appendLine("No Wi-Fi in window.")
            } else {
                table(
                    this,
                    listOf("channel", "MHz (sample)", "AP count", "hidden", "strongest RSSI"),
                    byCh.map { (ch, list) ->
                        listOf(
                            if (ch == 0) "?" else ch.toString(),
                            list.firstOrNull { it.frequencyMhz != 0 }?.frequencyMhz?.toString() ?: "—",
                            list.size.toString(),
                            list.count { it.hiddenSsid }.toString(),
                            list.maxOf { it.rssi }.toString(),
                        )
                    },
                )
            }
            appendLine()
            appendLine("## Mesh / hotspot flags")
            appendLine("- Mesh-tagged APs (capability/SSID): ${mesh.size}")
            mesh.take(20).forEach { append("- ").append(row(it, names, now, windowStart)).appendLine() }
            val hotspots = wifi.filter { looksHotspot(it) }
            appendLine("- Phone-hotspot-like SSIDs: ${hotspots.size}")
            hotspots.take(15).forEach { append("- ").append(row(it, names, now, windowStart)).appendLine() }
            appendLine()
            appendLine("## Top-5 strongest unique devices")
            table(
                this,
                listOf("rank", "kind", "name", "MAC", "RSSI", "dwell", "RSSI Δ first→last", "signatures"),
                top5.mapIndexed { i, d ->
                    listOf(
                        (i + 1).toString(),
                        if (d.kind == RadioKind.WIFI) "AP" else "LE",
                        d.displayName.take(24),
                        d.mac,
                        "${d.rssi} dBm",
                        fmtDur(dwellMs(d, windowStart, now)),
                        rssiDelta(d, windowStart),
                        d.fleetIds.joinToString("+") { names[it] ?: it }.ifBlank { "—" },
                    )
                },
            )
            appendLine()
            appendLine("## Operator co-location test")
            appendLine("Radios whose first seen is within ±10 s of window start **and** last seen within ±10 s of now (possible operator-carried gear):")
            if (colocated.isEmpty()) appendLine("- None.")
            else colocated.sortedByDescending { it.rssi }.forEach {
                append("- ").append(row(it, names, now, windowStart)).appendLine()
            }
            appendLine()
            appendLine("## Signature hits (all, 15 min)")
            if (named.isEmpty()) appendLine("None in this window.")
            else named.sortedByDescending { it.rssi }.forEach { d ->
                append("- ").append(row(d, names, now, windowStart))
                d.attentionNotes(fleets).forEach { (sig, note) ->
                    append(" | extra attention ($sig): ").append(note)
                }
                appendLine()
            }
            appendLine()
            appendLine("## Full Wi-Fi inventory (15 min)")
            if (wifi.isEmpty()) appendLine("None.")
            else wifi.forEach { d ->
                append("- ").append(row(d, names, now, windowStart))
                d.attentionNotes(fleets).forEach { (sig, note) ->
                    append(" | extra attention ($sig): ").append(note)
                }
                appendLine()
            }
            appendLine()
            appendLine("## Hidden-SSID APs")
            if (hidden.isEmpty()) appendLine("None.")
            else hidden.forEach { append("- ").append(row(it, names, now, windowStart)).appendLine() }
            appendLine()
            appendLine("## BLE inventory (named, signed, mfg, appearance, or ≥ −70 dBm)")
            val notableBle = ble.filter {
                it.name.isNotBlank() || it.fleetIds.isNotEmpty() || it.rssi >= -70 ||
                    it.manufacturerId != null || it.facts.appearance != null
            }.sortedByDescending { it.rssi }
            if (notableBle.isEmpty()) {
                appendLine("No named/strong BLE in window. Randomized unnamed ads: $randomized.")
            } else {
                notableBle.forEach { d ->
                    append("- ").append(row(d, names, now, windowStart))
                    val guess = DeviceExplain.guess(d, d.fleetIds.map { names[it] ?: it })
                    append(" | looks-like: ").append(guess.headline)
                    d.attentionNotes(fleets).forEach { (sig, note) ->
                        append(" | extra attention ($sig): ").append(note)
                    }
                    val rec = d.facts.mfgRecords.firstOrNull()
                        ?: d.manufacturerId?.let { MfgRecord(it, d.manufacturerDataHex) }
                    if (rec != null) {
                        val payload = AdvPayloadDecoder.decodeManufacturer(rec).take(4)
                        if (payload.isNotEmpty()) {
                            append(" | payload: ")
                            append(payload.joinToString("; ") { "${it.label}=${it.value.take(100)}" })
                        }
                    }
                    appendLine()
                }
            }
            val omitted = ble.size - notableBle.size
            if (omitted > 0) {
                appendLine("Unlisted BLE (mostly unnamed/randomized): $omitted. RSSI bands among them:")
                val rest = ble.filter { it !in notableBle }
                appendLine(
                    "  ≥−50: ${rest.count { it.rssi >= -50 }}  " +
                        "−50..−70: ${rest.count { it.rssi in -69..-50 }}  " +
                        "−70..−85: ${rest.count { it.rssi in -84..-70 }}  " +
                        "<−85: ${rest.count { it.rssi < -85 }}",
                )
            }
            appendLine()
            appendLine("## Persistence (15 min)")
            appendLine("Sat through most of the window (${persistent.size}):")
            if (persistent.isEmpty()) appendLine("- None.")
            else persistent.forEach {
                append("- ").append(it.displayName).append(" ").append(it.mac)
                append(" dwell ").append(fmtDur(dwellMs(it, windowStart, now)))
                appendLine()
            }
            appendLine("First appeared in this window (${arrived.size} total):")
            if (arrived.isEmpty()) appendLine("- None.")
            else arrived.sortedByDescending { it.rssi }.forEach {
                append("- ").append(row(it, names, now, windowStart)).appendLine()
            }
            appendLine("Quiet or gone by end of window (${departed.size} total):")
            if (departed.isEmpty()) appendLine("- None.")
            else departed.sortedBy { it.lastSeen }.forEach {
                append("- ").append(it.displayName).append(" ").append(it.mac)
                append(" last ").append(utc(it.lastSeen))
                if (it.gone) append(" (gone)")
                appendLine()
            }
            appendLine()
            appendLine("## Extra attention (operator caution on a matched signature)")
            val extraHits = in15.flatMap { d ->
                d.attentionNotes(fleets).map { (sig, note) -> Triple(d, sig, note) }
            }
            if (extraHits.isEmpty()) {
                appendLine("None in this window.")
            } else {
                extraHits.forEach { (d, sig, note) ->
                    append("- ").append(row(d, names, now, windowStart))
                    append(" | ").append(sig).append(": ").append(note)
                    appendLine()
                }
                appendLine("These are pattern matches, not identity and not a skimmer detector.")
            }
            appendLine()
            appendLine("## Anomaly flags (machine)")
            val flags = machineFlags(in15)
            if (flags.isEmpty()) appendLine("- No extra flags. Signature hits, Extra attention, and GPS co-travel already cover named matches.")
            else flags.forEach { append("- ").append(it).appendLine() }
            appendLine()
            appendLine("## End of working data")
            appendLine("Write the addendum now, following **Your output** at the top. Do not rewrite the onboard Debrief.")
        }
        return if (body.length <= MAX_CHARS) body
        else body.take(MAX_CHARS) + "\n\n[truncated for share-sheet size]\n"
    }

    fun experimentalDisclaimerMarkdown(): String = FieldwatchDisclaimer.experimentalMarkdown()

    private fun row(d: Sighting, names: Map<String, String>, now: Long, windowStart: Long): String = buildString {
        append(if (d.kind == RadioKind.WIFI) "AP" else "LE")
        append(" rssi=").append(d.rssi).append("dBm")
        append(" ").append(d.displayName)
        append(" ").append(d.mac)
        d.vendor?.let { append(" vendor=").append(it) }
        if (d.kind == RadioKind.WIFI && d.channel != 0) append(" ch").append(d.channel)
        if (d.hiddenSsid) append(" HIDDEN")
        if (d.randomized) append(" RAND")
        if (d.fleetIds.isNotEmpty()) {
            append(" sig=")
            append(d.fleetIds.joinToString("+") { names[it] ?: it })
        }
        append(" first=").append(age(now, d.firstSeen))
        append(" last=").append(age(now, d.lastSeen))
        append(" dwell=").append(fmtDur(dwellMs(d, windowStart, now)))
        append(" Δrssi=").append(rssiDelta(d, windowStart))
        d.facts.security?.let { if (it.isNotBlank()) append(" sec=").append(it.take(48)) }
        d.facts.wifiStandard?.let { append(" std=").append(it) }
    }

    private fun machineFlags(devices: List<Sighting>): List<String> {
        val out = ArrayList<String>()
        val pairing = devices.filter { d ->
            d.facts.serviceData.any { it.uuid.contains("FE2C", true) && it.dataHex.length == 6 }
        }
        if (pairing.isNotEmpty()) {
            out += "Google Fast Pair in pairing mode: " +
                pairing.joinToString { "${it.displayName} (${it.mac})" }
        }
        val loudUnknown = devices.filter {
            it.rssi >= -50 && it.fleetIds.isEmpty() && it.name.isBlank()
        }
        if (loudUnknown.isNotEmpty()) {
            out += "Very strong unnamed radios (≥ −50 dBm): " +
                loudUnknown.take(8).joinToString { "${it.mac} ${it.rssi} dBm" }
        }
        val rand = devices.count { it.kind == RadioKind.BLE && it.randomized }
        if (rand >= 20) {
            out += "High randomized BLE count ($rand) — typical of phones; not a tracking finding."
        }
        return out
    }

    private fun colocatedWithSit(d: Sighting, windowStart: Long, now: Long): Boolean {
        val startHit = abs(d.firstSeen - windowStart) <= COLOCATE_MS
        val endHit = abs(d.lastSeen - now) <= COLOCATE_MS
        return startHit && endHit
    }

    private fun isMesh(d: Sighting): Boolean {
        val cap = (d.facts.capabilities ?: "").uppercase()
        val sec = (d.facts.security ?: "").uppercase()
        val name = d.name.uppercase()
        return "MESH" in cap || "MESH" in sec || "MESH" in name
    }

    private fun looksHotspot(d: Sighting): Boolean {
        val n = d.name
        return n.startsWith("ANDROID-", true) ||
            n.contains("hotspot", true) ||
            n.startsWith("DIRECT-", true) ||
            n.startsWith("iPhone", true) ||
            n.contains("Galaxy", true) && n.contains("hotspot", true)
    }

    private fun rssiDelta(d: Sighting, from: Long): String {
        val inWin = d.rssiHistory.filter { it.at >= from }
        val samples = if (inWin.size >= 2) inWin else d.rssiHistory
        if (samples.size < 2) return "n/a"
        val delta = samples.last().rssi - samples.first().rssi
        return (if (delta >= 0) "+" else "") + "${delta} dB"
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

    private fun table(sb: StringBuilder, headers: List<String>, rows: List<List<String>>) {
        sb.append("| ").append(headers.joinToString(" | ")).appendLine(" |")
        sb.append("| ").append(headers.joinToString(" | ") { "---" }).appendLine(" |")
        rows.forEach { sb.append("| ").append(it.joinToString(" | ")).appendLine(" |") }
    }

    private fun utc(ms: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(ms))
    }

    private fun age(now: Long, then: Long): String = fmtDur(now - then) + " ago"

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
}
