package app.fieldwatch.domain

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * This phone's serving cell, learned at a place and checked on a later visit.
 * A drive does not open a place. A drop to 2G or 3G can fire while moving inside
 * a place that has only learned 4G and 5G. No cause is named.
 */
enum class CellGen {
    GSM, WCDMA, TDSCDMA, LTE, NR;

    fun band(): String = when (this) {
        GSM -> "2G"
        WCDMA, TDSCDMA -> "3G"
        LTE -> "4G"
        NR -> "5G"
    }

    fun low(): Boolean = this == GSM || this == WCDMA || this == TDSCDMA

    fun high(): Boolean = this == LTE || this == NR
}

enum class CellKind { NEW, DROP2, DROP3 }

/** One group on the full place list. A place is in a single group. */
enum class CellPlaceBand { HERE, TODAY, WEEK, OLDER }

data class CellPlaceSection(val band: CellPlaceBand, val places: List<CellPlace>)

/** The short card, and the grouped list behind it. */
data class CellPlaceRoll(
    val preview: List<CellPlace>,
    val more: Int,
    val sections: List<CellPlaceSection>,
)

enum class CellField { NETWORK, AREA, CELL }

@Serializable
data class CellIdentity(
    val gen: CellGen,
    val plmn: String,
    val area: Int,
    val cell: Long,
) {
    fun key(): String = "${gen.name}|$plmn|$area|$cell"

    /** Same cell with a different area code. That change is not its own warning. */
    fun towerKey(): String = "${gen.name}|$plmn|$cell"
}

@Serializable
data class CellRecord(
    val identity: CellIdentity,
    val pci: Int? = null,
    val channel: Int? = null,
    val signal: Int? = null,
    val timingAdvance: Int? = null,
    val heardAt: Long = 0L,
    /** When this cell was first saved here. Later stays do not move it. Older cells keep the earliest time already stored. */
    val firstAt: Long = 0L,
    /** Separate stays on this cell. A poll while the stay continues does not add one. */
    val camps: Int = 0,
)

@Serializable
data class CellPlace(
    val id: String,
    val lat: Double,
    val lon: Double,
    val cells: List<CellRecord> = emptyList(),
    val openedAt: Long = 0L,
    val lastVisitAt: Long = 0L,
    val baselined: Boolean = false,
    val visiting: Boolean = false,
    /** Street from the system geocoder. Hidden when Online place names is off or Privacy mode is on. */
    val name: String = "",
    /** Tower keys already counted during this visit. Cleared when the visit ends. */
    val camped: List<String> = emptyList(),
)

@Serializable
data class CellEpisode(
    val id: Long,
    val kind: CellKind,
    val placeId: String,
    val identity: CellIdentity,
    val pci: Int? = null,
    val channel: Int? = null,
    val signal: Int? = null,
    val timingAdvance: Int? = null,
    val startedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val endedAt: Long = 0L,
) {
    val open: Boolean get() = endedAt <= 0L
}

@Serializable
data class CellHold(
    val key: String,
    val lastSeen: Long,
)

@Serializable
data class CellBook(
    val format: String = FORMAT,
    val places: List<CellPlace> = emptyList(),
    val episodes: List<CellEpisode> = emptyList(),
    val anchorLat: Double? = null,
    val anchorLon: Double? = null,
    val holds: List<CellHold> = emptyList(),
    val dropHeld: Boolean = false,
    val dropClearSince: Long = 0L,
    val nextPlace: Long = 1L,
    val nextEpisode: Long = 1L,
) {
    companion object {
        const val FORMAT = "fieldwatch-cells"
    }
}

/** One cached reading. An incomplete identity does not learn and does not warn. */
data class CellReading(
    val registered: Boolean = false,
    val generation: CellGen? = null,
    val identity: CellIdentity? = null,
    val pci: Int? = null,
    val channel: Int? = null,
    val signal: Int? = null,
    val timingAdvance: Int? = null,
    val emptyFields: List<CellField> = emptyList(),
    val neighborCount: Int = 0,
    /** Null when the phone did not return a second SIM. */
    val secondSimRegistered: Boolean? = null,
)

data class CellFix(
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyM: Float? = null,
    val speedMps: Float? = null,
    val paused: Boolean = false,
)

data class CellSwitches(
    val watch: Boolean = false,
    val warnNew: Boolean = true,
    val warnDrop: Boolean = true,
    /** Days after the place opened. 0 is none: the first visit is the only quiet one. */
    val learnDays: Int = 3,
)

data class CellStep(
    val book: CellBook,
    val fresh: List<CellEpisode> = emptyList(),
    val reading: CellReading = CellReading(),
)

@Serializable
data class CellMark(
    val at: Long,
    val endedAt: Long = 0L,
    val kind: String,
    val gen: String,
    val plmn: String = "",
    val area: String = "",
    val cell: String = "",
    val signal: Int? = null,
    val timingAdvance: Int? = null,
)

object CellWatch {
    const val NEAR_M = 300.0

    /** Meters from this phone to the place. Null when the phone has no fix. */
    fun metersFromPhone(place: CellPlace, phoneLat: Double?, phoneLon: Double?): Double? {
        if (phoneLat == null || phoneLon == null) return null
        if (!phoneLat.isFinite() || !phoneLon.isFinite()) return null
        val meters = Geo.meters(place.lat, place.lon, phoneLat, phoneLon)
        return meters.takeIf { it.isFinite() }
    }

    /**
     * The settings card shows [PREVIEW] places. The place this phone is inside stays on that card.
     * The rest open as Here, Today, This week, and Older. This week follows the phone's calendar.
     */
    fun arrange(
        places: List<CellPlace>,
        phoneLat: Double?,
        phoneLon: Double?,
        now: Long,
        zone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault(),
    ): CellPlaceRoll {
        val byVisit = places.sortedWith(compareByDescending<CellPlace> { it.lastVisitAt }.thenBy { it.id })
        val here = byVisit.filter { inside(it, phoneLat, phoneLon) }
        val hereIds = here.map { it.id }.toSet()
        val pinned = here + byVisit.filter { it.id !in hereIds }
        val preview = if (pinned.size <= PREVIEW) pinned else pinned.take(PREVIEW)
        val today = dayStartMillis(now, zone, locale)
        val week = weekStartMillis(now, zone, locale)
        val grouped = linkedMapOf<CellPlaceBand, MutableList<CellPlace>>(
            CellPlaceBand.HERE to mutableListOf(),
            CellPlaceBand.TODAY to mutableListOf(),
            CellPlaceBand.WEEK to mutableListOf(),
            CellPlaceBand.OLDER to mutableListOf(),
        )
        for (place in byVisit) {
            val band = when {
                place.id in hereIds -> CellPlaceBand.HERE
                place.lastVisitAt >= today -> CellPlaceBand.TODAY
                place.lastVisitAt >= week -> CellPlaceBand.WEEK
                else -> CellPlaceBand.OLDER
            }
            grouped.getValue(band).add(place)
        }
        val sections = grouped.mapNotNull { (band, rows) ->
            if (rows.isEmpty()) null else CellPlaceSection(band, rows)
        }
        return CellPlaceRoll(preview, pinned.size - preview.size, sections)
    }

    /**
     * Places that fit on one map picture. A fix keeps places within [MAP_SPAN_M] of this phone.
     * With no fix, that circle is around the newest place. Farther places stay on the list.
     */
    const val MAP_SPAN_M = 40_000.0

    fun mapPlaces(places: List<CellPlace>, phoneLat: Double?, phoneLon: Double?): List<CellPlace> {
        val valid = places.filter { onEarth(it.lat, it.lon) }
        if (valid.isEmpty()) return emptyList()
        val newest = valid.maxBy { it.lastVisitAt }
        val lat = if (phoneLat != null && phoneLon != null && onEarth(phoneLat, phoneLon)) phoneLat else newest.lat
        val lon = if (phoneLat != null && phoneLon != null && onEarth(phoneLat, phoneLon)) phoneLon else newest.lon
        val near = valid.filter { Geo.meters(lat, lon, it.lat, it.lon) <= MAP_SPAN_M }
        return if (near.isNotEmpty()) near else listOf(newest)
    }

    private fun onEarth(lat: Double, lon: Double): Boolean =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0

    /** Street when Online place names is on and one is saved. Otherwise the coordinate. Privacy masks both. */
    fun placeTitle(place: CellPlace, onlineNames: Boolean, privacy: Boolean): String {
        if (privacy) return Geo.screenCoord(place.lat, place.lon, true) ?: "masked"
        if (onlineNames) {
            val street = place.name.trim()
            if (street.isNotEmpty()) return street
        }
        return Geo.screenCoord(place.lat, place.lon, false).orEmpty()
    }

    /** Fill a blank name from a geocoder result. A name already saved stays. */
    fun applyNames(book: CellBook, namesByCell: Map<String, String>): CellBook {
        if (namesByCell.isEmpty()) return book
        var changed = false
        val places = book.places.map { place ->
            if (place.name.isNotBlank()) return@map place
            val raw = namesByCell[Geo.cellKey(place.lat, place.lon)]?.trim().orEmpty()
            if (raw.isEmpty()) return@map place
            changed = true
            place.copy(name = raw.take(NAME_CAP))
        }
        return if (changed) book.copy(places = places) else book
    }

    private fun inside(place: CellPlace, phoneLat: Double?, phoneLon: Double?): Boolean {
        val meters = metersFromPhone(place, phoneLat, phoneLon) ?: return false
        return meters <= NEAR_M
    }

    private fun dayStartMillis(millis: Long, zone: TimeZone, locale: Locale): Long =
        dayStart(millis, zone, locale).timeInMillis

    private fun weekStartMillis(now: Long, zone: TimeZone, locale: Locale): Long {
        val cal = dayStart(now, zone, locale)
        val delta = (cal.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek + 7) % 7
        cal.add(Calendar.DAY_OF_MONTH, -delta)
        return cal.timeInMillis
    }

    private fun dayStart(millis: Long, zone: TimeZone, locale: Locale): Calendar =
        Calendar.getInstance(zone, locale).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    const val ACC_M = 100f
    const val CAP = 32
    const val PREVIEW = 10
    private const val NAME_CAP = 160
    const val GONE_MS = 10 * 60_000L
    private const val CLOSED_CAP = 40

    fun tick(
        book: CellBook,
        reading: CellReading,
        fix: CellFix,
        switches: CellSwitches,
        now: Long,
    ): CellStep {
        var next = expireHolds(touchHolds(book, reading, now), now)
        next = ageNew(next, reading, now)
        next = recover(next, reading, now)
        if (!switches.watch) {
            return CellStep(trim(endVisits(next, now)), emptyList(), reading)
        }
        val usable = usable(fix)
        val still = usable && still(next, fix)
        if (usable) next = moveAnchor(next, fix, still)
        if (!usable) return CellStep(trim(next), emptyList(), reading)
        val fresh = ArrayList<CellEpisode>(2)
        val index = nearest(next, fix)
        if (index < 0) {
            next = endVisits(next, now)
            if (still && reading.identity != null) next = openPlace(next, fix, reading, now)
            return CellStep(trim(next), fresh, reading)
        }
        next = endOtherVisits(next, index, now)
        val here = nearest(next, fix)
        if (here < 0) return CellStep(trim(next), fresh, reading)
        next = markInside(next, here, now)
        next = considerDrop(next, here, reading, switches, now, fresh)
        if (!still || reading.identity == null) return CellStep(trim(next), fresh, reading)
        val place = next.places[here]
        if (!place.baselined) {
            next = replace(next, here, store(place, reading, now))
            return CellStep(trim(next), fresh, reading)
        }
        if (learning(place, switches.learnDays, now)) {
            next = learnQuiet(next, here, reading, switches, now)
            return CellStep(trim(next), fresh, reading)
        }
        next = considerNew(next, here, reading, switches, now, fresh)
        return CellStep(trim(next), fresh, reading)
    }

    fun showing(book: CellBook): CellEpisode? =
        book.episodes.filter { it.open }
            .sortedWith(compareBy<CellEpisode> { it.startedAt }.thenBy { if (it.kind == CellKind.NEW) 1 else 0 })
            .firstOrNull()

    fun learn(book: CellBook, episodeId: Long, now: Long): CellBook {
        val episode = book.episodes.find { it.open && it.id == episodeId } ?: return book
        val index = book.places.indexOfFirst { it.id == episode.placeId }
        var next = if (index < 0) book else replace(book, index, store(book.places[index], episode.reading(), now))
        next = endEpisode(next, episodeId, now)
        next = next.copy(holds = next.holds.filter { it.key != episode.identity.key() })
        val place = next.places.find { it.id == episode.placeId }
        if (place != null) next = endNewForStored(next, place, now)
        return trim(next)
    }

    fun dismiss(book: CellBook, episodeId: Long, now: Long): CellBook {
        val episode = book.episodes.find { it.open && it.id == episodeId } ?: return book
        var next = endEpisode(book, episodeId, now)
        next = if (episode.kind == CellKind.NEW) {
            val holds = next.holds.filter { it.key != episode.identity.key() } + CellHold(episode.identity.key(), now)
            next.copy(holds = holds)
        } else {
            next.copy(dropHeld = true, dropClearSince = 0L)
        }
        return trim(next)
    }

    fun removeCell(book: CellBook, placeId: String, key: String): CellBook {
        val index = book.places.indexOfFirst { it.id == placeId }
        if (index < 0) return book
        val place = book.places[index]
        return replace(book, index, place.copy(cells = place.cells.filter { it.identity.key() != key }))
    }

    fun forget(book: CellBook, placeId: String, now: Long): CellBook {
        val episodes = book.episodes.map { episode ->
            if (episode.open && episode.placeId == placeId) episode.copy(endedAt = now) else episode
        }
        return trim(book.copy(places = book.places.filter { it.id != placeId }, episodes = episodes))
    }

    fun marks(book: CellBook): List<CellMark> = book.episodes.map { it.toMark() }

    fun diagRows(reading: CellReading): List<Pair<String, String>> {
        val empty = reading.emptyFields.joinToString(", ") { field ->
            LanguageText.text(
                when (field) {
                    CellField.NETWORK -> "cell.network"
                    CellField.AREA -> "cell.area"
                    CellField.CELL -> "cell.cell"
                },
            )
        }
        val second = when (reading.secondSimRegistered) {
            null -> LanguageText.text("diag.cell.missing")
            true -> LanguageText.text("diag.on")
            false -> LanguageText.text("diag.off")
        }
        return listOf(
            LanguageText.text("diag.cell.registered") to
                if (reading.registered) LanguageText.text("diag.on") else LanguageText.text("diag.off"),
            LanguageText.text("cell.gen") to (reading.generation?.band() ?: ""),
            LanguageText.text("diag.cell.empty") to empty,
            LanguageText.text("diag.cell.neighbors") to reading.neighborCount.coerceAtLeast(0).toString(),
            LanguageText.text("diag.cell.second") to second,
        )
    }

    /** One Debrief block. Privacy mode masks the area code and the cell id. */
    fun block(marks: List<CellMark>, start: Long, end: Long, demo: Boolean): String? {
        val rows = marks.filter { overlaps(it, start, end) }.sortedBy { it.at }
        if (rows.isEmpty()) return null
        return rows.joinToString("\n\n") { render(it, demo) }
    }

    private fun overlaps(mark: CellMark, start: Long, end: Long): Boolean {
        val stop = if (mark.endedAt > 0L) mark.endedAt else end
        return mark.at <= end && stop >= start
    }

    private fun render(mark: CellMark, demo: Boolean): String = buildString {
        appendLine(sentenceForKind(mark.kind))
        appendLine("${utc(mark.at)} UTC")
        if (mark.gen.isNotBlank()) appendLine("${LanguageText.text("cell.gen")}: ${mark.gen}")
        if (mark.plmn.isNotBlank()) appendLine("${LanguageText.text("cell.network")}: ${mark.plmn}")
        CellPlmn.country(mark.plmn)?.let { appendLine("${LanguageText.text("cell.country")}: $it") }
        CellPlmn.operator(mark.plmn)?.let { appendLine("${LanguageText.text("cell.operator")}: $it") }
        val area = masked(mark.area, demo)
        val cell = masked(mark.cell, demo)
        if (area.isNotBlank()) appendLine("${LanguageText.text("cell.area")}: $area")
        if (cell.isNotBlank()) appendLine("${LanguageText.text("cell.cell")}: $cell")
        if (mark.signal != null) appendLine("${LanguageText.text("cell.signal")}: ${mark.signal} dBm")
        if (mark.timingAdvance != null) appendLine("${LanguageText.text("cell.timing")}: ${mark.timingAdvance}")
    }.trimEnd()

    private fun masked(value: String, demo: Boolean): String {
        if (value.isBlank()) return ""
        return if (demo) "masked" else value
    }

    private fun sentenceForKind(kind: String): String = LanguageText.text(
        when (kind) {
            "2g" -> "cell.sentence.2g"
            "3g" -> "cell.sentence.3g"
            else -> "cell.sentence.new"
        },
    )

    private fun utc(at: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(at))
    }

    private fun considerDrop(
        book: CellBook,
        index: Int,
        reading: CellReading,
        switches: CellSwitches,
        now: Long,
        fresh: MutableList<CellEpisode>,
    ): CellBook {
        val identity = reading.identity ?: return book
        if (!identity.gen.low()) return book
        val place = book.places[index]
        if (!place.baselined || !onlyHigh(place) || !switches.warnDrop || book.dropHeld) return book
        val open = book.episodes.find { it.open && it.kind != CellKind.NEW }
        if (open != null) return touchEpisode(book, open.id, reading, now)
        val episode = episode(book, dropKind(identity.gen), place.id, reading, now)
        fresh += episode
        return book.copy(
            episodes = book.episodes + episode,
            nextEpisode = book.nextEpisode + 1,
            dropClearSince = 0L,
        )
    }

    private fun considerNew(
        book: CellBook,
        index: Int,
        reading: CellReading,
        switches: CellSwitches,
        now: Long,
        fresh: MutableList<CellEpisode>,
    ): CellBook {
        val identity = reading.identity ?: return book
        val place = book.places[index]
        val exact = place.cells.any { it.identity.key() == identity.key() }
        val areaOnly = !exact && place.cells.any { it.identity.towerKey() == identity.towerKey() }
        if (exact || areaOnly) {
            val stored = store(place, reading, now)
            return endNewForStored(replace(book, index, stored), stored, now)
        }
        if (!switches.warnNew) {
            // A drop is stored from Learn this cell. The switch being off is not that.
            if (identity.gen.low() && switches.warnDrop && onlyHigh(place) && !book.dropHeld) return book
            return replace(book, index, store(place, reading, now))
        }
        if (book.holds.any { it.key == identity.key() }) return book
        val open = book.episodes.find { it.open && it.kind == CellKind.NEW && it.identity.key() == identity.key() }
        if (open != null) return touchEpisode(book, open.id, reading, now)
        val episode = episode(book, CellKind.NEW, place.id, reading, now)
        fresh += episode
        return book.copy(episodes = book.episodes + episode, nextEpisode = book.nextEpisode + 1)
    }

    /** A later visit inside How long to learn. A new 4G or 5G cell is stored. A drop is not. */
    private fun learnQuiet(
        book: CellBook,
        index: Int,
        reading: CellReading,
        switches: CellSwitches,
        now: Long,
    ): CellBook {
        val identity = reading.identity ?: return book
        val place = book.places[index]
        val exact = place.cells.any { it.identity.key() == identity.key() }
        val areaOnly = !exact && place.cells.any { it.identity.towerKey() == identity.towerKey() }
        if (exact || areaOnly) {
            val stored = store(place, reading, now)
            return endNewForStored(replace(book, index, stored), stored, now)
        }
        if (identity.gen.low() && switches.warnDrop && onlyHigh(place)) return book
        return replace(book, index, store(place, reading, now))
    }

    private fun learning(place: CellPlace, learnDays: Int, now: Long): Boolean {
        val window = LearnLeft.cellWindowMs(learnDays) ?: return false
        if (place.openedAt <= 0L) return false
        return now - place.openedAt < window
    }

    private fun openPlace(book: CellBook, fix: CellFix, reading: CellReading, now: Long): CellBook {
        val place = store(
            CellPlace(
                id = "p${book.nextPlace}",
                lat = fix.lat!!,
                lon = fix.lon!!,
                openedAt = now,
                lastVisitAt = now,
                visiting = true,
            ),
            reading,
            now,
        )
        return book.copy(places = book.places + place, nextPlace = book.nextPlace + 1)
    }

    private fun store(place: CellPlace, reading: CellReading, now: Long): CellPlace {
        val identity = reading.identity ?: return place
        val cells = place.cells.toMutableList()
        val exact = cells.indexOfFirst { it.identity.key() == identity.key() }
        if (exact >= 0) return noteCamp(place, cells, exact, reading, identity, now)
        val tower = cells.indexOfFirst { it.identity.towerKey() == identity.towerKey() }
        if (tower >= 0) return noteCamp(place, cells, tower, reading, identity, now)
        if (cells.size >= CAP) {
            val drop = cells.minBy { it.heardAt }
            cells.removeAll { it.identity.key() == drop.identity.key() }
        }
        val key = identity.towerKey()
        cells += CellRecord(
            identity = identity,
            pci = reading.pci,
            channel = reading.channel,
            signal = reading.signal,
            timingAdvance = reading.timingAdvance,
            heardAt = now,
            firstAt = now,
            camps = 1,
        )
        val camped = if (key in place.camped) place.camped else place.camped + key
        return place.copy(cells = cells, camped = camped)
    }

    private fun noteCamp(
        place: CellPlace,
        cells: MutableList<CellRecord>,
        index: Int,
        reading: CellReading,
        identity: CellIdentity,
        now: Long,
    ): CellPlace {
        val record = cells[index]
        val key = identity.towerKey()
        val seen = key in place.camped || record.identity.towerKey() in place.camped
        val camps = when {
            seen -> record.camps.coerceAtLeast(1)
            record.camps <= 0 -> 1
            else -> record.camps + 1
        }
        val firstAt = when {
            record.firstAt > 0L -> record.firstAt
            record.heardAt > 0L -> record.heardAt
            else -> now
        }
        cells[index] = record.heard(reading, identity, now).copy(camps = camps, firstAt = firstAt)
        val camped = if (seen) place.camped else place.camped + key
        return place.copy(cells = cells, camped = camped)
    }

    private fun CellRecord.heard(reading: CellReading, identity: CellIdentity, now: Long) = copy(
        identity = identity,
        pci = reading.pci ?: pci,
        channel = reading.channel ?: channel,
        signal = reading.signal ?: signal,
        timingAdvance = reading.timingAdvance ?: timingAdvance,
        heardAt = now,
    )

    private fun onlyHigh(place: CellPlace): Boolean =
        place.cells.isNotEmpty() && place.cells.all { it.identity.gen.high() }

    private fun dropKind(gen: CellGen): CellKind =
        if (gen == CellGen.GSM) CellKind.DROP2 else CellKind.DROP3

    private fun episode(book: CellBook, kind: CellKind, placeId: String, reading: CellReading, now: Long): CellEpisode {
        val identity = reading.identity!!
        return CellEpisode(
            id = book.nextEpisode,
            kind = kind,
            placeId = placeId,
            identity = identity,
            pci = reading.pci,
            channel = reading.channel,
            signal = reading.signal,
            timingAdvance = reading.timingAdvance,
            startedAt = now,
            updatedAt = now,
        )
    }

    private fun CellEpisode.reading() = CellReading(
        registered = true,
        generation = identity.gen,
        identity = identity,
        pci = pci,
        channel = channel,
        signal = signal,
        timingAdvance = timingAdvance,
    )

    private fun CellEpisode.toMark() = CellMark(
        at = startedAt,
        endedAt = endedAt,
        kind = when (kind) {
            CellKind.NEW -> "new"
            CellKind.DROP2 -> "2g"
            CellKind.DROP3 -> "3g"
        },
        gen = identity.gen.band(),
        plmn = identity.plmn,
        area = identity.area.toString(),
        cell = identity.cell.toString(),
        signal = signal,
        timingAdvance = timingAdvance,
    )

    private fun touchEpisode(book: CellBook, id: Long, reading: CellReading, now: Long): CellBook {
        val episodes = book.episodes.map { episode ->
            if (episode.id != id || !episode.open) episode
            else episode.copy(
                identity = reading.identity ?: episode.identity,
                pci = reading.pci ?: episode.pci,
                channel = reading.channel ?: episode.channel,
                signal = reading.signal ?: episode.signal,
                timingAdvance = reading.timingAdvance ?: episode.timingAdvance,
                updatedAt = now,
            )
        }
        return book.copy(episodes = episodes)
    }

    private fun endEpisode(book: CellBook, id: Long, now: Long): CellBook {
        val episodes = book.episodes.map { episode ->
            if (episode.id == id && episode.open) episode.copy(endedAt = now) else episode
        }
        return book.copy(episodes = episodes)
    }

    private fun endNewForStored(book: CellBook, place: CellPlace, now: Long): CellBook {
        val keys = place.cells.map { it.identity.key() }.toSet()
        val towers = place.cells.map { it.identity.towerKey() }.toSet()
        val episodes = book.episodes.map { episode ->
            val known = episode.identity.key() in keys || episode.identity.towerKey() in towers
            if (episode.open && episode.kind == CellKind.NEW && known) episode.copy(endedAt = now) else episode
        }
        return book.copy(episodes = episodes)
    }

    private fun ageNew(book: CellBook, reading: CellReading, now: Long): CellBook {
        val current = reading.identity?.key()
        var changed = false
        val episodes = book.episodes.map { episode ->
            val gone = episode.open && episode.kind == CellKind.NEW &&
                episode.identity.key() != current &&
                now - episode.updatedAt >= GONE_MS
            if (!gone) episode else {
                changed = true
                episode.copy(endedAt = now)
            }
        }
        return if (changed) book.copy(episodes = episodes) else book
    }

    private fun recover(book: CellBook, reading: CellReading, now: Long): CellBook {
        val openDrop = book.episodes.any { it.open && it.kind != CellKind.NEW }
        if (!openDrop && !book.dropHeld) {
            return if (book.dropClearSince == 0L) book else book.copy(dropClearSince = 0L)
        }
        if (reading.generation?.high() != true) return book.copy(dropClearSince = 0L)
        val since = if (book.dropClearSince == 0L) now else book.dropClearSince
        if (now - since < GONE_MS) return book.copy(dropClearSince = since)
        val episodes = book.episodes.map { episode ->
            if (episode.open && episode.kind != CellKind.NEW) episode.copy(endedAt = now) else episode
        }
        return book.copy(episodes = episodes, dropHeld = false, dropClearSince = 0L)
    }

    private fun touchHolds(book: CellBook, reading: CellReading, now: Long): CellBook {
        val key = reading.identity?.key() ?: return book
        var changed = false
        val holds = book.holds.map { hold ->
            if (hold.key != key) hold else {
                changed = true
                hold.copy(lastSeen = now)
            }
        }
        return if (changed) book.copy(holds = holds) else book
    }

    private fun expireHolds(book: CellBook, now: Long): CellBook {
        val holds = book.holds.filter { now - it.lastSeen < GONE_MS }
        return if (holds.size == book.holds.size) book else book.copy(holds = holds)
    }

    private fun endVisits(book: CellBook, now: Long): CellBook {
        val places = book.places.mapNotNull { place ->
            if (!place.visiting) place else finishVisit(place, now)
        }
        return if (places == book.places) book else book.copy(places = places)
    }

    private fun endOtherVisits(book: CellBook, keep: Int, now: Long): CellBook {
        val keptId = book.places[keep].id
        val places = book.places.mapNotNull { place ->
            if (place.id == keptId || !place.visiting) place else finishVisit(place, now)
        }
        return if (places == book.places) book else book.copy(places = places)
    }

    private fun finishVisit(place: CellPlace, now: Long): CellPlace? {
        val done = place.copy(
            visiting = false,
            lastVisitAt = now,
            baselined = place.baselined || place.cells.isNotEmpty(),
            camped = emptyList(),
        )
        return if (!done.baselined && done.cells.isEmpty()) null else done
    }

    private fun markInside(book: CellBook, index: Int, now: Long): CellBook {
        val place = book.places[index]
        val minute = now / 60_000L
        val stale = place.lastVisitAt > 0L && now - place.lastVisitAt >= GONE_MS
        val arriving = !place.visiting || stale
        if (!arriving && place.lastVisitAt / 60_000L == minute) return book
        return replace(
            book,
            index,
            place.copy(
                visiting = true,
                lastVisitAt = now,
                camped = if (arriving) emptyList() else place.camped,
            ),
        )
    }

    private fun moveAnchor(book: CellBook, fix: CellFix, still: Boolean): CellBook {
        val lat = fix.lat ?: return book
        val lon = fix.lon ?: return book
        if (book.anchorLat == null || !still) return book.copy(anchorLat = lat, anchorLon = lon)
        return book
    }

    private fun still(book: CellBook, fix: CellFix): Boolean {
        if (!usable(fix)) return false
        val speed = fix.speedMps
        if (speed != null && speed.isFinite() && speed >= Geo.TravelCourse.WALK_MPS) return false
        val anchorLat = book.anchorLat ?: return true
        val anchorLon = book.anchorLon ?: return true
        return Geo.meters(anchorLat, anchorLon, fix.lat!!, fix.lon!!) < Geo.TravelCourse.STEP_M
    }

    private fun usable(fix: CellFix): Boolean {
        if (fix.paused) return false
        val lat = fix.lat ?: return false
        val lon = fix.lon ?: return false
        val accuracy = fix.accuracyM ?: return false
        if (!lat.isFinite() || !lon.isFinite() || !accuracy.isFinite()) return false
        if (accuracy >= ACC_M) return false
        return abs(lat) <= 90.0 && abs(lon) <= 180.0
    }

    private fun nearest(book: CellBook, fix: CellFix): Int {
        val lat = fix.lat ?: return -1
        val lon = fix.lon ?: return -1
        var best = -1
        var bestM = NEAR_M
        book.places.forEachIndexed { index, place ->
            val meters = Geo.meters(place.lat, place.lon, lat, lon)
            if (meters <= bestM) {
                best = index
                bestM = meters
            }
        }
        return best
    }

    private fun replace(book: CellBook, index: Int, place: CellPlace): CellBook {
        val places = book.places.toMutableList()
        places[index] = place
        return book.copy(places = places)
    }

    private fun trim(book: CellBook): CellBook {
        val open = book.episodes.filter { it.open }
        val closed = book.episodes.filter { !it.open }
        if (closed.size <= CLOSED_CAP) return book
        return book.copy(episodes = closed.sortedBy { it.startedAt }.takeLast(CLOSED_CAP) + open)
    }
}

object CellCopy {
    fun sentence(kind: CellKind): String = LanguageText.text(
        when (kind) {
            CellKind.NEW -> "cell.sentence.new"
            CellKind.DROP2 -> "cell.sentence.2g"
            CellKind.DROP3 -> "cell.sentence.3g"
        },
    )

    fun body(kind: CellKind): String = LanguageText.text(
        when (kind) {
            CellKind.NEW -> "cell.body.new"
            CellKind.DROP2 -> "cell.body.2g"
            CellKind.DROP3 -> "cell.body.3g"
        },
    )

    fun details(episode: CellEpisode): String = buildString {
        line("cell.gen", episode.identity.gen.band())
        line("cell.network", episode.identity.plmn)
        line("cell.country", CellPlmn.country(episode.identity.plmn).orEmpty())
        line("cell.operator", CellPlmn.operator(episode.identity.plmn).orEmpty())
        line("cell.area", episode.identity.area.toString())
        line("cell.cell", episode.identity.cell.toString())
        line("cell.pci", episode.pci?.toString().orEmpty())
        line("cell.channel", episode.channel?.toString().orEmpty())
        line("cell.band", episode.channel?.let { CellBand.label(episode.identity.gen, it) }.orEmpty())
    }.trimEnd()

    private fun StringBuilder.line(key: String, value: String) {
        if (value.isBlank()) return
        if (isNotEmpty()) append('\n')
        append(LanguageText.text(key))
        append(": ")
        append(value)
    }
}
