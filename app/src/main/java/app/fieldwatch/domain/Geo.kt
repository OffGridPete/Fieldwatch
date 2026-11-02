package app.fieldwatch.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    /** Corner label holds inside this. Ordinary GPS noise is smaller. A real step leaves it. */
    private const val MAP_FOOTER_HOLD_M = 15.0

    fun meters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Degrees clockwise from true north, from the first point to the second. */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        val wrapped = Math.toDegrees(atan2(y, x)) % 360.0
        return if (wrapped < 0.0) wrapped + 360.0 else wrapped
    }

    /**
     * Which way this phone is moving, for turning the map.
     * A GPS bearing counts while speed is at least [TravelCourse.WALK_MPS].
     * Otherwise a step of [TravelCourse.STEP_M] sets it. Standing still, a short
     * step, and a turn in place keep the last course. The first fix only marks a place.
     */
    class TravelCourse {
        var degrees: Double = Double.NaN
            private set

        private var anchorLat = Double.NaN
        private var anchorLon = Double.NaN

        @Synchronized
        fun note(
            lat: Double,
            lon: Double,
            speedMps: Float?,
            bearingDeg: Float?,
            bearingAccuracyDeg: Float?,
        ): Double {
            if (!lat.isFinite() || !lon.isFinite()) return degrees
            val bearing = bearingDeg
            val acc = bearingAccuracyDeg
            val moving = speedMps != null && speedMps.isFinite() && speedMps >= TravelCourse.WALK_MPS
            val accOk = acc == null || (acc.isFinite() && acc <= TravelCourse.BEARING_ACC_MAX)
            val trusted = if (bearing != null && bearing.isFinite() && accOk) bearing else null
            if (moving && trusted != null) {
                degrees = wrap(trusted.toDouble())
                anchorLat = lat
                anchorLon = lon
                return degrees
            }
            if (!anchorLat.isFinite() || !anchorLon.isFinite()) {
                anchorLat = lat
                anchorLon = lon
                return degrees
            }
            if (meters(anchorLat, anchorLon, lat, lon) >= TravelCourse.STEP_M) {
                degrees = bearingDegrees(anchorLat, anchorLon, lat, lon)
                anchorLat = lat
                anchorLon = lon
            }
            return degrees
        }

        private fun wrap(deg: Double): Double {
            val turned = deg % 360.0
            return if (turned < 0.0) turned + 360.0 else turned
        }

        companion object {
            const val WALK_MPS = 0.7f
            const val BEARING_ACC_MAX = 45f
            const val STEP_M = 4.0
        }
    }

    fun midpoint(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Pair<Double, Double> {
        val p1 = Math.toRadians(lat1)
        val l1 = Math.toRadians(lon1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val bx = cos(p2) * cos(dl)
        val by = cos(p2) * sin(dl)
        val lat = atan2(
            sin(p1) + sin(p2),
            sqrt((cos(p1) + bx) * (cos(p1) + bx) + by * by),
        )
        val lon = l1 + atan2(by, cos(p1) + bx)
        return Math.toDegrees(lat) to Math.toDegrees(lon)
    }

    fun pathLengthM(samples: List<GpsSample>): Double {
        var sum = 0.0
        for (i in 1 until samples.size) {
            sum += meters(samples[i - 1].lat, samples[i - 1].lon, samples[i].lat, samples[i].lon)
        }
        return sum
    }

    fun spanM(samples: List<GpsSample>): Double {
        if (samples.size < 2) return 0.0
        var max = 0.0
        for (i in samples.indices) {
            for (j in i + 1 until samples.size) {
                val d = meters(samples[i].lat, samples[i].lon, samples[j].lat, samples[j].lon)
                if (d > max) max = d
            }
        }
        return max
    }

    /**
     * Width of a path for the number on the path screen.
     * A short path uses [spanM]. A long drive uses the farthest of the
     * north, south, east, and west fixes, which is the same span for a road.
     */
    fun reachM(samples: List<GpsSample>): Double {
        if (samples.size < 2) return 0.0
        if (samples.size <= REACH_EXACT) return spanM(samples)
        var south = samples[0]
        var north = samples[0]
        var west = samples[0]
        var east = samples[0]
        for (sample in samples) {
            if (sample.lat < south.lat) south = sample
            if (sample.lat > north.lat) north = sample
            if (sample.lon < west.lon) west = sample
            if (sample.lon > east.lon) east = sample
        }
        val ends = arrayOf(south, north, west, east)
        var best = 0.0
        for (end in ends) {
            val far = farthest(samples, end)
            val span = meters(end.lat, end.lon, far.lat, far.lon)
            if (span > best) best = span
            val far2 = farthest(samples, far)
            val span2 = meters(far.lat, far.lon, far2.lat, far2.lon)
            if (span2 > best) best = span2
        }
        return best
    }

    private fun farthest(samples: List<GpsSample>, from: GpsSample): GpsSample {
        var best = from
        var bestM = 0.0
        for (sample in samples) {
            val span = meters(from.lat, from.lon, sample.lat, sample.lon)
            if (span > bestM) {
                bestM = span
                best = sample
            }
        }
        return best
    }

    /**
     * True when some pair is at least [limitM] apart.
     * A drive stops at the first far pair. A path still inside one place is one pass.
     */
    fun widerThan(samples: List<GpsSample>, limitM: Double): Boolean {
        val n = samples.size
        if (n < 2 || limitM <= 0.0 || !limitM.isFinite()) return false
        var minLat = samples[0].lat
        var maxLat = samples[0].lat
        var minLon = samples[0].lon
        var maxLon = samples[0].lon
        var south = 0
        var north = 0
        var west = 0
        var east = 0
        for (i in 1 until n) {
            val s = samples[i]
            var grew = false
            if (s.lat < minLat) {
                minLat = s.lat
                south = i
                grew = true
            } else if (s.lat > maxLat) {
                maxLat = s.lat
                north = i
                grew = true
            }
            if (s.lon < minLon) {
                minLon = s.lon
                west = i
                grew = true
            } else if (s.lon > maxLon) {
                maxLon = s.lon
                east = i
                grew = true
            }
            if (grew && extremePairAtLeast(samples, south, north, west, east, limitM)) return true
        }
        val corner = maxOf(
            meters(minLat, minLon, maxLat, maxLon),
            meters(minLat, maxLon, maxLat, minLon),
        )
        if (corner < limitM) return false
        return spanM(samples) >= limitM
    }

    /** Pairs that include a point at or after [fromIndex]. Earlier points were already inside [limitM]. */
    fun widerThanNew(samples: List<GpsSample>, fromIndex: Int, limitM: Double): Boolean {
        if (samples.size < 2 || limitM <= 0.0 || !limitM.isFinite()) return false
        val start = fromIndex.coerceIn(0, samples.size)
        if (start <= 1) return widerThan(samples, limitM)
        for (i in start until samples.size) {
            val a = samples[i]
            for (j in 0 until i) {
                if (meters(a.lat, a.lon, samples[j].lat, samples[j].lon) >= limitM) return true
            }
        }
        return false
    }

    private fun extremePairAtLeast(
        samples: List<GpsSample>,
        south: Int,
        north: Int,
        west: Int,
        east: Int,
        limitM: Double,
    ): Boolean {
        val idx = intArrayOf(south, north, west, east)
        for (i in 0 until 3) {
            for (j in i + 1 until 4) {
                if (idx[i] == idx[j]) continue
                val p = samples[idx[i]]
                val q = samples[idx[j]]
                if (meters(p.lat, p.lon, q.lat, q.lon) >= limitM) return true
            }
        }
        return false
    }

    /**
     * One stretch of the operator path: a sit (clustered within [stayM]) or a
     * hop between sits. Coordinates are the phone at hear-time.
     */
    data class PathLeg(
        val stay: Boolean,
        val startAt: Long,
        val endAt: Long,
        val lat: Double,
        val lon: Double,
        val endLat: Double,
        val endLon: Double,
        val samples: Int,
        val pathM: Double,
    ) {
        val durationMs: Long get() = (endAt - startAt).coerceAtLeast(0L)
        fun cellKey(): String = Geo.cellKey(lat, lon)
    }

    fun cellKey(lat: Double, lon: Double): String =
        "%.4f,%.4f".format(java.util.Locale.US, lat, lon)

    /** Exact GPS cell, then the nearest named cell within [maxM]. */
    fun nameNear(lat: Double, lon: Double, namesByCell: Map<String, String>, maxM: Double = 90.0): String? {
        if (namesByCell.isEmpty()) return null
        namesByCell[cellKey(lat, lon)]?.let { return it }
        var best: String? = null
        var bestD = maxM
        for ((key, name) in namesByCell) {
            val parts = key.split(',')
            if (parts.size != 2) continue
            val klat = parts[0].toDoubleOrNull() ?: continue
            val klon = parts[1].toDoubleOrNull() ?: continue
            val d = meters(lat, lon, klat, klon)
            if (d < bestD) {
                bestD = d
                best = name
            }
        }
        return best
    }

    fun screenCoord(lat: Double?, lon: Double?, demo: Boolean): String? {
        if (lat == null || lon == null) return null
        if (demo) return "masked"
        return "%.5f, %.5f".format(Locale.US, lat, lon)
    }

    /** 1 m MGRS beside a decimal. Privacy masks it. Polar latitudes have no grid. */
    fun screenGrid(lat: Double?, lon: Double?, demo: Boolean): String? {
        if (!PayloadLocation.validCoord(lat, lon)) return null
        if (demo) return "masked"
        return Mgrs.format(lat!!, lon!!)
    }

    /** Lower-left map line for this phone now. Off, or no fix, stays blank. */
    fun mapFooterText(mode: MapFooter, lat: Double?, lon: Double?, demo: Boolean): String? {
        if (mode == MapFooter.OFF) return null
        if (!PayloadLocation.validCoord(lat, lon)) return null
        return when (mode) {
            MapFooter.OFF -> null
            MapFooter.GPS -> screenCoord(lat, lon, demo)
            MapFooter.GRID -> screenGrid(lat, lon, demo)
        }
    }

    /**
     * Same corner line, held still while the phone wanders inside [MAP_FOOTER_HOLD_M].
     * A 1 m grid rewrites on ordinary GPS noise. A choice change, privacy, or no fix applies at once.
     */
    fun settleMapFooter(
        previous: MapFooterHold?,
        mode: MapFooter,
        lat: Double?,
        lon: Double?,
        demo: Boolean,
    ): MapFooterHold? {
        val text = mapFooterText(mode, lat, lon, demo) ?: return null
        if (lat == null || lon == null) return null
        val prev = previous
        if (
            prev != null &&
            prev.mode == mode &&
            prev.demo == demo &&
            meters(prev.lat, prev.lon, lat, lon) < MAP_FOOTER_HOLD_M
        ) {
            return prev
        }
        return MapFooterHold(mode, demo, lat, lon, text)
    }

    class MapFooterHold(
        val mode: MapFooter,
        val demo: Boolean,
        val lat: Double,
        val lon: Double,
        val text: String,
    )

    /**
     * Distance and compass bearing from the phone to an advertised point.
     * Under 5 m the bearing is left off. Privacy leaves the whole line off.
     */
    fun liveRange(
        phoneLat: Double?,
        phoneLon: Double?,
        lat: Double?,
        lon: Double?,
        demo: Boolean,
    ): String? {
        if (demo) return null
        val parts = rangeParts(phoneLat, phoneLon, lat, lon) ?: return null
        if (parts.second == null) return parts.first
        return LanguageText.text(
            "ui.range.bearing",
            mapOf("distance" to parts.first, "degrees" to parts.second!!),
        )
    }

    /**
     * Report line. [at] is when that position was read, and the phone
     * coordinates are where this phone was then. Not where the reader is now.
     */
    fun reportRange(
        phoneLat: Double?,
        phoneLon: Double?,
        lat: Double?,
        lon: Double?,
        at: Long?,
    ): String? {
        if (at == null || at <= 0L) return null
        val parts = rangeParts(phoneLat, phoneLon, lat, lon) ?: return null
        val whenText = SimpleDateFormat("HH:mm", Locale.US).format(Date(at))
        val bearing = parts.second
        return if (bearing == null) {
            "${parts.first}, from this phone at $whenText"
        } else {
            "${parts.first}, bearing $bearing, from this phone at $whenText"
        }
    }

    fun distanceText(meters: Double): String =
        if (meters >= 1000.0) {
            "%.1f km".format(Locale.US, meters / 1000.0)
        } else {
            "${meters.toInt()} m"
        }

    private fun rangeParts(
        phoneLat: Double?,
        phoneLon: Double?,
        lat: Double?,
        lon: Double?,
    ): Pair<String, String?>? {
        if (!PayloadLocation.validCoord(phoneLat, phoneLon)) return null
        if (!PayloadLocation.validCoord(lat, lon)) return null
        val meters = meters(phoneLat!!, phoneLon!!, lat!!, lon!!)
        val distance = distanceText(meters)
        if (meters < 5.0) return distance to null
        return distance to bearingText(bearingDegrees(phoneLat, phoneLon, lat, lon))
    }

    private fun bearingText(bearing: Double): String {
        val wrapped = ((bearing % 360.0) + 360.0) % 360.0
        val shown = (wrapped + 0.5).toInt() % 360
        return "%03d°".format(Locale.US, shown)
    }

    fun redactCoordsIn(text: String, demo: Boolean): String {
        if (!demo || text.isEmpty()) return text
        val coords = COORD_RE.replace(text, "masked")
        val grids = MGRS_RE.replace(coords, "masked")
        return RANGE_RE.replace(grids, "masked")
    }

    private val COORD_RE = Regex("""-?\d{1,3}\.\d{3,8}\s*,\s*-?\d{1,3}\.\d{3,8}""")
    private val MGRS_RE = Regex("""\b\d{1,2}[C-HJ-NP-X]\s*[A-HJ-NP-Z]{2}\s*\d{5}\s*\d{5}\b""")
    private val RANGE_RE = Regex(
        """\d+(?:\.\d+)? (?:m|km), bearing \d{3}°(?:, from this phone at \d{2}:\d{2})?|\d+(?:\.\d+)? (?:m|km), from this phone at \d{2}:\d{2}""",
    )

    /**
     * Split a time-ordered path into stays (~40 m) and transits. A single GPS
     * sample that holds until the next hop is a stay (sitting still does not
     * add points). Caps at 10 legs.
     */
    fun legs(
        samples: List<GpsSample>,
        stayM: Double = 40.0,
        minStayMs: Long = 40_000L,
        now: Long = samples.lastOrNull()?.at ?: 0L,
    ): List<PathLeg> {
        if (samples.isEmpty()) return emptyList()
        val clusters = clusterFixes(samples, stayM)
        val classified = clusters.mapIndexed { i, cluster ->
            val start = cluster.first()
            val endSample = cluster.last()
            val nextStart = clusters.getOrNull(i + 1)?.first()?.at
            val endAt = nextStart ?: now.coerceAtLeast(endSample.at)
            var slat = 0.0
            var slon = 0.0
            for (p in cluster) {
                slat += p.lat
                slon += p.lon
            }
            val n = cluster.size.toDouble()
            val lat = slat / n
            val lon = slon / n
            val dur = (endAt - start.at).coerceAtLeast(0L)
            val stay = clusters.size == 1 || dur >= minStayMs || spanM(cluster) <= 25.0
            PathLeg(
                stay = stay,
                startAt = start.at,
                endAt = endAt,
                lat = lat,
                lon = lon,
                endLat = endSample.lat,
                endLon = endSample.lon,
                samples = cluster.size,
                pathM = pathLengthM(cluster),
            )
        }
        val merged = ArrayList<PathLeg>()
        for (leg in classified) {
            val last = merged.lastOrNull()
            if (last != null && !last.stay && !leg.stay) {
                merged[merged.lastIndex] = last.copy(
                    endAt = leg.endAt,
                    endLat = leg.endLat,
                    endLon = leg.endLon,
                    samples = last.samples + leg.samples,
                    pathM = last.pathM + meters(last.endLat, last.endLon, leg.lat, leg.lon) + leg.pathM,
                )
            } else {
                merged.add(leg)
            }
        }
        return merged.take(10)
    }

    /**
     * Places the phone actually stayed. A lone corner is not a stop, and the
     * time until the next bend is not time spent there. No leg cap: a later
     * stop still counts.
     */
    fun realStops(
        samples: List<GpsSample>,
        stayM: Double = STOP_M,
        minStayMs: Long = STOP_MS,
    ): List<PathLeg> {
        if (samples.size < 2) return emptyList()
        val out = ArrayList<PathLeg>()
        for (cluster in clusterFixes(samples, stayM)) {
            val start = cluster.first()
            val endSample = cluster.last()
            val spent = endSample.at - start.at
            if (spent < minStayMs) continue
            var slat = 0.0
            var slon = 0.0
            for (p in cluster) {
                slat += p.lat
                slon += p.lon
            }
            val n = cluster.size.toDouble()
            out += PathLeg(
                stay = true,
                startAt = start.at,
                endAt = endSample.at,
                lat = slat / n,
                lon = slon / n,
                endLat = endSample.lat,
                endLon = endSample.lon,
                samples = cluster.size,
                pathM = pathLengthM(cluster),
            )
        }
        return out
    }

    /** True when both ends of a drawn segment sit inside one real stop. */
    fun stopCovers(stops: List<PathLeg>, fromAt: Long, toAt: Long): Boolean =
        stops.any { fromAt >= it.startAt && toAt <= it.endAt }

    /**
     * Fixes that stay within [stayM] of where this cluster started. The anchor
     * stays on that first fix. Following the average lets GPS jitter on opposite
     * sides of a parked phone split one stop, and the green stretch flips blue.
     */
    private fun clusterFixes(samples: List<GpsSample>, stayM: Double): List<ArrayList<GpsSample>> {
        val clusters = ArrayList<ArrayList<GpsSample>>()
        var cur = arrayListOf(samples[0])
        var anchorLat = samples[0].lat
        var anchorLon = samples[0].lon
        for (i in 1 until samples.size) {
            val s = samples[i]
            if (meters(anchorLat, anchorLon, s.lat, s.lon) <= stayM) {
                cur.add(s)
            } else {
                clusters.add(cur)
                cur = arrayListOf(s)
                anchorLat = s.lat
                anchorLon = s.lon
            }
        }
        clusters.add(cur)
        return clusters
    }

    /** ~150 km/h. Walking sits and city driving stay under this; a GPS glitch does not. */
    const val SPIKE_MAX_SPEED_MPS = 42.0
    const val SPIKE_MIN_HOP_M = 40.0
    const val SPIKE_MIN_DT_MS = 800L

    fun hopPlausible(
        from: GpsSample,
        lat: Double,
        lon: Double,
        at: Long,
        maxSpeedMps: Double = SPIKE_MAX_SPEED_MPS,
    ): Boolean {
        val d = meters(from.lat, from.lon, lat, lon)
        val dt = at - from.at
        if (d <= SPIKE_MIN_HOP_M) return true
        if (dt < SPIKE_MIN_DT_MS) return true
        val mps = d / (dt / 1000.0).coerceAtLeast(0.001)
        return mps <= maxSpeedMps
    }

    /**
     * Drop GPS glitches: a point that shoots out and back, or a hop faster than
     * [SPIKE_MAX_SPEED_MPS] over at least [SPIKE_MIN_DT_MS]. Existing sits clean
     * up on Path; new samples are rejected in recordPath.
     */
    fun despikePath(samples: List<GpsSample>): List<GpsSample> {
        if (samples.size < 2) return samples
        var cur = samples
        repeat(4) {
            val next = despikeOnce(cur)
            if (next.size == cur.size) return next
            cur = next
            if (cur.size < 2) return cur
        }
        return cur
    }

    private fun despikeOnce(samples: List<GpsSample>): List<GpsSample> {
        if (samples.size < 2) return samples
        val out = ArrayList<GpsSample>(samples.size)
        out += samples.first()
        var i = 1
        while (i < samples.size) {
            val prev = out.last()
            val cur = samples[i]
            val next = samples.getOrNull(i + 1)
            if (next != null) {
                val dAb = meters(prev.lat, prev.lon, cur.lat, cur.lon)
                val dBc = meters(cur.lat, cur.lon, next.lat, next.lon)
                val dAc = meters(prev.lat, prev.lon, next.lat, next.lon)
                val spike = dAb > SPIKE_MIN_HOP_M && dBc > SPIKE_MIN_HOP_M &&
                    dAc < dAb * 0.4 && dAc < dBc * 0.4
                if (spike) {
                    i++
                    continue
                }
            }
            if (!hopPlausible(prev, cur.lat, cur.lon, cur.at)) {
                i++
                continue
            }
            out += cur
            i++
        }
        return if (out.size >= 2) out else samples.take(1) + samples.takeLast(1)
    }

    /**
     * A point is kept this often along the drive. The open end is only the last
     * piece, so a turn cannot move the road already driven. Same rule in a sit
     * and out of one.
     */
    const val DRIVE_STEP_M = 4.0
    /** At or under this many fixes, [reachM] is the exact pair distance. */
    private const val REACH_EXACT = 512
    const val DRIVE_MAX_ACCURACY_M = 25f
    const val TRACK_CAP = 16_000

    /** Inside this, for [STOP_MS], is a real stop. A walk leaves it before the clock finishes. */
    const val STOP_M = 40.0
    const val STOP_HOLD_M = 25.0
    const val STOP_MS = 40_000L

    class DriveApex {
        var sample: GpsSample? = null
        val recent = kotlin.collections.ArrayDeque<GpsSample>()
        var stillAt: Long = 0L
        var stillLat: Double = Double.NaN
        var stillLon: Double = Double.NaN

        /** Heading just after the last corner, while that bend may still be turning. */
        var bendFrom: Double = Double.NaN

        fun clear() {
            sample = null
            recent.clear()
            stillAt = 0L
            stillLat = Double.NaN
            stillLon = Double.NaN
            bendFrom = Double.NaN
        }
    }

    /** GPS only, and only when the fix is tight enough to belong on the driven line. */
    fun acceptDriveFix(gps: Boolean, accuracyM: Float?): Boolean {
        if (!gps) return false
        if (accuracyM != null && accuracyM > DRIVE_MAX_ACCURACY_M) return false
        return true
    }

    /** Signed meters of [p] off the line from [a] to [b]. Zero when the line is too short to trust. */
    fun crossTrackMeters(
        aLat: Double,
        aLon: Double,
        bLat: Double,
        bLon: Double,
        pLat: Double,
        pLon: Double,
    ): Double {
        val cosLat = cos(Math.toRadians(aLat)).coerceAtLeast(0.2)
        val eastB = (bLon - aLon) * 111_320.0 * cosLat
        val northB = (bLat - aLat) * 111_320.0
        val eastP = (pLon - aLon) * 111_320.0 * cosLat
        val northP = (pLat - aLat) * 111_320.0
        val len = hypot(eastB, northB)
        if (len < 1.0) return 0.0
        return (eastB * northP - northB * eastP) / len
    }

    /**
     * Frozen points plus a short moving tip. The tip is where the phone is now.
     * Once the phone is [DRIVE_STEP_M] past the last frozen point, that tip stays
     * and a new one starts. A real stop pins the place the phone arrived, so the
     * green mark has a start and an end. Returns true when the oldest point was
     * dropped to stay inside [cap].
     */
    fun extendDrivePath(
        path: MutableList<GpsSample>,
        apex: DriveApex,
        lat: Double,
        lon: Double,
        at: Long,
        cap: Int = TRACK_CAP,
    ): Boolean {
        val fix = GpsSample(at, lat, lon)
        noteStill(apex, lat, lon, at)
        if (path.size < 2) {
            path += fix
            return false
        }
        val frozen = path[path.lastIndex - 1]
        val tip = path.last()
        val travelled = meters(frozen.lat, frozen.lon, lat, lon)
        val tipLeft = meters(frozen.lat, frozen.lon, tip.lat, tip.lon)
        if (travelled >= DRIVE_STEP_M && tipLeft >= 1.0) {
            path += fix
            return trimPath(path, cap, apex)
        }
        path[path.lastIndex] = fix
        return pinStop(path, apex, fix, cap)
    }

    private fun noteStill(apex: DriveApex, lat: Double, lon: Double, at: Long) {
        if (apex.stillAt == 0L || meters(apex.stillLat, apex.stillLon, lat, lon) > STOP_HOLD_M) {
            apex.stillAt = at
            apex.stillLat = lat
            apex.stillLon = lon
        }
    }

    /** Pin the arrival of a real stop once, then keep the moving tip. */
    private fun pinStop(path: MutableList<GpsSample>, apex: DriveApex, fix: GpsSample, cap: Int): Boolean {
        if (path.size < 2 || apex.stillAt == 0L) return false
        if (fix.at - apex.stillAt < STOP_MS) return false
        if (meters(apex.stillLat, apex.stillLon, fix.lat, fix.lon) > STOP_HOLD_M) return false
        val prev = path[path.lastIndex - 1]
        if (prev.at == apex.stillAt && meters(prev.lat, prev.lon, apex.stillLat, apex.stillLon) < 3.0) {
            return false
        }
        path[path.lastIndex] = GpsSample(apex.stillAt, apex.stillLat, apex.stillLon)
        path += fix
        apex.sample = null
        return trimPath(path, cap, apex)
    }

    private fun trimPath(path: MutableList<GpsSample>, cap: Int, apex: DriveApex): Boolean {
        var trimmed = false
        while (path.size > cap) {
            path.removeAt(0)
            trimmed = true
        }
        if (trimmed) apex.clear()
        return trimmed
    }

    fun append(trail: List<GpsSample>, at: Long, lat: Double, lon: Double, rssi: Int, cap: Int = 48): List<GpsSample> {
        val last = trail.lastOrNull()
        if (last != null) {
            val d = meters(last.lat, last.lon, lat, lon)
            if (d < 8.0) return trail
            if (d < 18.0 && at - last.at < 30_000L) return trail
        }
        val next = trail + GpsSample(at, lat, lon, rssi)
        return capSpread(next, cap)
    }

    /**
     * Keep [cap] samples spread across the whole trail (first, last, and even
     * steps). takeLast would only keep the end of a long sit.
     */
    fun capSpread(samples: List<GpsSample>, cap: Int): List<GpsSample> {
        if (samples.size <= cap) return samples
        if (cap <= 1) return listOf(samples.last())
        if (cap == 2) return listOf(samples.first(), samples.last())
        val lastIdx = samples.size - 1
        val out = ArrayList<GpsSample>(cap)
        for (i in 0 until cap) {
            val idx = (i * lastIdx) / (cap - 1)
            val s = samples[idx]
            if (out.isEmpty() || out.last().at != s.at) out += s
        }
        if (out.last().at != samples.last().at) out += samples.last()
        return out
    }
}

/**
 * Live co-travel, BLE only. Phone GPS is stamped at hear-time. A bag/car tag
 * stays loud along the path. Wi-Fi APs are excluded: a loud AP you drive past
 * paints hundreds of meters of your path and looks like it moved with you.
 * Cheap loud-RSSI reject first so the list is not O(n²) GPS on every radio.
 */
object CoTravel {
    const val MOVE_M = 45.0
    const val NEAR_M = 50.0
    const val HEARD_MS = 90_000L
    const val LOUD_DBM = -70
    const val TRAIL_LOUD_DBM = -75

    data class Ctx(
        val pathLengthM: Double,
        val durationMs: Long,
        val here: GpsSample?,
        val ready: Boolean,
    ) {
        companion object {
            val None = Ctx(0.0, 0L, null, false)

            fun of(path: List<GpsSample>): Ctx {
                if (path.size < 2) return None
                val len = Geo.pathLengthM(path)
                val dur = (path.last().at - path.first().at).coerceAtLeast(0L)
                return Ctx(len, dur, path.last(), len >= MOVE_M)
            }
        }
    }

    private data class TrailGeom(
        val n: Int,
        val at: Long,
        val lat: Double,
        val lon: Double,
        val len: Double,
        val bbox: Double,
    )

    private val trailGeom = java.util.concurrent.ConcurrentHashMap<String, TrailGeom>(64)

    fun withYou(device: Sighting, ctx: Ctx, now: Long = System.currentTimeMillis()): Boolean {
        if (device.kind == RadioKind.WIFI) return false
        if (!ctx.ready || ctx.here == null) return false
        if (now - device.lastSeen > HEARD_MS) return false
        // Last packet can dip on a highway; the cheap reject is the trail floor, not −70 instant.
        if (device.rssi < TRAIL_LOUD_DBM) return false
        val trail = device.gpsTrail
        if (trail.size < 2) return false
        val geom = geomFor(device.key, trail) ?: return false
        if (geom.bbox < MOVE_M * 0.6) return false
        var loud = 0
        for (s in trail) if (s.rssi >= TRAIL_LOUD_DBM) loud++
        if (loud < (trail.size * 2 + 2) / 3) return false
        if (geom.len < MOVE_M * 0.6) return false
        val last = trail.last()
        val moved = Geo.meters(ctx.here.lat, ctx.here.lon, last.lat, last.lon)
        // Last GPS stamp is the phone at hear-time. 50 m of driving is ~2 s on an interstate,
        // so a bag tag that advertises every few seconds would flash without a speed-aware slack.
        val speed = (ctx.pathLengthM / (ctx.durationMs / 1000.0).coerceAtLeast(1.0)).coerceIn(0.0, 40.0)
        val allowM = max(NEAR_M, speed * 15.0) + 25.0
        return moved <= allowM
    }

    private fun geomFor(key: String, trail: List<GpsSample>): TrailGeom? {
        val last = trail.lastOrNull() ?: return null
        val hit = trailGeom[key]
        if (hit != null && hit.n == trail.size && hit.at == last.at &&
            hit.lat == last.lat && hit.lon == last.lon
        ) {
            return hit
        }
        val next = TrailGeom(
            n = trail.size,
            at = last.at,
            lat = last.lat,
            lon = last.lon,
            len = Geo.pathLengthM(trail),
            bbox = bboxSpanM(trail),
        )
        trailGeom[key] = next
        if (trailGeom.size > 1024) trailGeom.clear()
        return next
    }

    private fun bboxSpanM(samples: List<GpsSample>): Double {
        var minLat = samples[0].lat
        var maxLat = minLat
        var minLon = samples[0].lon
        var maxLon = minLon
        for (i in 1 until samples.size) {
            val s = samples[i]
            if (s.lat < minLat) minLat = s.lat
            if (s.lat > maxLat) maxLat = s.lat
            if (s.lon < minLon) minLon = s.lon
            if (s.lon > maxLon) maxLon = s.lon
        }
        return Geo.meters(minLat, minLon, maxLat, maxLon)
    }
}

object TrackerMatch {
    enum class Kind { FINDER, BEACON, WEARABLE }

    private val finderTokens = listOf(
        "airtag", "smarttag", "tile", "chipolo", "pebblebee", "moto tag", "find my",
        "find hub", "dult",
    )
    private val beaconTokens = listOf("ibeacon", "minew", "estimote", "kontakt")
    private val wearableTokens = listOf("garmin", "fitbit", "oura", "polar")

    /** Apple Continuity / pairing types — not Offline Finding 0x12. */
    private val APPLE_CONTINUITY_PREFIXES = setOf(
        "05", "07", "08", "09", "0A", "0B", "0C", "0D", "0E", "0F", "10",
    )

    fun isTrackerFleet(name: String): Boolean {
        val n = name.lowercase()
        return finderTokens.any { it in n }
    }

    private fun nameHits(device: Sighting, names: Map<String, String>, tokens: List<String>): List<String> =
        device.fleetIds.mapNotNull { id ->
            val n = names[id] ?: return@mapNotNull null
            val low = n.lowercase()
            if (tokens.any { it in low }) n else null
        }

    fun fleetHits(device: Sighting, names: Map<String, String>): List<String> =
        nameHits(device, names, finderTokens)

    fun isFindMyPayload(device: Sighting): Boolean {
        if (isAppleContinuity(device)) return false
        if (device.facts.mfgRecords.any { it.companyId == 0x004C && mfgPrefix(it) == "12" }) return true
        return device.manufacturerId == 0x004C &&
            device.manufacturerDataHex.filter { it.isLetterOrDigit() }.take(2).uppercase() == "12"
    }

    /** Nearby Info / Handoff / AirDrop / AirPods / … — a phone, Mac, or buds, not a tag. */
    fun isAppleContinuity(device: Sighting): Boolean {
        val recs = device.facts.mfgRecords.ifEmpty {
            val id = device.manufacturerId ?: return false
            listOf(MfgRecord(id, device.manufacturerDataHex))
        }
        return recs.any { rec ->
            rec.companyId == 0x004C && mfgPrefix(rec) in APPLE_CONTINUITY_PREFIXES
        }
    }

    private fun mfgPrefix(rec: MfgRecord): String =
        rec.dataHex.filter { it.isLetterOrDigit() }.take(2).uppercase()

    fun isAppleCompany(device: Sighting): Boolean {
        if (device.manufacturerId == 0x004C) return true
        return device.facts.mfgRecords.any { it.companyId == 0x004C }
    }

    fun isTracker(device: Sighting, names: Map<String, String>): Boolean =
        kind(device, names) == Kind.FINDER

    /**
     * Pocket iPhone / Continuity: loud Apple BLE that stayed loud.
     * Not every Apple TV on the block — followAssessments still requires a GPS trail.
     * Not iBeacon / Minew — those are [Kind.BEACON] even though the payload is 0x004C.
     */
    fun isCarriedApple(device: Sighting, names: Map<String, String>): Boolean {
        if (device.kind != RadioKind.BLE) return false
        if (device.rssiMax < -55 || device.rssiMin < -70) return false
        if (nameHits(device, names, beaconTokens).isNotEmpty()) return false
        if (isAppleCompany(device)) return true
        return device.fleetIds.any { id -> (names[id] ?: "").contains("apple", ignoreCase = true) }
    }

    fun kind(device: Sighting, names: Map<String, String>): Kind? {
        if (nameHits(device, names, finderTokens).isNotEmpty() || isFindMyPayload(device)) return Kind.FINDER
        if (nameHits(device, names, beaconTokens).isNotEmpty()) return Kind.BEACON
        if (nameHits(device, names, wearableTokens).isNotEmpty()) return Kind.WEARABLE
        if (isCarriedApple(device, names)) return Kind.FINDER
        return null
    }

    fun label(device: Sighting, names: Map<String, String>): String {
        val finder = nameHits(device, names, finderTokens)
        if (finder.isNotEmpty()) return finder.joinToString(" + ")
        val beacon = nameHits(device, names, beaconTokens)
        if (beacon.isNotEmpty()) return beacon.joinToString(" + ")
        val wear = nameHits(device, names, wearableTokens)
        if (wear.isNotEmpty()) return wear.joinToString(" + ")
        if (isFindMyPayload(device)) return "Apple Find My / Offline Finding"
        if (isCarriedApple(device, names)) return "Apple BLE (phone / Continuity)"
        return "tracker-like"
    }
}
