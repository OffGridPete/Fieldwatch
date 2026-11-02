package app.fieldwatch.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One stable radio heard at this phone's stops.
 *
 * A place is a cluster of this phone's GPS, about 100 m across. A second place
 * opens only after a quiet stretch, so a tag heard the whole drive stays one
 * visit. The update looks at that one record. It does not scan sits or the log.
 *
 * A fleet name is not an identity. Rotating BLE addresses are skipped unless
 * the radio already carries a stable Remote ID. Ordinary Wi-Fi and unmatched
 * Bluetooth stay out unless [accept] is asked to include them.
 */
object PlaceMemory {
    const val SEPARATION_M = 100.0
    const val QUIET_MS = 15 * 60_000L
    const val RETURN_MS = 20 * 60_000L
    const val DAY_MS = 24 * 60 * 60_000L
    /** Stops listed on the radio page, in the sit report, and on the Live filter. */
    const val WINDOW_MS = 7 * DAY_MS
    /** How long “Heard again” stays on the page. The gap itself can be longer. */
    const val RETURN_SHOW_MS = DAY_MS
    const val RETAIN_MS = WINDOW_MS
    const val MIN_PLACES = 2
    const val CONFIRM_HITS = 2
    const val MAX_PLACES = 8
    const val MAX_RECORDS = 400
    const val FORMAT = "fieldwatch-places"

    @Serializable
    data class Place(
        val lat: Double,
        val lon: Double,
        val firstAt: Long,
        val lastAt: Long,
        val count: Int,
        val name: String? = null,
    )

    @Serializable
    data class Record(
        val id: String,
        val deviceKey: String,
        val places: List<Place> = emptyList(),
        val lastHeardAt: Long = 0L,
        val returnAt: Long = 0L,
        val returnGapMs: Long = 0L,
        val sitId: String? = null,
        val localDay: Long = Long.MIN_VALUE,
        /** Last name worth showing after this radio leaves the live list. */
        val label: String = "",
    )

    data class Hear(
        val id: String,
        val deviceKey: String,
        val at: Long,
        val lat: Double?,
        val lon: Double?,
        val sitId: String?,
        val sitLabel: String?,
        val localDay: Long,
        val label: String = "",
    )

    /**
     * Stable id, or null when this radio cannot be followed across time.
     * Mine radios are out. [generic] is for tests; the phone leaves it off.
     */
    fun accept(
        device: Sighting,
        names: Map<String, String>,
        mineKeys: Set<String>,
        generic: Boolean = false,
    ): String? {
        if (device.key in mineKeys) return null
        val uas = device.payloadUasId?.trim().orEmpty()
        if (uas.isNotEmpty()) return "rid:$uas"
        if (device.kind == RadioKind.WIFI) {
            return if (generic) "wifi:${device.mac}" else null
        }
        if (MacUtil.isRandomized(device.mac)) return null
        val finder = TrackerMatch.kind(device, names) == TrackerMatch.Kind.FINDER
        if (!finder && !generic) return null
        return "ble:${device.mac}"
    }

    /**
     * Name to keep on the place record.
     * A custom name wins. Otherwise the live title, when it is a real name.
     * A blank result keeps whatever the record already stored.
     */
    fun heardLabel(device: Sighting, signatureNames: List<String>, custom: String?): String {
        val named = keptLabel("", custom.orEmpty())
        if (named.isNotEmpty() && !named.equals(device.mac, ignoreCase = true)) return named
        val title = device.listTitle(signatureNames).trim()
        return keptLabel("", title).let { if (it.equals(device.mac, ignoreCase = true)) "" else it }
    }

    fun note(prev: Record?, hear: Hear): Record {
        val at = hear.at
        val gap = if (prev == null) 0L else (at - prev.lastHeardAt).coerceAtLeast(0L)
        var returnAt = prev?.returnAt ?: 0L
        var returnGap = prev?.returnGapMs ?: 0L
        val sameDay = prev != null && prev.localDay == hear.localDay
        val sameSit = prev != null && hear.sitId != null && hear.sitId == prev.sitId
        if (prev != null && gap >= RETURN_MS && (sameDay || sameSit)) {
            returnAt = at
            returnGap = gap
        }
        val places = when {
            hear.lat == null || hear.lon == null -> prev?.places.orEmpty()
            prev == null || prev.places.isEmpty() -> listOf(openPlace(hear))
            else -> {
                val near = nearest(prev.places, hear.lat, hear.lon)
                when {
                    near != null -> cap(prev.places.map { if (it == near) touch(it, hear) else it })
                    gap >= QUIET_MS -> cap(prev.places + openPlace(hear))
                    else -> prev.places
                }
            }
        }
        return Record(
            id = hear.id,
            deviceKey = hear.deviceKey,
            places = places,
            lastHeardAt = at,
            returnAt = returnAt,
            returnGapMs = returnGap,
            sitId = hear.sitId,
            localDay = hear.localDay,
            label = keptLabel(prev?.label, hear.label),
        )
    }

    fun prune(record: Record, now: Long): Record? {
        if (now - record.lastHeardAt > RETAIN_MS) return null
        val cut = now - RETAIN_MS
        val places = record.places.filter { it.lastAt >= cut }
        return if (places.size == record.places.size) record else record.copy(places = places)
    }

    fun placeCount(record: Record, now: Long): Int = counted(record, now).size

    fun placesInWindow(record: Record, now: Long): List<Place> = counted(record, now)

    fun detailLines(
        record: Record,
        now: Long,
        zone: ZoneId,
        demo: Boolean = false,
        streets: Map<String, String> = emptyMap(),
    ): List<String> {
        val counted = counted(record, now).sortedBy { it.firstAt }
        val lines = ArrayList<String>(counted.size + 2)
        if (counted.size >= MIN_PLACES) {
            val today = counted.all { onSameDay(it.firstAt, now, zone) && onSameDay(it.lastAt, now, zone) }
            lines += if (today) {
                "Seen at ${counted.size} places today"
            } else {
                "Seen at ${counted.size} places"
            }
            val named = if (demo) emptyMap() else streets
            counted.forEach { lines += placeLine(it, zone, now, demo, named) }
        }
        val again = returnLine(record, now)
        if (again.isNotEmpty()) lines += again
        return lines
    }

    /** Time, sit name, street, and coordinate kept apart for the places report. */
    internal fun visitParts(
        place: Place,
        now: Long,
        zone: ZoneId,
        demo: Boolean,
        streets: Map<String, String>,
    ): VisitParts {
        val coord = if (demo) "masked" else Geo.screenCoord(place.lat, place.lon, false).orEmpty()
        val street = if (demo) "" else Geo.nameNear(place.lat, place.lon, streets, SEPARATION_M).orEmpty()
        return VisitParts(
            time = span(place, zone, now, spaced = true),
            name = blankToNull(place.name).orEmpty(),
            street = street,
            coord = coord,
        )
    }

    /** Quiet return, or blank. The places report shows this under the numbered stops. */
    internal fun returnLine(record: Record, now: Long): String {
        if (record.returnAt > 0L &&
            record.returnGapMs >= RETURN_MS &&
            now - record.returnAt in 0..RETURN_SHOW_MS
        ) {
            return "Heard again after ${gapPhrase(record.returnGapMs)}"
        }
        return ""
    }

    /**
     * Sit-report body for radios in [devices] that already have two places.
     * Null when there is nothing to add.
     */
    fun section(
        devices: List<Sighting>,
        records: List<Record>,
        names: Map<String, String>,
        customNames: Map<String, String>,
        mineKeys: Set<String>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        demo: Boolean = false,
        streets: Map<String, String> = emptyMap(),
    ): String? {
        if (records.isEmpty() || devices.isEmpty()) return null
        val byId = HashMap<String, Record>(records.size)
        for (record in records) byId[record.id] = record
        val seen = HashSet<String>()
        val blocks = ArrayList<Pair<Int, String>>()
        for (device in devices) {
            val id = accept(device, names, mineKeys) ?: continue
            if (!seen.add(id)) continue
            val record = byId[id] ?: continue
            val lines = detailLines(record, now, zone, demo, streets)
            if (lines.none { it.startsWith("Seen at ") }) continue
            val count = placeCount(record, now)
            val text = buildString {
                append(device.reportName(customNames))
                append("  ")
                append(device.mac)
                lines.forEach {
                    append('\n')
                    append("  ")
                    append(it)
                }
            }
            blocks += count to text
        }
        if (blocks.isEmpty()) return null
        blocks.sortByDescending { it.first }
        return buildString {
            append("A stable address heard at two or more places in the last 7 days. ")
            append("The times are when this phone heard it. ")
            append("The coordinates are where this phone was.")
            blocks.forEach { (_, text) ->
                append('\n')
                append(text)
            }
        }
    }

    private fun counted(record: Record, now: Long): List<Place> {
        val cut = now - WINDOW_MS
        return record.places.filter { it.count >= CONFIRM_HITS && it.lastAt >= cut }
    }

    private fun nearest(places: List<Place>, lat: Double, lon: Double): Place? {
        var best: Place? = null
        var bestM = SEPARATION_M
        for (place in places) {
            val meters = Geo.meters(place.lat, place.lon, lat, lon)
            if (meters < bestM) {
                best = place
                bestM = meters
            }
        }
        return best
    }

    private fun openPlace(hear: Hear): Place = Place(
        lat = hear.lat ?: 0.0,
        lon = hear.lon ?: 0.0,
        firstAt = hear.at,
        lastAt = hear.at,
        count = 1,
        name = blankToNull(hear.sitLabel),
    )

    private fun touch(place: Place, hear: Hear): Place = place.copy(
        lastAt = hear.at,
        count = place.count + 1,
        name = place.name ?: blankToNull(hear.sitLabel),
    )

    /**
     * Keep the latest [MAX_PLACES] confirmed stops. One unconfirmed stop may sit
     * past that until the second hear, so a single jump does not erase an older stop.
     * The next confirmed stop drops the one heard longest ago.
     */
    private fun cap(places: List<Place>): List<Place> {
        val out = places.toMutableList()
        while (out.size > MAX_PLACES) {
            val unconfirmed = out.filter { it.count < CONFIRM_HITS }
            val confirmed = out.filter { it.count >= CONFIRM_HITS }
            if (unconfirmed.size == 1 && confirmed.size <= MAX_PLACES) break
            val drop = when {
                unconfirmed.size > 1 -> unconfirmed.minBy { it.lastAt }
                confirmed.isNotEmpty() -> confirmed.minBy { it.lastAt }
                else -> unconfirmed.minBy { it.lastAt }
            }
            out.remove(drop)
        }
        return out
    }

    internal data class VisitParts(
        val time: String,
        val name: String,
        val street: String,
        val coord: String,
    )

    private fun placeLine(
        place: Place,
        zone: ZoneId,
        now: Long,
        demo: Boolean,
        streets: Map<String, String>,
    ): String {
        val parts = visitParts(place, now, zone, demo, streets)
        val time = span(place, zone, now, spaced = false)
        val head = if (parts.name.isEmpty()) time else "${parts.name} · $time"
        if (parts.coord.isEmpty()) return head
        return if (parts.street.isEmpty()) "$head · ${parts.coord}" else "$head · ${parts.street} · ${parts.coord}"
    }

    private fun span(place: Place, zone: ZoneId, now: Long, spaced: Boolean): String {
        val dash = if (spaced) " – " else "–"
        val dated = !onSameDay(place.firstAt, now, zone) || !onSameDay(place.lastAt, now, zone)
        val start = formatAt(place.firstAt, zone, dated)
        val endSame = formatAt(place.lastAt, zone, dated)
        val endClock = clock.format(Instant.ofEpochMilli(place.lastAt).atZone(zone))
        return when {
            start == endSame -> start
            dated && onSameDay(place.firstAt, place.lastAt, zone) -> start + dash + endClock
            dated -> start + dash + formatAt(place.lastAt, zone, true)
            else -> if (start == endClock) start else start + dash + endClock
        }
    }

    private fun onSameDay(at: Long, now: Long, zone: ZoneId): Boolean =
        localDate(at, zone) == localDate(now, zone)

    /** LocalDate.ofInstant is API 31. minSdk is 29. */
    internal fun localDate(atMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate()

    private fun formatAt(at: Long, zone: ZoneId, dated: Boolean): String {
        val zoned = Instant.ofEpochMilli(at).atZone(zone)
        return if (dated) dayClock.format(zoned) else clock.format(zoned)
    }

    private fun gapPhrase(ms: Long): String {
        val minutes = ((ms + 30_000L) / 60_000L).toInt().coerceAtLeast(1)
        if (minutes < 120) return if (minutes == 1) "1 minute" else "$minutes minutes"
        val hours = (minutes + 30) / 60
        if (hours < 48) return if (hours == 1) "1 hour" else "$hours hours"
        val days = (hours + 12) / 24
        return if (days == 1) "1 day" else "$days days"
    }

    private fun keptLabel(prev: String?, next: String): String {
        val fresh = next.trim()
        if (fresh.isNotEmpty() && fresh != "unnamed LE") return fresh
        val older = prev?.trim().orEmpty()
        return if (older == "unnamed LE") "" else older
    }

    private fun blankToNull(raw: String?): String? = raw?.trim()?.ifBlank { null }

    private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val dayClock: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US)
}
