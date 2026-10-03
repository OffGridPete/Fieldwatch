package app.fieldwatch.domain

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** North-up path of this phone. Alert dots are hear-points, unless the radio advertised a position. */
object SitPathPlot {
    /**
     * Shortest half-width of the walk plot, in meters. A sit that barely moved
     * still shows the surrounding streets. A longer walk is unchanged.
     */
    const val MIN_HALF_SPAN_M = 180f
    data class Dot(
        val key: String,
        val lat: Double,
        val lon: Double,
        val label: String,
        val extraAttention: Boolean,
        val named: Boolean,
        val kind: RadioKind = RadioKind.WIFI,
        val mac: String = "",
        val fleetNames: List<String> = emptyList(),
        val observerNotes: String = "",
        val rssiMin: Int = 0,
        val rssiMax: Int = 0,
        /** First matched signature class, for the path icon. Null when the radio has no signature. */
        val classKind: SignatureClass? = null,
        /** Palette color of that signature. 0 when there is no signature. */
        val accentArgb: Int = 0,
        /**
         * On-screen Path. [lat]/[lon] is the last advertised fix, not this phone's hear.
         * The aircraft marker is the tap target. No second class icon on that point.
         */
        val advertised: Boolean = false,
        /**
         * Letter Path key. Live status, UAS id, last position, motion, and pilot
         * when this radio advertised them. Empty for a hear-point.
         */
        val advertisedNote: String = "",
    )

    data class Model(
        val samples: List<GpsSample>,
        val dots: List<Dot>,
        val lengthM: Double,
        val spanM: Double,
        val title: String,
        val emptyHint: String? = null,
        val live: Boolean = false,
        /** Extra points that must stay inside the frame, without the walking speed filter. */
        val frameSamples: List<GpsSample> = emptyList(),
        /** When the track is a single advertised fix, give the marker some ground around it. */
        val minHalfSpanM: Float = 0f,
        /** Advertised tracks drawn on this plot. They are not part of [samples]. */
        val craft: List<FigureTrack> = emptyList(),
        /** Pilot pins already limited to tracks within range. */
        val pilots: List<Mark> = emptyList(),
        /** Farther aircraft, each its own north-up plot. */
        val aircraftCards: List<Model> = emptyList(),
        /** Advertised positions with no UAS id that do not get a map. */
        val looseAdvertised: Int = 0,
        val caption: String = "",
    )

    data class Pt(val x: Float, val y: Float)

    data class Layout(
        val path: List<Pt>,
        val dots: List<Pair<Dot, Pt>>,
        val scaleBarM: Double,
        val scaleBarFrac: Float,
        val project: (Double, Double) -> Pt = { _, _ -> Pt(0f, 0f) },
        val plotLeft: Float = 0f,
        val plotTop: Float = 0f,
        val plotRight: Float = 0f,
        val plotBottom: Float = 0f,
        val samples: List<GpsSample> = emptyList(),
    )

    data class Mark(
        val lat: Double,
        val lon: Double,
        val label: String,
    )

    data class FigureTrack(
        val name: String,
        val samples: List<GpsSample>,
        val secondary: Boolean = false,
        /** Advertised latitude/longitude. Drawn apart from the phone path. */
        val aircraft: Boolean = false,
        /** Last advertised course, degrees clockwise from north. Null draws a diamond. */
        val headingDeg: Double? = null,
    )

    data class Figure(
        val kicker: String,
        val tracks: List<FigureTrack>,
        val dots: List<Dot>,
        val lengthM: Double,
        val spanM: Double,
        val caption: String,
        val pilots: List<Mark> = emptyList(),
        /**
         * Advertised aircraft drawn on this figure as a class icon, not a numbered alert.
         * One line each, same order as the tracks.
         */
        val craftKeys: List<String> = emptyList(),
    ) {
        val drawable: Boolean
            get() = tracks.any { it.samples.size >= 2 || (it.aircraft && it.samples.isNotEmpty()) }
    }

    data class Cluster(
        val id: String,
        val center: Pt,
        val members: List<Member>,
    ) {
        val stacked: Boolean get() = members.size > 1
    }

    data class Member(
        val number: Int,
        val dot: Dot,
        val at: Pt,
    )

    fun clusters(
        dots: List<Pair<Dot, Pt>>,
        threshPx: Float = 28f,
        numberOf: (Dot) -> Int = { dot ->
            val i = dots.indexOfFirst { it.first.key == dot.key }
            if (i < 0) 1 else i + 1
        },
    ): List<Cluster> {
        val used = BooleanArray(dots.size)
        val out = ArrayList<Cluster>()
        for (i in dots.indices) {
            if (used[i]) continue
            val idx = ArrayList<Int>()
            val q = ArrayDeque<Int>()
            q.add(i)
            used[i] = true
            while (q.isNotEmpty()) {
                val j = q.removeFirst()
                idx += j
                for (k in dots.indices) {
                    if (used[k]) continue
                    val dx = dots[j].second.x - dots[k].second.x
                    val dy = dots[j].second.y - dots[k].second.y
                    if (dx * dx + dy * dy <= threshPx * threshPx) {
                        used[k] = true
                        q.add(k)
                    }
                }
            }
            val members = idx.sorted().map { i ->
                val pair = dots[i]
                Member(numberOf(pair.first), pair.first, pair.second)
            }
            val cx = members.map { it.at.x }.average().toFloat()
            val cy = members.map { it.at.y }.average().toFloat()
            val id = members.map { it.dot.key }.sorted().joinToString("|")
            out += Cluster(id, Pt(cx, cy), members)
        }
        return out
    }

    /** Start or end mark. A tap on the dot or its label belongs to a detection it covers. */
    data class HitMarker(
        val x: Float,
        val y: Float,
        val radius: Float,
        val labelLeft: Float,
        val labelTop: Float,
        val labelRight: Float,
        val labelBottom: Float,
    ) {
        fun contains(px: Float, py: Float, pad: Float = 0f): Boolean {
            if (hypot(px - x, py - y) <= radius + pad) return true
            return px in (labelLeft - pad)..(labelRight + pad) &&
                py in (labelTop - pad)..(labelBottom + pad)
        }

        fun overlaps(cluster: Cluster, visual: Float): Boolean {
            if (hypot(cluster.center.x - x, cluster.center.y - y) <= radius + visual) return true
            val nearestX = cluster.center.x.coerceIn(labelLeft, labelRight)
            val nearestY = cluster.center.y.coerceIn(labelTop, labelBottom)
            return hypot(cluster.center.x - nearestX, cluster.center.y - nearestY) <= visual
        }
    }

    fun clusterVisualRadius(cluster: Cluster): Float = if (cluster.stacked) 20f else 16f

    /**
     * Detection under a tap. The start and end marks are included, so the part of an
     * icon they cover still opens that detection.
     */
    fun clusterAt(
        clusters: List<Cluster>,
        x: Float,
        y: Float,
        markers: List<HitMarker> = emptyList(),
        slopPx: Float = 40f,
    ): Cluster? {
        fun dist(c: Cluster) = hypot((c.center.x - x).toDouble(), (c.center.y - y).toDouble())
        val direct = clusters.filter { dist(it) <= slopPx }
        val marked = markers.flatMap { marker ->
            if (!marker.contains(x, y)) {
                emptyList()
            } else {
                clusters.filter { marker.overlaps(it, clusterVisualRadius(it)) }
            }
        }
        return (direct + marked).distinctBy { it.id }.minByOrNull { dist(it) }
    }

    fun layout(
        model: Model,
        width: Float,
        height: Float,
        pad: Float = 16f,
        scaleBarReserve: Float = 44f,
    ): Layout? {
        if (width <= pad * 2 || height <= pad * 2 + scaleBarReserve) return null
        val phone = Geo.despikePath(model.samples).let { cleaned ->
            if (cleaned.size >= 2) cleaned else model.samples
        }
        val pts = phone + model.frameSamples
        if (pts.isEmpty()) return null
        val lat0 = pts.map { it.lat }.average()
        val lon0 = pts.map { it.lon }.average()
        val cos0 = cos(Math.toRadians(lat0)).coerceAtLeast(0.2)
        fun mx(lat: Double, lon: Double) = ((lon - lon0) * 111_320.0 * cos0).toFloat()
        fun my(lat: Double, lon: Double) = ((lat - lat0) * 110_540.0).toFloat()
        val raw = pts.map { mx(it.lat, it.lon) to my(it.lat, it.lon) }
        var minX = raw.minOf { it.first }
        var maxX = raw.maxOf { it.first }
        var minY = raw.minOf { it.second }
        var maxY = raw.maxOf { it.second }
        model.dots.forEach { d ->
            val x = mx(d.lat, d.lon)
            val y = my(d.lat, d.lon)
            minX = min(minX, x); maxX = max(maxX, x)
            minY = min(minY, y); maxY = max(maxY, y)
        }
        run {
            val midX = (minX + maxX) / 2f
            val midY = (minY + maxY) / 2f
            val floor = if (model.minHalfSpanM > 0f) model.minHalfSpanM else MIN_HALF_SPAN_M
            val hx = ((maxX - minX) / 2f).coerceAtLeast(floor) * 1.22f
            val hy = ((maxY - minY) / 2f).coerceAtLeast(floor) * 1.22f
            minX = midX - hx
            maxX = midX + hx
            minY = midY - hy
            maxY = midY + hy
        }
        val spanX = (maxX - minX).coerceAtLeast(8f)
        val spanY = (maxY - minY).coerceAtLeast(8f)
        val innerW = width - pad * 2
        val innerH = height - pad * 2 - scaleBarReserve
        val scale = min(innerW / spanX, innerH / spanY)
        val usedW = spanX * scale
        val usedH = spanY * scale
        val ox = pad + (innerW - usedW) / 2f
        val oy = pad + (innerH - usedH) / 2f
        fun map(mxv: Float, myv: Float) = Pt(
            x = ox + (mxv - minX) * scale,
            y = oy + (maxY - myv) * scale,
        )
        val path = phone.map { map(mx(it.lat, it.lon), my(it.lat, it.lon)) }
        val dots = model.dots.map { it to map(mx(it.lat, it.lon), my(it.lat, it.lon)) }
        val barM = niceMeters(spanX / scale * 0.28)
        val barFrac = ((barM.toFloat() * scale) / innerW).coerceIn(0.08f, 0.45f)
        return Layout(
            path = path,
            dots = dots,
            scaleBarM = barM,
            scaleBarFrac = barFrac,
            project = { lat, lon -> map(mx(lat, lon), my(lat, lon)) },
            plotLeft = pad,
            plotTop = pad,
            plotRight = width - pad,
            plotBottom = height - pad - scaleBarReserve,
            samples = phone,
        )
    }

    fun niceMeters(raw: Double): Double {
        val v = raw.coerceAtLeast(5.0)
        val mag = 10.0.pow(floor(ln(v) / ln(10.0)))
        val n = v / mag
        val nice = when {
            n < 1.5 -> 1.0
            n < 3.5 -> 2.0
            n < 7.5 -> 5.0
            else -> 10.0
        }
        return nice * mag
    }

    data class PlotRadios(
        val points: List<Dot>,
    )

    fun dotsFrom(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        namedKeys: Set<String>,
        cap: Int = 48,
        customNames: Map<String, String> = emptyMap(),
        observerNotes: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        /** Signatures with Alert on. Used when [alertsOnly] is set. */
        watchedFleetIds: Set<String> = emptySet(),
        /**
         * Reports → Path and the letter-size path figure.
         * MAC alerts and signature alerts. A decoded position uses the last advertised fix.
         */
        alertsOnly: Boolean = false,
        mineKeys: Set<String> = emptySet(),
    ): PlotRadios {
        val points = ArrayList<Dot>()
        devices
            .filter { device ->
                val macAlert = device.key in bookmarkedKeys
                val signatureAlert = device.fleetIds.any { it in watchedFleetIds }
                if (alertsOnly) macAlert || signatureAlert
                else macAlert || device.attentionNotes(fleets).isNotEmpty()
            }
            .groupBy { it.key }
            .forEach { (_, group) ->
                val d = group.maxBy { it.rssi }
                val pin = if (alertsOnly) advertisedFix(d) else null
                val fix = pin ?: loudestFix(d) ?: return@forEach
                val bookmarked = d.key in bookmarkedKeys
                val notes = RadioBookmarks.pathNote(
                    bookmarked,
                    observerNotes[d.key].orEmpty(),
                    d.key in mineKeys,
                )
                val fleet = d.fleetIds.firstNotNullOfOrNull { id ->
                    fleets.firstOrNull { it.id == id && id in watchedFleetIds }
                } ?: d.fleetIds.firstNotNullOfOrNull { id -> fleets.firstOrNull { it.id == id } }
                points += Dot(
                    key = d.key,
                    lat = fix.lat,
                    lon = fix.lon,
                    label = d.reportName(customNames).ifBlank { d.mac },
                    extraAttention = d.attentionNotes(fleets).isNotEmpty(),
                    named = bookmarked || d.key in namedKeys,
                    kind = d.kind,
                    mac = d.mac,
                    fleetNames = d.fleetIds.mapNotNull { id -> fleets.firstOrNull { it.id == id }?.name },
                    observerNotes = notes,
                    rssiMin = d.rssiMin,
                    rssiMax = d.rssiMax,
                    classKind = fleet?.kind,
                    accentArgb = fleet?.let { Palette.color(it.colorIndex) } ?: 0,
                    advertised = pin != null,
                    advertisedNote = if (pin != null) {
                        val kept = d.payloadTrail.lastOrNull { PayloadLocation.validCoord(it.lat, it.lon) }
                        AircraftTrail.advertisedNote(
                            status = d.liveDecode.reportLabels().joinToString(", "),
                            uasId = d.payloadUasId?.trim().orEmpty(),
                            label = d.reportName(customNames),
                            lat = fix.lat,
                            lon = fix.lon,
                            alt = kept?.alt ?: d.payloadAlt,
                            heading = kept?.heading ?: d.payloadHeading,
                            speed = kept?.speed ?: d.payloadSpeed,
                            pilotLat = d.payloadOpLat,
                            pilotLon = d.payloadOpLon,
                            aircraft = d.payloadAircraft?.trim().orEmpty(),
                        )
                    } else {
                        ""
                    },
                )
            }
        val sorted = if (alertsOnly) {
            points.sortedBy { it.label }
        } else {
            points.sortedWith(compareByDescending<Dot> { it.extraAttention }.thenBy { it.label })
        }
        return PlotRadios(sorted.take(cap))
    }

    /**
     * Last advertised fix the sit kept. Empty trail uses the current payload pin
     * (last 15 minutes, or a sit from before the trail). Pilot coordinates are not a pin.
     */
    fun advertisedFix(d: Sighting): GpsSample? {
        val kept = d.payloadTrail.lastOrNull { PayloadLocation.validCoord(it.lat, it.lon) }
        if (kept != null) return GpsSample(kept.at, kept.lat, kept.lon, 0)
        val lat = d.payloadLat ?: return null
        val lon = d.payloadLon ?: return null
        if (!PayloadLocation.validCoord(lat, lon)) return null
        return GpsSample(d.lastSeen, lat, lon, 0)
    }

    /** True when [dot] is the end marker of an advertised track on this plot. */
    fun sitsOnCraft(dot: Dot, craft: List<FigureTrack>, meters: Double = 30.0): Boolean {
        if (!dot.advertised) return false
        return craft.any { track ->
            val end = track.samples.lastOrNull() ?: return@any false
            Geo.meters(end.lat, end.lon, dot.lat, dot.lon) <= meters
        }
    }

    /** One hear-point per radio: loudest GPS-trail sample. rssi 0 is treated as unset. */
    fun loudestFix(trail: List<GpsSample>): GpsSample? {
        if (trail.isEmpty()) return null
        return trail.maxWithOrNull(
            compareBy<GpsSample> {
                if (it.rssi == 0 || !Rssi.measured(it.rssi)) Int.MIN_VALUE else it.rssi
            }.thenBy { it.at },
        )
    }

    fun loudestFix(d: Sighting): GpsSample? {
        loudestFix(d.gpsTrail)?.let { return it }
        val lat = d.latitude ?: return null
        val lon = d.longitude ?: return null
        return GpsSample(d.lastSeen, lat, lon, d.rssi)
    }
}
