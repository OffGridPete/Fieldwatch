package app.fieldwatch.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DebriefSection(
    val number: String,
    val title: String,
    val body: String,
    val alert: Boolean = false,
)

data class DebriefPlaces(
    val attempted: Boolean,
    val available: Boolean,
    val note: String,
    val lines: List<String> = emptyList(),
    val namesByCell: Map<String, String> = emptyMap(),
) {
    /** Exact GPS cell, then nearest named cell within [maxM]. */
    fun nameNear(lat: Double, lon: Double, maxM: Double = 90.0): String? {
        namesByCell[Geo.cellKey(lat, lon)]?.let { return it }
        var best: String? = null
        var bestD = maxM
        for ((key, name) in namesByCell) {
            val parts = key.split(',')
            if (parts.size != 2) continue
            val klat = parts[0].toDoubleOrNull() ?: continue
            val klon = parts[1].toDoubleOrNull() ?: continue
            val d = Geo.meters(lat, lon, klat, klon)
            if (d < bestD) {
                bestD = d
                best = name
            }
        }
        return best
    }

    fun areaLine(): String {
        if (!attempted) return "off"
        val named = namesByCell.values.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (named.isEmpty()) return note
        return named.joinToString(" · ")
    }

    companion object {
        val Off = DebriefPlaces(false, false, "off")
    }
}

data class ExtraAttentionHit(
    val signature: String,
    val radioLabel: String,
    val note: String,
)

data class DebriefDoc(
    val generatedUtc: String,
    val windowLine: String,
    val meta: List<Pair<String, String>>,
    val disclaimer: String,
    val trackingAlert: Boolean,
    val takeaway: String,
    val sections: List<DebriefSection>,
    val extraAttention: List<ExtraAttentionHit> = emptyList(),
) {
    fun toPlainText(): String = buildString {
        appendLine("FIELDWATCH FIELD DEBRIEF")
        appendLine()
        appendLine("DISCLAIMER")
        appendLine(disclaimer)
        appendLine()
        meta.forEach { (k, v) -> appendLine("${k.padEnd(14)}$v") }
        appendLine()
        sections.forEach { sec ->
            appendLine("${sec.number}. ${sec.title.uppercase()}")
            appendLine(sec.body.trimEnd())
            appendLine()
        }
        appendLine("—")
        appendLine("Takeaway: $takeaway")
    }

    fun withDemoMacs(macs: Collection<String>, demo: Boolean): DebriefDoc {
        if (!demo) return this
        fun t(s: String) = Geo.redactCoordsIn(MacUtil.redactMacsIn(s, macs, true), true)
        val note = "MAC tails (**:**:**) and GPS coordinates masked. Logs on the phone are unchanged."
        return copy(
            meta = listOf("Privacy" to note) + meta.map { it.first to t(it.second) },
            disclaimer = t(disclaimer),
            takeaway = t(takeaway),
            sections = sections.map { it.copy(title = t(it.title), body = t(it.body)) },
            extraAttention = extraAttention.map {
                it.copy(signature = t(it.signature), radioLabel = t(it.radioLabel), note = t(it.note))
            },
        )
    }
}

/**
 * Standalone field debrief (not an AI prompt). Heuristic sit report from
 * the last 15 minutes plus GPS co-travel of tracker-like radios.
 */
object DebriefReport {
    private const val WINDOW_MS = 15 * 60_000L
    private const val SHORT_MS = 5 * 60_000L
    private const val MOVE_M = 45.0
    /** Possible-tail extra gates. Own-kit uses a louder, longer “still here” window. */
    private const val COVER_FRAC = 0.5
    private const val FADE_DB = 12
    private const val TRAIL_LOUD_DBM = CoTravel.TRAIL_LOUD_DBM
    /** Own-kit “still here” — AirTags advertise slowly and rotate. */
    private const val OWN_HERE_MS = 180_000L
    private const val TAIL_HERE_MS = 20_000L
    private const val ON_BODY_MAX = -55
    private const val ON_BODY_MIN = -70

    fun build(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        settings: AppSettings,
        operatorPath: List<GpsSample>,
        now: Long = System.currentTimeMillis(),
        places: DebriefPlaces = DebriefPlaces.Off,
    ): String = document(devices, fleets, settings, operatorPath, now, places).toPlainText()

    fun document(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        settings: AppSettings,
        operatorPath: List<GpsSample>,
        now: Long = System.currentTimeMillis(),
        places: DebriefPlaces = DebriefPlaces.Off,
    ): DebriefDoc {
        val names = fleets.associate { it.id to it.name }
        val windowStart = now - WINDOW_MS
        val inWin = devices.filter { it.lastSeen >= windowStart || it.firstSeen >= windowStart }
            .sortedByDescending { it.rssi }
        val wifi = inWin.filter { it.kind == RadioKind.WIFI }
        val ble = inWin.filter { it.kind == RadioKind.BLE }
        val named = inWin.filter { it.fleetIds.isNotEmpty() }
        val hidden = wifi.filter { it.hiddenSsid }
        val randomized = ble.count { it.randomized }
        val arrived = inWin.filter { it.firstSeen >= windowStart }
        val persistent = inWin.filter { dwellMs(it, windowStart, now) >= WINDOW_MS * 2 / 3 }
        val path = operatorPath.filter { it.at >= windowStart }
        val pathSpan = Geo.spanM(path)
        val pathLen = Geo.pathLengthM(path)
        val trackers = inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.FINDER }
        val follow = followAssessments(trackers, names, path, windowStart, now, TrackerMatch.Kind.FINDER)
        val following = follow.filter { it.verdict == Verdict.FOLLOWING }
        val withYou = follow.filter { it.verdict == Verdict.MOVED_WITH_YOU }
        val ownLikely = follow.filter { it.verdict == Verdict.OWN_LIKELY }
        val wholeSit = ownLikely + withYou
        val beaconFollow = followAssessments(
            inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.BEACON },
            names, path, windowStart, now, TrackerMatch.Kind.BEACON,
        )
        val wearableFollow = followAssessments(
            inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.WEARABLE },
            names, path, windowStart, now, TrackerMatch.Kind.WEARABLE,
        )
        val beaconsWithYou = stayedWithYou(beaconFollow)
        val wearablesWithYou = stayedWithYou(wearableFollow)

        val byCh = wifi.groupBy { it.channel }.toSortedMap()
        val networks = buildString {
            appendLine("Heard ${wifi.size} AP(s); ${hidden.size} hidden SSID; ${persistent.count { it.kind == RadioKind.WIFI }} sat most of the window.")
            if (byCh.isNotEmpty()) {
                appendLine("Channel occupancy:")
                byCh.forEach { (ch, list) ->
                    val label = if (ch == 0) "unknown" else "ch $ch"
                    appendLine("  $label — ${list.size} AP(s), strongest ${list.maxOf { it.rssi }} dBm")
                }
            }
            appendLine("Loudest APs:")
            wifi.take(12).forEach { d ->
                appendLine("  · ${wifiLine(d, names, windowStart, now)}")
                d.attentionNotes(fleets).forEach { (sig, note) ->
                    appendLine("    extra attention ($sig): $note")
                }
            }
            if (hidden.isNotEmpty()) {
                appendLine("Hidden SSIDs:")
                hidden.forEach { appendLine("  · ${it.mac}  ${it.vendor ?: ""}  ${it.rssi} dBm  ch ${it.channel}") }
            }
        }
        val notable = ble.filter {
            it.fleetIds.isNotEmpty() || it.name.isNotBlank() || it.rssi >= -65 || it.manufacturerId != null
        }.sortedByDescending { it.rssi }.take(20)
        val bleBody = buildString {
            appendLine("Heard ${ble.size} advertiser(s); $randomized with randomized addresses; ${named.count { it.kind == RadioKind.BLE }} signature-matched.")
            if (notable.isNotEmpty()) {
                appendLine("Notable BLE:")
                notable.forEach { d ->
                    val guess = DeviceExplain.guess(d, d.fleetIds.map { names[it] ?: it })
                    appendLine("  · ${bleLine(d, names, windowStart, now)}  |  ${guess.headline}")
                    d.attentionNotes(fleets).forEach { (sig, note) ->
                        appendLine("    extra attention ($sig): $note")
                    }
                    SignatureFieldDecoder.decodeSighting(d, fleets).forEach { row ->
                        appendLine("    ${row.label}: ${row.display}")
                    }
                }
            }
        }
        val sigBody = buildString {
            if (named.isEmpty()) appendLine("None in this window.")
            else {
                named.groupBy { it.fleetIds.joinToString("+") { id -> names[id] ?: id } }
                    .toList().sortedByDescending { it.second.size }
                    .forEach { (sig, list) ->
                        appendLine("${list.size}× $sig")
                        list.sortedByDescending { it.rssi }.take(8).forEach {
                            appendLine("  · ${it.displayName}  ${it.mac}  ${it.rssi} dBm")
                        }
                        list.flatMap { it.attentionNotes(fleets) }.distinct().forEach { (name, note) ->
                            appendLine("  extra attention ($name): $note")
                        }
                    }
            }
        }
        val persistBody = buildString {
            appendLine("Sat most of the 15 minutes: ${persistent.size}")
            persistent.take(15).forEach {
                appendLine("  · ${it.displayName}  ${it.mac}  dwell ${fmtDur(dwellMs(it, windowStart, now))}")
            }
            if (persistent.isEmpty()) appendLine("  · None.")
            appendLine("First seen in this window: ${arrived.size} (loudest 8 below)")
            arrived.sortedByDescending { it.rssi }.take(8).forEach {
                appendLine("  · ${it.displayName}  ${it.mac}  ${it.rssi} dBm")
            }
        }
        val flags = anomalyLines(inWin)
        val anomalyBody = if (flags.isEmpty()) {
            "No extra flags. Signature hits, Extra attention, and tracking callouts already cover named pattern matches."
        } else flags.joinToString("\n") { "  · $it" }
        val attentionHits = inWin.flatMap { d ->
            d.attentionNotes(fleets).map { (sig, note) -> Triple(d, sig, note) }
        }
        val actionBody = actions(following, withYou, ownLikely, beaconsWithYou, wearablesWithYou, settings, pathSpan)
            .joinToString("\n") { "  · $it" }

        val distanceLine = when {
            !settings.tagLocation -> "GPS tagging off — no path"
            path.size < 2 -> "GPS tagging on, fewer than 2 fixes in this window"
            else -> "traveled ${fmtDist(pathLen)} along path · span ${fmtDist(pathSpan)} · ${path.size} fixes"
        }
        val lookupLine = when {
            !places.attempted -> "off"
            places.namesByCell.isNotEmpty() -> places.areaLine()
            else -> places.note
        }
        var n = 1
        fun next() = (n++).toString()
        val sections = buildList {
            add(DebriefSection(next(), "Executive summary", execSummary(wifi, ble, named, hidden, randomized, pathSpan, pathLen, following, withYou, ownLikely, beaconsWithYou, wearablesWithYou, settings, places)))
            add(DebriefSection(next(), "Where you were", whereYouWere(settings, path, pathLen, pathSpan, inWin, names, places, now)))
            add(
                DebriefSection(
                    next(),
                    "Tracking assessment",
                    trackingSection(settings, path, pathSpan, pathLen, following, wholeSit, beaconsWithYou, wearablesWithYou),
                ),
            )
            if (wholeSit.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        "Possible trackers with you",
                        trackerCallout(
                            "Finder tags (AirTag / Find My, SmartTag, Tile, Chipolo, Pebblebee) and loud pocket Apple BLE. " +
                                "These radios stayed with your GPS path for this sit. " +
                                "Fieldwatch cannot tell your own tag or phone from a tracker planted in the car, bag, or on you before you started. " +
                                "Account for each MAC. Not a finding and not identity.",
                            wholeSit,
                        ),
                        alert = true,
                    ),
                )
            }
            if (following.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        "Possible tail",
                        trackerCallout(
                            "Finder tags that were not heard when this sit started, then stayed with your path. " +
                                "That can mean someone started following you (their phone or tag), or a device was added during the trip. " +
                                "Not a finding and not identity.",
                            following,
                        ),
                        alert = true,
                    ),
                )
            }
            if (beaconsWithYou.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        "Retail beacons with you",
                        trackerCallout(
                            "iBeacon / Minew / Estimote / Kontakt.io / Target Atrius basket radios that stayed with your GPS path. " +
                                "Location beacons are usually fixtures in a store or venue — they do not typically move with you. " +
                                "If one did, account for it (a Target basket you pushed, your own test tag, a badge, or a short path that still overlaps a fixture). " +
                                "Not the same as a Find My tail. Not a finding and not identity.",
                            beaconsWithYou,
                        ),
                        alert = true,
                    ),
                )
            }
            if (wearablesWithYou.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        "Wearables with you",
                        trackerCallout(
                            "Garmin / Fitbit / Oura radios that stayed with your GPS path. " +
                                "Watches and rings usually move with the person wearing them — often your own kit or someone walking with you. " +
                                "They are not typically planted trackers. Account for each MAC. Not a finding and not identity.",
                            wearablesWithYou,
                        ),
                        alert = true,
                    ),
                )
            }
            add(DebriefSection(next(), "Environment", environment(wifi, ble, randomized, persistent, pathSpan, pathLen)))
            add(DebriefSection(next(), "Networks (Wi-Fi access points)", networks.trimEnd()))
            add(DebriefSection(next(), "Bluetooth LE", bleBody.trimEnd()))
            add(DebriefSection(next(), "Signature hits", sigBody.trimEnd()))
            add(DebriefSection(next(), "Persistence", persistBody.trimEnd()))
            if (attentionHits.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        "Extra attention",
                        buildString {
                            appendLine("Pattern match, not identity, not a skimmer detector, not a safety finding.")
                            attentionHits.forEach { (d, sig, note) ->
                                appendLine("  · ${d.displayName}  ${d.mac}  ${d.rssi} dBm  [$sig]")
                                appendLine("    $note")
                            }
                        }.trimEnd(),
                        alert = true,
                    ),
                )
            }
            add(DebriefSection(next(), "Anomalies", anomalyBody))
            add(DebriefSection(next(), "Privacy", privacy(wifi, ble, randomized, hidden, settings, places)))
            add(DebriefSection(next(), "Recommended actions", actionBody))
        }

        return DebriefDoc(
            generatedUtc = utc(now),
            windowLine = "last 15 minutes (${utc(windowStart)} → ${utc(now)} UTC)",
            meta = listOf(
                "Generated" to "${utc(now)} UTC",
                "Window" to "last 15 minutes (${utc(windowStart)} → ${utc(now)} UTC)",
                "Tool" to "Fieldwatch (app.fieldwatch) · stock Android · receive-only Wi-Fi AP + BLE advertiser",
                "Scan" to "${settings.intensity.name.lowercase()} · stale ${settings.staleSec}s · brief hold ${settings.decaySec}s",
                "GPS tag" to if (settings.tagLocation) "on" else "off",
                "Distance" to distanceLine,
                "Places" to lookupLine,
                "Classification" to "Operationally sensitive — neighbor SSIDs, MACs, operator GPS",
            ),
            disclaimer = FieldwatchDisclaimer.report,
            trackingAlert = following.isNotEmpty() || ownLikely.isNotEmpty() || withYou.isNotEmpty(),
            takeaway = takeaway(following, withYou, ownLikely, beaconsWithYou, wearablesWithYou, pathSpan, settings, named),
            sections = sections,
            extraAttention = attentionHits.map { (d, sig, note) ->
                ExtraAttentionHit(
                    signature = sig,
                    radioLabel = "${d.displayName}  ${d.mac}  ${d.rssi} dBm",
                    note = note,
                )
            },
        )
    }

    /**
     * GPS / places / co-travel block for the AI Export prompt. Same heuristics
     * as the field debrief; markdown so a chat model can cite it.
     */
    fun gpsAnalystMarkdown(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        settings: AppSettings,
        operatorPath: List<GpsSample>,
        now: Long = System.currentTimeMillis(),
        places: DebriefPlaces = DebriefPlaces.Off,
    ): String = buildString {
        val names = fleets.associate { it.id to it.name }
        val windowStart = now - WINDOW_MS
        val path = operatorPath.filter { it.at >= windowStart }
        val pathSpan = Geo.spanM(path)
        val pathLen = Geo.pathLengthM(path)
        val inWin = devices.filter { it.lastSeen >= windowStart || it.firstSeen >= windowStart }
        val trackers = inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.FINDER }
        val follow = followAssessments(trackers, names, path, windowStart, now, TrackerMatch.Kind.FINDER)
        val beaconsMd = stayedWithYou(
            followAssessments(
                inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.BEACON },
                names, path, windowStart, now, TrackerMatch.Kind.BEACON,
            ),
        )
        val wearablesMd = stayedWithYou(
            followAssessments(
                inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.WEARABLE },
                names, path, windowStart, now, TrackerMatch.Kind.WEARABLE,
            ),
        )

        appendLine("## Where you were (operator GPS)")
        appendLine("- Tag detections with GPS: ${if (settings.tagLocation) "on" else "off"}.")
        appendLine(
            "- Online place names: " +
                if (places.attempted) places.note
                else "off (Settings → Online place names in Debrief). No reverse-geocode this export.",
        )
        append(whereYouWere(settings, path, pathLen, pathSpan, inWin, names, places, now).trimEnd())
        appendLine()
        appendLine()
        if (path.size < 2 || pathSpan < MOVE_M) {
            appendLine("- Following test: insufficient movement (need ~45 m span). Do not infer a tail.")
            appendLine()
        }
        appendLine("## GPS co-travel")
        appendLine(
            "Only radios that stayed with the operator path are listed. " +
                "House tags and other radios the operator only passed are omitted — they are not tracking. " +
                "Not identity. Find My MAC rotation will not stitch a tail that changes address. " +
                "Possible tail extra gates (walks): trail covers ≥ half the operator path, " +
                "≥ 2/3 of GPS stamps at −75 dBm or louder, last stamp not 12 dB below loudest. " +
                "Fail any one → omit (pass-by), not a tail. " +
                "Finder tags (AirTag / SmartTag / Tile / Chipolo / Pebblebee / Find My / loud pocket Apple) " +
                "are the tracking test. Retail beacons and wearables that co-travel are listed separately — " +
                "they do not typically move with you (beacons) or are usually own kit (wearables).",
        )
        val followingMd = follow.filter { it.verdict == Verdict.FOLLOWING }
        val wholeSitMd = follow.filter {
            it.verdict == Verdict.OWN_LIKELY || it.verdict == Verdict.MOVED_WITH_YOU
        }
        if (followingMd.isEmpty() && wholeSitMd.isEmpty() && beaconsMd.isEmpty() && wearablesMd.isEmpty()) {
            appendLine("- None stayed with the path.")
        } else {
            fun dump(title: String, rows: List<FollowHit>) {
                if (rows.isEmpty()) return
                appendLine()
                appendLine("### $title")
                rows.forEach { h ->
                    val d = h.device
                    appendLine(
                        "- ${h.label}  ${d.displayName}  ${d.mac}  RSSI ${d.rssi} dBm " +
                            "(min ${d.rssiMin} / max ${d.rssiMax})  trail ${h.samples} fixes, span ${h.spanM.toInt()} m",
                    )
                    appendLine("  ${h.detail}")
                }
            }
            dump(
                "Possible trackers with you (finder tags, whole sit — yours or planted before you started)",
                wholeSitMd,
            )
            dump(
                "Possible tail (finder tags, first heard after this sit started, then stayed)",
                followingMd,
            )
            dump(
                "Retail beacons with you (iBeacon / Minew / Estimote / Kontakt.io / Target Atrius basket — fixtures; a pushed cart will co-travel)",
                beaconsMd,
            )
            dump(
                "Wearables with you (Garmin / Fitbit / Oura — usually own kit or a companion)",
                wearablesMd,
            )
        }
    }

    private enum class Verdict { FOLLOWING, MOVED_WITH_YOU, OWN_LIKELY, STATIONARY, INSUFFICIENT }

    private data class FollowHit(
        val device: Sighting,
        val label: String,
        val verdict: Verdict,
        val detail: String,
        val spanM: Double,
        val samples: Int,
    )

    private fun stayedWithYou(hits: List<FollowHit>): List<FollowHit> =
        hits.filter {
            it.verdict == Verdict.FOLLOWING ||
                it.verdict == Verdict.MOVED_WITH_YOU ||
                it.verdict == Verdict.OWN_LIKELY
        }

    private fun followAssessments(
        trackers: List<Sighting>,
        names: Map<String, String>,
        operatorPath: List<GpsSample>,
        windowStart: Long,
        now: Long,
        kind: TrackerMatch.Kind,
    ): List<FollowHit> {
        val opSpan = Geo.spanM(operatorPath)
        val opLen = Geo.pathLengthM(operatorPath)
        return trackers.map { d ->
            val label = TrackerMatch.label(d, names)
            val trail = d.gpsTrail.filter { it.at >= windowStart }
            val span = Geo.spanM(trail)
            val trailLen = Geo.pathLengthM(trail)
            val presentAtStart = d.firstSeen <= windowStart + 15_000L
            val stillHere = now - d.lastSeen <= TAIL_HERE_MS
            val ownHere = now - d.lastSeen <= OWN_HERE_MS
            val onBody = d.rssiMax >= ON_BODY_MAX && d.rssiMin >= ON_BODY_MIN && trail.size >= 2
            val cover = opLen > 0.0 && trailLen >= COVER_FRAC * opLen
            val (verdict, detail) = when {
                operatorPath.size < 2 || opSpan < MOVE_M ->
                    Verdict.INSUFFICIENT to "Operator GPS path too short (${opSpan.toInt()} m) to test following."
                trail.size < 2 ->
                    Verdict.INSUFFICIENT to "Heard, but not at two GPS points. Cannot test co-travel."
                onBody && ownHere ->
                    Verdict.OWN_LIKELY to onBodyLine(kind, d, trail.size)
                cover && ownHere && d.rssiMax >= ON_BODY_MAX ->
                    Verdict.OWN_LIKELY to
                        "Heard along ${trailLen.toInt()} m of your ${opLen.toInt()} m path and still loud (${d.rssiMax} dBm). " +
                        withYouNote(kind)
                span < MOVE_M * 0.6 ->
                    Verdict.STATIONARY to "Heard near one place (${span.toInt()} m span) while you moved ${opSpan.toInt()} m. Looks stationary — you walked away from it."
                presentAtStart && stillHere && d.rssiMax >= ON_BODY_MAX ->
                    Verdict.OWN_LIKELY to
                        "Moved ${span.toInt()} m with you, already on the air when this 15-minute window opened, strong (${d.rssi} dBm). " +
                        withYouNote(kind)
                presentAtStart && stillHere ->
                    Verdict.MOVED_WITH_YOU to
                        "GPS samples span ${span.toInt()} m along your path (${trail.size} fixes). Already on the air when this window opened and still here. " +
                        withYouNote(kind)
                !presentAtStart && span >= MOVE_M && trail.size >= 3 ->
                    possibleTail(trail, span, opLen, kind)
                else ->
                    Verdict.STATIONARY to
                        "Heard along ${span.toInt()} m (${trail.size} GPS stamps) but did not stay loud on you. Neighborhood arc / pass-by, not a tail."
            }
            FollowHit(d, label, verdict, detail, span, trail.size)
        }.sortedBy { it.verdict.ordinal }
    }

    private fun onBodyLine(kind: TrackerMatch.Kind, d: Sighting, stamps: Int): String {
        val loud = "Stayed loud with you the whole sit (${d.rssiMax} to ${d.rssiMin} dBm, $stamps GPS stamps). "
        return loud + withYouNote(kind)
    }

    private fun withYouNote(kind: TrackerMatch.Kind): String = when (kind) {
        TrackerMatch.Kind.FINDER ->
            "With you the whole sit — yours or planted before you started. Account for it. Find My / iPhone addresses rotate; this MAC is this session."
        TrackerMatch.Kind.BEACON ->
            "Location beacons do not typically move with you. Account for it (own test tag, badge, or a short overlap with a fixture)."
        TrackerMatch.Kind.WEARABLE ->
            "Typical of a watch or ring you or a companion are wearing. Not typically a planted tracker."
    }

    /**
     * Extra gates on possible tail only. A neighborhood radio heard on a sidewalk
     * arc, or that faded as you walked, is stationary — not a follower.
     * Bag/car tags still cover most of the path and stay loud.
     */
    private fun possibleTail(
        trail: List<GpsSample>,
        span: Double,
        opLen: Double,
        kind: TrackerMatch.Kind,
    ): Pair<Verdict, String> {
        val trailLen = Geo.pathLengthM(trail)
        val peak = trail.maxOf { it.rssi }
        val last = trail.last().rssi
        val fade = peak - last
        val loudN = trail.count { it.rssi >= TRAIL_LOUD_DBM }
        val loudNeed = (trail.size * 2 + 2) / 3
        val coverNeed = opLen * COVER_FRAC
        val coverPct = if (opLen <= 0.0) 0 else ((trailLen / opLen) * 100.0).toInt()
        return when {
            fade >= FADE_DB ->
                Verdict.STATIONARY to
                    "Appeared after the sit started, but last GPS stamp was $last dBm after a loudest of $peak dBm (−${fade} dB). Looks like you walked away from a fixture, not a tail."
            loudN < loudNeed ->
                Verdict.STATIONARY to
                    "Appeared after the sit started and GPS span was ${span.toInt()} m, but only $loudN/${trail.size} stamps were loud (−75 dBm+). Looks like a pass-by, not a tail."
            trailLen < coverNeed ->
                Verdict.STATIONARY to
                    "Appeared after the sit started, but was only heard along ${trailLen.toInt()} m of your ${opLen.toInt()} m path ($coverPct%). Neighborhood arc / pass-by, not a tail."
            else -> {
                val stats =
                    "Appeared after the sit started, then stayed loud with you across ${span.toInt()} m " +
                        "(${trailLen.toInt()} m of your ${opLen.toInt()} m path, $coverPct%; " +
                        "$loudN/${trail.size} GPS stamps ≥ −75 dBm). "
                val note = when (kind) {
                    TrackerMatch.Kind.FINDER ->
                        "Treat as a possible tail until you visually account for it."
                    TrackerMatch.Kind.BEACON ->
                        "Unusual for a retail/location beacon — they do not typically move with you. Account for it; not the same as a Find My tail."
                    TrackerMatch.Kind.WEARABLE ->
                        "Typical of a watch that joined the sit (you put it on, or someone walking with you). Not typically a planted tracker."
                }
                Verdict.FOLLOWING to stats + note
            }
        }
    }

    private fun execSummary(
        wifi: List<Sighting>,
        ble: List<Sighting>,
        named: List<Sighting>,
        hidden: List<Sighting>,
        randomized: Int,
        pathSpan: Double,
        pathLen: Double,
        following: List<FollowHit>,
        withYou: List<FollowHit>,
        ownLikely: List<FollowHit>,
        beaconsWithYou: List<FollowHit>,
        wearablesWithYou: List<FollowHit>,
        settings: AppSettings,
        places: DebriefPlaces,
    ): String = buildString {
        append("In the last 15 minutes Fieldwatch heard ${wifi.size} Wi-Fi access points and ${ble.size} BLE advertisers")
        append(" (${named.size} signature-matched, ${hidden.size} hidden SSIDs, $randomized randomized BLE). ")
        if (settings.tagLocation && pathLen > 0) {
            append("Overall distance traveled: ${fmtDist(pathLen)} along the GPS path (straight-line span ${fmtDist(pathSpan)}). ")
        }
        if (places.namesByCell.isNotEmpty()) {
            append("Stops / area: ${places.areaLine()}. ")
        } else if (places.attempted && settings.tagLocation) {
            append("${places.note} ")
        }
        val wholeSit = ownLikely + withYou
        when {
            following.isNotEmpty() || wholeSit.isNotEmpty() -> {
                append("TRACKING NOTE. ")
                if (wholeSit.isNotEmpty()) {
                    append("${wholeSit.size} finder tag(s) with you the whole sit (your kit or planted before you started): ")
                    append(wholeSit.joinToString { "${it.label} ${it.device.mac}" })
                    append(". ")
                }
                if (following.isNotEmpty()) {
                    append("${following.size} possible tail(s) first heard after this sit started: ")
                    append(following.joinToString { "${it.label} ${it.device.mac}" })
                    append(". ")
                }
                append("Account for every MAC — Fieldwatch cannot tell yours from a plant. ")
            }
            !settings.tagLocation -> {
                append("GPS tagging is off, so a following test was not performed. Enable “Tag detections with GPS” and walk to test. ")
            }
            pathSpan < MOVE_M -> {
                append("GPS displacement was only ${pathSpan.toInt()} m — too short to test whether a tracker is following. Walk farther with tagging on. ")
            }
            else -> append("No finder tag clearly stayed with the GPS path in this window. ")
        }
        if (beaconsWithYou.isNotEmpty()) {
            append("Retail beacon(s) also stayed with the path (unusual — fixtures do not typically move with you): ")
            append(beaconsWithYou.joinToString { "${it.label} ${it.device.mac}" })
            append(". ")
        }
        if (wearablesWithYou.isNotEmpty()) {
            append("Wearable(s) stayed with the path (usually your watch/ring or a companion): ")
            append(wearablesWithYou.joinToString { "${it.label} ${it.device.mac}" })
            append(".")
        }
    }

    private fun trackingSection(
        settings: AppSettings,
        path: List<GpsSample>,
        pathSpan: Double,
        pathLen: Double,
        following: List<FollowHit>,
        wholeSit: List<FollowHit>,
        beaconsWithYou: List<FollowHit>,
        wearablesWithYou: List<FollowHit>,
    ): String = buildString {
        if (!settings.tagLocation) {
            appendLine("GPS tagging is OFF. Fieldwatch cannot test whether a radio moved with you.")
            appendLine("Turn on Settings → Tag detections with GPS, walk or drive 50+ m, then run Debrief again.")
            return@buildString
        }
        appendLine("Overall distance traveled: ${fmtDist(pathLen)} along the GPS path (${path.size} samples). Straight-line span ${fmtDist(pathSpan)}.")
        appendLine("Co-travel is split by class: finder tags (AirTag / Find My, SmartTag, Tile, Chipolo, Pebblebee, loud pocket Apple), retail beacons (iBeacon, Minew, Estimote, Kontakt.io, Target Atrius basket), and wearables (Garmin, Fitbit, Oura).")
        if (path.size < 2 || pathSpan < MOVE_M) {
            appendLine("Insufficient movement to distinguish a radio that stayed with you from one you passed. Walk or drive farther and re-run.")
            return@buildString
        }
        if (following.isEmpty() && wholeSit.isEmpty() && beaconsWithYou.isEmpty() && wearablesWithYou.isEmpty()) {
            appendLine("No finder tag, retail beacon, or wearable stayed with you. House tags and other radios you only passed are not listed.")
        } else {
            appendLine("Callouts below are only radios that stayed with the path. Radios you passed (store fixtures, house tags) are omitted.")
        }
    }

    private fun trackerCallout(intro: String, rows: List<FollowHit>): String = buildString {
        appendLine(intro)
        appendLine()
        rows.forEach { h ->
            val d = h.device
            appendLine("  • ${h.label}")
            appendLine("    ${d.displayName}  ${d.mac}  RSSI ${d.rssi} dBm (min ${d.rssiMin} / max ${d.rssiMax})")
            appendLine("    ${h.detail}")
        }
    }.trimEnd()

    private fun whereYouWere(
        settings: AppSettings,
        path: List<GpsSample>,
        pathLen: Double,
        pathSpan: Double,
        devices: List<Sighting>,
        names: Map<String, String>,
        places: DebriefPlaces,
        now: Long,
    ): String = buildString {
        appendLine("Phone GPS at hear-time, not the other radio’s location and not a camera pole. Stays are clusters within about 40 m; hops between them are transit. Coordinates are not repeated on every Wi-Fi/BLE line.")
        if (!settings.tagLocation) {
            appendLine("GPS tagging is OFF. Turn on Settings → Tag detections with GPS to record where you were when radios were heard.")
            return@buildString
        }
        if (path.isEmpty()) {
            appendLine("GPS tagging is on, but this window has no fixes yet.")
            return@buildString
        }
        appendLine("Overall: ${fmtDist(pathLen)} along-track, span ${fmtDist(pathSpan)}, ${path.size} fixes.")
        if (places.attempted) {
            appendLine(places.note)
            appendLine("Street names are approximate. Do not treat a street as the location of a matched camera or tag.")
        }
        val legs = Geo.legs(path, now = now)
        if (legs.isEmpty()) {
            appendLine("No path legs.")
            return@buildString
        }
        val stopNames = legs.filter { it.stay }.mapNotNull { places.nameNear(it.lat, it.lon) }
        if (stopNames.isNotEmpty()) {
            appendLine("Stops: " + stopNames.joinToString(" → "))
        }
        var stayN = 0
        legs.forEachIndexed { i, leg ->
            if (leg.stay) {
                stayN++
                appendLine()
                appendLine("${i + 1}. Stay  ${clock(leg.startAt)}–${clock(leg.endAt)} UTC  (${fmtDur(leg.durationMs)})")
                appendLine("   ${placeAndGps(leg.lat, leg.lon, places)}")
                val here = devices.filter { heardAt(it, leg) }
                val aps = here.count { it.kind == RadioKind.WIFI }
                val ble = here.count { it.kind == RadioKind.BLE }
                val sigs = here.flatMap { d -> d.fleetIds.map { names[it] ?: it } }.distinct()
                append("   Heard here: $aps AP(s), $ble BLE")
                if (sigs.isNotEmpty()) append("  ·  ${sigs.take(6).joinToString(", ")}")
                appendLine()
                here.sortedByDescending { it.rssi }.take(4).forEach { d ->
                    appendLine("   · ${d.displayName}  ${d.mac}  ${d.rssi} dBm")
                }
                if (here.isEmpty()) appendLine("   · No GPS-stamped radios tied to this stay (tagging may have started after they were first heard).")
            } else {
                appendLine()
                appendLine(
                    "${i + 1}. Transit  ${clock(leg.startAt)}–${clock(leg.endAt)} UTC  " +
                        "${fmtDist(leg.pathM)} along track",
                )
                appendLine("   ${placeAndGps(leg.lat, leg.lon, places)}")
                appendLine("   → ${placeAndGps(leg.endLat, leg.endLon, places)}")
            }
        }
        val stays = legs.count { it.stay }
        if (stays == 1 && pathSpan < MOVE_M) {
            appendLine()
            appendLine("One stay — you did not move far enough in this window to split locations.")
        }
    }

    private fun heardAt(device: Sighting, leg: Geo.PathLeg): Boolean {
        val nearM = 60.0
        val trail = device.gpsTrail.filter { it.at >= leg.startAt && it.at <= leg.endAt }
        if (trail.isNotEmpty()) {
            return trail.any { Geo.meters(it.lat, it.lon, leg.lat, leg.lon) <= nearM }
        }
        val lat = device.latitude ?: return false
        val lon = device.longitude ?: return false
        if (device.lastSeen < leg.startAt || device.firstSeen > leg.endAt) return false
        return Geo.meters(lat, lon, leg.lat, leg.lon) <= nearM
    }

    private fun environment(
        wifi: List<Sighting>,
        ble: List<Sighting>,
        randomized: Int,
        persistent: List<Sighting>,
        pathSpan: Double,
        pathLen: Double,
    ): String {
        val ap = wifi.size
        val persistAp = persistent.count { it.kind == RadioKind.WIFI }
        val guess = when {
            pathSpan > 200 && ap in 1..25 -> "In motion (walk/vehicle) through mixed RF."
            ap <= 4 && ble.size < 30 && persistAp >= 1 -> "Likely a dwelling or small office — few sitting APs, limited BLE."
            ap >= 15 && randomized >= 40 -> "Dense public / retail / street: many APs and phone-like randomized BLE."
            ap >= 8 && persistAp >= 4 -> "Likely a building with standing infrastructure APs plus patrons."
            else -> "Mixed or under-sampled environment."
        }
        return "$guess  (${ap} APs, ${ble.size} BLE, ${persistAp} persistent APs, traveled ${fmtDist(pathLen)}, span ${fmtDist(pathSpan)}.)"
    }

    private fun wifiLine(d: Sighting, names: Map<String, String>, from: Long, now: Long): String = buildString {
        append(d.displayName).append("  ").append(d.mac)
        d.vendor?.let { append("  ").append(it) }
        append("  ").append(d.rssi).append(" dBm")
        if (d.channel != 0) append("  ch ").append(d.channel)
        if (d.hiddenSsid) append("  hidden")
        if (d.fleetIds.isNotEmpty()) append("  ").append(d.fleetIds.joinToString("+") { names[it] ?: it })
        append("  dwell ").append(fmtDur(dwellMs(d, from, now)))
    }

    private fun bleLine(d: Sighting, names: Map<String, String>, from: Long, now: Long): String = buildString {
        append(d.displayName).append("  ").append(d.mac)
        if (d.randomized) append("  RAND")
        append("  ").append(d.rssi).append(" dBm")
        if (d.fleetIds.isNotEmpty()) append("  ").append(d.fleetIds.joinToString("+") { names[it] ?: it })
        append("  dwell ").append(fmtDur(dwellMs(d, from, now)))
    }

    private fun anomalyLines(devices: List<Sighting>): List<String> {
        val out = ArrayList<String>()
        val pairing = devices.filter { d ->
            d.facts.serviceData.any { it.uuid.contains("FE2C", true) && it.dataHex.length == 6 }
        }
        if (pairing.isNotEmpty()) {
            out += "Google Fast Pair in pairing mode: " +
                pairing.joinToString { "${it.displayName} ${it.mac}" }
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
            out += "High randomized BLE ($rand) — typical of phones, not a tracking finding."
        }
        return out
    }

    private fun privacy(
        wifi: List<Sighting>,
        ble: List<Sighting>,
        randomized: Int,
        hidden: List<Sighting>,
        settings: AppSettings,
        places: DebriefPlaces,
    ): String = buildString {
        append("A passive observer with the same radios would see ${wifi.size} named/hidden APs ")
        append("and ${ble.size} BLE advertisers ($randomized randomized). ")
        if (hidden.isNotEmpty()) append("Hidden SSIDs still beacon and identify the AP by BSSID. ")
        if (settings.tagLocation) append("This debrief includes operator GPS samples used for distance and the following test. ")
        if (places.attempted && places.available) {
            append("Street names came from the phone’s system geocoder while online. ")
        }
        append("Do not share this file off-device without redaction.")
    }

    private fun actions(
        following: List<FollowHit>,
        withYou: List<FollowHit>,
        ownLikely: List<FollowHit>,
        beaconsWithYou: List<FollowHit>,
        wearablesWithYou: List<FollowHit>,
        settings: AppSettings,
        pathSpan: Double,
    ): List<String> = buildList {
        if (following.isNotEmpty()) {
            add("Possible tail (appeared after this sit started): ${following.joinToString { it.label + " " + it.device.mac }}. Pause Live, open detail, note RSSI while you walk a dog-leg. Do not disable someone else’s tag.")
        }
        if (ownLikely.isNotEmpty() || withYou.isNotEmpty()) {
            add(
                "Possible trackers with you: ${(ownLikely + withYou).joinToString { it.label + " " + it.device.mac }}. " +
                    "Could be yours or planted in the car/bag/on you before you started. Account for each MAC — do not dismiss as yours.",
            )
        }
        if (beaconsWithYou.isNotEmpty()) {
            add(
                "Retail beacons with you (unusual — fixtures do not typically move with you): " +
                    beaconsWithYou.joinToString { it.label + " " + it.device.mac } +
                    ". Account for a test tag or badge before treating it as a follower.",
            )
        }
        if (wearablesWithYou.isNotEmpty()) {
            add(
                "Wearables with you (usually own kit or a companion): " +
                    wearablesWithYou.joinToString { it.label + " " + it.device.mac } +
                    ".",
            )
        }
        if (!settings.tagLocation) add("Enable Tag detections with GPS and walk 50+ m, then run Debrief again for a following test.")
        else if (pathSpan < MOVE_M) add("Walk farther (50+ m) with GPS tagging on, then re-run Debrief.")
        add("Use Live → Pause to inspect a busy list. Watch tracker signatures if this sit was noisy.")
        add("Station-side Wi-Fi (probes/clients) still needs a dedicated sniffer — Fieldwatch cannot see them.")
    }

    private fun takeaway(
        following: List<FollowHit>,
        withYou: List<FollowHit>,
        ownLikely: List<FollowHit>,
        beaconsWithYou: List<FollowHit>,
        wearablesWithYou: List<FollowHit>,
        pathSpan: Double,
        settings: AppSettings,
        named: List<Sighting>,
    ): String {
        val extra = buildString {
            if (beaconsWithYou.isNotEmpty()) {
                append(" Retail beacon(s) also with the path (unusual): ")
                append(beaconsWithYou.joinToString { it.label + " (" + it.device.mac + ")" })
                append(".")
            }
            if (wearablesWithYou.isNotEmpty()) {
                append(" Wearable(s) with the path (usually own kit): ")
                append(wearablesWithYou.joinToString { it.label + " (" + it.device.mac + ")" })
                append(".")
            }
        }
        val core = when {
            following.isNotEmpty() && (ownLikely.isNotEmpty() || withYou.isNotEmpty()) ->
                "Possible tail (appeared after sit started): ${following.joinToString { it.label + " (" + it.device.mac + ")" }}. " +
                    "Also finder tags with you (yours or planted before): ${(ownLikely + withYou).joinToString { it.label + " (" + it.device.mac + ")" }}. Account for every MAC."
            following.isNotEmpty() ->
                "Possible tail (appeared after this sit started): ${following.joinToString { it.label + " (" + it.device.mac + ")" }}. Account for it on the person/vehicle."
            !settings.tagLocation ->
                "Turn on GPS tagging and walk before you can test whether a tracker is following you."
            pathSpan < MOVE_M ->
                "Not enough GPS movement (${pathSpan.toInt()} m) to test following; walk and re-run Debrief."
            ownLikely.isNotEmpty() || withYou.isNotEmpty() ->
                "Finder tags with you (yours or planted before you started): ${(ownLikely + withYou).joinToString { it.label + " (" + it.device.mac + ")" }}. No new arrival this window. Account for each MAC — do not dismiss as yours."
            beaconsWithYou.isNotEmpty() || wearablesWithYou.isNotEmpty() ->
                "No finder tag stayed with the path."
            named.isEmpty() ->
                "No signature hits and no GPS co-travel of trackers in this 15-minute window."
            else ->
                "No finder tag, retail beacon, or wearable clearly stayed with your GPS path in this window."
        }
        return (core + extra).trim()
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

    private fun absDelta(a: Long, b: Long) = kotlin.math.abs(a - b)

    private fun utc(ms: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(ms))
    }

    private fun clock(ms: Long): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(ms))
    }

    private fun fmtDist(m: Double): String =
        if (m >= 1000.0) String.format(Locale.US, "%.2f km", m / 1000.0) else "${m.toInt()} m"

    private fun fmtCoord(s: GpsSample): String =
        String.format(Locale.US, "%.5f, %.5f", s.lat, s.lon)

    private fun placeAndGps(lat: Double, lon: Double, places: DebriefPlaces): String {
        val gps = fmtCoord(GpsSample(0L, lat, lon))
        val name = places.nameNear(lat, lon)
        return if (!name.isNullOrBlank()) {
            "$name  ($gps, operator phone)"
        } else if (places.attempted) {
            "$gps  (operator phone; no street name this export)"
        } else {
            "$gps  (operator phone)"
        }
    }

    private fun fmtDur(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val m = s / 60
        val r = s % 60
        return if (m >= 60) "${m / 60}h${m % 60}m" else if (m > 0) "${m}m${r}s" else "${r}s"
    }
}
