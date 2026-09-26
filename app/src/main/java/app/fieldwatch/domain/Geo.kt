package app.fieldwatch.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    fun meters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
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

    fun screenCoord(lat: Double?, lon: Double?, demo: Boolean): String? {
        if (lat == null || lon == null) return null
        if (demo) return "masked"
        return "%.5f, %.5f".format(java.util.Locale.US, lat, lon)
    }

    fun redactCoordsIn(text: String, demo: Boolean): String {
        if (!demo || text.isEmpty()) return text
        return COORD_RE.replace(text, "masked")
    }

    private val COORD_RE = Regex("""-?\d{1,3}\.\d{3,8}\s*,\s*-?\d{1,3}\.\d{3,8}""")

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
        val clusters = ArrayList<ArrayList<GpsSample>>()
        var cur = arrayListOf(samples[0])
        var cLat = samples[0].lat
        var cLon = samples[0].lon
        for (i in 1 until samples.size) {
            val s = samples[i]
            if (meters(cLat, cLon, s.lat, s.lon) <= stayM) {
                cur.add(s)
                var slat = 0.0
                var slon = 0.0
                for (p in cur) {
                    slat += p.lat
                    slon += p.lon
                }
                cLat = slat / cur.size
                cLon = slon / cur.size
            } else {
                clusters.add(cur)
                cur = arrayListOf(s)
                cLat = s.lat
                cLon = s.lon
            }
        }
        clusters.add(cur)
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
    )
    private val beaconTokens = listOf("ibeacon", "minew", "estimote", "kontakt")
    private val wearableTokens = listOf("garmin", "fitbit", "oura")

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
