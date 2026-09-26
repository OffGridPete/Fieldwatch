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
        val randomized: Boolean = false,
        val lat: Double? = null,
        val lon: Double? = null,
        val observerNotes: String = "",
        val gpsTrail: List<GpsSample> = emptyList(),
        val firstSeen: Long = 0L,
        val lastSeen: Long = 0L,
    )

    data class Side(
        val name: String,
        val ram: Boolean,
        val radios: List<Radio>,
        val path: List<GpsSample> = emptyList(),
    ) {
        val keys: Set<String> get() = radios.map { it.key }.toSet()
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
    ): Radio = Radio(
        key = device.key,
        kind = device.kind,
        mac = device.mac,
        name = device.reportName(customNames),
        extraAttention = device.attentionNotes(fleets).isNotEmpty(),
        named = device.key in customNames,
        bookmarked = device.key in bookmarkedKeys,
        fleetNames = device.fleetIds.map { id -> fleets.firstOrNull { it.id == id }?.name ?: id },
        randomized = device.randomized,
        lat = SitPathPlot.loudestFix(device)?.lat ?: device.latitude,
        lon = SitPathPlot.loudestFix(device)?.lon ?: device.longitude,
        observerNotes = observerNotes[device.key].orEmpty(),
        gpsTrail = device.gpsTrail,
        firstSeen = device.firstSeen,
        lastSeen = device.lastSeen,
    )

    fun fromSitRadio(
        row: SitRadio,
        fleets: List<Fleet>,
        customNames: Map<String, String>,
        observerNotes: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
    ): Radio = Radio(
        key = row.key,
        kind = row.kind,
        mac = row.mac,
        name = customNames[row.key]?.trim()?.takeIf { it.isNotEmpty() } ?: row.name.ifBlank { row.mac },
        extraAttention = row.extraAttention,
        named = row.key in customNames,
        bookmarked = row.key in bookmarkedKeys,
        fleetNames = row.fleetIds.map { id -> fleets.firstOrNull { it.id == id }?.name ?: id },
        randomized = row.randomized,
        lat = SitPathPlot.loudestFix(row.gpsTrail)?.lat,
        lon = SitPathPlot.loudestFix(row.gpsTrail)?.lon,
        observerNotes = observerNotes[row.key].orEmpty(),
        gpsTrail = row.gpsTrail,
        firstSeen = row.firstSeen,
        lastSeen = row.lastSeen,
    )

    fun report(
        thisSit: Side,
        second: Side,
        demoMode: Boolean,
    ): String {
        val macs = (thisSit.radios + second.radios).map { it.mac }
        return document(thisSit, second).withDemoMacs(macs, demoMode).toPlainText()
    }

    /** Same shape as Debrief so Compare (PDF) uses the Debrief letter layout. */
    fun document(thisSit: Side, second: Side): DebriefDoc {
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
        )
        observerNotesSection(thisSit, second)?.let { body ->
            sections += DebriefSection(next(), "Observer notes", body)
        }
        if (extraHits.isNotEmpty()) {
            sections += DebriefSection(
                next(),
                "Extra attention",
                extraHits.joinToString("\n") { "${it.radioLabel}\n${it.note}" },
                alert = true,
            )
        }
        sections += DebriefSection(next(), "Only in this sit (${onlyThis.size})", listBody(onlyThis, byKey))
        sections += DebriefSection(next(), "Only in second sit (${onlySecond.size})", listBody(onlySecond, byKey))
        sections += DebriefSection(next(), "In both (${both.size})", listBody(both, byKey))
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
            takeaway = "${onlyThis.size} only in this sit · ${onlySecond.size} only in the second · ${both.size} in both.",
            sections = sections,
            extraAttention = extraHits,
            heading = "FIELDWATCH SIT COMPARE",
            pdfKicker = "SIT COMPARE",
            pdfTitle = "Sit compare",
            pathFigure = pathFigure(thisSit, second),
        )
    }

    private fun pathFigure(
        thisSit: Side,
        second: Side,
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
        val points = ArrayList<SitPathPlot.Dot>()
        (thisSit.radios + second.radios)
            .filter { it.extraAttention || it.bookmarked }
            .distinctBy { it.key }
            .forEach { r ->
                val lat = r.lat ?: return@forEach
                val lon = r.lon ?: return@forEach
                val notes = if (r.bookmarked) r.observerNotes else ""
                points += SitPathPlot.Dot(
                    key = r.key,
                    lat = lat,
                    lon = lon,
                    label = r.name.ifBlank { r.mac },
                    extraAttention = r.extraAttention,
                    named = r.bookmarked || r.named,
                    kind = r.kind,
                    mac = r.mac,
                    fleetNames = r.fleetNames,
                    observerNotes = notes,
                )
            }
        val dots = points.take(24)
        val all = tracks.flatMap { it.samples }
        val cap = if (tracks.size == 2) {
            "Two walks on one north-up frame. Green = this sit. Slate = second sit. A number is a place on this phone's path; stacked radios share a number (Path key). Hear-points, not radio fixes."
        } else {
            "North-up. Line is this phone. A number is a place on this path; stacked radios share a number (Path key). Hear-points, not radio fixes."
        }
        return SitPathPlot.Figure(
            kicker = if (tracks.size == 2) "OPERATOR PATHS" else "OPERATOR PATH",
            tracks = tracks,
            dots = dots,
            lengthM = Geo.pathLengthM(all),
            spanM = Geo.spanM(all),
            caption = cap,
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

    private fun line(row: Radio): String = buildString {
        append(if (row.kind == RadioKind.WIFI) "WIFI" else "BLE ")
        append("  ")
        append(row.mac)
        val label = row.name.trim()
        if (label.isNotEmpty() && !label.equals(row.mac, ignoreCase = true)) {
            append("  ")
            append(label)
        }
        row.fleetNames.filter { it.isNotBlank() }.forEach { name ->
            append("  ")
            append(name)
        }
        if (row.extraAttention) append("  Extra attention")
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
