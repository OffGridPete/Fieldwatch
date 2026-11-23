package app.fieldwatch.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Remote ID aircraft this phone has heard.
 *
 * The identity is the UAS ID. Bluetooth and Wi-Fi for that ID are one aircraft.
 * A pass is one visit. The next pass starts after [QUIET_MS] of quiet.
 * One house is one place, so a daily pass still counts. The window is 14 days.
 */
object AircraftHistory {
    const val QUIET_MS = 20 * 60_000L
    const val DAY_MS = 24 * 60 * 60_000L
    const val WINDOW_MS = 14 * DAY_MS
    const val MAX_PASSES = 48
    const val MAX_RECORDS = 80
    const val MAX_ID = 80
    const val MAX_LABEL = 64
    const val FORMAT = "fieldwatch-aircraft"

    @Serializable
    data class Pass(
        val start: Long,
        val end: Long,
        val lat: Double? = null,
        val lon: Double? = null,
        val hasPosition: Boolean = false,
        val opKind: Int? = null,
        val ble: Boolean = false,
        val wifi: Boolean = false,
        /** Advertised aircraft points. The phone's fix stays in [lat] and [lon]. */
        val fixes: List<PayloadFix> = emptyList(),
        val pilotLat: Double? = null,
        val pilotLon: Double? = null,
        /** Times an emergency was declared during this visit. Empty on a visit that had none. */
        val emergencies: List<Emergency> = emptyList(),
        /**
         * This hear's Location status was not an emergency, and it sent no emergency description.
         * Consumed when this hear joins the visit. A stored visit leaves it false.
         */
        val clearEmergency: Boolean = false,
    )

    /** One declared emergency. [open] stays true until a later Location message clears it. */
    @Serializable
    data class Emergency(
        val start: Long,
        val end: Long,
        val lat: Double? = null,
        val lon: Double? = null,
        val text: String = "",
        val open: Boolean = false,
    )

    @Serializable
    data class Record(
        val id: String,
        val passes: List<Pass> = emptyList(),
        val label: String = "",
        val selfId: String = "",
    )

    data class Hear(
        val id: String,
        val at: Long,
        val lat: Double? = null,
        val lon: Double? = null,
        val hasPosition: Boolean = false,
        val opKind: Int? = null,
        val ble: Boolean = false,
        val wifi: Boolean = false,
        val label: String = "",
        val selfId: String = "",
        /** One advertised point from this hear. Empty when the broadcast had no position. */
        val fix: PayloadFix? = null,
        val pilotLat: Double? = null,
        val pilotLon: Double? = null,
        /**
         * True when this advertisement declared an emergency.
         * False when a Location message cleared one. Null when this advertisement said neither.
         */
        val emergency: Boolean? = null,
        /** Emergency self-ID text from this advertisement. Blank when the declaration had no words. */
        val emergencyText: String = "",
    )

    /** One map. [passes] are indexes into the record, oldest first. */
    data class MapPicture(
        val passes: List<Int>,
        val model: SitPathPlot.Model,
    )

    sealed class Earlier {
        data class Clock(val at: Long) : Earlier()
        data object Yesterday : Earlier()
        data class Date(val at: Long) : Earlier()
    }

    data class Live(
        val days: Int,
        val times: Int,
        val earlier: Earlier?,
    )

    fun idOf(raw: String?): String? {
        val id = raw?.trim().orEmpty()
        if (id.isEmpty() || id.length > MAX_ID) return null
        return id
    }

    fun accept(device: Sighting): String? = idOf(device.payloadUasId)

    fun hearOf(device: Sighting, at: Long): Hear? {
        val id = accept(device) ?: return null
        val phone = PayloadLocation.validCoord(device.latitude, device.longitude)
        val op = PayloadLocation.validCoord(device.payloadOpLat, device.payloadOpLon)
        val positioned = PayloadLocation.validCoord(device.payloadLat, device.payloadLon)
        val text = device.payloadEmergencyText
        val declared = device.payloadStatus == OpenDroneId.STATUS_EMERGENCY || text != null
        val cleared = device.payloadStatus != null &&
            device.payloadStatus != OpenDroneId.STATUS_EMERGENCY &&
            text == null
        return Hear(
            id = id,
            at = at,
            lat = if (phone) device.latitude else null,
            lon = if (phone) device.longitude else null,
            hasPosition = positioned,
            opKind = if (op) device.payloadOpKind else null,
            ble = device.kind == RadioKind.BLE,
            wifi = device.kind == RadioKind.WIFI,
            label = clip(device.payloadAircraft),
            selfId = clip(device.payloadSelfId),
            fix = if (!positioned) {
                null
            } else {
                PayloadFix(
                    at = at,
                    lat = device.payloadLat!!,
                    lon = device.payloadLon!!,
                    alt = device.payloadAlt,
                    heading = device.payloadHeading,
                    speed = device.payloadSpeed,
                    emergency = declared,
                )
            },
            pilotLat = if (op) device.payloadOpLat else null,
            pilotLon = if (op) device.payloadOpLon else null,
            emergency = when {
                declared -> true
                cleared -> false
                else -> null
            },
            emergencyText = text?.let(::clip).orEmpty(),
        )
    }

    /** Whole minutes. A visit shorter than a minute is 0. */
    fun lengthMinutes(start: Long, end: Long): Int {
        val span = (end - start).coerceAtLeast(0L)
        return (span / 60_000L).toInt()
    }

    /**
     * Maps for one aircraft. Visits within [AircraftTrail.NEAR_M] share a picture.
     * A farther visit is its own picture. A visit with no advertised point is left out.
     */
    fun pictures(passes: List<Pass>): List<MapPicture> {
        val mapped = passes.indices.filter { passes[it].fixes.isNotEmpty() }
        if (mapped.isEmpty()) return emptyList()
        val parent = IntArray(mapped.size) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        for (a in mapped.indices) {
            for (b in a + 1 until mapped.size) {
                if (near(passes[mapped[a]], passes[mapped[b]])) {
                    val rb = find(b)
                    val ra = find(a)
                    if (ra != rb) parent[rb] = ra
                }
            }
        }
        return mapped.indices.groupBy { find(it) }.values.map { members ->
            val indexes = members.map { mapped[it] }.sorted()
            MapPicture(indexes, pictureModel(passes, indexes))
        }.sortedByDescending { picture -> picture.passes.maxOf { passes[it].end } }
    }

    /** Color for a visit that has a path. Zero when that visit has no advertised point. */
    fun colorAt(passes: List<Pass>, index: Int): Long {
        if (index !in passes.indices || passes[index].fixes.isEmpty()) return 0L
        var ordinal = 0
        for (i in passes.indices) {
            if (passes[i].fixes.isEmpty()) continue
            if (i == index) return PlaceReport.colorAt(ordinal)
            ordinal++
        }
        return 0L
    }

    fun note(prev: Record?, hear: Hear, now: Long): Record? {
        val kept = prunePasses(prev?.passes.orEmpty(), now)
        val added = coalesce(kept + point(hear)).takeLast(MAX_PASSES)
        return prune(
            Record(
                id = hear.id,
                passes = added,
                label = hear.label.ifBlank { prev?.label.orEmpty() },
                selfId = hear.selfId.ifBlank { prev?.selfId.orEmpty() },
            ),
            now,
        )
    }

    fun prune(record: Record?, now: Long): Record? {
        if (record == null) return null
        val kept = prunePasses(record.passes, now)
        if (kept.isEmpty()) return null
        return record.copy(passes = kept)
    }

    fun live(record: Record, at: Long, now: Long, zone: ZoneId): Live? {
        val passes = prune(record, now)?.passes ?: return null
        if (passes.isEmpty()) return null
        val days = passes.map { localDate(it.start, zone).toEpochDay() }.distinct().size
        val idx = passes.indexOfLast { at >= it.start && at <= it.end + QUIET_MS }
        val current = if (idx >= 0) idx else passes.lastIndex
        val prev = passes.getOrNull(current - 1)
        return Live(
            days = days,
            times = passes.size,
            earlier = prev?.let { earlier(it.end, passes[current].start, zone) },
        )
    }

    fun earlier(previousAt: Long, currentAt: Long, zone: ZoneId): Earlier {
        val days = ChronoUnit.DAYS.between(localDate(previousAt, zone), localDate(currentAt, zone))
        return when {
            days <= 0L -> Earlier.Clock(previousAt)
            days == 1L -> Earlier.Yesterday
            else -> Earlier.Date(previousAt)
        }
    }

    fun localDate(atMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate()

    private fun prunePasses(passes: List<Pass>, now: Long): List<Pass> {
        val cutoff = now - WINDOW_MS
        return passes.filter { it.end >= cutoff }.takeLast(MAX_PASSES)
    }

    private fun point(hear: Hear): Pass {
        val declared = hear.emergency == true
        val flagged = hear.fix?.let { if (declared) it.copy(emergency = true) else it }
        return Pass(
            start = hear.at,
            end = hear.at,
            lat = hear.lat,
            lon = hear.lon,
            hasPosition = hear.hasPosition,
            opKind = hear.opKind,
            ble = hear.ble,
            wifi = hear.wifi,
            fixes = flagged?.let { AircraftTrail.append(emptyList(), it) }.orEmpty(),
            pilotLat = hear.pilotLat,
            pilotLon = hear.pilotLon,
            emergencies = if (declared) {
                listOf(
                    Emergency(
                        start = hear.at,
                        end = hear.at,
                        lat = flagged?.lat,
                        lon = flagged?.lon,
                        text = clip(hear.emergencyText),
                        open = true,
                    ),
                )
            } else {
                emptyList()
            },
            clearEmergency = hear.emergency == false,
        )
    }

    /**
     * Points on [pass] that belong to a declared emergency.
     * A kept point is included when it was flagged, when its time falls in a span,
     * or when it is the stored point within [AircraftTrail.MIN_M] of where the emergency was declared.
     */
    fun emergencyIndexes(pass: Pass): List<Int> {
        if (pass.fixes.isEmpty()) return emptyList()
        val hit = LinkedHashSet<Int>()
        for (i in pass.fixes.indices) {
            val fix = pass.fixes[i]
            if (fix.emergency || pass.emergencies.any { fix.at in it.start..it.end }) hit += i
        }
        for (span in pass.emergencies) {
            val lat = span.lat ?: continue
            val lon = span.lon ?: continue
            val already = hit.any { i ->
                Geo.meters(pass.fixes[i].lat, pass.fixes[i].lon, lat, lon) <= AircraftTrail.MIN_M
            }
            if (already) continue
            var best = -1
            var bestM = Double.MAX_VALUE
            for (i in pass.fixes.indices) {
                val meters = Geo.meters(pass.fixes[i].lat, pass.fixes[i].lon, lat, lon)
                if (meters < bestM) {
                    bestM = meters
                    best = i
                }
            }
            if (best >= 0 && bestM <= AircraftTrail.MIN_M) hit += best
        }
        return hit.sorted()
    }

    private fun coalesce(passes: List<Pass>): List<Pass> {
        if (passes.isEmpty()) return passes
        val sorted = passes.sortedBy { it.start }
        val out = ArrayList<Pass>(sorted.size)
        var cur = sorted[0]
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            cur = if (next.start - cur.end < QUIET_MS) merge(cur, next) else {
                out += stored(cur)
                next
            }
        }
        out += stored(cur)
        return out
    }

    /** [Pass.clearEmergency] is only a signal for the hear that joins a visit. */
    private fun stored(pass: Pass): Pass =
        if (pass.clearEmergency) pass.copy(clearEmergency = false) else pass

    private fun merge(older: Pass, newer: Pass) = Pass(
        start = minOf(older.start, newer.start),
        end = maxOf(older.end, newer.end),
        lat = newer.lat ?: older.lat,
        lon = newer.lon ?: older.lon,
        hasPosition = older.hasPosition || newer.hasPosition,
        opKind = newer.opKind ?: older.opKind,
        ble = older.ble || newer.ble,
        wifi = older.wifi || newer.wifi,
        fixes = newer.fixes.fold(older.fixes) { trail, fix -> AircraftTrail.append(trail, fix) },
        pilotLat = if (newer.pilotLat != null && newer.pilotLon != null) newer.pilotLat else older.pilotLat,
        pilotLon = if (newer.pilotLat != null && newer.pilotLon != null) newer.pilotLon else older.pilotLon,
        emergencies = joinEmergencies(older.emergencies, newer.emergencies, newer.clearEmergency),
    )

    private fun joinEmergencies(
        older: List<Emergency>,
        newer: List<Emergency>,
        clear: Boolean,
    ): List<Emergency> {
        val base = ArrayList<Emergency>(older.size + newer.size)
        base += older
        if (clear) {
            val last = base.lastOrNull()
            if (last != null && last.open) base[base.lastIndex] = last.copy(open = false)
        }
        for (span in newer) {
            val last = base.lastOrNull()
            if (last != null && last.open) {
                base[base.lastIndex] = last.copy(
                    end = maxOf(last.end, span.end),
                    lat = last.lat ?: span.lat,
                    lon = last.lon ?: span.lon,
                    text = span.text.ifBlank { last.text },
                    open = true,
                )
            } else {
                base += span
            }
        }
        return if (base.size <= 16) base else base.takeLast(16)
    }

    private fun near(a: Pass, b: Pass): Boolean {
        for (left in a.fixes) {
            for (right in b.fixes) {
                if (Geo.meters(left.lat, left.lon, right.lat, right.lon) <= AircraftTrail.NEAR_M) return true
            }
        }
        return false
    }

    private fun pictureModel(passes: List<Pass>, indexes: List<Int>): SitPathPlot.Model {
        val tracks = indexes.map { index ->
            val pass = passes[index]
            SitPathPlot.FigureTrack(
                name = "",
                samples = AircraftTrail.samples(pass.fixes),
                aircraft = true,
                headingDeg = pass.fixes.lastOrNull()?.heading,
                color = colorAt(passes, index),
                emergency = emergencyIndexes(pass),
            )
        }
        val pilots = indexes.mapNotNull { pilotMark(passes[it]) }
        val samples = tracks.flatMap { it.samples }
        val fig = SitPathPlot.Figure(
            kicker = "",
            tracks = tracks,
            dots = emptyList(),
            lengthM = indexes.sumOf { AircraftTrail.lengthM(passes[it].fixes) },
            spanM = Geo.spanM(samples),
            caption = "",
            pilots = pilots,
        )
        return AircraftTrail.cardModel(fig)
    }

    private fun pilotMark(pass: Pass): SitPathPlot.Mark? {
        val lat = pass.pilotLat ?: return null
        val lon = pass.pilotLon ?: return null
        if (!PayloadLocation.validCoord(lat, lon)) return null
        val close = pass.fixes.any { Geo.meters(it.lat, it.lon, lat, lon) <= AircraftTrail.NEAR_M }
        if (!close) return null
        return SitPathPlot.Mark(lat, lon, OperatorLoc.label(pass.opKind))
    }

    private fun clip(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return ""
        return text.take(MAX_LABEL)
    }
}
