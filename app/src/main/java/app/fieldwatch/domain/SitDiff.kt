package app.fieldwatch.domain

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
    )

    data class Side(
        val name: String,
        val ram: Boolean,
        val radios: List<Radio>,
        val path: List<GpsSample> = emptyList(),
        val floods: List<FloodBurst> = emptyList(),
        val gnss: List<GnssMark> = emptyList(),
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
        extraAttention = device.attentionNotes(fleets).isNotEmpty(),
        named = device.key in customNames,
        bookmarked = device.key in bookmarkedKeys,
        fleetNames = device.fleetIds.map { id -> fleets.firstOrNull { it.id == id }?.name ?: id },
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
        val radios = thisSit.radios + second.radios
        return document(thisSit, second, showAllRadios = showAllRadios)
            .withDemoMacs(radios.map { it.mac }, demoMode, radios.map { it.payloadUasId })
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
            "Last 15 minutes is the Live RAM set (about 400 radios). " +
                "A named sit keeps up to ${Sit.RADIO_CAP}. Counts are not the same net."
        } else {
            null
        }
        val extraHits = exclusiveExtra(onlyThis, onlySecond, byKey)
        val sections = ArrayList<DebriefSection>()
        var n = 1
        fun next() = n++.toString()
        sections += DebriefSection(
            next(),
            "Windows",
            buildString {
                append(sideBlock("This sit", thisSit))
                append(sideBlock("Second sit", second))
                if (ramNote != null) {
                    appendLine(ramNote)
                    appendLine()
                }
                append("Radios this phone heard. Kind + MAC. BLE rotation is a new row. Not a radio fix.")
            },
            chart = presenceChart(onlyThis, onlySecond, both, byKey),
        )
        val thisCraft = AircraftTrail.pictures(thisSit.radios.mapNotNull { it.toCraftSource() }, thisSit.path)
        val secondCraft = AircraftTrail.pictures(second.radios.mapNotNull { it.toCraftSource() }, second.path)
        val aircraftBody = AircraftTrail.compareBody(thisSit.name, thisCraft, second.name, secondCraft)
        if (aircraftBody.isNotEmpty()) {
            sections += DebriefSection(next(), "Aircraft", aircraftBody)
        }
        observerNotesSection(thisSit, second)?.let { body ->
            sections += DebriefSection(next(), "Observer notes", body)
        }
        markedMineSection(thisSit, second)?.let { body ->
            sections += DebriefSection(next(), "Marked mine", body)
        }
        if (extraHits.isNotEmpty()) {
            sections += DebriefSection(
                next(),
                "Extra attention",
                extraHits.joinToString("\n") { "${it.radioLabel}\n${it.note}" },
                alert = true,
            )
        }
        floodSection(thisSit, second)?.let { body ->
            sections += DebriefSection(next(), "Flood", body)
        }
        gnssSection(thisSit, second)?.let { body ->
            sections += DebriefSection(next(), "GNSS", body, alert = true)
        }
        sections += rosterSection(next(), "Only in this sit (${onlyThis.size})", onlyThis, byKey, showAllRadios)
        sections += rosterSection(next(), "Only in second sit (${onlySecond.size})", onlySecond, byKey, showAllRadios)
        sections += bothSection(next(), "In both (${both.size})", both, thisSit, second, byKey, showAllRadios)
        val meta = buildList {
            add("This sit" to thisSit.name)
            add("Second sit" to second.name)
            add("This radios" to thisSit.radios.size.toString())
            add("Second radios" to second.radios.size.toString())
            if (ramNote != null) add("Caps" to "RAM ~400 vs sit ${Sit.RADIO_CAP}")
        }
        return DebriefDoc(
            generatedUtc = "",
            windowLine = "${thisSit.name} vs ${second.name}",
            meta = meta,
            disclaimer = FieldwatchDisclaimer.compare(),
            trackingAlert = extraHits.isNotEmpty(),
            takeaway = compareTakeaway(onlyThis.size, onlySecond.size, both.size, mineCount(thisSit, second)),
            sections = sections,
            extraAttention = extraHits,
            heading = "FIELDWATCH SIT COMPARE",
            pdfKicker = "SIT COMPARE",
            pdfTitle = "Sit compare",
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
        val thisTrack = tracks.firstOrNull { !it.secondary }
        val secondTrack = tracks.firstOrNull { it.secondary }
        val gnss = buildList {
            if (thisTrack != null) addAll(SitPathPlot.gnssPins(thisSit.gnss, thisTrack.samples))
            if (secondTrack != null) addAll(SitPathPlot.gnssPins(second.gnss, secondTrack.samples, secondary = true))
        }
        val pinNote = buildString {
            append("A MAC alert or a signature alert is drawn once. ")
            append("A decoded latitude and longitude is the last advertised position. ")
            append("Anything else is the strongest hear. A number is that place (Path key).")
            if (gnss.isNotEmpty()) {
                append(" A red diamond is where this phone was during a GNSS detection. ")
                if (gnss.any { it.secondary }) {
                    append("A blue diamond is the second sit. ")
                }
                append("The Path key has the time and what changed. ")
                append("It does not say where the interference came from.")
            }
        }
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
            "Two walks on one north-up frame. Green = this sit. Slate = second sit. $pinNote"
        } else {
            "North-up. Line is this phone. $pinNote"
        }
        return SitPathPlot.Figure(
            kicker = if (tracks.size == 2) "OPERATOR PATHS" else "OPERATOR PATH",
            tracks = tracks,
            dots = dots,
            lengthM = Geo.pathLengthM(all),
            spanM = Geo.spanM(all),
            caption = cap,
            gnss = gnss,
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
            "last 15 minutes in memory (about 400 radios)"
        } else {
            "named window (up to ${Sit.RADIO_CAP})"
        }
        return "$heading: ${side.name}\n${side.radios.size} radios · $net\n"
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
                signature = row.fleetNames.firstOrNull() ?: "Extra attention",
                radioLabel = line(row),
                note = where,
            )
        }
        return hits(onlyThis, "Only in this sit.") + hits(onlySecond, "Only in second sit.")
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
                row("Only in this sit", onlyThis),
                row("In both", both),
                row("Only in second sit", onlySecond),
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
        if (keys.isEmpty()) return DebriefSection(number, title, "(none)")
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
        if (keys.isEmpty()) return DebriefSection(number, title, "(none)")
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
            val sigs = radio.fleetNames.filter { it.isNotBlank() }.distinct()
            val key = if (sigs.isEmpty()) "Unmatched" else sigs.joinToString(" + ")
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
        if (keys.isEmpty()) return "(none)"
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
        if (keys.isEmpty()) return "(none)"
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
                append("  decoded value changed: ")
                append(left.joinToString(", "))
                append(" → ")
                append(right.joinToString(", "))
            }
            else -> append(chipSuffix(if (right.isNotEmpty()) later.liveDecode else earlier.liveDecode))
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
        if (row.mine) append("  Marked mine")
        row.fleetNames.filter { it.isNotBlank() }.forEach { name ->
            append("  ")
            append(name)
        }
        if (row.extraAttention) append("  Extra attention")
        if (chips) append(chipSuffix(row.liveDecode))
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
        val counts = "$onlyThis only in this sit · $onlySecond only in the second · $both in both."
        return if (mine > 0) "$counts · $mine marked mine." else counts
    }

    private fun gnssSection(thisSit: Side, second: Side): String? {
        if (thisSit.gnss.isEmpty() && second.gnss.isEmpty()) return null
        return buildString {
            appendLine(GnssCopy.INTRO)
            appendLine(GnssCopy.ROUTER)
            appendLine()
            fun side(name: String, marks: List<GnssMark>) {
                if (marks.isEmpty()) return
                appendLine(name)
                marks.sortedBy { it.at }.forEach { appendLine("  ${it.reportLine()}") }
            }
            side(thisSit.name, thisSit.gnss)
            if (thisSit.gnss.isNotEmpty() && second.gnss.isNotEmpty()) appendLine()
            side(second.name, second.gnss)
            val onPath = SitPathPlot.gnssPins(thisSit.gnss, thisSit.path).isNotEmpty() ||
                SitPathPlot.gnssPins(second.gnss, second.path).isNotEmpty()
            if (onPath) {
                appendLine()
                appendLine("The path map marks where this phone was. It does not say where the interference came from.")
            }
        }.trimEnd()
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
            key in thisSit.keys -> "this sit"
            else -> "second sit"
        }
        val rows = (thisSit.radios + second.radios).distinctBy { it.key }.filter { it.mine }
        if (rows.isEmpty()) return null
        return buildString {
            appendLine("Radios you marked mine. Heard in either window. Still listed.")
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
            key in thisSit.keys -> "this sit"
            else -> "second sit"
        }
        val rows = (thisSit.radios + second.radios)
            .distinctBy { it.key }
            .mapNotNull { r ->
                val note = r.observerNotes.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                r to note
            }
        if (rows.isEmpty()) return null
        return buildString {
            appendLine("Your captions on radios heard in either window. Same KIND+MAC as Named radios. Not catalog Notes.")
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
