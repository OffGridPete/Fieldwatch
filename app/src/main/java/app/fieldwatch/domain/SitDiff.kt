package app.fieldwatch.domain

import app.fieldwatch.i18n.displayAttentionNotes
import app.fieldwatch.i18n.displayName
import app.fieldwatch.i18n.displayLiveDecode

import app.fieldwatch.i18n.localized

/** Two sit windows, keyed kind + MAC. BLE rotation is a new row. */
object SitDiff {
    data class Radio(
        val key: String,
        val kind: RadioKind,
        val mac: String,
        val name: String,
        val extraAttention: Boolean,
        val named: Boolean,
        val bookmarked: Boolean = false,
        val fleetNames: List<String>,
        val fleetIds: List<String> = emptyList(),
        val randomized: Boolean = false,
        val lat: Double? = null,
        val lon: Double? = null,
        val observerNotes: String = "",
        val gpsTrail: List<GpsSample> = emptyList(),
        val firstSeen: Long = 0L,
        val lastSeen: Long = 0L,
        val liveDecode: List<LiveDecodeChip> = emptyList(),
        val payloadLat: Double? = null,
        val payloadLon: Double? = null,
        val payloadAlt: Double? = null,
        val payloadHeading: Double? = null,
        val payloadSpeed: Double? = null,
        val payloadOpLat: Double? = null,
        val payloadOpLon: Double? = null,
        val payloadUasId: String = "",
        val payloadTrail: List<PayloadFix> = emptyList(),
        val payloadAircraft: String = "",
        val mine: Boolean = false,
        val displayFleetNames: List<String>? = null,
        val displayDecode: List<LiveDecodeChip>? = null,
    ) {
        val renderedFleetNames: List<String> get() = displayFleetNames ?: fleetNames
        val renderedDecode: List<LiveDecodeChip> get() = displayDecode ?: liveDecode
    }

    data class Side(
        val name: String,
        val ram: Boolean,
        val radios: List<Radio>,
        val path: List<GpsSample> = emptyList(),
        val floods: List<FloodBurst> = emptyList(),
    ) {
        val keys: Set<String> get() = radios.map { it.key }.toSet()

        /** The sit file still holds these radios. Reports leave the counted addresses out. */
        fun withoutFloodRadios(): Side {
            val aside = FloodBurst.keysOf(floods)
            if (aside.isEmpty()) return this
            return copy(radios = radios.filter { it.key !in aside })
        }
    }

    fun secondSitChoices(
        closed: List<SitSummary>,
        thisSavedId: String?,
    ): List<SitSummary> = closed.filter { it.id != thisSavedId }

    fun defaultSecondSitId(
        closed: List<SitSummary>,
        thisSavedId: String?,
    ): String? = secondSitChoices(closed, thisSavedId).firstOrNull()?.id

    fun thisSavedId(open: SitSummary?, selectedId: String?): String? =
        if (open != null) null else selectedId

    fun fromSighting(
        device: Sighting,
        fleets: List<Fleet>,
        customNames: Map<String, String>,
        observerNotes: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        mineKeys: Set<String> = emptySet(),
    ): Radio = Radio(
        key = device.key,
        kind = device.kind,
        mac = device.mac,
        name = device.reportName(customNames),
        extraAttention = device.displayAttentionNotes(fleets).isNotEmpty(),
        named = device.key in customNames,
        bookmarked = device.key in bookmarkedKeys,
        fleetNames = device.fleetIds.map { id -> fleets.firstOrNull { it.id == id }?.name ?: id },
        displayFleetNames = device.fleetIds.map { id -> fleets.firstOrNull { it.id == id }?.displayName() ?: id },
        displayDecode = device.displayLiveDecode(fleets),
        fleetIds = device.fleetIds.toList(),
        randomized = device.randomized,
        lat = SitPathPlot.loudestFix(device)?.lat ?: device.latitude,
        lon = SitPathPlot.loudestFix(device)?.lon ?: device.longitude,
        observerNotes = observerNotes[device.key].orEmpty(),
        gpsTrail = device.gpsTrail,
        firstSeen = device.firstSeen,
        lastSeen = device.lastSeen,
        liveDecode = device.liveDecode,
        payloadLat = device.payloadLat,
        payloadLon = device.payloadLon,
        payloadAlt = device.payloadAlt,
        payloadHeading = device.payloadHeading,
        payloadSpeed = device.payloadSpeed,
        payloadOpLat = device.payloadOpLat,
        payloadOpLon = device.payloadOpLon,
        payloadUasId = device.payloadUasId?.trim().orEmpty(),
        payloadTrail = device.payloadTrail,
        payloadAircraft = device.payloadAircraft?.trim().orEmpty(),
        mine = device.key in mineKeys,
    )

    fun fromSitRadio(
        row: SitRadio,
        fleets: List<Fleet>,
        customNames: Map<String, String>,
        observerNotes: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        mineKeys: Set<String> = emptySet(),
    ): Radio = Radio(
        key = row.key,
        kind = row.kind,
        mac = row.mac,
        name = customNames[row.key]?.trim()?.takeIf { it.isNotEmpty() } ?: row.name.ifBlank { row.mac },
        extraAttention = row.extraAttention,
        named = row.key in customNames,
        bookmarked = row.key in bookmarkedKeys,
        fleetNames = row.fleetIds.map { id -> fleets.firstOrNull { it.id == id }?.name ?: id },
        displayFleetNames = row.fleetIds.map { id -> fleets.firstOrNull { it.id == id }?.displayName() ?: id },
        displayDecode = row.toSighting().displayLiveDecode(fleets),
        fleetIds = row.fleetIds.toList(),
        randomized = row.randomized,
        lat = SitPathPlot.loudestFix(row.gpsTrail)?.lat,
        lon = SitPathPlot.loudestFix(row.gpsTrail)?.lon,
        observerNotes = observerNotes[row.key].orEmpty(),
        gpsTrail = row.gpsTrail,
        firstSeen = row.firstSeen,
        lastSeen = row.lastSeen,
        liveDecode = row.liveDecode,
        payloadLat = row.payloadLat,
        payloadLon = row.payloadLon,
        payloadAlt = row.payloadAlt,
        payloadHeading = row.payloadHeading,
        payloadSpeed = row.payloadSpeed,
        payloadOpLat = row.payloadOpLat,
        payloadOpLon = row.payloadOpLon,
        payloadUasId = row.payloadUasId?.trim().orEmpty(),
        payloadTrail = row.payloadTrail,
        payloadAircraft = row.payloadAircraft?.trim().orEmpty(),
        mine = row.key in mineKeys,
    )

    fun report(
        thisSit: Side,
        second: Side,
        demoMode: Boolean,
        showAllRadios: Boolean = false,
    ): String {
        val macs = (thisSit.radios + second.radios).map { it.mac }
        return document(thisSit, second, showAllRadios = showAllRadios)
            .withDemoMacs(macs, demoMode)
            .toPlainText()
    }

    /** Same shape as Debrief so Compare (PDF) uses the Debrief letter layout. */
    fun document(
        thisSit: Side,
        second: Side,
        watchedFleetIds: Set<String> = emptySet(),
        showAllRadios: Boolean = false,
    ): DebriefDoc {
        val thisSit = thisSit.withoutFloodRadios()
        val second = second.withoutFloodRadios()
        val thisKeys = thisSit.keys
        val secondKeys = second.keys
        val byKey = (thisSit.radios + second.radios).associateBy { it.key }
        val onlyThis = thisKeys.minus(secondKeys)
        val onlySecond = secondKeys.minus(thisKeys)
        val both = thisKeys.intersect(secondKeys)
        val ramNote = if (thisSit.ram || second.ram) {
            localized("sit_diff_last_15_minutes_is_the_live_ram", "Last 15 minutes is the Live RAM set (about 400 radios). A named sit keeps up to %1\$s. Counts are not the same net.", Sit.RADIO_CAP)
        } else {
            null
        }
        val extraHits = exclusiveExtra(onlyThis, onlySecond, byKey)
        val sections = ArrayList<DebriefSection>()
        var n = 1
        fun next() = n++.toString()
        sections += DebriefSection(
            next(),
            localized("sit_diff_windows", "Windows"),
            buildString {
                append(sideBlock(localized("sit_diff_this_sit", "This sit"), thisSit))
                append(sideBlock(localized("sit_diff_second_sit", "Second sit"), second))
                if (ramNote != null) {
                    appendLine(ramNote)
                    appendLine()
                }
                append(localized("sit_diff_radios_this_phone_heard_kind_mac_ble", "Radios this phone heard. Kind + MAC. BLE rotation is a new row. Not a radio fix."))
            },
            chart = presenceChart(onlyThis, onlySecond, both, byKey),
        )
        val thisCraft = AircraftTrail.pictures(thisSit.radios.mapNotNull { it.toCraftSource() }, thisSit.path)
        val secondCraft = AircraftTrail.pictures(second.radios.mapNotNull { it.toCraftSource() }, second.path)
        val aircraftBody = AircraftTrail.compareBody(thisSit.name, thisCraft, second.name, secondCraft)
        if (aircraftBody.isNotEmpty()) {
            sections += DebriefSection(next(), localized("sit_diff_aircraft", "Aircraft"), aircraftBody)
        }
        observerNotesSection(thisSit, second)?.let { body ->
            sections += DebriefSection(next(), localized("sit_diff_observer_notes", "Observer notes"), body)
        }
        markedMineSection(thisSit, second)?.let { body ->
            sections += DebriefSection(next(), localized("sit_diff_marked_mine", "Marked mine"), body)
        }
        if (extraHits.isNotEmpty()) {
            sections += DebriefSection(
                next(),
                localized("sit_diff_extra_attention", "Extra attention"),
                extraHits.joinToString("\n") { "${it.radioLabel}\n${it.note}" },
                alert = true,
            )
        }
        floodSection(thisSit, second)?.let { body ->
            sections += DebriefSection(next(), localized("sit_diff_flood", "Flood"), body)
        }
        sections += rosterSection(next(), localized("sit_diff_only_in_this_sit", "Only in this sit (%1\$s)", onlyThis.size), onlyThis, byKey, showAllRadios)
        sections += rosterSection(next(), localized("sit_diff_only_in_second_sit", "Only in second sit (%1\$s)", onlySecond.size), onlySecond, byKey, showAllRadios)
        sections += bothSection(next(), localized("sit_diff_in_both", "In both (%1\$s)", both.size), both, thisSit, second, byKey, showAllRadios)
        val meta = buildList {
            add(localized("sit_diff_this_sit", "This sit") to thisSit.name)
            add(localized("sit_diff_second_sit", "Second sit") to second.name)
            add(localized("sit_diff_this_radios", "This radios") to thisSit.radios.size.toString())
            add(localized("sit_diff_second_radios", "Second radios") to second.radios.size.toString())
            if (ramNote != null) add(localized("sit_diff_caps", "Caps") to localized("sit_diff_ram_400_vs_sit", "RAM ~400 vs sit %1\$s", Sit.RADIO_CAP))
        }
        return DebriefDoc(
            generatedUtc = "",
            windowLine = localized("sit_diff_vs", "%1\$s vs %2\$s", thisSit.name, second.name),
            meta = meta,
            disclaimer = FieldwatchDisclaimer.compare(),
            trackingAlert = extraHits.isNotEmpty(),
            takeaway = compareTakeaway(onlyThis.size, onlySecond.size, both.size, mineCount(thisSit, second)),
            sections = sections,
            extraAttention = extraHits,
            heading = localized("sit_diff_fieldwatch_sit_compare", "FIELDWATCH SIT COMPARE"),
            pdfKicker = localized("sit_diff_sit_compare_2", "SIT COMPARE"),
            pdfTitle = localized("sit_diff_sit_compare", "Sit compare"),
            pathFigure = AircraftTrail.applyWalk(
                AircraftTrail.applyWalk(
                    pathFigure(thisSit, second, watchedFleetIds),
                    thisCraft,
                    secondary = false,
                ),
                secondCraft,
                secondary = true,
            ),
            extraFigures = AircraftTrail.compareOwnFigures(thisCraft, secondCraft),
        )
    }

    private fun pathFigure(
        thisSit: Side,
        second: Side,
        watchedFleetIds: Set<String>,
    ): SitPathPlot.Figure? {
        val tracks = listOfNotNull(
            Geo.despikePath(thisSit.path).takeIf { it.size >= 2 }?.let {
                SitPathPlot.FigureTrack(thisSit.name, it, secondary = false)
            },
            Geo.despikePath(second.path).takeIf { it.size >= 2 }?.let {
                SitPathPlot.FigureTrack(second.name, it, secondary = true)
            },
        )
        if (tracks.isEmpty()) return null
        val pinNote = localized("sit_diff_a_mac_alert_or_a_signature_alert", "A MAC alert or a signature alert is drawn once. A decoded latitude and longitude is the last advertised position. Anything else is the strongest hear. A number is that place (Path key).")
        val points = (thisSit.radios + second.radios)
            .filter { it.bookmarked || it.fleetIds.any { id -> id in watchedFleetIds } }
            .distinctBy { it.key }
            .mapNotNull { r ->
                val advertised = r.advertisedCoord()
                val pin = advertised ?: r.hearCoord() ?: return@mapNotNull null
                val notes = RadioBookmarks.pathNote(r.bookmarked, r.observerNotes, r.mine)
                val label = r.name.ifBlank { r.mac }
                val kept = r.payloadTrail.lastOrNull { PayloadLocation.validCoord(it.lat, it.lon) }
                SitPathPlot.Dot(
                    key = r.key,
                    lat = pin.first,
                    lon = pin.second,
                    label = label,
                    extraAttention = r.extraAttention,
                    named = r.bookmarked || r.named,
                    kind = r.kind,
                    mac = r.mac,
                    fleetNames = r.fleetNames,
                    observerNotes = notes,
                    advertised = advertised != null,
                    advertisedNote = if (advertised != null) {
                        AircraftTrail.advertisedNote(
                            status = r.liveDecode.reportLabels().joinToString(", "),
                            uasId = r.payloadUasId,
                            label = label,
                            lat = pin.first,
                            lon = pin.second,
                            alt = kept?.alt ?: r.payloadAlt,
                            heading = kept?.heading ?: r.payloadHeading,
                            speed = kept?.speed ?: r.payloadSpeed,
                            pilotLat = r.payloadOpLat,
                            pilotLon = r.payloadOpLon,
                            aircraft = r.payloadAircraft,
                        )
                    } else {
                        ""
                    },
                )
            }
            .sortedBy { it.label }
            .take(48)
        val dots = points
        val all = tracks.flatMap { it.samples }
        val cap = if (tracks.size == 2) {
            localized("sit_diff_two_walks_on_one_north_up_frame", "Two walks on one north-up frame. Green = this sit. Slate = second sit. %1\$s", pinNote)
        } else {
            localized("sit_diff_north_up_line_is_this_phone", "North-up. Line is this phone. %1\$s", pinNote)
        }
        return SitPathPlot.Figure(
            kicker = if (tracks.size == 2) localized("sit_diff_operator_paths", "OPERATOR PATHS") else localized("sit_diff_operator_path", "OPERATOR PATH"),
            tracks = tracks,
            dots = dots,
            lengthM = Geo.pathLengthM(all),
            spanM = Geo.spanM(all),
            caption = cap,
        )
    }

    private fun Radio.advertisedCoord(): Pair<Double, Double>? {
        val kept = payloadTrail.lastOrNull { PayloadLocation.validCoord(it.lat, it.lon) }
        if (kept != null) return kept.lat to kept.lon
        if (PayloadLocation.validCoord(payloadLat, payloadLon)) return payloadLat!! to payloadLon!!
        return null
    }

    private fun Radio.hearCoord(): Pair<Double, Double>? {
        val la = lat ?: return null
        val lo = lon ?: return null
        return if (PayloadLocation.validCoord(la, lo)) la to lo else null
    }

    private fun Radio.toCraftSource(): AircraftTrail.Source? {
        val fixes = payloadTrail.ifEmpty {
            val lat = payloadLat ?: return null
            val lon = payloadLon ?: return null
            listOf(PayloadFix(lastSeen, lat, lon, payloadAlt, payloadHeading, payloadSpeed))
        }.filter { PayloadLocation.validCoord(it.lat, it.lon) }
        if (fixes.isEmpty()) return null
        val last = fixes.last()
        return AircraftTrail.Source(
            uasId = payloadUasId,
            title = name.ifBlank { mac },
            lastSeen = lastSeen,
            status = liveDecode.reportLabels().joinToString(", "),
            fixes = fixes,
            alt = last.alt ?: payloadAlt,
            heading = last.heading ?: payloadHeading,
            speed = last.speed ?: payloadSpeed,
            pilotLat = payloadOpLat,
            pilotLon = payloadOpLon,
            key = key,
            mac = mac,
            aircraft = payloadAircraft,
        )
    }

    private fun sideBlock(heading: String, side: Side): String {
        val net = if (side.ram) {
            localized("sit_diff_last_15_minutes_in_memory_about_400", "last 15 minutes in memory (about 400 radios)")
        } else {
            localized("sit_diff_named_window_up_to", "named window (up to %1\$s)", Sit.RADIO_CAP)
        }
        return localized("sit_diff_n_radios_n", "%1\$s: %2\$s\n%3\$s radios · %4\$s\n", heading, side.name, side.radios.size, net)
    }

    private fun exclusiveExtra(
        onlyThis: Set<String>,
        onlySecond: Set<String>,
        byKey: Map<String, Radio>,
    ): List<ExtraAttentionHit> {
        fun hits(keys: Set<String>, where: String) = keys.mapNotNull { key ->
            val row = byKey[key] ?: return@mapNotNull null
            if (!row.extraAttention) return@mapNotNull null
            ExtraAttentionHit(
                signature = row.renderedFleetNames.firstOrNull() ?: localized("sit_diff_extra_attention", "Extra attention"),
                radioLabel = line(row),
                note = where,
            )
        }
        return hits(onlyThis, localized("sit_diff_only_in_this_sit_2", "Only in this sit.")) + hits(onlySecond, localized("sit_diff_only_in_second_sit_2", "Only in second sit."))
    }

    private fun presenceChart(
        onlyThis: Set<String>,
        onlySecond: Set<String>,
        both: Set<String>,
        byKey: Map<String, Radio>,
    ): ReportChart {
        fun row(label: String, keys: Set<String>): ReportBar {
            var wifi = 0
            var ble = 0
            for (key in keys) {
                val radio = byKey[key] ?: continue
                if (radio.kind == RadioKind.WIFI) wifi++ else ble++
            }
            return ReportBar(label, wifi, second = ble)
        }
        return ReportChart(
            split = true,
            rows = listOf(
                row(localized("sit_diff_only_in_this_sit_3", "Only in this sit"), onlyThis),
                row(localized("sit_diff_in_both_2", "In both"), both),
                row(localized("sit_diff_only_in_second_sit_3", "Only in second sit"), onlySecond),
            ),
        )
    }

    private fun rosterSection(
        number: String,
        title: String,
        keys: Set<String>,
        byKey: Map<String, Radio>,
        showAll: Boolean,
    ): DebriefSection {
        if (keys.isEmpty()) return DebriefSection(number, title, localized("sit_diff_none", "(none)"))
        val radios = keys.mapNotNull { byKey[it] }
        val shown = if (showAll) keys else keys.filter { byKey[it]?.keepShort() == true }.toSet()
        return DebriefSection(
            number,
            title,
            body = "",
            chart = radioSignatureChart(radios),
            after = if (shown.isEmpty()) "" else listBody(shown, byKey),
        )
    }

    private fun bothSection(
        number: String,
        title: String,
        keys: Set<String>,
        thisSit: Side,
        second: Side,
        byKey: Map<String, Radio>,
        showAll: Boolean,
    ): DebriefSection {
        if (keys.isEmpty()) return DebriefSection(number, title, localized("sit_diff_none", "(none)"))
        val earlier = thisSit.radios.associateBy { it.key }
        val later = second.radios.associateBy { it.key }
        val shown = if (showAll) {
            keys
        } else {
            keys.filter { key ->
                val a = earlier[key] ?: return@filter false
                val b = later[key] ?: return@filter false
                a.keepShort() || b.keepShort() || decodeChanged(a, b)
            }.toSet()
        }
        return DebriefSection(
            number,
            title,
            body = "",
            chart = radioSignatureChart(keys.mapNotNull { byKey[it] }),
            after = if (shown.isEmpty()) "" else bothBody(shown, thisSit, second),
        )
    }

    private fun radioSignatureChart(radios: List<Radio>): ReportChart? {
        if (radios.isEmpty()) return null
        val counts = linkedMapOf<String, Int>()
        for (radio in radios) {
            val sigs = radio.renderedFleetNames.filter { it.isNotBlank() }.distinct()
            val key = if (sigs.isEmpty()) localized("sit_diff_unmatched", "Unmatched") else sigs.joinToString(" + ")
            counts[key] = (counts[key] ?: 0) + 1
        }
        val rows = counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { ReportBar(it.key, it.value) }
        return ReportChart(rows = rows)
    }

    private fun Radio.keepShort(): Boolean =
        extraAttention || named || mine || bookmarked

    private fun decodeChanged(earlier: Radio, later: Radio): Boolean {
        val left = earlier.liveDecode.reportLabels()
        val right = later.liveDecode.reportLabels()
        return left.isNotEmpty() && right.isNotEmpty() && left != right
    }

    private fun listBody(keys: Set<String>, byKey: Map<String, Radio>): String {
        if (keys.isEmpty()) return localized("sit_diff_none", "(none)")
        return keys.mapNotNull { byKey[it] }
            .sortedWith(
                compareByDescending<Radio> { it.extraAttention }
                    .thenByDescending { it.named }
                    .thenBy { it.kind.name }
                    .thenBy { it.mac },
            )
            .joinToString("\n") { line(it) }
    }

    /** Kind + MAC is the same radio. A live label can still change between windows. */
    private fun bothBody(keys: Set<String>, thisSit: Side, second: Side): String {
        if (keys.isEmpty()) return localized("sit_diff_none", "(none)")
        val earlier = thisSit.radios.associateBy { it.key }
        val later = second.radios.associateBy { it.key }
        return keys.mapNotNull { key ->
            val a = earlier[key] ?: return@mapNotNull null
            val b = later[key] ?: return@mapNotNull null
            a to b
        }.sortedWith(
            compareByDescending<Pair<Radio, Radio>> { it.second.extraAttention }
                .thenByDescending { it.second.named }
                .thenBy { it.second.kind.name }
                .thenBy { it.second.mac },
        ).joinToString("\n") { (a, b) -> bothLine(a, b) }
    }

    private fun bothLine(earlier: Radio, later: Radio): String = buildString {
        append(line(later, chips = false))
        val left = earlier.liveDecode.reportLabels()
        val right = later.liveDecode.reportLabels()
        when {
            left.isNotEmpty() && right.isNotEmpty() && left != right -> {
                append(localized("sit_diff_decoded_value_changed", "  decoded value changed: "))
                append(earlier.renderedDecode.reportLabels().joinToString(", "))
                append(" → ")
                append(later.renderedDecode.reportLabels().joinToString(", "))
            }
            else -> append(chipSuffix(if (right.isNotEmpty()) later.renderedDecode else earlier.renderedDecode))
        }
    }

    private fun line(row: Radio, chips: Boolean = true): String = buildString {
        append(if (row.kind == RadioKind.WIFI) "WIFI" else "BLE ")
        append("  ")
        append(row.mac)
        val label = row.name.trim()
        if (label.isNotEmpty() && !label.equals(row.mac, ignoreCase = true)) {
            append("  ")
            append(label)
        }
        if (row.mine) append(localized("sit_diff_marked_mine_2", "  Marked mine"))
        row.renderedFleetNames.filter { it.isNotBlank() }.forEach { name ->
            append("  ")
            append(name)
        }
        if (row.extraAttention) append(localized("sit_diff_extra_attention_2", "  Extra attention"))
        if (chips) append(chipSuffix(row.renderedDecode))
    }

    private fun chipSuffix(chips: List<LiveDecodeChip>): String {
        val labels = chips.reportLabels()
        val notes = chips.map { it.note.trim() }.filter { it.isNotEmpty() }.distinct()
        if (labels.isEmpty() && notes.isEmpty()) return ""
        return buildString {
            if (labels.isNotEmpty()) {
                append("  ")
                append(labels.joinToString(", "))
            }
            if (notes.isNotEmpty()) {
                append("  ")
                append(notes.joinToString(" "))
            }
        }
    }

    private fun mineCount(thisSit: Side, second: Side): Int =
        (thisSit.radios + second.radios).distinctBy { it.key }.count { it.mine }

    private fun compareTakeaway(onlyThis: Int, onlySecond: Int, both: Int, mine: Int): String {
        val counts = localized("sit_diff_only_in_this_sit_only_in_the", "%1\$s only in this sit · %2\$s only in the second · %3\$s in both.", onlyThis, onlySecond, both)
        return if (mine > 0) localized("sit_diff_marked_mine_3", "%1\$s · %2\$s marked mine.", counts, mine) else counts
    }

    private fun floodSection(thisSit: Side, second: Side): String? {
        if (thisSit.floods.isEmpty() && second.floods.isEmpty()) return null
        fun StringBuilder.linesFor(side: Side) {
            if (side.floods.isEmpty()) return
            appendLine(side.name)
            side.floods.sortedBy { it.at }.forEach { burst ->
                appendLine("  ${burst.reportLine(FloodBurst.clock(burst.at))}")
            }
        }
        return buildString {
            appendLine(FloodBurst.intro(thisSit.floods + second.floods))
            appendLine()
            linesFor(thisSit)
            if (thisSit.floods.isNotEmpty() && second.floods.isNotEmpty()) appendLine()
            linesFor(second)
        }.trimEnd()
    }

    private fun markedMineSection(thisSit: Side, second: Side): String? {
        fun where(key: String): String = when {
            key in thisSit.keys && key in second.keys -> "both"
            key in thisSit.keys -> localized("sit_diff_this_sit_2", "this sit")
            else -> localized("sit_diff_second_sit_2", "second sit")
        }
        val rows = (thisSit.radios + second.radios).distinctBy { it.key }.filter { it.mine }
        if (rows.isEmpty()) return null
        return buildString {
            appendLine(localized("sit_diff_radios_you_marked_mine_heard_in_either", "Radios you marked mine. Heard in either window. Still listed."))
            rows.sortedWith(
                compareBy<Radio> { where(it.key) }.thenBy { it.mac },
            ).forEach { r ->
                val kind = if (r.kind == RadioKind.WIFI) "WIFI" else "BLE"
                val label = r.name.trim().takeIf { it.isNotEmpty() && !it.equals(r.mac, ignoreCase = true) }
                append("  · $kind  ${r.mac}")
                if (label != null) append("  ").append(label)
                append("  ").append(where(r.key))
                appendLine()
            }
        }.trimEnd()
    }

    private fun observerNotesSection(thisSit: Side, second: Side): String? {
        fun where(key: String): String = when {
            key in thisSit.keys && key in second.keys -> "both"
            key in thisSit.keys -> localized("sit_diff_this_sit_2", "this sit")
            else -> localized("sit_diff_second_sit_2", "second sit")
        }
        val rows = (thisSit.radios + second.radios)
            .distinctBy { it.key }
            .mapNotNull { r ->
                val note = r.observerNotes.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                r to note
            }
        if (rows.isEmpty()) return null
        return buildString {
            appendLine(localized("sit_diff_your_captions_on_radios_heard_in_either", "Your captions on radios heard in either window. Same KIND+MAC as Named radios. Not catalog Notes."))
            rows.sortedWith(
                compareBy<Pair<Radio, String>> { where(it.first.key) }.thenBy { it.first.mac },
            ).forEach { (r, note) ->
                val kind = if (r.kind == RadioKind.WIFI) "WIFI" else "BLE"
                val label = r.name.trim().takeIf { it.isNotEmpty() && !it.equals(r.mac, ignoreCase = true) }
                append("  · $kind  ${r.mac}")
                if (label != null) append("  ").append(label)
                append("  ").append(where(r.key))
                appendLine()
                appendLine("    $note")
            }
        }.trimEnd()
    }
}
