package app.fieldwatch.domain

import app.fieldwatch.i18n.localized

import kotlinx.serialization.Serializable

/** One advertised position stored on a sit. Not the phone's GPS. */
@Serializable
data class PayloadFix(
    val at: Long,
    val lat: Double,
    val lon: Double,
    val alt: Double? = null,
    val heading: Double? = null,
    val speed: Double? = null,
)

/**
 * Advertised fixes for a sit report. Any radio that already carries a payload
 * latitude and longitude can grow a trail. Remote ID is the stock source.
 * The phone's walking speed filter is not applied here.
 */
object AircraftTrail {
    const val CAP = 40
    const val MIN_M = 8.0
    const val MAX_SPEED_MPS = 120.0
    const val NEAR_M = 2_000.0
    const val MAX_OWN_FIGURES = 3

    fun append(trail: List<PayloadFix>, device: Sighting): List<PayloadFix> {
        val lat = device.payloadLat ?: return trail
        val lon = device.payloadLon ?: return trail
        return append(
            trail,
            PayloadFix(
                at = device.lastSeen,
                lat = lat,
                lon = lon,
                alt = device.payloadAlt,
                heading = device.payloadHeading,
                speed = device.payloadSpeed,
            ),
        )
    }

    fun append(trail: List<PayloadFix>, fix: PayloadFix): List<PayloadFix> {
        if (!PayloadLocation.validCoord(fix.lat, fix.lon)) return trail
        val last = trail.lastOrNull()
        if (last != null) {
            val d = Geo.meters(last.lat, last.lon, fix.lat, fix.lon)
            if (d < MIN_M) return trail
            val dt = fix.at - last.at
            if (dt <= 0L) return trail
            val mps = d / (dt / 1000.0)
            if (mps > MAX_SPEED_MPS) return trail
        }
        return cap(trail + fix, CAP)
    }

    fun consolidate(fixes: List<PayloadFix>): List<PayloadFix> {
        var trail = emptyList<PayloadFix>()
        for (fix in fixes.sortedBy { it.at }) trail = append(trail, fix)
        return trail
    }

    fun pictures(sources: List<Source>, path: List<GpsSample>): List<Picture> {
        val named = sources.filter { it.uasId.isNotBlank() }.groupBy { it.uasId }
        val loose = sources.filter { it.uasId.isBlank() }
        val out = ArrayList<Picture>()
        for ((id, rows) in named) {
            out += picture(id, rows, path)
        }
        for (row in loose) {
            out += picture("", listOf(row), path)
        }
        return out.sortedByDescending { it.fixes.size }
    }

    private fun picture(uasId: String, rows: List<Source>, path: List<GpsSample>): Picture {
        val fixes = consolidate(rows.flatMap { it.fixes })
        val latest = rows.maxBy { it.lastSeen }
        val pilot = rows.filter { PayloadLocation.validCoord(it.pilotLat, it.pilotLon) }
            .maxByOrNull { it.lastSeen }
        val pilotNear = pilot != null && fixes.any { fix ->
            Geo.meters(fix.lat, fix.lon, pilot.pilotLat!!, pilot.pilotLon!!) <= NEAR_M
        }
        val walkReady = path.size >= 2
        val near = walkReady && fixes.isNotEmpty() && fixes.all { fix ->
            path.any { sample -> Geo.meters(fix.lat, fix.lon, sample.lat, sample.lon) <= NEAR_M }
        }
        val titled = uasId.ifBlank { latest.title.ifBlank { localized("aircraft_trail_advertised_position", "Advertised position") } }
        val aircraft = rows.filter { it.aircraft.isNotBlank() }.maxByOrNull { it.lastSeen }?.aircraft.orEmpty()
        return Picture(
            uasId = uasId,
            title = titled,
            fixes = fixes,
            status = latest.status,
            alt = latest.alt,
            heading = latest.heading,
            speed = latest.speed,
            pilotLat = pilot?.pilotLat,
            pilotLon = pilot?.pilotLon,
            pilotOnMap = pilotNear,
            onWalk = near && fixes.isNotEmpty(),
            ownFigure = uasId.isNotBlank() && !near && fixes.isNotEmpty(),
            keys = rows.map { it.key }.filter { it.isNotEmpty() }.toSet(),
            who = latest.title,
            mac = latest.mac,
            aircraft = aircraft,
        )
    }

    fun source(device: Sighting, title: String): Source? {
        val fixes = device.payloadTrail.ifEmpty {
            val lat = device.payloadLat ?: return null
            val lon = device.payloadLon ?: return null
            listOf(
                PayloadFix(
                    at = device.lastSeen,
                    lat = lat,
                    lon = lon,
                    alt = device.payloadAlt,
                    heading = device.payloadHeading,
                    speed = device.payloadSpeed,
                ),
            )
        }.filter { PayloadLocation.validCoord(it.lat, it.lon) }
        if (fixes.isEmpty()) return null
        val last = fixes.last()
        return Source(
            uasId = device.payloadUasId?.trim().orEmpty(),
            title = title,
            lastSeen = device.lastSeen,
            status = device.liveDecode.reportLabels().joinToString(", "),
            fixes = fixes,
            alt = last.alt ?: device.payloadAlt,
            heading = last.heading ?: device.payloadHeading,
            speed = last.speed ?: device.payloadSpeed,
            pilotLat = device.payloadOpLat,
            pilotLon = device.payloadOpLon,
            key = device.key,
            mac = device.mac,
            aircraft = device.payloadAircraft?.trim().orEmpty(),
        )
    }

    fun body(pictures: List<Picture>): String {
        if (pictures.isEmpty()) return ""
        return buildString {
            pictures.forEachIndexed { index, pic ->
                if (index > 0) appendLine()
                val head = if (pic.status.isBlank()) pic.title else "${pic.title} — ${pic.status}"
                appendLine("• $head")
                if (pic.aircraft.isNotBlank()) appendLine("  ${pic.aircraft}")
                val last = pic.fixes.last()
                appendLine(localized("aircraft_trail_last", "  Last %1\$s", fmtCoord(last.lat, last.lon)))
                val motion = listOfNotNull(
                    pic.alt?.let { "${fmtNum(it)} m" },
                    pic.heading?.let { localized("aircraft_trail_course", "course %1\$s°", fmtNum(it)) },
                    pic.speed?.let { "${fmtNum(it)} m/s" },
                )
                if (motion.isNotEmpty()) appendLine("  ${motion.joinToString("  ·  ")}")
                val length = lengthM(pic.fixes)
                val count = pic.fixes.size
                val shape = if (count == 1) localized("aircraft_trail_1_advertised_fix", "1 advertised fix") else localized("aircraft_trail_advertised_fixes", "%1\$s advertised fixes, %2\$s", count, fmtDist(length))
                appendLine("  $shape")
                if (pic.pilotLat != null && pic.pilotLon != null) {
                    appendLine(localized("aircraft_trail_pilot", "  Pilot %1\$s", fmtCoord(pic.pilotLat, pic.pilotLon)))
                }
            }
            val hidden = (pictures.count { it.ownFigure } - MAX_OWN_FIGURES).coerceAtLeast(0)
            if (hidden > 0) {
                appendLine(localized("aircraft_trail_more_aircraft_tracks_are_listed_here_and", "%1\$s more aircraft tracks are listed here and left off the map.", hidden))
            }
            append(localized("aircraft_trail_these_positions_were_broadcast_by_the_radio", "These positions were broadcast by the radio. They are not this phone's GPS."))
        }.trimEnd()
    }

    fun compareBody(leftName: String, left: List<Picture>, rightName: String, right: List<Picture>): String {
        val ids = (left.map { it.uasId } + right.map { it.uasId }).filter { it.isNotBlank() }.distinct()
        val looseLeft = left.filter { it.uasId.isBlank() }
        val looseRight = right.filter { it.uasId.isBlank() }
        if (ids.isEmpty() && looseLeft.isEmpty() && looseRight.isEmpty()) return ""
        return buildString {
            ids.forEachIndexed { index, id ->
                if (index > 0) appendLine()
                val a = left.find { it.uasId == id }
                val b = right.find { it.uasId == id }
                appendLine("• $id")
                if (a != null) appendLine("  $leftName: ${sideBit(a)}")
                if (b != null) appendLine("  $rightName: ${sideBit(b)}")
                val aStatus = a?.status.orEmpty()
                val bStatus = b?.status.orEmpty()
                if (aStatus.isNotBlank() && bStatus.isNotBlank() && aStatus != bStatus) {
                    appendLine(localized("aircraft_trail_status_changed", "  Status changed: %1\$s → %2\$s", aStatus, bStatus))
                }
            }
            looseLeft.forEach { appendLine(localized("aircraft_trail_no_uas_id", "%1\$s, no UAS id: %2\$s (%3\$s)", leftName, sideBit(it), it.title)) }
            looseRight.forEach { appendLine(localized("aircraft_trail_no_uas_id_2", "%1\$s, no UAS id: %2\$s (%3\$s)", rightName, sideBit(it), it.title)) }
            append(localized("aircraft_trail_these_positions_were_broadcast_by_the_radio", "These positions were broadcast by the radio. They are not this phone's GPS."))
        }.trimEnd()
    }

    fun track(pic: Picture, secondary: Boolean): SitPathPlot.FigureTrack = SitPathPlot.FigureTrack(
        name = pic.title.take(22),
        samples = samples(pic.fixes),
        secondary = secondary,
        aircraft = true,
        headingDeg = pic.heading,
    )

    fun pilotMark(pic: Picture): SitPathPlot.Mark? {
        if (!pic.pilotOnMap || pic.pilotLat == null || pic.pilotLon == null) return null
        return SitPathPlot.Mark(pic.pilotLat, pic.pilotLon, localized("aircraft_trail_pilot_2", "Pilot"))
    }

    fun applyWalk(base: SitPathPlot.Figure?, pictures: List<Picture>, secondary: Boolean): SitPathPlot.Figure? {
        if (base == null) return null
        val near = pictures.filter { it.onWalk }
        if (near.isEmpty()) return base
        val added = near.map { track(it, secondary) }
        val framed = base.tracks.flatMap { it.samples } + added.flatMap { it.samples }
        val covered = near.flatMap { it.keys }.filter { it.isNotEmpty() }.toSet()
        return base.copy(
            tracks = base.tracks + added,
            pilots = base.pilots + near.mapNotNull { pilotMark(it) },
            dots = base.dots.filter { it.key !in covered },
            craftKeys = base.craftKeys + near.map { pathKeyLine(it) },
            spanM = Geo.spanM(framed),
            caption = if (secondary) {
                if (localized("aircraft_trail_second_sit_s_advertised_track", "second sit’s advertised track") in base.caption) {
                    base.caption
                } else {
                    base.caption + localized("aircraft_trail_a_blue_dotted_line_is_the_second", " A blue dotted line is the second sit’s advertised track within 2 km of this path.")
                }
            } else if (localized("aircraft_trail_black_dotted_line_is_an_advertised_track", "black dotted line is an advertised track") in base.caption) {
                base.caption
            } else {
                base.caption + localized("aircraft_trail_a_black_dotted_line_is_an_advertised", " A black dotted line is an advertised track within 2 km of this path.")
            },
        )
    }

    fun ownFigures(pictures: List<Picture>, secondary: Boolean = false): List<SitPathPlot.Figure> =
        pictures.filter { it.ownFigure }.take(MAX_OWN_FIGURES).map { pictureFigure(it, secondary) }

    private fun pictureFigure(pic: Picture, secondary: Boolean = false): SitPathPlot.Figure {
        val samples = samples(pic.fixes)
        return SitPathPlot.Figure(
            kicker = localized("aircraft_trail_aircraft", "AIRCRAFT"),
            tracks = listOf(track(pic, secondary)),
            dots = emptyList(),
            lengthM = lengthM(pic.fixes),
            spanM = Geo.spanM(samples),
            caption = aircraftCaption(pic),
            pilots = listOfNotNull(pilotMark(pic)),
        )
    }

    fun compareOwnFigures(left: List<Picture>, right: List<Picture>): List<SitPathPlot.Figure> {
        val ids = (left + right).filter { it.ownFigure }.map { it.uasId }.filter { it.isNotBlank() }.distinct()
        return ids.take(MAX_OWN_FIGURES).map { id ->
            val a = left.find { it.uasId == id && it.ownFigure }
            val b = right.find { it.uasId == id && it.ownFigure }
            val tracks = listOfNotNull(a?.let { track(it, secondary = false) }, b?.let { track(it, secondary = true) })
            val samples = tracks.flatMap { it.samples }
            val lenA = a?.let { lengthM(it.fixes) } ?: 0.0
            val lenB = b?.let { lengthM(it.fixes) } ?: 0.0
            SitPathPlot.Figure(
                kicker = localized("aircraft_trail_aircraft", "AIRCRAFT"),
                tracks = tracks,
                dots = emptyList(),
                lengthM = maxOf(lenA, lenB),
                spanM = Geo.spanM(samples),
                caption = when {
                    a != null && b != null ->
                        localized("aircraft_trail_north_up_black_dots_are_this_sit", "North-up. Black dots are this sit. Blue dots are the second sit. The marker is the last advertised position.")
                    b != null ->
                        localized("aircraft_trail_north_up_the_blue_dotted_line_is", "North-up. The blue dotted line is the advertised track for %1\$s. The marker is the last advertised position.", b.title)
                    else -> aircraftCaption(a!!)
                },
                pilots = listOfNotNull(a?.let { pilotMark(it) }, b?.let { pilotMark(it) }),
            )
        }
    }

    /**
     * Same near / far split the report uses, for the on-screen Path card.
     * Near tracks share the phone plot. A farther UAS id becomes its own card.
     */
    fun overlay(model: SitPathPlot.Model, pictures: List<Picture>): SitPathPlot.Model {
        val near = pictures.filter { it.onWalk }
        val frames = ArrayList<GpsSample>()
        near.forEach { pic ->
            frames += samples(pic.fixes)
            pilotMark(pic)?.let { frames += GpsSample(0L, it.lat, it.lon, 0) }
        }
        val looseAlerts = pictures.filter { pic ->
            !pic.onWalk && !pic.ownFigure && pic.fixes.isNotEmpty() && pic.keys.any { key ->
                model.dots.any { it.key == key && it.advertised }
            }
        }
        val cards = (pictures.filter { it.ownFigure } + looseAlerts).take(MAX_OWN_FIGURES)
        val cardByKey = HashMap<String, Picture>()
        cards.forEach { pic -> pic.keys.forEach { cardByKey[it] = pic } }
        val walkDots = ArrayList<SitPathPlot.Dot>()
        val cardDots = LinkedHashMap<Picture, ArrayList<SitPathPlot.Dot>>()
        model.dots.forEach { dot ->
            val pic = cardByKey[dot.key]
            if (pic != null && dot.advertised) {
                cardDots.getOrPut(pic) { ArrayList() }.add(dot)
            } else {
                walkDots.add(dot)
            }
        }
        val cardKeys = cards.flatMap { it.keys }.toSet()
        return model.copy(
            dots = walkDots,
            frameSamples = frames,
            craft = near.map { track(it, secondary = false) },
            pilots = near.mapNotNull { pilotMark(it) },
            aircraftCards = cards.map { pic ->
                cardModel(pictureFigure(pic), cardDots[pic].orEmpty())
            },
            looseAdvertised = pictures.count { pic ->
                pic.fixes.isNotEmpty() && !pic.onWalk && pic.uasId.isBlank() &&
                    pic.keys.none { it in cardKeys }
            },
        )
    }

    fun cardModel(fig: SitPathPlot.Figure, dots: List<SitPathPlot.Dot> = emptyList()): SitPathPlot.Model {
        val tracks = fig.tracks.filter { it.aircraft }.ifEmpty { fig.tracks }
        val fixes = tracks.flatMap { it.samples }
        val frames = fixes + fig.pilots.map { GpsSample(0L, it.lat, it.lon, 0) }
        val title = tracks.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: localized("aircraft_trail_aircraft_2", "Aircraft")
        return SitPathPlot.Model(
            samples = emptyList(),
            dots = dots,
            lengthM = fig.lengthM,
            spanM = fig.spanM,
            title = title,
            caption = fig.caption,
            frameSamples = frames,
            minHalfSpanM = if (Geo.spanM(fixes) < 80.0) 140f else 0f,
            craft = tracks,
            pilots = fig.pilots,
        )
    }

    fun samples(fixes: List<PayloadFix>): List<GpsSample> =
        fixes.map { GpsSample(it.at, it.lat, it.lon, 0) }

    fun lengthM(fixes: List<PayloadFix>): Double {
        if (fixes.size < 2) return 0.0
        var sum = 0.0
        for (i in 1 until fixes.size) {
            sum += Geo.meters(fixes[i - 1].lat, fixes[i - 1].lon, fixes[i].lat, fixes[i].lon)
        }
        return sum
    }

    private fun aircraftCaption(pic: Picture): String {
        return if (pic.fixes.size < 2) {
            localized("aircraft_trail_last_advertised_position_for_the_marker_is", "Last advertised position for %1\$s. The marker is that position.", pic.title)
        } else {
            localized("aircraft_trail_north_up_the_black_dotted_line_is", "North-up. The black dotted line is the advertised track for %1\$s. The marker is the last advertised position.", pic.title)
        }
    }

    /**
     * One Path-key line for a radio that advertised a position.
     * Same facts as the Aircraft section, kept on the row under the map.
     */
    fun advertisedNote(
        status: String,
        uasId: String,
        label: String,
        lat: Double,
        lon: Double,
        alt: Double?,
        heading: Double?,
        speed: Double?,
        pilotLat: Double?,
        pilotLon: Double?,
        aircraft: String = "",
    ): String {
        val bits = ArrayList<String>()
        val craft = aircraft.trim()
        if (craft.isNotEmpty()) bits += craft
        val state = status.trim()
        if (state.isNotEmpty()) bits += state
        val id = uasId.trim()
        if (id.isNotEmpty() && !id.equals(label.trim(), ignoreCase = true)) bits += "UAS $id"
        bits += localized("aircraft_trail_last_2", "last %1\$s", fmtCoord(lat, lon))
        alt?.let { bits += "${fmtNum(it)} m" }
        heading?.let { bits += localized("aircraft_trail_course", "course %1\$s°", fmtNum(it)) }
        speed?.let { bits += "${fmtNum(it)} m/s" }
        if (PayloadLocation.validCoord(pilotLat, pilotLon)) {
            bits += localized("aircraft_trail_pilot_3", "pilot %1\$s", fmtCoord(pilotLat!!, pilotLon!!))
        }
        return bits.joinToString(" · ")
    }

    /** Path key row for the class icon at the end of an advertised track. */
    fun pathKeyLine(pic: Picture): String {
        val last = pic.fixes.lastOrNull()
        val head = listOf(pic.who.ifBlank { pic.title }, pic.mac)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" ")
            .ifBlank { localized("aircraft_trail_advertised_position", "Advertised position") }
        if (last == null) return head
        val note = advertisedNote(
            status = pic.status,
            uasId = pic.uasId.ifBlank { pic.title },
            label = head,
            lat = last.lat,
            lon = last.lon,
            alt = pic.alt,
            heading = pic.heading,
            speed = pic.speed,
            pilotLat = pic.pilotLat,
            pilotLon = pic.pilotLon,
            aircraft = pic.aircraft,
        )
        return "$head — $note"
    }

    private fun sideBit(pic: Picture): String {
        val craft = if (pic.aircraft.isBlank()) "" else "${pic.aircraft}, "
        val status = if (pic.status.isBlank()) "" else "${pic.status}, "
        val last = pic.fixes.lastOrNull()
        val where = if (last == null) "" else localized("aircraft_trail_last_3", " last %1\$s", fmtCoord(last.lat, last.lon))
        val count = if (pic.fixes.size == 1) localized("aircraft_trail_1_fix", "1 fix") else localized("aircraft_trail_fixes", "%1\$s fixes", pic.fixes.size)
        return "$craft$status$count$where".trim()
    }

    private fun cap(samples: List<PayloadFix>, limit: Int): List<PayloadFix> {
        if (samples.size <= limit) return samples
        if (limit <= 1) return listOf(samples.last())
        if (limit == 2) return listOf(samples.first(), samples.last())
        val lastIdx = samples.size - 1
        val out = ArrayList<PayloadFix>(limit)
        for (i in 0 until limit) {
            val idx = (i * lastIdx) / (limit - 1)
            val sample = samples[idx]
            if (out.isEmpty() || out.last().at != sample.at) out += sample
        }
        if (out.last().at != samples.last().at) out += samples.last()
        return out
    }

    private fun fmtCoord(lat: Double, lon: Double): String =
        "%.6f".format(java.util.Locale.US, lat) + ", " + "%.6f".format(java.util.Locale.US, lon)

    private fun fmtNum(n: Double): String =
        if (n % 1.0 == 0.0) n.toInt().toString() else "%.1f".format(java.util.Locale.US, n)

    private fun fmtDist(m: Double): String =
        if (m >= 1000) "${"%.1f".format(java.util.Locale.US, m / 1000)} km" else "${m.toInt()} m"

    data class Source(
        val uasId: String,
        val title: String,
        val lastSeen: Long,
        val status: String,
        val fixes: List<PayloadFix>,
        val alt: Double?,
        val heading: Double?,
        val speed: Double?,
        val pilotLat: Double?,
        val pilotLon: Double?,
        /** Device key of the radio that sent these fixes. Empty for a report-only source. */
        val key: String = "",
        val mac: String = "",
        val aircraft: String = "",
    )

    data class Picture(
        val uasId: String,
        val title: String,
        val fixes: List<PayloadFix>,
        val status: String,
        val alt: Double?,
        val heading: Double?,
        val speed: Double?,
        val pilotLat: Double?,
        val pilotLon: Double?,
        val pilotOnMap: Boolean,
        val onWalk: Boolean,
        val ownFigure: Boolean,
        /** Device keys joined into this picture. */
        val keys: Set<String> = emptySet(),
        /** Radio name when it is not already the UAS id. */
        val who: String = "",
        val mac: String = "",
        val aircraft: String = "",
    )
}
