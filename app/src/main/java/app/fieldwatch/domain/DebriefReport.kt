package app.fieldwatch.domain

import app.fieldwatch.i18n.displayLiveDecode

import app.fieldwatch.i18n.displayName

import app.fieldwatch.i18n.forDisplay

import app.fieldwatch.i18n.displayAttentionNotes

import app.fieldwatch.i18n.displayLabel

import app.fieldwatch.i18n.localized

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ReportBar(
    val label: String,
    val value: Int,
    val detail: String = "",
    val second: Int? = null,
)

data class ReportChart(
    val rows: List<ReportBar>,
    val split: Boolean = false,
    val caption: String = "",
) {
    fun asText(): String = buildString {
        if (caption.isNotBlank()) appendLine(caption)
        rows.forEach { row ->
            if (row.second == null) {
                append(row.label).append("  ").append(row.value)
                if (row.detail.isNotBlank()) append("  ").append(row.detail)
                appendLine()
            } else {
                append(row.label).append(": ")
                append(row.value).append(" Wi-Fi, ")
                append(row.second).append(" BLE")
                appendLine()
            }
        }
    }.trimEnd()
}

enum class ReportSectionKind { TEXT, EXTRA_ATTENTION }

data class DebriefSection(
    val number: String,
    val title: String,
    val body: String,
    val alert: Boolean = false,
    val chart: ReportChart? = null,
    val after: String = "",
    val kind: ReportSectionKind = ReportSectionKind.TEXT,
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
        if (!attempted) return localized("debrief_report_off", "off")
        val named = namesByCell.values.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (named.isEmpty()) return note
        return named.joinToString(" · ")
    }

    companion object {
        val Off get() = DebriefPlaces(false, false, localized("debrief_report_off", "off"))
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
    val heading: String = localized("debrief_report_fieldwatch_field_debrief", "FIELDWATCH FIELD DEBRIEF"),
    val pdfKicker: String = localized("debrief_report_field_debrief", "FIELD DEBRIEF"),
    val pdfTitle: String = localized("debrief_report_field_debrief_2", "Field debrief"),
    val pathFigure: SitPathPlot.Figure? = null,
    val extraFigures: List<SitPathPlot.Figure> = emptyList(),
) {
    fun toPlainText(): String = buildString {
        appendLine(heading)
        appendLine()
        appendLine(localized("debrief_report_disclaimer", "DISCLAIMER"))
        appendLine(disclaimer)
        appendLine()
        meta.forEach { (k, v) -> appendLine("${k.padEnd(14)}$v") }
        appendLine()
        sections.forEach { sec ->
            appendLine("${sec.number}. ${sec.title.uppercase()}")
            val body = sec.body.trimEnd()
            if (body.isNotEmpty()) appendLine(body)
            sec.chart?.asText()?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
            val after = sec.after.trimEnd()
            if (after.isNotEmpty()) appendLine(after)
            appendLine()
        }
        appendLine("—")
        appendLine(localized("debrief_report_takeaway", "Takeaway: %1\$s", takeaway))
    }

    fun withDemoMacs(macs: Collection<String>, demo: Boolean): DebriefDoc {
        if (!demo) return this
        fun t(s: String) = Geo.redactCoordsIn(MacUtil.redactMacsIn(s, macs, true), true)
        val note = localized("debrief_report_mac_tails_and_gps_coordinates_masked_logs", "MAC tails (**:**:**) and GPS coordinates masked. Logs on the phone are unchanged.")
        return copy(
            meta = listOf(localized("debrief_report_privacy", "Privacy") to note) + meta.map { it.first to t(it.second) },
            disclaimer = t(disclaimer),
            takeaway = t(takeaway),
            sections = sections.map {
                it.copy(
                    title = t(it.title),
                    body = t(it.body),
                    after = t(it.after),
                    chart = it.chart?.let { chart ->
                        chart.copy(
                            caption = t(chart.caption),
                            rows = chart.rows.map { row ->
                                row.copy(label = t(row.label), detail = t(row.detail))
                            },
                        )
                    },
                )
            },
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
        window: DebriefWindow? = null,
        customNames: Map<String, String> = emptyMap(),
        observerNotes: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        watchedFleetIds: Set<String> = emptySet(),
        mineKeys: Set<String> = emptySet(),
        floods: List<FloodBurst> = emptyList(),
    ): String = document(
        devices, fleets, settings, operatorPath, now, places, window,
        customNames, observerNotes, bookmarkedKeys, watchedFleetIds, mineKeys,
        floods,
    ).toPlainText()

    fun document(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        settings: AppSettings,
        operatorPath: List<GpsSample>,
        now: Long = System.currentTimeMillis(),
        places: DebriefPlaces = DebriefPlaces.Off,
        window: DebriefWindow? = null,
        customNames: Map<String, String> = emptyMap(),
        observerNotes: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        watchedFleetIds: Set<String> = emptySet(),
        mineKeys: Set<String> = emptySet(),
        floods: List<FloodBurst> = emptyList(),
    ): DebriefDoc {
        val devices = devices.map { it.copy(liveDecode = it.displayLiveDecode(fleets)) }
        val names = fleets.associate { it.id to it.name }
        val displayNames = fleets.associate { it.id to it.displayName() }
        val win = window ?: DebriefWindow(now - WINDOW_MS, now)
        val windowStart = win.startAt
        val windowEnd = win.endAt
        val aside = FloodBurst.keysOf(floods, windowStart, windowEnd)
        val inWin = devices
            .filter { (it.lastSeen >= windowStart || it.firstSeen >= windowStart) && it.key !in aside }
            .sortedByDescending { it.rssi }
        val wifi = inWin.filter { it.kind == RadioKind.WIFI }
        val ble = inWin.filter { it.kind == RadioKind.BLE }
        val named = inWin.filter { it.fleetIds.isNotEmpty() }
        val hidden = wifi.filter { it.hiddenSsid }
        val randomized = ble.count { it.randomized }
        val arrived = inWin.filter { it.firstSeen >= windowStart }
        val persistent = inWin.filter { dwellMs(it, windowStart, windowEnd) >= win.durationMs * 2 / 3 }
        val path = operatorPath.filter { it.at in windowStart..windowEnd }
        val pathSpan = Geo.spanM(path)
        val pathLen = Geo.pathLengthM(path)
        val trackers = inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.FINDER }
        val follow = followAssessments(trackers, names, path, windowStart, windowEnd, TrackerMatch.Kind.FINDER)
        val beaconFollow = followAssessments(
            inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.BEACON },
            names, path, windowStart, windowEnd, TrackerMatch.Kind.BEACON,
        )
        val wearableFollow = followAssessments(
            inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.WEARABLE },
            names, path, windowStart, windowEnd, TrackerMatch.Kind.WEARABLE,
        )
        val assessed = follow + beaconFollow + wearableFollow
        fun List<FollowHit>.open(): List<FollowHit> = filter { it.device.key !in mineKeys }
        val following = follow.filter { it.verdict == Verdict.FOLLOWING }.open()
        val withYou = follow.filter { it.verdict == Verdict.MOVED_WITH_YOU }.open()
        val ownLikely = follow.filter { it.verdict == Verdict.OWN_LIKELY }.open()
        val wholeSit = ownLikely + withYou
        val beaconsWithYou = stayedWithYou(beaconFollow).open()
        val wearablesWithYou = stayedWithYou(wearableFollow).open()
        val mineHeard = inWin.count { it.key in mineKeys }

        val showAll = settings.debriefShowAllRadios
        fun Sighting.listedWhenShort(): Boolean =
            key in customNames || key in mineKeys || key in bookmarkedKeys
        val byCh = wifi.groupBy { it.channel }.toSortedMap()
        val channelChart = if (byCh.isEmpty()) {
            null
        } else {
            ReportChart(
                rows = byCh.map { (ch, list) ->
                    val label = if (ch == 0) localized("debrief_report_unknown", "unknown") else localized("debrief_report_ch", "ch %1\$s", ch)
                    ReportBar(label, list.size, detail = localized("debrief_report_strongest_dbm", "strongest %1\$s dBm", list.maxOf { it.rssi }))
                },
            )
        }
        val networks = localized("debrief_report_heard_ap_s_hidden_ssid_sat_most", "Heard %1\$s AP(s); %2\$s hidden SSID; %3\$s sat most of the window.", wifi.size, hidden.size, persistent.count { it.kind == RadioKind.WIFI })
        val networksAfter = if (!showAll) {
            wifiLines(
                wifi.filter { it.listedWhenShort() && it.fleetIds.isEmpty() },
                displayNames, windowStart, now, customNames, fleets, mineKeys,
            )
        } else {
            buildString {
                appendLine(localized("debrief_report_loudest_aps", "Loudest APs:"))
                wifi.take(12).forEach { d ->
                    appendLine("  · ${wifiLine(d, displayNames, windowStart, now, customNames)}")
                    if (d.key in mineKeys) appendLine(localized("debrief_report_marked_mine_2", "    Marked mine"))
                    d.displayAttentionNotes(fleets).forEach { (sig, note) ->
                        appendLine(localized("debrief_report_extra_attention_2", "    extra attention (%1\$s): %2\$s", sig, note))
                    }
                }
                if (hidden.isNotEmpty()) {
                    appendLine(localized("debrief_report_hidden_ssids", "Hidden SSIDs:"))
                    hidden.forEach { appendLine(localized("debrief_report_dbm_ch", "  · %1\$s  %2\$s  %3\$s dBm  ch %4\$s", it.mac, it.vendor ?: "", it.rssi, it.channel)) }
                }
            }.trimEnd()
        }
        val notable = ble.filter {
            inventoryKeep(it, settings, bookmarkedKeys) &&
                (it.fleetIds.isNotEmpty() || it.name.isNotBlank() || it.rssi >= -65 || it.manufacturerId != null)
        }.sortedByDescending { it.rssi }.take(20)
        val omittedRand = ble.count { !inventoryKeep(it, settings, bookmarkedKeys) }
        val bleBody = buildString {
            appendLine(localized("debrief_report_heard_advertiser_s_with_randomized_addresses_signature", "Heard %1\$s advertiser(s); %2\$s with randomized addresses; %3\$s signature-matched.", ble.size, randomized, named.count { it.kind == RadioKind.BLE }))
            if (omittedRand > 0) {
                appendLine(localized("debrief_report_unmatched_rotating_ble_omitted_from_lists_counts", "Unmatched rotating BLE omitted from lists (%1\$s). Counts include them. Sit export has every radio.", omittedRand))
            }
        }.trimEnd()
        val bleAfter = if (showAll && notable.isNotEmpty()) {
            buildString {
                appendLine(localized("debrief_report_notable_ble", "Notable BLE:"))
                notable.forEach { d ->
                    val guess = DeviceExplain.guess(d, d.fleetIds.map { names[it] ?: it })
                    appendLine("  · ${bleLine(d, displayNames, windowStart, now, customNames)}  |  ${guess.headline}")
                    if (d.key in mineKeys) appendLine(localized("debrief_report_marked_mine_2", "    Marked mine"))
                    d.displayAttentionNotes(fleets).forEach { (sig, note) ->
                        appendLine(localized("debrief_report_extra_attention_2", "    extra attention (%1\$s): %2\$s", sig, note))
                    }
                    val decoded = SignatureFieldDecoder.decodeSighting(d, fleets).forDisplay(fleets)
                    if (decoded.isNotEmpty()) {
                        decoded.forEach { row ->
                            appendLine("    ${row.label}: ${row.display}")
                            if (row.note.isNotBlank()) appendLine("    ${row.note}")
                        }
                    } else {
                        d.liveDecode.forEach { chip ->
                            append("    ${chip.reportLabel()}")
                            if (chip.note.isNotBlank()) append("  ").append(chip.note)
                            appendLine()
                        }
                    }
                }
            }.trimEnd()
        } else if (!showAll) {
            bleLines(
                ble.filter { it.listedWhenShort() && it.fleetIds.isEmpty() },
                displayNames, windowStart, now, customNames, mineKeys,
            )
        } else {
            ""
        }
        val sigChart = signatureChart(named, displayNames)
        val sigBody = if (named.isEmpty()) localized("debrief_report_none_in_this_window", "None in this window.") else ""
        val sigAfter = if (named.isEmpty()) {
            ""
        } else if (showAll) {
            buildString {
                named.groupBy { it.fleetIds.joinToString("+") { id -> displayNames[id] ?: id } }
                    .toList().sortedByDescending { it.second.size }
                    .forEach { (sig, list) ->
                        appendLine(sig)
                        list.sortedByDescending { it.rssi }.take(8).forEach { d ->
                            append("  · ${d.reportName(customNames)}  ${d.mac}  ${d.rssi} dBm")
                            if (d.key in mineKeys) append(localized("debrief_report_marked_mine_3", "  Marked mine"))
                            val labels = d.liveDecode.reportLabels()
                            if (labels.isNotEmpty()) append("  ").append(labels.joinToString(", "))
                            appendLine()
                        }
                        list.flatMap { it.displayAttentionNotes(fleets) }.distinct().forEach { (name, note) ->
                            appendLine(localized("debrief_report_extra_attention_3", "  extra attention (%1\$s): %2\$s", name, note))
                        }
                    }
            }.trimEnd()
        } else {
            buildString {
                val marked = named.filter { it.listedWhenShort() }.sortedByDescending { it.rssi }
                if (marked.isNotEmpty()) appendLine(localized("debrief_report_named_or_marked", "Named or marked:"))
                marked.forEach { d ->
                    append("  · ${d.reportName(customNames)}  ${d.mac}  ${d.rssi} dBm")
                    if (d.key in mineKeys) append(localized("debrief_report_marked_mine_3", "  Marked mine"))
                    val labels = d.liveDecode.reportLabels()
                    if (labels.isNotEmpty()) append("  ").append(labels.joinToString(", "))
                    appendLine()
                }
            }.trimEnd()
        }
        val persistBody = buildString {
            appendLine(localized("debrief_report_sat_most_of_this_window", "Sat most of this window: %1\$s", persistent.size))
            append(localized("debrief_report_first_seen_in_this_window", "First seen in this window: %1\$s", arrived.size))
        }.trimEnd()
        val persistAfter = if (!showAll) {
            ""
        } else {
            buildString {
                persistent.filter { inventoryKeep(it, settings, bookmarkedKeys) }.take(15).forEach {
                    appendLine(localized("debrief_report_dwell", "  · %1\$s  %2\$s  dwell %3\$s", it.reportName(customNames), it.mac, fmtDur(dwellMs(it, windowStart, now))))
                }
                if (persistent.isEmpty()) appendLine(localized("debrief_report_none", "  · None."))
                appendLine(localized("debrief_report_loudest_first_seen", "Loudest first seen:"))
                arrived.filter { inventoryKeep(it, settings, bookmarkedKeys) }.sortedByDescending { it.rssi }.take(8).forEach {
                    appendLine("  · ${it.reportName(customNames)}  ${it.mac}  ${it.rssi} dBm")
                }
            }.trimEnd()
        }
        val flags = anomalyLines(inWin, customNames, settings, bookmarkedKeys, showAll)
        val anomalyBody = if (flags.isEmpty()) {
            localized("debrief_report_no_extra_flags_signature_hits_extra_attention", "No extra flags. Signature hits, Extra attention, and tracking callouts already cover named pattern matches.")
        } else flags.joinToString("\n") { "  · $it" }
        val attentionHits = inWin.flatMap { d ->
            d.displayAttentionNotes(fleets).map { (sig, note) -> Triple(d, sig, note) }
        }
        val actionBody = actions(following, withYou, ownLikely, beaconsWithYou, wearablesWithYou, settings, pathSpan)
            .joinToString("\n") { "  · $it" }

        val distanceLine = when {
            !settings.tagLocation -> localized("debrief_report_gps_tagging_off_no_path", "GPS tagging off — no path")
            path.size < 2 -> localized("debrief_report_gps_tagging_on_fewer_than_2_fixes", "GPS tagging on, fewer than 2 fixes in this window")
            else -> localized("debrief_report_traveled_along_path_span_fixes", "traveled %1\$s along path · span %2\$s · %3\$s fixes", fmtDist(pathLen), fmtDist(pathSpan), path.size)
        }
        val lookupLine = when {
            !places.attempted -> localized("debrief_report_off", "off")
            places.namesByCell.isNotEmpty() -> places.areaLine()
            else -> places.note
        }
        val pictures = AircraftTrail.pictures(
            inWin.mapNotNull { d ->
                AircraftTrail.source(d, d.reportName(customNames))
            },
            path,
        )
        val aircraftBody = AircraftTrail.body(pictures)
        var n = 1
        fun next() = (n++).toString()
        val sections = buildList {
            add(DebriefSection(
                next(),
                localized("debrief_report_executive_summary", "Executive summary"),
                execSummary(wifi, ble, named, hidden, randomized, pathSpan, pathLen, following, withYou, ownLikely, beaconsWithYou, wearablesWithYou, settings, places, win, mineHeard) + craftSentence(pictures),
                chart = classChart(inWin, fleets),
            ))
            add(DebriefSection(next(), localized("debrief_report_where_you_were", "Where you were"), whereYouWere(settings, path, pathLen, pathSpan, inWin, displayNames, places, windowEnd, customNames, bookmarkedKeys)))
            if (aircraftBody.isNotEmpty()) {
                add(DebriefSection(next(), localized("debrief_report_aircraft", "Aircraft"), aircraftBody))
            }
            observerNotesSection(inWin, customNames, observerNotes)?.let { body ->
                add(DebriefSection(next(), localized("debrief_report_observer_notes", "Observer notes"), body))
            }
            markedMineSection(inWin, customNames, mineKeys, assessed)?.let { body ->
                add(DebriefSection(next(), localized("debrief_report_marked_mine", "Marked mine"), body))
            }
            add(
                DebriefSection(
                    next(),
                    localized("debrief_report_tracking_assessment", "Tracking assessment"),
                    trackingSection(settings, path, pathSpan, pathLen, following, wholeSit, beaconsWithYou, wearablesWithYou),
                ),
            )
            if (wholeSit.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        localized("debrief_report_possible_trackers_with_you", "Possible trackers with you"),
                        trackerCallout(
                            localized("debrief_report_finder_tags_airtag_find_my_smarttag_tile", "Finder tags (AirTag / Find My, SmartTag, Tile, Chipolo, Pebblebee) and loud pocket Apple BLE. These radios stayed with your GPS path for this sit. Fieldwatch cannot tell your own tag or phone from a tracker planted in the car, bag, or on you before you started. Account for each MAC. Not a finding and not identity."),
                            wholeSit,
                            customNames,
                        ),
                        alert = true,
                    ),
                )
            }
            if (following.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        localized("debrief_report_possible_tail", "Possible tail"),
                        trackerCallout(
                            localized("debrief_report_finder_tags_that_were_not_heard_when", "Finder tags that were not heard when this sit started, then stayed with your path. That can mean someone started following you (their phone or tag), or a device was added during the trip. Not a finding and not identity."),
                            following,
                            customNames,
                        ),
                        alert = true,
                    ),
                )
            }
            if (beaconsWithYou.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        localized("debrief_report_retail_beacons_with_you", "Retail beacons with you"),
                        trackerCallout(
                            localized("debrief_report_ibeacon_minew_estimote_kontakt_io_target_atrius", "iBeacon / Minew / Estimote / Kontakt.io / Target Atrius basket radios that stayed with your GPS path. Location beacons are usually fixtures in a store or venue — they do not typically move with you. If one did, account for it (a Target basket you pushed, your own test tag, a badge, or a short path that still overlaps a fixture). Not the same as a Find My tail. Not a finding and not identity."),
                            beaconsWithYou,
                            customNames,
                        ),
                        alert = true,
                    ),
                )
            }
            if (wearablesWithYou.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        localized("debrief_report_wearables_with_you", "Wearables with you"),
                        trackerCallout(
                            localized("debrief_report_garmin_fitbit_oura_radios_that_stayed_with", "Garmin / Fitbit / Oura radios that stayed with your GPS path. Watches and rings usually move with the person wearing them — often your own kit or someone walking with you. They are not typically planted trackers. Account for each MAC. Not a finding and not identity."),
                            wearablesWithYou,
                            customNames,
                        ),
                        alert = true,
                    ),
                )
            }
            add(DebriefSection(next(), localized("debrief_report_environment", "Environment"), environment(wifi, ble, randomized, persistent, pathSpan, pathLen)))
            add(DebriefSection(
                next(),
                localized("debrief_report_networks_wi_fi_access_points", "Networks (Wi-Fi access points)"),
                networks.trimEnd(),
                chart = channelChart,
                after = networksAfter.trimEnd(),
            ))
            add(DebriefSection(next(), localized("debrief_report_bluetooth_le", "Bluetooth LE"), bleBody.trimEnd(), after = bleAfter.trimEnd()))
            add(DebriefSection(next(), localized("debrief_report_signature_hits", "Signature hits"), sigBody.trimEnd(), chart = sigChart, after = sigAfter.trimEnd()))
            add(DebriefSection(next(), localized("debrief_report_persistence", "Persistence"), persistBody.trimEnd(), after = persistAfter.trimEnd()))
            if (attentionHits.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        localized("debrief_report_extra_attention", "Extra attention"),
                        buildString {
                            appendLine(localized("debrief_report_pattern_match_not_identity_not_a_skimmer", "Pattern match, not identity, not a skimmer detector, not a safety finding."))
                            attentionHits.forEach { (d, sig, note) ->
                                append("  · ${d.reportName(customNames)}  ${d.mac}  ${d.rssi} dBm  [$sig]")
                                if (d.key in mineKeys) append(localized("debrief_report_marked_mine_3", "  Marked mine"))
                                appendLine()
                                appendLine("    $note")
                            }
                        }.trimEnd(),
                        alert = true,
                        kind = ReportSectionKind.EXTRA_ATTENTION,
                    ),
                )
            }
            add(DebriefSection(next(), localized("debrief_report_anomalies", "Anomalies"), anomalyBody))
            floodBody(floods, windowStart, windowEnd)?.let { body ->
                add(DebriefSection(next(), localized("debrief_report_flood", "Flood"), body))
            }
            add(DebriefSection(next(), localized("debrief_report_privacy", "Privacy"), privacy(wifi, ble, randomized, hidden, settings, places, pictures.isNotEmpty())))
            add(DebriefSection(next(), localized("debrief_report_recommended_actions", "Recommended actions"), actionBody))
        }

        val windowLine = if (win.sitName != null) {
            localized("debrief_report_sit_utc", "sit %1\$s (%2\$s → %3\$s UTC)", win.sitName, utc(windowStart), utc(windowEnd))
        } else {
            localized("debrief_report_last_15_minutes_utc", "last 15 minutes (%1\$s → %2\$s UTC)", utc(windowStart), utc(windowEnd))
        }
        val heading = if (win.sitName != null) {
            localized("debrief_report_fieldwatch_sit", "FIELDWATCH SIT — %1\$s", win.sitName)
        } else {
            localized("debrief_report_fieldwatch_field_debrief", "FIELDWATCH FIELD DEBRIEF")
        }
        val meta = buildList {
            add(localized("debrief_report_generated", "Generated") to "${utc(now)} UTC")
            if (win.sitName != null) add(localized("debrief_report_sit", "Sit") to win.sitName)
            add(localized("debrief_report_window", "Window") to windowLine)
            add(localized("debrief_report_radios", "Radios") to "${inWin.size}")
            add(localized("debrief_report_tool", "Tool") to localized("debrief_report_fieldwatch_app_fieldwatch_stock_android_receive_only", "Fieldwatch (app.fieldwatch) · stock Android · receive-only Wi-Fi AP + BLE advertiser"))
            add(localized("debrief_report_scan", "Scan") to localized("debrief_report_stale_s_brief_hold_s", "%1\$s · stale %2\$ss · brief hold %3\$ss", settings.intensity.displayLabel(), settings.staleSec, settings.decaySec))
            add(localized("debrief_report_gps_tag", "GPS tag") to if (settings.tagLocation) localized("debrief_report_on", "on") else localized("debrief_report_off", "off"))
            add(localized("debrief_report_distance", "Distance") to distanceLine)
            add(localized("debrief_report_places", "Places") to lookupLine)
            add(
                localized("debrief_report_classification", "Classification") to if (pictures.isNotEmpty()) {
                    localized("debrief_report_operationally_sensitive_neighbor_ssids_macs_operator_gps", "Operationally sensitive — neighbor SSIDs, MACs, operator GPS, advertised aircraft track")
                } else {
                    localized("debrief_report_operationally_sensitive_neighbor_ssids_macs_operator_gps_2", "Operationally sensitive — neighbor SSIDs, MACs, operator GPS")
                },
            )
        }
        return DebriefDoc(
            generatedUtc = utc(now),
            windowLine = windowLine,
            meta = meta,
            disclaimer = FieldwatchDisclaimer.report(win),
            trackingAlert = following.isNotEmpty() || ownLikely.isNotEmpty() || withYou.isNotEmpty(),
            takeaway = takeaway(following, withYou, ownLikely, beaconsWithYou, wearablesWithYou, pathSpan, settings, named),
            sections = sections,
            extraAttention = attentionHits.map { (d, sig, note) ->
                ExtraAttentionHit(
                    signature = sig,
                    radioLabel = buildString {
                        append("${d.reportName(customNames)}  ${d.mac}  ${d.rssi} dBm")
                        if (d.key in mineKeys) append(localized("debrief_report_marked_mine_3", "  Marked mine"))
                    },
                    note = note,
                )
            },
            heading = heading,
            pdfKicker = if (win.sitName != null) localized("debrief_report_sit_2", "SIT") else localized("debrief_report_field_debrief", "FIELD DEBRIEF"),
            pdfTitle = if (win.sitName != null) localized("debrief_report_sit_3", "Sit — %1\$s", win.sitName) else localized("debrief_report_field_debrief_2", "Field debrief"),
            pathFigure = AircraftTrail.applyWalk(
                pathFigure(
                    win.sitName ?: localized("debrief_report_last_15_minutes", "Last 15 minutes"), path, inWin, fleets,
                    customNames, observerNotes, bookmarkedKeys, watchedFleetIds, mineKeys,
                ),
                pictures,
                secondary = false,
            ),
            extraFigures = AircraftTrail.ownFigures(pictures),
        )
    }

    private fun pathFigure(
        title: String,
        path: List<GpsSample>,
        devices: List<Sighting>,
        fleets: List<Fleet>,
        customNames: Map<String, String>,
        observerNotes: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        watchedFleetIds: Set<String> = emptySet(),
        mineKeys: Set<String> = emptySet(),
    ): SitPathPlot.Figure? {
        val path = Geo.despikePath(path)
        if (path.size < 2) return null
        val plot = SitPathPlot.dotsFrom(
            devices, fleets, namedKeys = customNames.keys,
            customNames = customNames, observerNotes = observerNotes,
            bookmarkedKeys = bookmarkedKeys,
            watchedFleetIds = watchedFleetIds,
            alertsOnly = true,
            mineKeys = mineKeys,
        )
        return SitPathPlot.Figure(
            kicker = localized("debrief_report_operator_path", "OPERATOR PATH"),
            tracks = listOf(SitPathPlot.FigureTrack(title, path)),
            dots = plot.points,
            lengthM = Geo.pathLengthM(path),
            spanM = Geo.spanM(path),
            caption = localized("debrief_report_north_up_line_is_this_phone_a", "North-up. Line is this phone (%1\$s). A MAC alert or a signature alert is drawn once. A decoded latitude and longitude is the last advertised position. Anything else is the strongest hear. A number is that place (Path key).", path.lengthM()),
        )
    }

    private fun List<GpsSample>.lengthM(): String {
        val m = Geo.pathLengthM(this)
        return if (m >= 1000) "${"%.1f".format(java.util.Locale.US, m / 1000)} km" else "${m.toInt()} m"
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
        window: DebriefWindow? = null,
        customNames: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        mineKeys: Set<String> = emptySet(),
    ): String = buildString {
        val devices = devices.map { it.copy(liveDecode = it.displayLiveDecode(fleets)) }
        val names = fleets.associate { it.id to it.name }
        val displayNames = fleets.associate { it.id to it.displayName() }
        val win = window ?: DebriefWindow(now - WINDOW_MS, now)
        val windowStart = win.startAt
        val windowEnd = win.endAt
        val path = operatorPath.filter { it.at in windowStart..windowEnd }
        val pathSpan = Geo.spanM(path)
        val pathLen = Geo.pathLengthM(path)
        val inWin = devices.filter { it.lastSeen >= windowStart || it.firstSeen >= windowStart }
        val trackers = inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.FINDER }
        val follow = followAssessments(trackers, names, path, windowStart, windowEnd, TrackerMatch.Kind.FINDER)
        val beaconsMd = stayedWithYou(
            followAssessments(
                inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.BEACON },
                names, path, windowStart, windowEnd, TrackerMatch.Kind.BEACON,
            ),
        )
        val wearablesMd = stayedWithYou(
            followAssessments(
                inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.WEARABLE },
                names, path, windowStart, windowEnd, TrackerMatch.Kind.WEARABLE,
            ),
        )

        appendLine(localized("debrief_report_where_you_were_operator_gps", "## Where you were (operator GPS)"))
        appendLine(localized("debrief_report_tag_detections_with_gps", "- Tag detections with GPS: %1\$s.", if (settings.tagLocation) localized("debrief_report_on", "on") else localized("debrief_report_off", "off")))
        appendLine(
            localized("debrief_report_online_place_names", "- Online place names: ") +
                if (places.attempted) places.note
                else localized("debrief_report_off_settings_online_place_names_in_debrief", "off (Settings → Online place names in Debrief). No reverse-geocode this export."),
        )
        append(whereYouWere(settings, path, pathLen, pathSpan, inWin, displayNames, places, windowEnd, customNames, bookmarkedKeys).trimEnd())
        appendLine()
        appendLine()
        if (path.size < 2 || pathSpan < MOVE_M) {
            appendLine(localized("debrief_report_following_test_insufficient_movement_need_45_m", "- Following test: insufficient movement (need ~45 m span). Do not infer a tail."))
            appendLine()
        }
        appendLine(localized("debrief_report_gps_co_travel", "## GPS co-travel"))
        appendLine(
            localized("debrief_report_only_radios_that_stayed_with_the_operator", "Only radios that stayed with the operator path are listed. House tags and other radios the operator only passed are omitted — they are not tracking. Not identity. Find My MAC rotation will not stitch a tail that changes address. Possible tail extra gates (walks): trail covers ≥ half the operator path, ≥ 2/3 of GPS stamps at −75 dBm or louder, last stamp not 12 dB below loudest. Fail any one → omit (pass-by), not a tail. Finder tags (AirTag / SmartTag / Tile / Chipolo / Pebblebee / Find My / loud pocket Apple) are the tracking test. Retail beacons and wearables that co-travel are listed separately — they do not typically move with you (beacons) or are usually own kit (wearables)."),
        )
        fun List<FollowHit>.open(): List<FollowHit> = filter { it.device.key !in mineKeys }
        val followingMd = follow.filter { it.verdict == Verdict.FOLLOWING }.open()
        val wholeSitMd = follow.filter {
            it.verdict == Verdict.OWN_LIKELY || it.verdict == Verdict.MOVED_WITH_YOU
        }.open()
        val beaconsOpen = beaconsMd.open()
        val wearablesOpen = wearablesMd.open()
        if (followingMd.isEmpty() && wholeSitMd.isEmpty() && beaconsOpen.isEmpty() && wearablesOpen.isEmpty()) {
            appendLine(localized("debrief_report_none_stayed_with_the_path", "- None stayed with the path."))
        } else {
            fun dump(title: String, rows: List<FollowHit>) {
                if (rows.isEmpty()) return
                appendLine()
                appendLine("### $title")
                rows.forEach { h ->
                    val d = h.device
                    appendLine(
                        localized("debrief_report_rssi_dbm_min_max_trail_fixes_span", "- %1\$s  %2\$s  %3\$s  RSSI %4\$s dBm (min %5\$s / max %6\$s)  trail %7\$s fixes, span %8\$s m", h.label, d.reportName(customNames), d.mac, d.rssi, d.rssiMin, d.rssiMax, h.samples, h.spanM.toInt()),
                    )
                    appendLine("  ${h.detail}")
                }
            }
            dump(
                localized("debrief_report_possible_trackers_with_you_finder_tags_whole", "Possible trackers with you (finder tags, whole sit — yours or planted before you started)"),
                wholeSitMd,
            )
            dump(
                localized("debrief_report_possible_tail_finder_tags_first_heard_after", "Possible tail (finder tags, first heard after this sit started, then stayed)"),
                followingMd,
            )
            dump(
                localized("debrief_report_retail_beacons_with_you_ibeacon_minew_estimote", "Retail beacons with you (iBeacon / Minew / Estimote / Kontakt.io / Target Atrius basket — fixtures; a pushed cart will co-travel)"),
                beaconsOpen,
            )
            dump(
                localized("debrief_report_wearables_with_you_garmin_fitbit_oura_usually", "Wearables with you (Garmin / Fitbit / Oura — usually own kit or a companion)"),
                wearablesOpen,
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
                    Verdict.INSUFFICIENT to localized("debrief_report_operator_gps_path_too_short_m_to", "Operator GPS path too short (%1\$s m) to test following.", opSpan.toInt())
                trail.size < 2 ->
                    Verdict.INSUFFICIENT to localized("debrief_report_heard_but_not_at_two_gps_points", "Heard, but not at two GPS points. Cannot test co-travel.")
                onBody && ownHere ->
                    Verdict.OWN_LIKELY to onBodyLine(kind, d, trail.size)
                cover && ownHere && d.rssiMax >= ON_BODY_MAX ->
                    Verdict.OWN_LIKELY to
                        localized("debrief_report_heard_along_m_of_your_m_path", "Heard along %1\$s m of your %2\$s m path and still loud (%3\$s dBm). ", trailLen.toInt(), opLen.toInt(), d.rssiMax) +
                        withYouNote(kind, d)
                span < MOVE_M * 0.6 ->
                    Verdict.STATIONARY to localized("debrief_report_heard_near_one_place_m_span_while", "Heard near one place (%1\$s m span) while you moved %2\$s m. Looks stationary — you walked away from it.", span.toInt(), opSpan.toInt())
                presentAtStart && stillHere && d.rssiMax >= ON_BODY_MAX ->
                    Verdict.OWN_LIKELY to
                        localized("debrief_report_moved_m_with_you_already_on_the", "Moved %1\$s m with you, already on the air when this 15-minute window opened, strong (%2\$s dBm). ", span.toInt(), d.rssi) +
                        withYouNote(kind, d)
                presentAtStart && stillHere ->
                    Verdict.MOVED_WITH_YOU to
                        localized("debrief_report_gps_samples_span_m_along_your_path", "GPS samples span %1\$s m along your path (%2\$s fixes). Already on the air when this window opened and still here. ", span.toInt(), trail.size) +
                        withYouNote(kind, d)
                !presentAtStart && span >= MOVE_M && trail.size >= 3 ->
                    possibleTail(trail, span, opLen, kind, d)
                else ->
                    Verdict.STATIONARY to
                        localized("debrief_report_heard_along_m_gps_stamps_but_did", "Heard along %1\$s m (%2\$s GPS stamps) but did not stay loud on you. Neighborhood arc / pass-by, not a tail.", span.toInt(), trail.size)
            }
            FollowHit(d, label, verdict, detail, span, trail.size)
        }.sortedBy { it.verdict.ordinal }
    }

    private fun onBodyLine(kind: TrackerMatch.Kind, d: Sighting, stamps: Int): String {
        val loud = localized("debrief_report_stayed_loud_with_you_the_whole_sit", "Stayed loud with you the whole sit (%1\$s to %2\$s dBm, %3\$s GPS stamps). ", d.rssiMax, d.rssiMin, stamps)
        return loud + withYouNote(kind, d)
    }

    /**
     * Catalog sentence for a live decode, when the signature wrote one.
     * A label with no sentence is named only. No fleet id is special.
     */
    private fun liveDecodeSentence(device: Sighting): String? {
        val chips = device.liveDecode
        if (chips.isEmpty()) return null
        val notes = chips.map { it.note.trim() }.filter { it.isNotEmpty() }.distinct()
        if (notes.isNotEmpty()) return notes.joinToString(" ")
        val labels = chips.reportLabels()
        if (labels.isEmpty()) return null
        return localized("debrief_report_decoded", "Decoded: %1\$s.", labels.joinToString(", "))
    }

    private fun withYouNote(kind: TrackerMatch.Kind, device: Sighting): String {
        val decoded = liveDecodeSentence(device)
        val base = when (kind) {
            TrackerMatch.Kind.FINDER ->
                localized("debrief_report_with_you_the_whole_sit_yours_or", "With you the whole sit — yours or planted before you started. Account for it.")
            TrackerMatch.Kind.BEACON ->
                localized("debrief_report_location_beacons_do_not_typically_move_with", "Location beacons do not typically move with you. Account for it (own test tag, badge, or a short overlap with a fixture).")
            TrackerMatch.Kind.WEARABLE ->
                localized("debrief_report_typical_of_a_watch_or_ring_you", "Typical of a watch or ring you or a companion are wearing. Not typically a planted tracker.")
        }
        return when {
            decoded != null -> "$base $decoded"
            kind == TrackerMatch.Kind.FINDER ->
                localized("debrief_report_find_my_iphone_addresses_rotate_this_mac", "%1\$s Find My / iPhone addresses rotate; this MAC is this session.", base)
            else -> base
        }
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
        device: Sighting,
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
                    localized("debrief_report_appeared_after_the_sit_started_but_last", "Appeared after the sit started, but last GPS stamp was %1\$s dBm after a loudest of %2\$s dBm (−%3\$s dB). Looks like you walked away from a fixture, not a tail.", last, peak, fade)
            loudN < loudNeed ->
                Verdict.STATIONARY to
                    localized("debrief_report_appeared_after_the_sit_started_and_gps", "Appeared after the sit started and GPS span was %1\$s m, but only %2\$s/%3\$s stamps were loud (−75 dBm+). Looks like a pass-by, not a tail.", span.toInt(), loudN, trail.size)
            trailLen < coverNeed ->
                Verdict.STATIONARY to
                    localized("debrief_report_appeared_after_the_sit_started_but_was", "Appeared after the sit started, but was only heard along %1\$s m of your %2\$s m path (%3\$s%%). Neighborhood arc / pass-by, not a tail.", trailLen.toInt(), opLen.toInt(), coverPct)
            else -> {
                val stats =
                    localized("debrief_report_appeared_after_the_sit_started_then_stayed", "Appeared after the sit started, then stayed loud with you across %1\$s m (%2\$s m of your %3\$s m path, %4\$s%%; %5\$s/%6\$s GPS stamps ≥ −75 dBm). ", span.toInt(), trailLen.toInt(), opLen.toInt(), coverPct, loudN, trail.size)
                val note = when (kind) {
                    TrackerMatch.Kind.FINDER ->
                        localized("debrief_report_treat_as_a_possible_tail_until_you", "Treat as a possible tail until you visually account for it.")
                    TrackerMatch.Kind.BEACON ->
                        localized("debrief_report_unusual_for_a_retail_location_beacon_they", "Unusual for a retail/location beacon — they do not typically move with you. Account for it; not the same as a Find My tail.")
                    TrackerMatch.Kind.WEARABLE ->
                        localized("debrief_report_typical_of_a_watch_that_joined_the", "Typical of a watch that joined the sit (you put it on, or someone walking with you). Not typically a planted tracker.")
                }
                Verdict.FOLLOWING to stats + note
            }
        }.let { (verdict, text) ->
            val extra = liveDecodeSentence(device)
            verdict to if (extra == null) text else "$text $extra"
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
        window: DebriefWindow,
        mineHeard: Int = 0,
    ): String = buildString {
        val whenPhrase = if (window.sitName != null) {
            localized("debrief_report_in_sit", "In sit %1\$s", window.sitName)
        } else {
            localized("debrief_report_in_the_last_15_minutes", "In the last 15 minutes")
        }
        append(localized("debrief_report_fieldwatch_heard_wi_fi_access_points_and", "%1\$s Fieldwatch heard %2\$s Wi-Fi access points and %3\$s BLE advertisers", whenPhrase, wifi.size, ble.size))
        append(localized("debrief_report_signature_matched", " (%1\$s signature-matched", named.size))
        if (mineHeard > 0) append(localized("debrief_report_marked_mine_4", ", %1\$s marked mine", mineHeard))
        append(localized("debrief_report_hidden_ssids_randomized_ble", ", %1\$s hidden SSIDs, %2\$s randomized BLE). ", hidden.size, randomized))
        if (settings.tagLocation && pathLen > 0) {
            append(localized("debrief_report_overall_distance_traveled_along_the_gps_path", "Overall distance traveled: %1\$s along the GPS path (straight-line span %2\$s). ", fmtDist(pathLen), fmtDist(pathSpan)))
        }
        if (places.namesByCell.isNotEmpty()) {
            append(localized("debrief_report_stops_area", "Stops / area: %1\$s. ", places.areaLine()))
        } else if (places.attempted && settings.tagLocation) {
            append("${places.note} ")
        }
        val wholeSit = ownLikely + withYou
        when {
            following.isNotEmpty() || wholeSit.isNotEmpty() -> {
                append(localized("debrief_report_tracking_note", "TRACKING NOTE. "))
                if (wholeSit.isNotEmpty()) {
                    append(localized("debrief_report_finder_tag_s_with_you_the_whole", "%1\$s finder tag(s) with you the whole sit (your kit or planted before you started): ", wholeSit.size))
                    append(wholeSit.joinToString { trackId(it) })
                    append(". ")
                }
                if (following.isNotEmpty()) {
                    append(localized("debrief_report_possible_tail_s_first_heard_after_this", "%1\$s possible tail(s) first heard after this sit started: ", following.size))
                    append(following.joinToString { trackId(it) })
                    append(". ")
                }
                append(localized("debrief_report_account_for_every_mac_fieldwatch_cannot_tell", "Account for every MAC — Fieldwatch cannot tell yours from a plant. "))
            }
            !settings.tagLocation -> {
                append(localized("debrief_report_gps_tagging_is_off_so_a_following", "GPS tagging is off, so a following test was not performed. Enable “Tag detections with GPS” and walk to test. "))
            }
            pathSpan < MOVE_M -> {
                append(localized("debrief_report_gps_displacement_was_only_m_too_short", "GPS displacement was only %1\$s m — too short to test whether a tracker is following. Walk farther with tagging on. ", pathSpan.toInt()))
            }
            else -> append(localized("debrief_report_no_finder_tag_clearly_stayed_with_the", "No finder tag clearly stayed with the GPS path in this window. "))
        }
        if (beaconsWithYou.isNotEmpty()) {
            append(localized("debrief_report_retail_beacon_s_also_stayed_with_the", "Retail beacon(s) also stayed with the path (unusual — fixtures do not typically move with you): "))
            append(beaconsWithYou.joinToString { "${it.label} ${it.device.mac}" })
            append(". ")
        }
        if (wearablesWithYou.isNotEmpty()) {
            append(localized("debrief_report_wearable_s_stayed_with_the_path_usually", "Wearable(s) stayed with the path (usually your watch/ring or a companion): "))
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
            appendLine(localized("debrief_report_gps_tagging_is_off_fieldwatch_cannot_test", "GPS tagging is OFF. Fieldwatch cannot test whether a radio moved with you."))
            appendLine(localized("debrief_report_turn_on_settings_tag_detections_with_gps", "Turn on Settings → Tag detections with GPS, walk or drive 50+ m, then run Debrief again."))
            return@buildString
        }
        appendLine(localized("debrief_report_overall_distance_traveled_along_the_gps_path_2", "Overall distance traveled: %1\$s along the GPS path (%2\$s samples). Straight-line span %3\$s.", fmtDist(pathLen), path.size, fmtDist(pathSpan)))
        appendLine(localized("debrief_report_co_travel_is_split_by_class_finder", "Co-travel is split by class: finder tags (AirTag / Find My, SmartTag, Tile, Chipolo, Pebblebee, loud pocket Apple), retail beacons (iBeacon, Minew, Estimote, Kontakt.io, Target Atrius basket), and wearables (Garmin, Fitbit, Oura)."))
        if (path.size < 2 || pathSpan < MOVE_M) {
            appendLine(localized("debrief_report_insufficient_movement_to_distinguish_a_radio_that", "Insufficient movement to distinguish a radio that stayed with you from one you passed. Walk or drive farther and re-run."))
            return@buildString
        }
        if (following.isEmpty() && wholeSit.isEmpty() && beaconsWithYou.isEmpty() && wearablesWithYou.isEmpty()) {
            appendLine(localized("debrief_report_no_finder_tag_retail_beacon_or_wearable", "No finder tag, retail beacon, or wearable stayed with you. House tags and other radios you only passed are not listed."))
        } else {
            appendLine(localized("debrief_report_callouts_below_are_only_radios_that_stayed", "Callouts below are only radios that stayed with the path. Radios you passed (store fixtures, house tags) are omitted."))
        }
    }

    private fun markedMineSection(
        devices: List<Sighting>,
        customNames: Map<String, String>,
        mineKeys: Set<String>,
        assessed: List<FollowHit>,
    ): String? {
        val hits = devices.filter { it.key in mineKeys }
        if (hits.isEmpty()) return null
        return buildString {
            appendLine(localized("debrief_report_radios_you_marked_mine_heard_in_this", "Radios you marked mine. Heard in this window. Still listed. No beep while the mark is on."))
            hits.sortedWith(
                compareByDescending<Sighting> { it.rssi }.thenBy { it.mac },
            ).forEach { d ->
                val kind = if (d.kind == RadioKind.WIFI) "WIFI" else "BLE"
                appendLine("  · $kind  ${d.mac}  ${d.reportName(customNames)}  ${d.rssi} dBm")
                val verdict = assessed.firstOrNull { it.device.key == d.key }?.verdict
                val line = when (verdict) {
                    Verdict.FOLLOWING ->
                        localized("debrief_report_marked_mine_first_heard_after_the_sit", "Marked mine. First heard after the sit started and stayed with the path.")
                    Verdict.OWN_LIKELY, Verdict.MOVED_WITH_YOU ->
                        localized("debrief_report_marked_mine_with_you_the_whole_sit", "Marked mine. With you the whole sit.")
                    else -> localized("debrief_report_marked_mine_5", "Marked mine.")
                }
                appendLine("    $line")
            }
        }.trimEnd()
    }

    private fun observerNotesSection(
        devices: List<Sighting>,
        customNames: Map<String, String>,
        observerNotes: Map<String, String>,
    ): String? {
        val hits = devices.mapNotNull { d ->
            val note = observerNotes[d.key]?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            d to note
        }
        if (hits.isEmpty()) return null
        return buildString {
            appendLine(localized("debrief_report_your_captions_on_radios_heard_in_this", "Your captions on radios heard in this window. Same KIND+MAC as Named radios. Not catalog Notes."))
            hits.sortedWith(
                compareByDescending<Pair<Sighting, String>> { it.first.rssi }.thenBy { it.first.mac },
            ).forEach { (d, note) ->
                val kind = if (d.kind == RadioKind.WIFI) "WIFI" else "BLE"
                appendLine("  · $kind  ${d.reportName(customNames)}  ${d.mac}  ${d.rssi} dBm")
                appendLine("    $note")
            }
        }.trimEnd()
    }

    private fun trackerCallout(
        intro: String,
        rows: List<FollowHit>,
        customNames: Map<String, String> = emptyMap(),
    ): String = buildString {
        appendLine(intro)
        appendLine()
        rows.forEach { h ->
            val d = h.device
            appendLine("  • ${h.label}")
            appendLine(localized("debrief_report_rssi_dbm_min_max", "    %1\$s  %2\$s  RSSI %3\$s dBm (min %4\$s / max %5\$s)", d.reportName(customNames), d.mac, d.rssi, d.rssiMin, d.rssiMax))
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
        customNames: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
    ): String = buildString {
        appendLine(localized("debrief_report_phone_gps_at_hear_time_not_the", "Phone GPS at hear-time, not the other radio’s location and not a camera pole. Stays are clusters within about 40 m; hops between them are transit. Coordinates are not repeated on every Wi-Fi/BLE line."))
        if (!settings.tagLocation) {
            appendLine(localized("debrief_report_gps_tagging_is_off_turn_on_settings", "GPS tagging is OFF. Turn on Settings → Tag detections with GPS to record where you were when radios were heard."))
            return@buildString
        }
        if (path.isEmpty()) {
            appendLine(localized("debrief_report_gps_tagging_is_on_but_this_window", "GPS tagging is on, but this window has no fixes yet."))
            return@buildString
        }
        appendLine(localized("debrief_report_overall_along_track_span_fixes", "Overall: %1\$s along-track, span %2\$s, %3\$s fixes.", fmtDist(pathLen), fmtDist(pathSpan), path.size))
        if (places.attempted) {
            appendLine(places.note)
            appendLine(localized("debrief_report_street_names_are_approximate_do_not_treat", "Street names are approximate. Do not treat a street as the location of a matched camera or tag."))
        }
        val legs = Geo.legs(path, now = now)
        if (legs.isEmpty()) {
            appendLine(localized("debrief_report_no_path_legs", "No path legs."))
            return@buildString
        }
        val stopNames = legs.filter { it.stay }.mapNotNull { places.nameNear(it.lat, it.lon) }
        if (stopNames.isNotEmpty()) {
            appendLine(localized("debrief_report_stops", "Stops: ") + stopNames.joinToString(" → "))
        }
        var stayN = 0
        legs.forEachIndexed { i, leg ->
            if (leg.stay) {
                stayN++
                appendLine()
                appendLine(localized("debrief_report_stay_utc", "%1\$s. Stay  %2\$s–%3\$s UTC  (%4\$s)", i + 1, clock(leg.startAt), clock(leg.endAt), fmtDur(leg.durationMs)))
                appendLine("   ${placeAndGps(leg.lat, leg.lon, places)}")
                val here = devices.filter { heardAt(it, leg) }
                val aps = here.count { it.kind == RadioKind.WIFI }
                val ble = here.count { it.kind == RadioKind.BLE }
                val sigs = here.flatMap { d -> d.fleetIds.map { names[it] ?: it } }.distinct()
                append(localized("debrief_report_heard_here_ap_s_ble", "   Heard here: %1\$s AP(s), %2\$s BLE", aps, ble))
                if (sigs.isNotEmpty()) append("  ·  ${sigs.take(6).joinToString(", ")}")
                appendLine()
                here.filter { inventoryKeep(it, settings, bookmarkedKeys) }.sortedByDescending { it.rssi }.take(4).forEach { d ->
                    appendLine("   · ${d.reportName(customNames)}  ${d.mac}  ${d.rssi} dBm")
                }
                if (here.isEmpty()) appendLine(localized("debrief_report_no_gps_stamped_radios_tied_to_this", "   · No GPS-stamped radios tied to this stay (tagging may have started after they were first heard)."))
            } else {
                appendLine()
                appendLine(
                    localized("debrief_report_transit_utc_along_track", "%1\$s. Transit  %2\$s–%3\$s UTC  %4\$s along track", i + 1, clock(leg.startAt), clock(leg.endAt), fmtDist(leg.pathM)),
                )
                appendLine("   ${placeAndGps(leg.lat, leg.lon, places)}")
                appendLine("   → ${placeAndGps(leg.endLat, leg.endLon, places)}")
            }
        }
        val stays = legs.count { it.stay }
        if (stays == 1 && pathSpan < MOVE_M) {
            appendLine()
            appendLine(localized("debrief_report_one_stay_you_did_not_move_far", "One stay — you did not move far enough in this window to split locations."))
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
            pathSpan > 200 && ap in 1..25 -> localized("debrief_report_in_motion_walk_vehicle_through_mixed_rf", "In motion (walk/vehicle) through mixed RF.")
            ap <= 4 && ble.size < 30 && persistAp >= 1 -> localized("debrief_report_likely_a_dwelling_or_small_office_few", "Likely a dwelling or small office — few sitting APs, limited BLE.")
            ap >= 15 && randomized >= 40 -> localized("debrief_report_dense_public_retail_street_many_aps_and", "Dense public / retail / street: many APs and phone-like randomized BLE.")
            ap >= 8 && persistAp >= 4 -> localized("debrief_report_likely_a_building_with_standing_infrastructure_aps", "Likely a building with standing infrastructure APs plus patrons.")
            else -> localized("debrief_report_mixed_or_under_sampled_environment", "Mixed or under-sampled environment.")
        }
        return localized("debrief_report_aps_ble_persistent_aps_traveled_span", "%1\$s  (%2\$s APs, %3\$s BLE, %4\$s persistent APs, traveled %5\$s, span %6\$s.)", guess, ap, ble.size, persistAp, fmtDist(pathLen), fmtDist(pathSpan))
    }

    private fun wifiLine(
        d: Sighting,
        names: Map<String, String>,
        from: Long,
        now: Long,
        customNames: Map<String, String> = emptyMap(),
    ): String = buildString {
        append(d.reportName(customNames)).append("  ").append(d.mac)
        d.vendor?.let { append("  ").append(it) }
        append("  ").append(d.rssi).append(" dBm")
        if (d.channel != 0) append(localized("debrief_report_ch_2", "  ch ")).append(d.channel)
        if (d.hiddenSsid) append(localized("debrief_report_hidden", "  hidden"))
        if (d.fleetIds.isNotEmpty()) append("  ").append(d.fleetIds.joinToString("+") { names[it] ?: it })
        append(localized("debrief_report_dwell_2", "  dwell ")).append(fmtDur(dwellMs(d, from, now)))
    }

    private fun bleLine(
        d: Sighting,
        names: Map<String, String>,
        from: Long,
        now: Long,
        customNames: Map<String, String> = emptyMap(),
    ): String = buildString {
        append(d.reportName(customNames)).append("  ").append(d.mac)
        if (d.randomized) append(localized("debrief_report_rand", "  RAND"))
        append("  ").append(d.rssi).append(" dBm")
        if (d.fleetIds.isNotEmpty()) append("  ").append(d.fleetIds.joinToString("+") { names[it] ?: it })
        append(localized("debrief_report_dwell_2", "  dwell ")).append(fmtDur(dwellMs(d, from, now)))
    }

    /** Unmatched rotating BLE stays in counts/export; inventories omit it unless Extra attention, named, bookmark, or payload. */
    private fun inventoryKeep(
        d: Sighting,
        settings: AppSettings,
        bookmarkedKeys: Set<String>,
    ): Boolean {
        if (settings.debriefShowUnmatchedRandomBle && settings.debriefShowAllRadios) return true
        if (d.kind != RadioKind.BLE) return true
        if (!d.randomized) return true
        if (d.fleetIds.isNotEmpty()) return true
        if (d.payloadLat != null && d.payloadLon != null) return true
        if (d.key in bookmarkedKeys) return true
        return false
    }

    private fun classChart(devices: List<Sighting>, fleets: List<Fleet>): ReportChart? {
        if (devices.isEmpty()) return null
        val byId = fleets.associate { it.id to it.kind }
        val counts = linkedMapOf<SignatureClass, Int>()
        var unmatched = 0
        var multi = false
        for (d in devices) {
            val classes = d.fleetIds.mapNotNull { byId[it] }.toSet()
            if (classes.isEmpty()) {
                unmatched++
            } else {
                if (classes.size > 1) multi = true
                classes.forEach { counts[it] = (counts[it] ?: 0) + 1 }
            }
        }
        val rows = SignatureClass.entries.mapNotNull { kind ->
            val n = counts[kind] ?: return@mapNotNull null
            ReportBar(kind.displayLabel(), n)
        }.toMutableList()
        if (unmatched > 0) rows += ReportBar(localized("debrief_report_unmatched", "Unmatched"), unmatched)
        if (rows.isEmpty()) return null
        return ReportChart(
            rows = rows,
            caption = if (multi) localized("debrief_report_a_radio_in_two_classes_counts_in", "A radio in two classes counts in each.") else "",
        )
    }

    private fun signatureChart(named: List<Sighting>, names: Map<String, String>): ReportChart? {
        if (named.isEmpty()) return null
        val counts = linkedMapOf<String, Int>()
        for (d in named) {
            val sigs = d.fleetIds.map { names[it] ?: it }.filter { it.isNotBlank() }.distinct()
            val key = if (sigs.isEmpty()) localized("debrief_report_unmatched", "Unmatched") else sigs.joinToString(" + ")
            counts[key] = (counts[key] ?: 0) + 1
        }
        val rows = counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { ReportBar(it.key, it.value) }
        return ReportChart(rows = rows)
    }

    private fun wifiLines(
        rows: List<Sighting>,
        names: Map<String, String>,
        from: Long,
        now: Long,
        customNames: Map<String, String>,
        fleets: List<Fleet>,
        mineKeys: Set<String>,
    ): String {
        if (rows.isEmpty()) return ""
        return buildString {
            appendLine(localized("debrief_report_named_or_marked", "Named or marked:"))
            rows.sortedByDescending { it.rssi }.forEach { d ->
                appendLine("  · ${wifiLine(d, names, from, now, customNames)}")
                if (d.key in mineKeys) appendLine(localized("debrief_report_marked_mine_2", "    Marked mine"))
                d.displayAttentionNotes(fleets).forEach { (sig, note) ->
                    appendLine(localized("debrief_report_extra_attention_2", "    extra attention (%1\$s): %2\$s", sig, note))
                }
            }
        }.trimEnd()
    }

    private fun bleLines(
        rows: List<Sighting>,
        names: Map<String, String>,
        from: Long,
        now: Long,
        customNames: Map<String, String>,
        mineKeys: Set<String>,
    ): String {
        if (rows.isEmpty()) return ""
        return buildString {
            appendLine(localized("debrief_report_named_or_marked", "Named or marked:"))
            rows.sortedByDescending { it.rssi }.forEach { d ->
                appendLine("  · ${bleLine(d, names, from, now, customNames)}")
                if (d.key in mineKeys) appendLine(localized("debrief_report_marked_mine_2", "    Marked mine"))
            }
        }.trimEnd()
    }

    private fun floodBody(floods: List<FloodBurst>, start: Long, end: Long): String? {
        val rows = floods.filter { it.at in start..end }.sortedBy { it.at }
        if (rows.isEmpty()) return null
        return buildString {
            appendLine(FloodBurst.intro(rows))
            appendLine()
            rows.forEach { appendLine(it.reportLine(FloodBurst.clock(it.at))) }
        }.trimEnd()
    }

    private fun anomalyLines(
        devices: List<Sighting>,
        customNames: Map<String, String> = emptyMap(),
        settings: AppSettings,
        bookmarkedKeys: Set<String>,
        showAll: Boolean,
    ): List<String> {
        val out = ArrayList<String>()
        val pairing = devices.filter { d ->
            d.facts.serviceData.any { it.uuid.contains("FE2C", true) && it.dataHex.length == 6 }
        }
        if (pairing.isNotEmpty()) {
            out += if (showAll || pairing.size <= 8) {
                localized("debrief_report_google_fast_pair_in_pairing_mode", "Google Fast Pair in pairing mode: ") +
                    pairing.joinToString { "${it.reportName(customNames)} ${it.mac}" }
            } else {
                localized("debrief_report_google_fast_pair_in_pairing_mode_radios", "Google Fast Pair in pairing mode: %1\$s radios.", pairing.size)
            }
        }
        val loudUnknown = devices.filter {
            it.rssi >= -50 && it.fleetIds.isEmpty() && it.name.isBlank() &&
                inventoryKeep(it, settings, bookmarkedKeys)
        }
        if (loudUnknown.isNotEmpty()) {
            out += localized("debrief_report_very_strong_unnamed_radios_50_dbm", "Very strong unnamed radios (≥ −50 dBm): ") +
                loudUnknown.take(8).joinToString { "${it.mac} ${it.rssi} dBm" }
        }
        val rand = devices.count { it.kind == RadioKind.BLE && it.randomized }
        if (rand >= 20) {
            out += localized("debrief_report_high_randomized_ble_typical_of_phones_not", "High randomized BLE (%1\$s) — typical of phones, not a tracking finding.", rand)
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
        includeAircraft: Boolean,
    ): String = buildString {
        append(localized("debrief_report_a_passive_observer_with_the_same_radios", "A passive observer with the same radios would see %1\$s named/hidden APs ", wifi.size))
        append(localized("debrief_report_and_ble_advertisers_randomized", "and %1\$s BLE advertisers (%2\$s randomized). ", ble.size, randomized))
        if (hidden.isNotEmpty()) append(localized("debrief_report_hidden_ssids_still_beacon_and_identify_the", "Hidden SSIDs still beacon and identify the AP by BSSID. "))
        if (settings.tagLocation) append(localized("debrief_report_this_debrief_includes_operator_gps_samples_used", "This debrief includes operator GPS samples used for distance and the following test. "))
        if (includeAircraft) {
            append(localized("debrief_report_this_debrief_includes_advertised_aircraft_positions_from", "This debrief includes advertised aircraft positions from radios that broadcast a latitude and longitude. "))
        }
        if (places.attempted && places.available) {
            append(localized("debrief_report_street_names_came_from_the_phone_s", "Street names came from the phone’s system geocoder while online. "))
        }
        append(localized("debrief_report_do_not_share_this_file_off_device", "Do not share this file off-device without redaction."))
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
            add(localized("debrief_report_possible_tail_appeared_after_this_sit_started", "Possible tail (appeared after this sit started): %1\$s. Pause Live, open detail, note RSSI while you walk a dog-leg. Do not disable someone else’s tag.", following.joinToString { trackId(it) }))
        }
        if (ownLikely.isNotEmpty() || withYou.isNotEmpty()) {
            add(
                localized("debrief_report_possible_trackers_with_you_could_be_yours", "Possible trackers with you: %1\$s. Could be yours or planted in the car/bag/on you before you started. Account for each MAC — do not dismiss as yours.", (ownLikely + withYou).joinToString { trackId(it) }),
            )
        }
        if (beaconsWithYou.isNotEmpty()) {
            add(
                localized("debrief_report_retail_beacons_with_you_unusual_fixtures_do", "Retail beacons with you (unusual — fixtures do not typically move with you): ") +
                    beaconsWithYou.joinToString { it.label + " " + it.device.mac } +
                    localized("debrief_report_account_for_a_test_tag_or_badge", ". Account for a test tag or badge before treating it as a follower."),
            )
        }
        if (wearablesWithYou.isNotEmpty()) {
            add(
                localized("debrief_report_wearables_with_you_usually_own_kit_or", "Wearables with you (usually own kit or a companion): ") +
                    wearablesWithYou.joinToString { it.label + " " + it.device.mac } +
                    ".",
            )
        }
        if (!settings.tagLocation) add(localized("debrief_report_enable_tag_detections_with_gps_and_walk", "Enable Tag detections with GPS and walk 50+ m, then run Debrief again for a following test."))
        else if (pathSpan < MOVE_M) add(localized("debrief_report_walk_farther_50_m_with_gps_tagging", "Walk farther (50+ m) with GPS tagging on, then re-run Debrief."))
        add(localized("debrief_report_use_live_pause_to_inspect_a_busy", "Use Live → Pause to inspect a busy list. Watch tracker signatures if this sit was noisy."))
        add(localized("debrief_report_station_side_wi_fi_probes_clients_still", "Station-side Wi-Fi (probes/clients) still needs a dedicated sniffer — Fieldwatch cannot see them."))
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
                append(localized("debrief_report_retail_beacon_s_also_with_the_path", " Retail beacon(s) also with the path (unusual): "))
                append(beaconsWithYou.joinToString { it.label + " (" + it.device.mac + ")" })
                append(".")
            }
            if (wearablesWithYou.isNotEmpty()) {
                append(localized("debrief_report_wearable_s_with_the_path_usually_own", " Wearable(s) with the path (usually own kit): "))
                append(wearablesWithYou.joinToString { it.label + " (" + it.device.mac + ")" })
                append(".")
            }
        }
        val core = when {
            following.isNotEmpty() && (ownLikely.isNotEmpty() || withYou.isNotEmpty()) ->
                localized("debrief_report_possible_tail_appeared_after_sit_started_also", "Possible tail (appeared after sit started): %1\$s. Also finder tags with you (yours or planted before): %2\$s. Account for every MAC.", following.joinToString { trackId(it) }, (ownLikely + withYou).joinToString { trackId(it) })
            following.isNotEmpty() ->
                localized("debrief_report_possible_tail_appeared_after_this_sit_started_2", "Possible tail (appeared after this sit started): %1\$s. Account for it on the person/vehicle.", following.joinToString { trackId(it) })
            !settings.tagLocation ->
                localized("debrief_report_turn_on_gps_tagging_and_walk_before", "Turn on GPS tagging and walk before you can test whether a tracker is following you.")
            pathSpan < MOVE_M ->
                localized("debrief_report_not_enough_gps_movement_m_to_test", "Not enough GPS movement (%1\$s m) to test following; walk and re-run Debrief.", pathSpan.toInt())
            ownLikely.isNotEmpty() || withYou.isNotEmpty() ->
                localized("debrief_report_finder_tags_with_you_yours_or_planted", "Finder tags with you (yours or planted before you started): %1\$s. No new arrival this window. Account for each MAC — do not dismiss as yours.", (ownLikely + withYou).joinToString { trackId(it) })
            beaconsWithYou.isNotEmpty() || wearablesWithYou.isNotEmpty() ->
                localized("debrief_report_no_finder_tag_stayed_with_the_path", "No finder tag stayed with the path.")
            named.isEmpty() ->
                localized("debrief_report_no_signature_hits_and_no_gps_co", "No signature hits and no GPS co-travel of trackers in this 15-minute window.")
            else ->
                localized("debrief_report_no_finder_tag_retail_beacon_or_wearable_2", "No finder tag, retail beacon, or wearable clearly stayed with your GPS path in this window.")
        }
        return (core + extra).trim()
    }

    /** Label and MAC, plus the live-decode name when the signature asked for one. */
    private fun trackId(hit: FollowHit): String {
        val labels = hit.device.liveDecode.reportLabels()
        val id = "${hit.label} ${hit.device.mac}"
        return if (labels.isEmpty()) id else "$id (${labels.joinToString(", ")})"
    }

    private fun craftSentence(pictures: List<AircraftTrail.Picture>): String {
        if (pictures.isEmpty()) return ""
        val bits = pictures.take(3).joinToString { pic ->
            if (pic.status.isBlank()) pic.title else "${pic.title} (${pic.status})"
        }
        val more = if (pictures.size > 3) localized("debrief_report_and_more", " and %1\$s more", pictures.size - 3) else ""
        return localized("debrief_report_advertised_position", " Advertised position: %1\$s%2\$s.", bits, more)
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
            localized("debrief_report_operator_phone", "%1\$s  (%2\$s, operator phone)", name, gps)
        } else if (places.attempted) {
            localized("debrief_report_operator_phone_no_street_name_this_export", "%1\$s  (operator phone; no street name this export)", gps)
        } else {
            localized("debrief_report_operator_phone_2", "%1\$s  (operator phone)", gps)
        }
    }

    private fun fmtDur(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val m = s / 60
        val r = s % 60
        return if (m >= 60) localized("report_duration_hours", "%1\$sh%2\$sm", m / 60, m % 60)
        else if (m > 0) localized("report_duration_minutes", "%1\$sm%2\$ss", m, r)
        else localized("report_duration_seconds", "%1\$ss", r)
    }
}
