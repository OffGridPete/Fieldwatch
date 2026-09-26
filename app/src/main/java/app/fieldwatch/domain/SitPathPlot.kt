package app.fieldwatch.domain

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** North-up path of this phone. Dots are hear-points, not radio fixes. */
object SitPathPlot {
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
    )

    data class Model(
        val samples: List<GpsSample>,
        val dots: List<Dot>,
        val lengthM: Double,
        val spanM: Double,
        val title: String,
        val emptyHint: String? = null,
        val live: Boolean = false,
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

    data class FigureTrack(
        val name: String,
        val samples: List<GpsSample>,
        val secondary: Boolean = false,
    )

    data class Figure(
        val kicker: String,
        val tracks: List<FigureTrack>,
        val dots: List<Dot>,
        val lengthM: Double,
        val spanM: Double,
        val caption: String,
    ) {
        val drawable: Boolean
            get() = tracks.any { it.samples.size >= 2 }
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

    fun clusters(dots: List<Pair<Dot, Pt>>, threshPx: Float = 28f): List<Cluster> {
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
            val members = idx.sorted().map { Member(it + 1, dots[it].first, dots[it].second) }
            val cx = members.map { it.at.x }.average().toFloat()
            val cy = members.map { it.at.y }.average().toFloat()
            val id = members.map { it.dot.key }.sorted().joinToString("|")
            out += Cluster(id, Pt(cx, cy), members)
        }
        return out
    }

    fun layout(
        model: Model,
        width: Float,
        height: Float,
        pad: Float = 16f,
    ): Layout? {
        if (width <= pad * 2 || height <= pad * 2) return null
        val pts = Geo.despikePath(model.samples).let { cleaned ->
            if (cleaned.size >= 2) cleaned else model.samples
        }
        if (pts.size < 2) return null
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
            val hx = ((maxX - minX) / 2f).coerceAtLeast(4f) * 1.22f
            val hy = ((maxY - minY) / 2f).coerceAtLeast(4f) * 1.22f
            minX = midX - hx
            maxX = midX + hx
            minY = midY - hy
            maxY = midY + hy
        }
        val spanX = (maxX - minX).coerceAtLeast(8f)
        val spanY = (maxY - minY).coerceAtLeast(8f)
        val innerW = width - pad * 2
        val innerH = height - pad * 2 - 44f
        val scale = min(innerW / spanX, innerH / spanY)
        val usedW = spanX * scale
        val usedH = spanY * scale
        val ox = pad + (innerW - usedW) / 2f
        val oy = pad + (innerH - usedH) / 2f
        fun map(mxv: Float, myv: Float) = Pt(
            x = ox + (mxv - minX) * scale,
            y = oy + (maxY - myv) * scale,
        )
        val path = raw.map { map(it.first, it.second) }
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
            plotBottom = height - pad - 44f,
            samples = pts,
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
    ): PlotRadios {
        val points = ArrayList<Dot>()
        devices
            .filter {
                it.attentionNotes(fleets).isNotEmpty() || it.key in bookmarkedKeys
            }
            .groupBy { it.key }
            .forEach { (_, group) ->
                val d = group.maxBy { it.rssi }
                val fix = loudestFix(d) ?: return@forEach
                val bookmarked = d.key in bookmarkedKeys
                val notes = if (bookmarked) observerNotes[d.key].orEmpty() else ""
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
                )
            }
        return PlotRadios(
            points.sortedWith(compareByDescending<Dot> { it.extraAttention }.thenBy { it.label }).take(cap),
        )
    }

    /** One hear-point per radio: loudest GPS-trail sample. rssi 0 is treated as unset. */
    fun loudestFix(trail: List<GpsSample>): GpsSample? {
        if (trail.isEmpty()) return null
        return trail.maxWithOrNull(
            compareBy<GpsSample> { if (it.rssi == 0) Int.MIN_VALUE else it.rssi }.thenBy { it.at },
        )
    }

    fun loudestFix(d: Sighting): GpsSample? {
        loudestFix(d.gpsTrail)?.let { return it }
        val lat = d.latitude ?: return null
        val lon = d.longitude ?: return null
        return GpsSample(d.lastSeen, lat, lon, d.rssi)
    }
}
