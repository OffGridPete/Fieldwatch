package app.fieldwatch.domain

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
                append(row.value).append((" " + LanguageText.text("ui.wi.fi") + " "))
                append(row.second).append(" BLE")
                appendLine()
            }
        }
    }.trimEnd()
}

data class DebriefSection(
    val number: String,
    val title: String,
    val body: String,
    val alert: Boolean = false,
    val chart: ReportChart? = null,
    val after: String = "",
)

data class DebriefPlaces(
    val attempted: Boolean,
    val available: Boolean,
    val note: String,
    val lines: List<String> = emptyList(),
    val namesByCell: Map<String, String> = emptyMap(),
) {
    /** Exact GPS cell, then nearest named cell within [maxM]. */
    fun nameNear(lat: Double, lon: Double, maxM: Double = 90.0): String? =
        Geo.nameNear(lat, lon, namesByCell, maxM)

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
    val heading: String = LanguageText.text("ui.fieldwatch.field.debrief"),
    val pdfKicker: String = LanguageText.text("ui.field.debrief"),
    val pdfTitle: String = LanguageText.text("ui.field.debrief.2"),
    val pathFigure: SitPathPlot.Figure? = null,
    val extraFigures: List<SitPathPlot.Figure> = emptyList(),
) {
    fun toPlainText(): String = buildString {
        appendLine(heading)
        appendLine()
        appendLine(LanguageText.text("report.disclaimer"))
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
        appendLine(LanguageText.text("report.takeaway", mapOf("text" to takeaway)))
    }

    fun withDemoMacs(
        macs: Collection<String>,
        demo: Boolean,
        uasIds: Collection<String> = emptyList(),
    ): DebriefDoc {
        if (!demo) return this
        fun t(s: String) = MacUtil.redactPrivateText(s, macs, uasIds, true)
        val note = LanguageText.text("ui.mac.tails.uas.ids")
        return copy(
            meta = listOf(LanguageText.text("ui.privacy.label") to note) + meta.map { it.first to t(it.second) },
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
            pathFigure = pathFigure?.withDemoText(::t),
            extraFigures = extraFigures.map { it.withDemoText(::t) },
        )
    }
}

/** Path-key and caption text. The drawn track keeps its coordinates. */
private fun SitPathPlot.Figure.withDemoText(t: (String) -> String): SitPathPlot.Figure = copy(
    caption = t(caption),
    craftKeys = craftKeys.map(t),
    tracks = tracks.map { track -> track.copy(name = t(track.name)) },
    pilots = pilots.map { mark -> mark.copy(label = t(mark.label)) },
    dots = dots.map { dot ->
        dot.copy(
            label = t(dot.label),
            mac = MacUtil.screenMac(dot.mac, true),
            fleetNames = dot.fleetNames.map(t),
            observerNotes = t(dot.observerNotes),
            advertisedNote = t(dot.advertisedNote),
        )
    },
    gnss = gnss.map { pin -> pin.copy(detail = t(pin.detail), confidence = t(pin.confidence)) },
)

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
        gnss: List<GnssMark> = emptyList(),
        placeRecords: List<PlaceMemory.Record> = emptyList(),
        cameras: PathCameras.Report? = null,
        trusted: List<TrustedSitLine> = emptyList(),
        cells: List<CellMark> = emptyList(),
        savedCameras: List<FlockCamera>? = null,
    ): String = document(
        devices, fleets, settings, operatorPath, now, places, window,
        customNames, observerNotes, bookmarkedKeys, watchedFleetIds, mineKeys,
        floods, gnss, placeRecords, cameras, trusted, cells, savedCameras,
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
        gnss: List<GnssMark> = emptyList(),
        placeRecords: List<PlaceMemory.Record> = emptyList(),
        cameras: PathCameras.Report? = null,
        trusted: List<TrustedSitLine> = emptyList(),
        cells: List<CellMark> = emptyList(),
        savedCameras: List<FlockCamera>? = null,
    ): DebriefDoc {
        val names = fleets.associate { it.id to it.name }
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
        val gnssInWindow = gnss.filter { mark ->
            mark.at in windowStart..windowEnd || (mark.endedAt > 0L && mark.endedAt in windowStart..windowEnd)
        }
        val gnssPins = SitPathPlot.gnssPins(gnssInWindow, Geo.despikePath(path))
        val gnssOnPath = gnssPins.isNotEmpty()
        val pathSpan = Geo.spanM(path)
        val pathLen = Geo.pathLengthM(path)
        val cameraTally = cameras?.let { source ->
            if (savedCameras != null) {
                PathCameras.fromSaved(savedCameras, source.plates, source.others, path.isNotEmpty())
            } else {
                PathCameras.tally(path, source.plates, source.others, source.near)
            }
        }?.let { raw ->
            PathCameras.shownInReport(raw, settings.flockLayer, settings.sensorLayer)
        }
        val cameraBody = cameraTally?.let { PathCameras.text(it) }
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
        val placeBody = if (!settings.placeWatch || placeRecords.isEmpty()) {
            null
        } else {
            PlaceMemory.section(
                inWin, placeRecords, names, customNames, mineKeys, windowEnd,
                demo = settings.demoMode,
                streets = places.namesByCell,
            )
        }

        val showAll = settings.debriefShowAllRadios
        fun Sighting.listedWhenShort(): Boolean =
            key in customNames || key in mineKeys || key in bookmarkedKeys
        val byCh = wifi.groupBy { it.channel }.toSortedMap()
        val channelChart = if (byCh.isEmpty()) {
            null
        } else {
            ReportChart(
                rows = byCh.map { (ch, list) ->
                    val label = if (ch == 0) {
                        LanguageText.text("report.ch.unknown")
                    } else {
                        LanguageText.text("report.ch", mapOf("n" to "$ch"))
                    }
                    ReportBar(
                        label,
                        list.size,
                        detail = LanguageText.text("report.strongest", mapOf("rssi" to "${list.maxOf { it.rssi }}")),
                    )
                },
            )
        }
        val networks = LanguageText.text(
            "report.heard.wifi",
            mapOf(
                "n" to "${wifi.size}",
                "hidden" to "${hidden.size}",
                "sat" to "${persistent.count { it.kind == RadioKind.WIFI }}",
            ),
        )
        val networksAfter = if (!showAll) {
            wifiLines(
                wifi.filter { it.listedWhenShort() && it.fleetIds.isEmpty() },
                names, windowStart, now, customNames, fleets, mineKeys,
            )
        } else {
            buildString {
                appendLine(LanguageText.text("ui.loudest.aps"))
                wifi.take(12).forEach { d ->
                    appendLine("  · ${wifiLine(d, names, windowStart, now, customNames)}")
                    if (d.key in mineKeys) appendLine(("    " + LanguageText.text("ui.marked.mine")))
                    d.attentionNotes(fleets).forEach { (sig, note) ->
                        appendLine("    " + LanguageText.text("report.attention", mapOf("name" to sig, "note" to note)))
                    }
                }
                if (hidden.isNotEmpty()) {
                    appendLine(LanguageText.text("ui.hidden.ssids"))
                    hidden.forEach {
                        appendLine(
                            "  · ${it.mac}  ${it.vendor ?: ""}  ${it.rssi} dBm  " +
                                LanguageText.text("report.ch", mapOf("n" to "${it.channel}")),
                        )
                    }
                }
            }.trimEnd()
        }
        val notable = ble.filter {
            inventoryKeep(it, settings, bookmarkedKeys) &&
                (it.fleetIds.isNotEmpty() || it.name.isNotBlank() || it.rssi >= -65 || it.manufacturerId != null)
        }.sortedByDescending { it.rssi }.take(20)
        val omittedRand = ble.count { !inventoryKeep(it, settings, bookmarkedKeys) }
        val bleBody = buildString {
            appendLine(
                LanguageText.text(
                    "report.heard.ble",
                    mapOf(
                        "n" to "${ble.size}",
                        "rand" to "$randomized",
                        "named" to "${named.count { it.kind == RadioKind.BLE }}",
                    ),
                ),
            )
            if (omittedRand > 0) {
                appendLine(LanguageText.text("report.rotating", mapOf("n" to "$omittedRand")))
            }
        }.trimEnd()
        val bleAfter = if (showAll && notable.isNotEmpty()) {
            buildString {
                appendLine(LanguageText.text("ui.notable.ble"))
                notable.forEach { d ->
                    val guess = DeviceExplain.guess(d, d.fleetIds.map { names[it] ?: it })
                    appendLine("  · ${bleLine(d, names, windowStart, now, customNames)}  |  ${guess.headline}")
                    if (d.key in mineKeys) appendLine(("    " + LanguageText.text("ui.marked.mine")))
                    d.attentionNotes(fleets).forEach { (sig, note) ->
                        appendLine("    " + LanguageText.text("report.attention", mapOf("name" to sig, "note" to note)))
                    }
                    val decoded = SignatureFieldDecoder.decodeSighting(d, fleets)
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
                names, windowStart, now, customNames, mineKeys,
            )
        } else {
            ""
        }
        val sigChart = signatureChart(named, names)
        val sigBody = if (named.isEmpty()) LanguageText.text("ui.none.window") else ""
        val sigAfter = if (named.isEmpty()) {
            ""
        } else if (showAll) {
            buildString {
                named.groupBy { it.fleetIds.joinToString("+") { id -> names[id] ?: id } }
                    .toList().sortedByDescending { it.second.size }
                    .forEach { (sig, list) ->
                        appendLine(sig)
                        list.sortedByDescending { it.rssi }.take(8).forEach { d ->
                            append("  · ${d.reportName(customNames)}  ${d.mac}  ${d.rssi} dBm")
                            if (d.key in mineKeys) append(("  " + LanguageText.text("ui.marked.mine.2")))
                            val labels = d.liveDecode.reportLabels()
                            if (labels.isNotEmpty()) append("  ").append(labels.joinToString(", "))
                            appendLine()
                        }
                        list.flatMap { it.attentionNotes(fleets) }.distinct().forEach { (name, note) ->
                            appendLine("  " + LanguageText.text("report.attention", mapOf("name" to name, "note" to note)))
                        }
                    }
            }.trimEnd()
        } else {
            buildString {
                val marked = named.filter { it.listedWhenShort() }.sortedByDescending { it.rssi }
                if (marked.isNotEmpty()) appendLine(LanguageText.text("ui.named.marked"))
                marked.forEach { d ->
                    append("  · ${d.reportName(customNames)}  ${d.mac}  ${d.rssi} dBm")
                    if (d.key in mineKeys) append(("  " + LanguageText.text("ui.marked.mine.2")))
                    val labels = d.liveDecode.reportLabels()
                    if (labels.isNotEmpty()) append("  ").append(labels.joinToString(", "))
                    appendLine()
                }
            }.trimEnd()
        }
        val persistBody = buildString {
            appendLine(LanguageText.text("report.sat", mapOf("n" to "${persistent.size}")))
            append(LanguageText.text("report.first", mapOf("n" to "${arrived.size}")))
        }.trimEnd()
        val persistAfter = if (!showAll) {
            ""
        } else {
            buildString {
                persistent.filter { inventoryKeep(it, settings, bookmarkedKeys) }.take(15).forEach {
                    appendLine(
                        "  · ${it.reportName(customNames)}  ${it.mac}  " +
                            LanguageText.text("report.dwell", mapOf("time" to fmtDur(dwellMs(it, windowStart, now)))),
                    )
                }
                if (persistent.isEmpty()) appendLine(("  " + LanguageText.text("ui.none.label")))
                appendLine(LanguageText.text("ui.loudest.first.seen"))
                arrived.filter { inventoryKeep(it, settings, bookmarkedKeys) }.sortedByDescending { it.rssi }.take(8).forEach {
                    appendLine("  · ${it.reportName(customNames)}  ${it.mac}  ${it.rssi} dBm")
                }
            }.trimEnd()
        }
        val flags = anomalyLines(inWin, customNames, settings, bookmarkedKeys, showAll)
        val anomalyBody = if (flags.isEmpty()) {
            LanguageText.text("ui.no.extra.flags.signature")
        } else flags.joinToString("\n") { "  · $it" }
        val attentionHits = inWin.flatMap { d ->
            d.attentionNotes(fleets).map { (sig, note) -> Triple(d, sig, note) }
        }
        val actionBody = actions(following, withYou, ownLikely, beaconsWithYou, wearablesWithYou, settings, pathSpan)
            .joinToString("\n") { "  · $it" }

        val distanceLine = when {
            !settings.tagLocation -> LanguageText.text("ui.gps.tagging.off.no")
            path.size < 2 -> LanguageText.text("ui.gps.tagging.fewer.than")
            else -> LanguageText.text(
                "report.traveled",
                mapOf(
                    "dist" to fmtDist(pathLen),
                    "span" to fmtDist(pathSpan),
                    "n" to "${path.size}",
                ),
            )
        }
        val lookupLine = when {
            !places.attempted -> LanguageText.text("report.off")
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
                LanguageText.text("ui.executive.summary"),
                execSummary(wifi, ble, named, hidden, randomized, pathSpan, pathLen, following, withYou, ownLikely, beaconsWithYou, wearablesWithYou, settings, places, win, mineHeard) + craftSentence(pictures),
                chart = classChart(inWin, fleets),
            ))
            add(DebriefSection(next(), LanguageText.text("ui.where.were"), whereYouWere(settings, path, pathLen, pathSpan, inWin, names, places, windowEnd, customNames, bookmarkedKeys)))
            if (cameraBody != null) {
                add(DebriefSection(next(), LanguageText.text("ui.cameras.on.path"), cameraBody))
            }
            val trustedText = trustedReport(
                trusted.filter { line -> line.at <= windowEnd && line.until >= windowStart },
                settings.demoMode,
            )
            if (trustedText.isNotEmpty()) {
                add(DebriefSection(next(), LanguageText.text("settings.topic.trusted"), trustedText))
            }
            if (aircraftBody.isNotEmpty()) {
                add(DebriefSection(next(), LanguageText.text("ui.aircraft.label"), aircraftBody))
            }
            observerNotesSection(inWin, customNames, observerNotes)?.let { body ->
                add(DebriefSection(next(), LanguageText.text("ui.observer.notes"), body))
            }
            markedMineSection(inWin, customNames, mineKeys, assessed)?.let { body ->
                add(DebriefSection(next(), LanguageText.text("ui.marked.mine.3"), body))
            }
            add(
                DebriefSection(
                    next(),
                    LanguageText.text("ui.tracking.assessment"),
                    trackingSection(settings, path, pathSpan, pathLen, following, wholeSit, beaconsWithYou, wearablesWithYou),
                ),
            )
            if (wholeSit.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        LanguageText.text("ui.possible.trackers"),
                        trackerCallout(
                            (LanguageText.text("ui.finder.tags.airtag.find") + " ") +
                                (LanguageText.text("ui.these.radios.stayed.gps") + " ") +
                                (LanguageText.text("ui.fieldwatch.cannot.tell.own") + " ") +
                                LanguageText.text("ui.account.each.mac.not"),
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
                        LanguageText.text("ui.possible.tail"),
                        trackerCallout(
                            (LanguageText.text("ui.finder.tags.were.not") + " ") +
                                (LanguageText.text("ui.can.mean.someone.started") + " ") +
                                LanguageText.text("ui.not.finding.not.identity"),
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
                        LanguageText.text("ui.retail.beacons"),
                        trackerCallout(
                            (LanguageText.text("ui.ibeacon.minew.estimote.kontakt") + " ") +
                                (LanguageText.text("ui.location.beacons.usually.fixtures") + " ") +
                                (LanguageText.text("ui.if.one.did.account") + " ") +
                                LanguageText.text("ui.not.same.find.my"),
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
                        LanguageText.text("ui.wearables.label"),
                        trackerCallout(
                            (LanguageText.text("ui.garmin.fitbit.oura.polar") + " ") +
                                (LanguageText.text("ui.watches.rings.usually.move") + " ") +
                                LanguageText.text("ui.they.not.typically.planted"),
                            wearablesWithYou,
                            customNames,
                        ),
                        alert = true,
                    ),
                )
            }
            if (!placeBody.isNullOrBlank()) {
                add(DebriefSection(next(), LanguageText.text("ui.seen.more.than.one"), placeBody))
            }
            add(DebriefSection(next(), LanguageText.text("ui.environment.label"), environment(wifi, ble, randomized, persistent, pathSpan, pathLen)))
            add(DebriefSection(
                next(),
                LanguageText.text("ui.networks.wi.fi.access"),
                networks.trimEnd(),
                chart = channelChart,
                after = networksAfter.trimEnd(),
            ))
            add(DebriefSection(next(), LanguageText.text("ui.bluetooth.le"), bleBody.trimEnd(), after = bleAfter.trimEnd()))
            add(DebriefSection(next(), LanguageText.text("ui.signature.hits"), sigBody.trimEnd(), chart = sigChart, after = sigAfter.trimEnd()))
            add(DebriefSection(next(), LanguageText.text("ui.persistence.label"), persistBody.trimEnd(), after = persistAfter.trimEnd()))
            if (attentionHits.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        LanguageText.text("chrome.extra.attention"),
                        buildString {
                            appendLine(LanguageText.text("ui.pattern.match.not.identity.2"))
                            attentionHits.forEach { (d, sig, note) ->
                                append("  · ${d.reportName(customNames)}  ${d.mac}  ${d.rssi} dBm  [$sig]")
                                if (d.key in mineKeys) append(("  " + LanguageText.text("ui.marked.mine.2")))
                                appendLine()
                                appendLine("    $note")
                            }
                        }.trimEnd(),
                        alert = true,
                    ),
                )
            }
            add(DebriefSection(next(), LanguageText.text("ui.anomalies.label"), anomalyBody))
            floodBody(floods, windowStart, windowEnd)?.let { body ->
                add(DebriefSection(next(), LanguageText.text("ui.flood.label"), body))
            }
            gnssBody(gnss, windowStart, windowEnd, onPath = gnssOnPath)?.let { body ->
                add(DebriefSection(next(), "GNSS", body, alert = true))
            }
            CellWatch.block(cells, windowStart, windowEnd, settings.demoMode)?.let { body ->
                add(DebriefSection(next(), LanguageText.text("settings.topic.cell"), body, alert = true))
            }
            add(DebriefSection(next(), LanguageText.text("ui.privacy.label"), privacy(wifi, ble, randomized, hidden, settings, places, pictures.isNotEmpty())))
            add(DebriefSection(next(), LanguageText.text("ui.recommended.actions"), actionBody))
        }

        val windowLine = if (win.sitName != null) {
            LanguageText.text(
                "report.window.sit",
                mapOf("name" to win.sitName, "start" to utc(windowStart), "end" to utc(windowEnd)),
            )
        } else {
            LanguageText.text(
                "report.window.last",
                mapOf("start" to utc(windowStart), "end" to utc(windowEnd)),
            )
        }
        val heading = if (win.sitName != null) {
            LanguageText.text("report.heading.sit", mapOf("name" to win.sitName))
        } else {
            LanguageText.text("ui.fieldwatch.field.debrief")
        }
        val meta = buildList {
            add(LanguageText.text("ui.generated.label") to "${utc(now)} UTC")
            if (win.sitName != null) add(LanguageText.text("ui.sit.label") to win.sitName)
            add(LanguageText.text("ui.window.label") to windowLine)
            add(LanguageText.text("ui.radios.label") to "${inWin.size}")
            add(LanguageText.text("ui.tool.label") to LanguageText.text("ui.fieldwatch.app.fieldwatch.stock"))
            add(
                LanguageText.text("ui.scan.label") to LanguageText.text(
                    "report.scan",
                    mapOf(
                        "level" to settings.intensity.name.lowercase(),
                        "stale" to "${settings.staleSec}",
                        "hold" to "${settings.decaySec}",
                    ),
                ),
            )
            add(
                LanguageText.text("ui.gps.tag") to
                    LanguageText.text(if (settings.tagLocation) "report.on" else "report.off"),
            )
            add(LanguageText.text("ui.distance.label") to distanceLine)
            add(LanguageText.text("ui.places.label") to lookupLine)
            add(
                LanguageText.text("ui.classification.label") to if (pictures.isNotEmpty()) {
                    LanguageText.text("ui.operationally.sensitive.neighbor.ssids")
                } else {
                    LanguageText.text("ui.operationally.sensitive.neighbor.ssids.2")
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
                        if (d.key in mineKeys) append(("  " + LanguageText.text("ui.marked.mine.2")))
                    },
                    note = note,
                )
            },
            heading = heading,
            pdfKicker = if (win.sitName != null) LanguageText.text("pdf.sit") else LanguageText.text("ui.field.debrief"),
            pdfTitle = if (win.sitName != null) {
                LanguageText.text("pdf.sit.name", mapOf("name" to win.sitName))
            } else {
                LanguageText.text("ui.field.debrief.2")
            },
            pathFigure = AircraftTrail.applyWalk(
                pathFigure(
                    win.sitName ?: LanguageText.text("ui.last.15.minutes"), path, inWin, fleets,
                    customNames, observerNotes, bookmarkedKeys, watchedFleetIds, mineKeys,
                    gnssPins, cameraTally?.cameras.orEmpty(),
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
        gnss: List<SitPathPlot.GnssPin> = emptyList(),
        pathCameras: List<FlockCamera> = emptyList(),
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
        val caption = buildString {
            append(LanguageText.text("report.north", mapOf("path" to path.lengthM()))).append(" ")
            append((LanguageText.text("ui.mac.alert.signature.alert") + " "))
            append((LanguageText.text("ui.decoded.latitude.longitude.last") + " "))
            append(LanguageText.text("ui.anything.else.strongest.hear"))
            if (gnss.isNotEmpty()) {
                append((" " + LanguageText.text("ui.red.diamond.where.phone") + " "))
                append((LanguageText.text("ui.path.key.has.time") + " "))
                append(LanguageText.text("ui.does.not.say.where"))
            }
        }
        return SitPathPlot.Figure(
            kicker = LanguageText.text("ui.operator.path"),
            tracks = listOf(SitPathPlot.FigureTrack(title, path)),
            dots = plot.points,
            lengthM = Geo.pathLengthM(path),
            spanM = Geo.spanM(path),
            caption = caption,
            gnss = gnss,
            cameras = pathCameras,
        )
    }

    private fun List<GpsSample>.lengthM(): String {
        val m = Geo.pathLengthM(this)
        return if (m >= 1000) {
            LanguageText.text("pdf.km", mapOf("n" to "%.1f".format(Locale.US, m / 1000)))
        } else {
            LanguageText.text("pdf.m", mapOf("n" to "${m.toInt()}"))
        }
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
        val names = fleets.associate { it.id to it.name }
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

        appendLine(LanguageText.text("ui.where.were.operator.gps"))
        appendLine("- Tag detections with GPS: ${if (settings.tagLocation) "on" else "off"}.")
        appendLine(
            (LanguageText.text("ui.online.place.names") + " ") +
                if (places.attempted) places.note
                else LanguageText.text("ui.off.settings.online.place"),
        )
        append(whereYouWere(settings, path, pathLen, pathSpan, inWin, names, places, windowEnd, customNames, bookmarkedKeys).trimEnd())
        appendLine()
        appendLine()
        if (path.size < 2 || pathSpan < MOVE_M) {
            appendLine(LanguageText.text("ui.following.test.insufficient.movement"))
            appendLine()
        }
        appendLine(LanguageText.text("ui.gps.co.travel"))
        appendLine(
            (LanguageText.text("ui.only.radios.stayed.operator") + " ") +
                (LanguageText.text("ui.house.tags.other.radios") + " ") +
                (LanguageText.text("ui.not.identity.find.my") + " ") +
                (LanguageText.text("ui.possible.tail.extra.gates") + " ") +
                (LanguageText.text("ui.2.3.gps.stamps") + " ") +
                (LanguageText.text("ui.fail.any.one.omit") + " ") +
                (LanguageText.text("ui.finder.tags.airtag.smarttag") + " ") +
                (LanguageText.text("ui.tracking.test.retail.beacons") + " ") +
                LanguageText.text("ui.they.do.not.typically"),
        )
        fun List<FollowHit>.open(): List<FollowHit> = filter { it.device.key !in mineKeys }
        val followingMd = follow.filter { it.verdict == Verdict.FOLLOWING }.open()
        val wholeSitMd = follow.filter {
            it.verdict == Verdict.OWN_LIKELY || it.verdict == Verdict.MOVED_WITH_YOU
        }.open()
        val beaconsOpen = beaconsMd.open()
        val wearablesOpen = wearablesMd.open()
        if (followingMd.isEmpty() && wholeSitMd.isEmpty() && beaconsOpen.isEmpty() && wearablesOpen.isEmpty()) {
            appendLine(LanguageText.text("ui.none.stayed.path"))
        } else {
            fun dump(title: String, rows: List<FollowHit>) {
                if (rows.isEmpty()) return
                appendLine()
                appendLine("### $title")
                rows.forEach { h ->
                    val d = h.device
                    appendLine(
                        "- ${h.label}  ${d.reportName(customNames)}  ${d.mac}  RSSI ${d.rssi} dBm " +
                            "(min ${d.rssiMin} / max ${d.rssiMax})  trail ${h.samples} fixes, span ${h.spanM.toInt()} m",
                    )
                    appendLine("  ${h.detail}")
                }
            }
            dump(
                LanguageText.text("ui.possible.trackers.finder.tags"),
                wholeSitMd,
            )
            dump(
                LanguageText.text("ui.possible.tail.finder.tags"),
                followingMd,
            )
            dump(
                LanguageText.text("ui.retail.beacons.ibeacon.minew"),
                beaconsOpen,
            )
            dump(
                LanguageText.text("ui.wearables.garmin.fitbit.oura"),
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
        val subjects = if (kind == TrackerMatch.Kind.FINDER) SmartTag.poolForFollow(trackers) else trackers
        return subjects.map { d ->
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
                    Verdict.INSUFFICIENT to LanguageText.text("report.path.short", mapOf("m" to "${opSpan.toInt()}"))
                trail.size < 2 ->
                    Verdict.INSUFFICIENT to LanguageText.text("ui.heard.but.not.two")
                onBody && ownHere ->
                    Verdict.OWN_LIKELY to onBodyLine(kind, d, trail.size)
                cover && ownHere && d.rssiMax >= ON_BODY_MAX ->
                    Verdict.OWN_LIKELY to
                        LanguageText.text(
                            "report.heard.loud",
                            mapOf(
                                "trail" to "${trailLen.toInt()}",
                                "path" to "${opLen.toInt()}",
                                "rssi" to "${d.rssiMax}",
                            ),
                        ) + " " + withYouNote(kind, d)
                span < MOVE_M * 0.6 ->
                    Verdict.STATIONARY to LanguageText.text(
                        "report.one.place",
                        mapOf("span" to "${span.toInt()}", "moved" to "${opSpan.toInt()}"),
                    )
                presentAtStart && stillHere && d.rssiMax >= ON_BODY_MAX ->
                    Verdict.OWN_LIKELY to
                        LanguageText.text(
                            "report.moved.strong",
                            mapOf("span" to "${span.toInt()}", "rssi" to "${d.rssi}"),
                        ) + " " + withYouNote(kind, d)
                presentAtStart && stillHere ->
                    Verdict.MOVED_WITH_YOU to
                        LanguageText.text(
                            "report.moved.here",
                            mapOf("span" to "${span.toInt()}", "fixes" to "${trail.size}"),
                        ) + " " + withYouNote(kind, d)
                !presentAtStart && span >= MOVE_M && trail.size >= 3 ->
                    possibleTail(trail, span, opLen, kind, d)
                else ->
                    Verdict.STATIONARY to LanguageText.text(
                        "report.not.loud",
                        mapOf("span" to "${span.toInt()}", "fixes" to "${trail.size}"),
                    )
            }
            FollowHit(d, label, verdict, detail, span, trail.size)
        }.sortedBy { it.verdict.ordinal }
    }

    private fun onBodyLine(kind: TrackerMatch.Kind, d: Sighting, stamps: Int): String {
        val loud = LanguageText.text(
            "report.stayed.loud",
            mapOf("high" to "${d.rssiMax}", "low" to "${d.rssiMin}", "stamps" to "$stamps"),
        ) + " "
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
        return LanguageText.text("report.decoded", mapOf("labels" to labels.joinToString(", ")))
    }

    private fun withYouNote(kind: TrackerMatch.Kind, device: Sighting): String {
        val decoded = liveDecodeSentence(device)
        val base = when (kind) {
            TrackerMatch.Kind.FINDER ->
                LanguageText.text("ui.whole.sit.yours.planted")
            TrackerMatch.Kind.BEACON ->
                LanguageText.text("ui.location.beacons.do.not")
            TrackerMatch.Kind.WEARABLE ->
                LanguageText.text("ui.typical.watch.ring.companion")
        }
        return when {
            decoded != null -> "$base $decoded"
            kind == TrackerMatch.Kind.FINDER ->
                "$base " + LanguageText.text("report.findmy.rotate")
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
                Verdict.STATIONARY to LanguageText.text(
                    "report.walked.off",
                    mapOf("last" to "$last", "peak" to "$peak", "fade" to "$fade"),
                )
            loudN < loudNeed ->
                Verdict.STATIONARY to LanguageText.text(
                    "report.passby",
                    mapOf(
                        "span" to "${span.toInt()}",
                        "loud" to "$loudN",
                        "total" to "${trail.size}",
                    ),
                )
            trailLen < coverNeed ->
                Verdict.STATIONARY to LanguageText.text(
                    "report.arc",
                    mapOf(
                        "heard" to "${trailLen.toInt()}",
                        "path" to "${opLen.toInt()}",
                        "pct" to "$coverPct",
                    ),
                )
            else -> {
                val stats = LanguageText.text(
                    "report.followed",
                    mapOf(
                        "span" to "${span.toInt()}",
                        "heard" to "${trailLen.toInt()}",
                        "path" to "${opLen.toInt()}",
                        "pct" to "$coverPct",
                        "loud" to "$loudN",
                        "total" to "${trail.size}",
                    ),
                ) + " "
                val note = when (kind) {
                    TrackerMatch.Kind.FINDER ->
                        LanguageText.text("ui.treat.possible.tail.until")
                    TrackerMatch.Kind.BEACON ->
                        LanguageText.text("ui.unusual.retail.location.beacon")
                    TrackerMatch.Kind.WEARABLE ->
                        LanguageText.text("ui.typical.watch.joined.sit")
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
            LanguageText.text("report.in.sit", mapOf("name" to window.sitName))
        } else {
            LanguageText.text("ui.last.15.minutes.3")
        }
        append(whenPhrase).append(" ")
        append(
            LanguageText.text(
                "report.heard.counts",
                mapOf("wifi" to "${wifi.size}", "ble" to "${ble.size}"),
            ),
        )
        append(" ")
        append(LanguageText.text("report.matched.open", mapOf("named" to "${named.size}")))
        if (mineHeard > 0) {
            append(", ")
            append(LanguageText.text("report.marked.mine", mapOf("n" to "$mineHeard")))
        }
        append(", ")
        append(
            LanguageText.text(
                "report.hidden.rand",
                mapOf("hidden" to "${hidden.size}", "rand" to "$randomized"),
            ),
        )
        append(" ")
        if (settings.tagLocation && pathLen > 0) {
            append(
                LanguageText.text(
                    "report.distance",
                    mapOf("dist" to fmtDist(pathLen), "span" to fmtDist(pathSpan)),
                ),
            )
            append(" ")
        }
        if (places.namesByCell.isNotEmpty()) {
            append(LanguageText.text("report.stops.area", mapOf("area" to places.areaLine())))
            append(" ")
        } else if (places.attempted && settings.tagLocation) {
            append("${places.note} ")
        }
        val wholeSit = ownLikely + withYou
        when {
            following.isNotEmpty() || wholeSit.isNotEmpty() -> {
                append((LanguageText.text("ui.tracking.note") + " "))
                if (wholeSit.isNotEmpty()) {
                    append(LanguageText.text("report.finder.whole", mapOf("n" to "${wholeSit.size}")))
                    append(" ")
                    append(wholeSit.joinToString { trackId(it) })
                    append(". ")
                }
                if (following.isNotEmpty()) {
                    append(LanguageText.text("report.possible.tails", mapOf("n" to "${following.size}")))
                    append(" ")
                    append(following.joinToString { trackId(it) })
                    append(". ")
                }
                append((LanguageText.text("ui.account.every.mac.fieldwatch") + " "))
            }
            !settings.tagLocation -> {
                append((LanguageText.text("ui.gps.tagging.off.so") + " "))
            }
            pathSpan < MOVE_M -> {
                append(LanguageText.text("report.gps.short", mapOf("m" to "${pathSpan.toInt()}"))).append(" ")
            }
            else -> append((LanguageText.text("ui.no.finder.tag.clearly") + " "))
        }
        if (beaconsWithYou.isNotEmpty()) {
            append((LanguageText.text("ui.retail.beacon.s.also") + " "))
            append(beaconsWithYou.joinToString { "${it.label} ${it.device.mac}" })
            append(". ")
        }
        if (wearablesWithYou.isNotEmpty()) {
            append((LanguageText.text("ui.wearable.s.stayed.path") + " "))
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
            appendLine(LanguageText.text("ui.gps.tagging.off.fieldwatch"))
            appendLine(LanguageText.text("ui.turn.settings.tag.detections.2"))
            return@buildString
        }
        appendLine("Overall distance traveled: ${fmtDist(pathLen)} along the GPS path (${path.size} samples). Straight-line span ${fmtDist(pathSpan)}.")
        appendLine(LanguageText.text("ui.co.travel.split.class"))
        if (path.size < 2 || pathSpan < MOVE_M) {
            appendLine(LanguageText.text("ui.insufficient.movement.distinguish.radio"))
            return@buildString
        }
        if (following.isEmpty() && wholeSit.isEmpty() && beaconsWithYou.isEmpty() && wearablesWithYou.isEmpty()) {
            appendLine(LanguageText.text("ui.no.finder.tag.retail"))
        } else {
            appendLine(LanguageText.text("ui.callouts.below.only.radios"))
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
            appendLine(LanguageText.text("ui.radios.marked.mine.heard"))
            hits.sortedWith(
                compareByDescending<Sighting> { it.rssi }.thenBy { it.mac },
            ).forEach { d ->
                val kind = if (d.kind == RadioKind.WIFI) "WIFI" else "BLE"
                appendLine("  · $kind  ${d.mac}  ${d.reportName(customNames)}  ${d.rssi} dBm")
                val verdict = assessed.firstOrNull { it.device.key == d.key }?.verdict
                val line = when (verdict) {
                    Verdict.FOLLOWING ->
                        LanguageText.text("ui.marked.mine.first.heard")
                    Verdict.OWN_LIKELY, Verdict.MOVED_WITH_YOU ->
                        LanguageText.text("ui.marked.mine.whole.sit")
                    else -> LanguageText.text("ui.marked.mine.4")
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
            appendLine(LanguageText.text("ui.captions.radios.heard.window"))
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
            appendLine("    ${d.reportName(customNames)}  ${d.mac}  RSSI ${d.rssi} dBm (min ${d.rssiMin} / max ${d.rssiMax})")
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
        appendLine(LanguageText.text("ui.phone.gps.hear.time"))
        if (!settings.tagLocation) {
            appendLine(LanguageText.text("ui.gps.tagging.off.turn"))
            return@buildString
        }
        if (path.isEmpty()) {
            appendLine(LanguageText.text("ui.gps.tagging.but.window"))
            return@buildString
        }
        appendLine(
            LanguageText.text(
                "report.overall",
                mapOf("dist" to fmtDist(pathLen), "span" to fmtDist(pathSpan), "n" to "${path.size}"),
            ),
        )
        if (places.attempted) {
            appendLine(places.note)
            appendLine(LanguageText.text("ui.street.names.approximate.do"))
        }
        val legs = Geo.legs(path, now = now)
        if (legs.isEmpty()) {
            appendLine(LanguageText.text("ui.no.path.legs"))
            return@buildString
        }
        val stopNames = legs.filter { it.stay }.mapNotNull { places.nameNear(it.lat, it.lon) }
        if (stopNames.isNotEmpty()) {
            appendLine((LanguageText.text("ui.stops.label") + " ") + stopNames.joinToString(" → "))
        }
        var stayN = 0
        legs.forEachIndexed { i, leg ->
            if (leg.stay) {
                stayN++
                appendLine()
                appendLine(
                    "${i + 1}. ${LanguageText.text("report.stay.word")}  " +
                        "${clock(leg.startAt)}–${clock(leg.endAt)} UTC  (${fmtDur(leg.durationMs)})",
                )
                appendLine("   ${placeAndGps(leg.lat, leg.lon, places)}")
                val here = devices.filter { heardAt(it, leg) }
                val aps = here.count { it.kind == RadioKind.WIFI }
                val ble = here.count { it.kind == RadioKind.BLE }
                val sigs = here.flatMap { d -> d.fleetIds.map { names[it] ?: it } }.distinct()
                append(
                    "   " + LanguageText.text("report.heard.here", mapOf("aps" to "$aps", "ble" to "$ble")),
                )
                if (sigs.isNotEmpty()) append("  ·  ${sigs.take(6).joinToString(", ")}")
                appendLine()
                here.filter { inventoryKeep(it, settings, bookmarkedKeys) }.sortedByDescending { it.rssi }.take(4).forEach { d ->
                    appendLine("   · ${d.reportName(customNames)}  ${d.mac}  ${d.rssi} dBm")
                }
                if (here.isEmpty()) appendLine(("   " + LanguageText.text("ui.no.gps.stamped.radios")))
            } else {
                appendLine()
                appendLine(
                    "${i + 1}. ${LanguageText.text("report.transit.word")}  " +
                        "${clock(leg.startAt)}–${clock(leg.endAt)} UTC  " +
                        LanguageText.text("report.along.track", mapOf("dist" to fmtDist(leg.pathM))),
                )
                appendLine("   ${placeAndGps(leg.lat, leg.lon, places)}")
                appendLine("   → ${placeAndGps(leg.endLat, leg.endLon, places)}")
            }
        }
        val stays = legs.count { it.stay }
        if (stays == 1 && pathSpan < MOVE_M) {
            appendLine()
            appendLine(LanguageText.text("ui.one.stay.did.not"))
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
            pathSpan > 200 && ap in 1..25 -> LanguageText.text("ui.motion.walk.vehicle.through")
            ap <= 4 && ble.size < 30 && persistAp >= 1 -> LanguageText.text("ui.likely.dwelling.small.office")
            ap >= 15 && randomized >= 40 -> LanguageText.text("ui.dense.public.retail.street")
            ap >= 8 && persistAp >= 4 -> LanguageText.text("ui.likely.building.standing.infrastructure")
            else -> LanguageText.text("ui.mixed.under.sampled.environment")
        }
        return "$guess  " + LanguageText.text(
            "report.env.counts",
            mapOf(
                "aps" to "$ap",
                "ble" to "${ble.size}",
                "persist" to "$persistAp",
                "dist" to fmtDist(pathLen),
                "span" to fmtDist(pathSpan),
            ),
        )
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
        if (d.channel != 0) {
            append("  ").append(LanguageText.text("report.ch", mapOf("n" to "${d.channel}")))
        }
        if (d.hiddenSsid) append("  ").append(LanguageText.text("report.hidden"))
        if (d.fleetIds.isNotEmpty()) append("  ").append(d.fleetIds.joinToString("+") { names[it] ?: it })
        append("  ").append(LanguageText.text("report.dwell", mapOf("time" to fmtDur(dwellMs(d, from, now)))))
    }

    private fun bleLine(
        d: Sighting,
        names: Map<String, String>,
        from: Long,
        now: Long,
        customNames: Map<String, String> = emptyMap(),
    ): String = buildString {
        append(d.reportName(customNames)).append("  ").append(d.mac)
        if (d.randomized) append("  RAND")
        append("  ").append(d.rssi).append(" dBm")
        if (d.fleetIds.isNotEmpty()) append("  ").append(d.fleetIds.joinToString("+") { names[it] ?: it })
        append("  ").append(LanguageText.text("report.dwell", mapOf("time" to fmtDur(dwellMs(d, from, now)))))
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
            ReportBar(kind.label(), n)
        }.toMutableList()
        if (unmatched > 0) rows += ReportBar(LanguageText.text("ui.unmatched.label"), unmatched)
        if (rows.isEmpty()) return null
        return ReportChart(
            rows = rows,
            caption = if (multi) LanguageText.text("ui.radio.two.classes.counts") else "",
        )
    }

    private fun signatureChart(named: List<Sighting>, names: Map<String, String>): ReportChart? {
        if (named.isEmpty()) return null
        val counts = linkedMapOf<String, Int>()
        for (d in named) {
            val sigs = d.fleetIds.map { names[it] ?: it }.filter { it.isNotBlank() }.distinct()
            val key = if (sigs.isEmpty()) LanguageText.text("ui.unmatched.label") else sigs.joinToString(" + ")
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
            appendLine(LanguageText.text("ui.named.marked"))
            rows.sortedByDescending { it.rssi }.forEach { d ->
                appendLine("  · ${wifiLine(d, names, from, now, customNames)}")
                if (d.key in mineKeys) appendLine(("    " + LanguageText.text("ui.marked.mine")))
                d.attentionNotes(fleets).forEach { (sig, note) ->
                    appendLine("    " + LanguageText.text("report.attention", mapOf("name" to sig, "note" to note)))
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
            appendLine(LanguageText.text("ui.named.marked"))
            rows.sortedByDescending { it.rssi }.forEach { d ->
                appendLine("  · ${bleLine(d, names, from, now, customNames)}")
                if (d.key in mineKeys) appendLine(("    " + LanguageText.text("ui.marked.mine")))
            }
        }.trimEnd()
    }

    private fun gnssBody(marks: List<GnssMark>, start: Long, end: Long, onPath: Boolean): String? {
        val rows = marks.filter { it.at in start..end || (it.endedAt > 0L && it.endedAt in start..end) }
            .sortedBy { it.at }
        if (rows.isEmpty()) return null
        return buildString {
            appendLine(GnssCopy.INTRO)
            appendLine()
            appendLine(GnssCopy.ROUTER)
            if (rows.any { it.kind == "spoofing" }) {
                appendLine()
                appendLine(GnssCopy.SPOOF_LIMIT)
            }
            appendLine()
            rows.forEach { appendLine(it.reportLine()) }
            if (onPath) {
                appendLine()
                appendLine(LanguageText.text("ui.path.map.marks.where"))
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
                (LanguageText.text("ui.google.fast.pair.pairing") + " ") +
                    pairing.joinToString { "${it.reportName(customNames)} ${it.mac}" }
            } else {
                LanguageText.text("report.fast.pair.count", mapOf("n" to "${pairing.size}"))
            }
        }
        val loudUnknown = devices.filter {
            it.rssi >= -50 && it.fleetIds.isEmpty() && it.name.isBlank() &&
                inventoryKeep(it, settings, bookmarkedKeys)
        }
        if (loudUnknown.isNotEmpty()) {
            out += (LanguageText.text("ui.very.strong.unnamed.radios") + " ") +
                loudUnknown.take(8).joinToString { "${it.mac} ${it.rssi} dBm" }
        }
        val rand = devices.count { it.kind == RadioKind.BLE && it.randomized }
        if (rand >= 20) {
            out += LanguageText.text("report.rand.high", mapOf("n" to "$rand"))
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
        append(
            LanguageText.text(
                "report.privacy.open",
                mapOf("wifi" to "${wifi.size}", "ble" to "${ble.size}", "rand" to "$randomized"),
            ),
        )
        append(" ")
        if (hidden.isNotEmpty()) append((LanguageText.text("ui.hidden.ssids.still.beacon") + " "))
        if (settings.tagLocation) append((LanguageText.text("ui.debrief.includes.operator.gps") + " "))
        if (includeAircraft) {
            append((LanguageText.text("ui.debrief.includes.advertised.aircraft") + " "))
        }
        if (places.attempted && places.available) {
            append((LanguageText.text("ui.street.names.came.phone") + " "))
        }
        append(LanguageText.text("ui.do.not.share.file"))
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
            add(
                LanguageText.text(
                    "report.action.tail",
                    mapOf("who" to following.joinToString { trackId(it) }),
                ),
            )
        }
        if (ownLikely.isNotEmpty() || withYou.isNotEmpty()) {
            add(
                LanguageText.text(
                    "report.action.with",
                    mapOf("who" to (ownLikely + withYou).joinToString { trackId(it) }),
                ) + " " + LanguageText.text("ui.could.yours.planted.car"),
            )
        }
        if (beaconsWithYou.isNotEmpty()) {
            add(
                (LanguageText.text("ui.retail.beacons.unusual.fixtures") + " ") +
                    beaconsWithYou.joinToString { it.label + " " + it.device.mac } +
                    LanguageText.text("ui.account.test.tag.badge"),
            )
        }
        if (wearablesWithYou.isNotEmpty()) {
            add(
                (LanguageText.text("ui.wearables.usually.own.kit") + " ") +
                    wearablesWithYou.joinToString { it.label + " " + it.device.mac } +
                    ".",
            )
        }
        if (!settings.tagLocation) add(LanguageText.text("ui.enable.tag.detections.gps"))
        else if (pathSpan < MOVE_M) add(LanguageText.text("ui.walk.farther.50.m"))
        add(LanguageText.text("ui.use.live.pause.inspect"))
        add(LanguageText.text("ui.station.side.wi.fi"))
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
                append((" " + LanguageText.text("ui.retail.beacon.s.also.2") + " "))
                append(beaconsWithYou.joinToString { it.label + " (" + it.device.mac + ")" })
                append(".")
            }
            if (wearablesWithYou.isNotEmpty()) {
                append((" " + LanguageText.text("ui.wearable.s.path.usually") + " "))
                append(wearablesWithYou.joinToString { it.label + " (" + it.device.mac + ")" })
                append(".")
            }
        }
        val core = when {
            following.isNotEmpty() && (ownLikely.isNotEmpty() || withYou.isNotEmpty()) ->
                LanguageText.text(
                    "report.take.both",
                    mapOf(
                        "tails" to following.joinToString { trackId(it) },
                        "with" to (ownLikely + withYou).joinToString { trackId(it) },
                    ),
                )
            following.isNotEmpty() ->
                LanguageText.text("report.take.tail", mapOf("who" to following.joinToString { trackId(it) }))
            !settings.tagLocation ->
                LanguageText.text("ui.turn.gps.tagging.walk")
            pathSpan < MOVE_M ->
                LanguageText.text("report.take.short", mapOf("m" to "${pathSpan.toInt()}"))
            ownLikely.isNotEmpty() || withYou.isNotEmpty() ->
                LanguageText.text(
                    "report.take.with",
                    mapOf("who" to (ownLikely + withYou).joinToString { trackId(it) }),
                )
            beaconsWithYou.isNotEmpty() || wearablesWithYou.isNotEmpty() ->
                LanguageText.text("ui.no.finder.tag.stayed")
            named.isEmpty() ->
                LanguageText.text("ui.no.signature.hits.no")
            else ->
                LanguageText.text("ui.no.finder.tag.retail.2")
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
        val more = if (pictures.size > 3) {
            " " + LanguageText.text("candidates.more", mapOf("n" to "${pictures.size - 3}"))
        } else {
            ""
        }
        return " " + LanguageText.text("report.advertised", mapOf("bits" to "$bits$more"))
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

    /** Name, reason, time, and address. No coordinates. */
    private fun trustedReport(lines: List<TrustedSitLine>, demo: Boolean): String =
        lines.joinToString("\n\n") { line ->
            buildString {
                append(line.ssid)
                if (line.label.isNotBlank() && line.label != line.ssid) {
                    append(" (")
                    append(line.label)
                    append(')')
                }
                append('\n')
                append(TrustedNets.reasonLine(line.reasons))
                append('\n')
                append(utc(line.at))
                append('\n')
                append(MacUtil.screenMac(line.bssid, demo))
            }
        }

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
            LanguageText.text("report.place.named", mapOf("name" to name, "gps" to gps))
        } else if (places.attempted) {
            LanguageText.text("report.place.none", mapOf("gps" to gps))
        } else {
            LanguageText.text("report.place.phone", mapOf("gps" to gps))
        }
    }

    private fun fmtDur(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val m = s / 60
        val r = s % 60
        return if (m >= 60) {
            LanguageText.text("report.dur.hm", mapOf("h" to "${m / 60}", "m" to "${m % 60}"))
        } else if (m > 0) {
            LanguageText.text("report.dur.ms", mapOf("m" to "$m", "s" to "$r"))
        } else {
            LanguageText.text("report.dur.s", mapOf("s" to "$r"))
        }
    }
}
