package app.fieldwatch.domain

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** North-up path of this phone. Alert dots are hear-points, unless the radio advertised a position. */
object SitPathPlot {
    /**
     * Shortest half-width of the walk plot, in meters. A sit that barely moved
     * still shows the surrounding streets. A longer walk is unchanged.
     */
    const val MIN_HALF_SPAN_M = 180f

    /** Room around that floor. A sit that has not moved uses both. */
    const val FIT_PAD = 1.22f
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
        /** GNSS detections placed on this phone's line. Not drawn on an aircraft card. */
        val gnss: List<GnssPin> = emptyList(),
        /** Cameras the sit path came near. Empty on an aircraft card and on Show live path. */
        val pathCameras: List<FlockCamera> = emptyList(),
        /** Same camera count as the sit report. Null on an aircraft card and on Show live path. */
        val pathCameraTally: PathCameras.Tally? = null,
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
        /** Meters per screen pixel. 1 when a caller built a layout without a fit. */
        val metersPerPx: Float = 1f,
        /**
         * On-screen geographic window. The drive line is clipped to this.
         * It can be inset from [plotLeft] when the window is not the shape of the map.
         */
        val pictureLeft: Float = 0f,
        val pictureTop: Float = 0f,
        val pictureRight: Float = 0f,
        val pictureBottom: Float = 0f,
        /** Inverse of [project]. Latitude, then longitude. */
        val unproject: (Float, Float) -> Pair<Double, Double> = { _, _ -> 0.0 to 0.0 },
    ) {
        /**
         * South, north, west, east of the four screen corners, grown a little.
         * A turned map puts those corners outside the path's north-up box.
         */
        fun tileWindow(): DoubleArray? {
            val corners = arrayOf(
                unproject(plotLeft, plotTop),
                unproject(plotRight, plotTop),
                unproject(plotLeft, plotBottom),
                unproject(plotRight, plotBottom),
            )
            var south = Double.POSITIVE_INFINITY
            var north = Double.NEGATIVE_INFINITY
            var west = Double.POSITIVE_INFINITY
            var east = Double.NEGATIVE_INFINITY
            for ((lat, lon) in corners) {
                if (!lat.isFinite() || !lon.isFinite()) return null
                if (lat < south) south = lat
                if (lat > north) north = lat
                if (lon < west) west = lon
                if (lon > east) east = lon
            }
            if (north <= south || east <= west) return null
            val midLat = (south + north) / 2.0
            val midLon = (west + east) / 2.0
            val halfLat = (north - south) / 2.0 * TILE_WINDOW_PAD
            val halfLon = (east - west) / 2.0 * TILE_WINDOW_PAD
            return doubleArrayOf(midLat - halfLat, midLat + halfLat, midLon - halfLon, midLon + halfLon)
        }

        /** True when this pixel sits on the geographic window. */
        fun onPicture(x: Float, y: Float): Boolean =
            x.isFinite() && y.isFinite() &&
                x in pictureLeft..pictureRight && y in pictureTop..pictureBottom

        /** A mark this close can still be drawn. A point far off the map is not. */
        fun nearPicture(x: Float, y: Float, margin: Float = 24f): Boolean {
            if (!x.isFinite() || !y.isFinite()) return false
            return x in (pictureLeft - margin)..(pictureRight + margin) &&
                y in (pictureTop - margin)..(pictureBottom + margin)
        }
    }

    /**
     * Held live view. East and north stay separate so the first pinch on a tall
     * walk does not square the window. A null viewport follows the whole walk.
     */
    data class Viewport(
        val centerLat: Double,
        val centerLon: Double,
        val halfEastM: Float,
        val halfNorthM: Float,
        /** Course when the finger took the map. NaN if that picture was north-up. */
        val heldCourseDeg: Double = Double.NaN,
    ) {
        fun clamped(minHalfM: Float = MIN_HALF_M): Viewport {
            val floor = if (minHalfM > 0f) minHalfM else MIN_HALF_M
            val east = halfEastM.coerceIn(floor, MAX_HALF_M)
            val north = halfNorthM.coerceIn(floor, MAX_HALF_M)
            return if (east == halfEastM && north == halfNorthM) this
            else copy(halfEastM = east, halfNorthM = north)
        }

        /** [factor] above 1 zooms in. The focus point stays put. */
        fun zoomAbout(
            factor: Float,
            focusLat: Double,
            focusLon: Double,
            minHalfM: Float = MIN_HALF_M,
        ): Viewport {
            if (factor <= 0f || factor == 1f) return this
            val floor = if (minHalfM > 0f) minHalfM else MIN_HALF_M
            val wanted = 1f / factor
            val minScale = max(floor / halfEastM, floor / halfNorthM)
            val maxScale = min(MAX_HALF_M / halfEastM, MAX_HALF_M / halfNorthM)
            val scale = wanted.coerceIn(minScale, maxScale)
            val newEast = halfEastM * scale
            val newNorth = halfNorthM * scale
            val applied = halfEastM / newEast
            return copy(
                centerLat = focusLat + (centerLat - focusLat) / applied,
                centerLon = focusLon + (centerLon - focusLon) / applied,
                halfEastM = newEast,
                halfNorthM = newNorth,
            )
        }

        /**
         * South, north, west, east of this view, padded a little past the screen.
         * A short extra drag stays on tiles that are already loaded.
         */
        fun coveredBounds(): DoubleArray {
            val v = clamped()
            val cos0 = cos(Math.toRadians(v.centerLat)).coerceAtLeast(0.2)
            val dLat = v.halfNorthM * 1.2 / 110_540.0
            val dLon = v.halfEastM * 1.2 / (111_320.0 * cos0)
            return doubleArrayOf(
                v.centerLat - dLat,
                v.centerLat + dLat,
                v.centerLon - dLon,
                v.centerLon + dLon,
            )
        }

        /** Moves the center east and north, in meters. */
        fun panMeters(eastM: Float, northM: Float): Viewport {
            val cos0 = cos(Math.toRadians(centerLat)).coerceAtLeast(0.2)
            val dLon = eastM / (111_320.0 * cos0)
            val dLat = northM / 110_540.0
            return copy(centerLat = centerLat + dLat, centerLon = centerLon + dLon)
        }

        /**
         * Screen drag, in pixels, y down. [courseDeg] is the course the picture
         * was drawn with. NaN keeps the drag on east and north.
         */
        fun panScreen(panX: Float, panY: Float, metersPerPx: Float, courseDeg: Double): Viewport {
            val screenRight = -panX * metersPerPx
            val screenUp = panY * metersPerPx
            val turn = if (courseDeg.isFinite()) Math.toRadians(courseDeg) else 0.0
            val cosT = cos(turn)
            val sinT = sin(turn)
            val east = screenRight * cosT + screenUp * sinT
            val north = -screenRight * sinT + screenUp * cosT
            return panMeters(east.toFloat(), north.toFloat())
        }

        companion object {
            const val MIN_HALF_M = 25f
            const val MAX_HALF_M = 80_000f
        }
    }

    /** Stable key for a [Layout.tileWindow]. About one meter. */
    fun tileWindowKey(bounds: DoubleArray): String =
        bounds.joinToString(":") { Math.round(it * 100_000.0).toString() }

    private const val TILE_WINDOW_PAD = 1.12

    /** The geographic window currently drawn, so the next pinch does not jump. */
    fun viewportOf(layout: Layout, courseDeg: Double = Double.NaN): Viewport {
        val cx = (layout.plotLeft + layout.plotRight) / 2f
        val cy = (layout.plotTop + layout.plotBottom) / 2f
        val (lat, lon) = layout.unproject(cx, cy)
        val halfEast = (layout.plotRight - layout.plotLeft) / 2f * layout.metersPerPx
        val halfNorth = (layout.plotBottom - layout.plotTop) / 2f * layout.metersPerPx
        return Viewport(lat, lon, halfEast, halfNorth, courseDeg).clamped()
    }

    data class Mark(
        val lat: Double,
        val lon: Double,
        val label: String,
    )

    /**
     * Where this phone was when a GNSS detection started.
     * The spot is the path sample nearest in time. It is not the source of the interference.
     */
    data class GnssPin(
        val at: Long,
        val endedAt: Long = 0L,
        val kind: String,
        val confidence: String,
        val detail: String,
        val lat: Double,
        val lon: Double,
        val secondary: Boolean = false,
    ) {
        fun title(): String = when (kind) {
            "spoofing" -> "Possible GNSS spoofing"
            else -> "Possible GNSS interference"
        }

        /** Local clock, same as the times drawn on the path. */
        fun whenLine(): String {
            val start = GnssCopy.localHm(at)
            return if (endedAt > at) "$start–${GnssCopy.localHm(endedAt)}" else start
        }

        fun keyLine(): String {
            val sit = if (secondary) "Second sit. " else ""
            val what = detail.trim().ifBlank { "Signal changed." }
            return "$sit${title()} · $confidence · ${whenLine()}. $what"
        }
    }

    /**
     * One pin per mark, on the path sample closest in time to the start.
     * An empty path leaves the mark in the report text only.
     */
    fun gnssPins(
        marks: List<GnssMark>,
        path: List<GpsSample>,
        secondary: Boolean = false,
    ): List<GnssPin> {
        if (marks.isEmpty() || path.isEmpty()) return emptyList()
        return marks.sortedBy { it.at }.map { mark ->
            val sample = path.minBy { abs(it.at - mark.at) }
            GnssPin(
                at = mark.at,
                endedAt = mark.endedAt,
                kind = mark.kind,
                confidence = mark.confidence,
                detail = mark.detail,
                lat = sample.lat,
                lon = sample.lon,
                secondary = secondary,
            )
        }
    }

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
        /** GNSS detections on this phone's line. Empty on an aircraft figure. */
        val gnss: List<GnssPin> = emptyList(),
        /** Cameras along this phone's path. Empty on an aircraft figure and on Compare. */
        val cameras: List<FlockCamera> = emptyList(),
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

    /**
     * Marks that share a screen spot. [chain] follows neighbors, so a line of
     * marks stays one number. Reports → Path leaves that off: a zoomed view
     * then draws each spot, and only marks on the same spot stay a number.
     */
    fun clusters(
        dots: List<Pair<Dot, Pt>>,
        threshPx: Float = 28f,
        chain: Boolean = true,
        numberOf: (Dot) -> Int = { dot ->
            val i = dots.indexOfFirst { it.first.key == dot.key }
            if (i < 0) 1 else i + 1
        },
    ): List<Cluster> {
        val used = BooleanArray(dots.size)
        val out = ArrayList<Cluster>()
        val thresh2 = threshPx * threshPx
        for (i in dots.indices) {
            if (used[i]) continue
            val idx = ArrayList<Int>()
            if (chain) {
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
                        if (dx * dx + dy * dy <= thresh2) {
                            used[k] = true
                            q.add(k)
                        }
                    }
                }
            } else {
                val anchor = dots[i].second
                used[i] = true
                idx += i
                for (k in i + 1 until dots.size) {
                    if (used[k]) continue
                    val dx = anchor.x - dots[k].second.x
                    val dy = anchor.y - dots[k].second.y
                    if (dx * dx + dy * dy <= thresh2) {
                        used[k] = true
                        idx += k
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
        viewport: Viewport? = null,
        minHalfM: Float = Viewport.MIN_HALF_M,
        /**
         * Close follow. Half the short side, in meters, centered on the last point.
         * 0 fits the whole picture. A held view ignores this.
         */
        followHalfM: Float = 0f,
        /** Degrees clockwise from north. NaN stays north-up. */
        courseDeg: Double = Double.NaN,
    ): Layout? = prepare(
        ground(model),
        width,
        height,
        pad,
        scaleBarReserve,
        viewport,
        minHalfM,
        followHalfM,
        courseDeg,
        minHalfSpanM = model.minHalfSpanM,
        trace = true,
    )?.layout

    /**
     * One despiked track, in meters east and north of a fixed origin.
     * The cosine stays out, so a later view is a multiply. The line keeps
     * every sample [layout] plots.
     */
    internal class Ground(
        val samples: List<GpsSample>,
        val dx: DoubleArray,
        val dy: DoubleArray,
        val frameSamples: List<GpsSample>,
        val frameDx: DoubleArray,
        val frameDy: DoubleArray,
        val dots: List<Dot>,
        val dotDx: DoubleArray,
        val dotDy: DoubleArray,
        val latC: Double,
        val lonC: Double,
        val stops: List<Geo.PathLeg>,
    )

    internal fun ground(model: Model): Ground {
        val phone = Geo.despikePath(model.samples).let { cleaned ->
            if (cleaned.size >= 2) cleaned else model.samples
        }
        val frame = model.frameSamples
        val dots = model.dots
        val origin = phone.firstOrNull() ?: frame.firstOrNull()
        val latC = origin?.lat ?: dots.firstOrNull()?.lat ?: 0.0
        val lonC = origin?.lon ?: dots.firstOrNull()?.lon ?: 0.0
        return Ground(
            samples = phone,
            dx = eastOf(phone, lonC),
            dy = northOf(phone, latC),
            frameSamples = frame,
            frameDx = eastOf(frame, lonC),
            frameDy = northOf(frame, latC),
            dots = dots,
            dotDx = eastOfDots(dots, lonC),
            dotDy = northOfDots(dots, latC),
            latC = latC,
            lonC = lonC,
            stops = Geo.realStops(phone),
        )
    }

    /** One clipped piece of the drive. [join] continues the piece already open. */
    internal fun interface DriveSink {
        fun segment(x1: Float, y1: Float, x2: Float, y2: Float, join: Boolean)
    }

    /**
     * A placed [Ground]. [trace] false leaves [Layout.path] empty so a heading
     * turn does not allocate a point per sample. [stroke] is that line.
     */
    internal class Prepared(
        val layout: Layout,
        val stopCenters: List<Pt>,
        private val fit: Fit,
        private val ground: Ground,
    ) {
        /**
         * The drive, clipped so the round caps land on the edge.
         * [padPx] keeps a margin outside the picture while a finger or a turn is moving it.
         * Zero leaves the caps where they land today. A close view skips the rest of a long line.
         */
        fun stroke(blue: DriveSink, green: DriveSink, padPx: Float = 0f) {
            val samples = ground.samples
            val n = samples.size
            if (n < 2) return
            val pad = if (padPx > 0f) padPx else 0f
            val left = layout.pictureLeft - pad
            val top = layout.pictureTop - pad
            val right = layout.pictureRight + pad
            val bottom = layout.pictureBottom + pad
            if (!(right > left && bottom > top)) return
            val pts = FloatArray(4)
            val clip = FloatArray(4)
            val dx = ground.dx
            val dy = ground.dy
            val stops = ground.stops
            val box = cullBox(left, top, right, bottom)
            var blueOn = false
            var greenOn = false
            var prevBx = 0f
            var prevBy = 0f
            var prevGx = 0f
            var prevGy = 0f
            for (i in 1 until n) {
                if (box != null && segmentOutside(dx[i - 1], dy[i - 1], dx[i], dy[i], box)) {
                    greenOn = false
                    continue
                }
                fit.write(dx[i - 1], dy[i - 1], pts, 0)
                fit.write(dx[i], dy[i], pts, 2)
                if (!clipSegmentTo(pts[0], pts[1], pts[2], pts[3], left, top, right, bottom, clip)) {
                    greenOn = false
                    continue
                }
                val joinBlue = blueOn && nearCoord(clip[0], clip[1], prevBx, prevBy)
                blue.segment(clip[0], clip[1], clip[2], clip[3], joinBlue)
                blueOn = true
                prevBx = clip[2]
                prevBy = clip[3]
                if (Geo.stopCovers(stops, samples[i - 1].at, samples[i].at)) {
                    val joinGreen = greenOn && nearCoord(clip[0], clip[1], prevGx, prevGy)
                    green.segment(clip[0], clip[1], clip[2], clip[3], joinGreen)
                    greenOn = true
                    prevGx = clip[2]
                    prevGy = clip[3]
                } else {
                    greenOn = false
                }
            }
        }

        /** Ground box of the clip, or null when the line is already inside it. */
        private fun cullBox(left: Float, top: Float, right: Float, bottom: Float): DoubleArray? {
            val corners = floatArrayOf(left, top, right, top, left, bottom, right, bottom)
            var minX = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            var i = 0
            while (i < corners.size) {
                val (lat, lon) = layout.unproject(corners[i], corners[i + 1])
                if (!lat.isFinite() || !lon.isFinite()) return null
                val x = (lon - ground.lonC) * M_PER_DEG_LON
                val y = (lat - ground.latC) * M_PER_DEG_LAT
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                i += 2
            }
            minX -= 12.0
            maxX += 12.0
            minY -= 12.0
            maxY += 12.0
            val dx = ground.dx
            val dy = ground.dy
            for (k in dx.indices) {
                if (dx[k] < minX || dx[k] > maxX || dy[k] < minY || dy[k] > maxY) {
                    return doubleArrayOf(minX, maxX, minY, maxY)
                }
            }
            return null
        }
    }

    internal fun prepare(
        ground: Ground,
        width: Float,
        height: Float,
        pad: Float = 16f,
        scaleBarReserve: Float = 44f,
        viewport: Viewport? = null,
        minHalfM: Float = Viewport.MIN_HALF_M,
        followHalfM: Float = 0f,
        courseDeg: Double = Double.NaN,
        minHalfSpanM: Float = 0f,
        trace: Boolean = true,
    ): Prepared? {
        if (width <= pad * 2 || height <= pad * 2 + scaleBarReserve) return null
        val phone = ground.samples
        val frame = ground.frameSamples
        val held = viewport?.clamped(minHalfM)
        val anchor = if (held == null && followHalfM > 0f) {
            phone.lastOrNull() ?: frame.lastOrNull()
        } else {
            null
        }
        if (held == null && anchor == null && phone.isEmpty() && frame.isEmpty() && ground.dots.isEmpty()) {
            return null
        }
        val lat0: Double
        val lon0: Double
        if (held != null) {
            lat0 = held.centerLat
            lon0 = held.centerLon
        } else if (anchor != null) {
            lat0 = anchor.lat
            lon0 = anchor.lon
        } else if (phone.isNotEmpty() || frame.isNotEmpty()) {
            val mean = meanLatLon(phone, frame)
            lat0 = mean.first
            lon0 = mean.second
        } else {
            var lat = 0.0
            var lon = 0.0
            for (dot in ground.dots) {
                lat += dot.lat
                lon += dot.lon
            }
            val n = ground.dots.size.toDouble()
            lat0 = lat / n
            lon0 = lon / n
        }
        val spin = Spin(
            cos0 = cos(Math.toRadians(lat0)).coerceAtLeast(0.2),
            cosT = cos(if (courseDeg.isFinite()) Math.toRadians(courseDeg) else 0.0),
            sinT = sin(if (courseDeg.isFinite()) Math.toRadians(courseDeg) else 0.0),
            dx0 = (lon0 - ground.lonC) * M_PER_DEG_LON,
            dy0 = (lat0 - ground.latC) * M_PER_DEG_LAT,
            latC = ground.latC,
            lonC = ground.lonC,
            lat0 = lat0,
            lon0 = lon0,
        )
        val innerW = width - pad * 2
        val innerH = height - pad * 2 - scaleBarReserve
        val minX: Float
        val maxX: Float
        val minY: Float
        val maxY: Float
        if (held != null) {
            minX = -held.halfEastM
            maxX = held.halfEastM
            minY = -held.halfNorthM
            maxY = held.halfNorthM
        } else if (anchor != null) {
            val shortPx = min(innerW, innerH)
            val metersPerPx = (followHalfM * 2f) / shortPx
            val halfX = innerW / 2f * metersPerPx
            val halfY = innerH / 2f * metersPerPx
            minX = -halfX
            maxX = halfX
            minY = -halfY
            maxY = halfY
        } else {
            var loX = Float.POSITIVE_INFINITY
            var hiX = Float.NEGATIVE_INFINITY
            var loY = Float.POSITIVE_INFINITY
            var hiY = Float.NEGATIVE_INFINITY
            fun take(dx: Double, dy: Double) {
                val x = spin.mx(dx, dy)
                val y = spin.my(dx, dy)
                loX = min(loX, x); hiX = max(hiX, x)
                loY = min(loY, y); hiY = max(hiY, y)
            }
            for (i in ground.dx.indices) take(ground.dx[i], ground.dy[i])
            for (i in ground.frameDx.indices) take(ground.frameDx[i], ground.frameDy[i])
            for (i in ground.dotDx.indices) take(ground.dotDx[i], ground.dotDy[i])
            val midX = (loX + hiX) / 2f
            val midY = (loY + hiY) / 2f
            val floor = if (minHalfSpanM > 0f) minHalfSpanM else MIN_HALF_SPAN_M
            val hx = ((hiX - loX) / 2f).coerceAtLeast(floor) * FIT_PAD
            val hy = ((hiY - loY) / 2f).coerceAtLeast(floor) * FIT_PAD
            minX = midX - hx
            maxX = midX + hx
            minY = midY - hy
            maxY = midY + hy
        }
        val spanX = (maxX - minX).coerceAtLeast(8f)
        val spanY = (maxY - minY).coerceAtLeast(8f)
        val scale = min(innerW / spanX, innerH / spanY)
        val usedW = spanX * scale
        val usedH = spanY * scale
        val ox = pad + (innerW - usedW) / 2f
        val oy = pad + (innerH - usedH) / 2f
        val fit = Fit(spin, minX, maxY, scale, ox, oy)
        val path = if (!trace || phone.isEmpty()) {
            emptyList()
        } else {
            val out = ArrayList<Pt>(phone.size)
            val buf = FloatArray(2)
            for (i in phone.indices) {
                fit.write(ground.dx[i], ground.dy[i], buf, 0)
                out.add(Pt(buf[0], buf[1]))
            }
            out
        }
        val placedDots = ArrayList<Pair<Dot, Pt>>(ground.dots.size)
        val dotBuf = FloatArray(2)
        for (i in ground.dots.indices) {
            fit.write(ground.dotDx[i], ground.dotDy[i], dotBuf, 0)
            placedDots.add(ground.dots[i] to Pt(dotBuf[0], dotBuf[1]))
        }
        val barM = niceMeters(spanX / scale * 0.28)
        val barFrac = ((barM.toFloat() * scale) / innerW).coerceIn(0.08f, 0.45f)
        val placed = Layout(
            path = path,
            dots = placedDots,
            scaleBarM = barM,
            scaleBarFrac = barFrac,
            project = { lat, lon -> fit.project(lat, lon) },
            plotLeft = pad,
            plotTop = pad,
            plotRight = width - pad,
            plotBottom = height - pad - scaleBarReserve,
            samples = phone,
            metersPerPx = 1f / scale,
            pictureLeft = ox,
            pictureTop = oy,
            pictureRight = ox + usedW,
            pictureBottom = oy + usedH,
            unproject = { x, y -> fit.unproject(x, y) },
        )
        val stopCenters = ArrayList<Pt>(ground.stops.size)
        for (stay in ground.stops) stopCenters.add(placed.project(stay.lat, stay.lon))
        return Prepared(placed, stopCenters, fit, ground)
    }

    internal class Spin(
        val cos0: Double,
        val cosT: Double,
        val sinT: Double,
        val dx0: Double,
        val dy0: Double,
        val latC: Double,
        val lonC: Double,
        val lat0: Double,
        val lon0: Double,
    ) {
        fun mx(dx: Double, dy: Double): Float {
            val east = (dx - dx0) * cos0
            val north = dy - dy0
            return (east * cosT - north * sinT).toFloat()
        }

        fun my(dx: Double, dy: Double): Float {
            val east = (dx - dx0) * cos0
            val north = dy - dy0
            return (east * sinT + north * cosT).toFloat()
        }
    }

    internal class Fit(
        val spin: Spin,
        val minX: Float,
        val maxY: Float,
        val scale: Float,
        val ox: Float,
        val oy: Float,
    ) {
        fun write(dx: Double, dy: Double, out: FloatArray, at: Int) {
            val mx = spin.mx(dx, dy)
            val my = spin.my(dx, dy)
            out[at] = ox + (mx - minX) * scale
            out[at + 1] = oy + (maxY - my) * scale
        }

        fun project(lat: Double, lon: Double): Pt {
            val mx = spin.mx((lon - spin.lonC) * M_PER_DEG_LON, (lat - spin.latC) * M_PER_DEG_LAT)
            val my = spin.my((lon - spin.lonC) * M_PER_DEG_LON, (lat - spin.latC) * M_PER_DEG_LAT)
            return Pt(ox + (mx - minX) * scale, oy + (maxY - my) * scale)
        }

        fun unproject(x: Float, y: Float): Pair<Double, Double> {
            val screenEast = minX + (x - ox) / scale
            val screenNorth = maxY - (y - oy) / scale
            val east = screenEast * spin.cosT + screenNorth * spin.sinT
            val north = -screenEast * spin.sinT + screenNorth * spin.cosT
            val lat = spin.lat0 + north / M_PER_DEG_LAT
            val lon = spin.lon0 + east / (M_PER_DEG_LON * spin.cos0)
            return lat to lon
        }
    }

    private fun eastOf(samples: List<GpsSample>, lonC: Double): DoubleArray {
        val out = DoubleArray(samples.size)
        for (i in samples.indices) out[i] = (samples[i].lon - lonC) * M_PER_DEG_LON
        return out
    }

    private fun northOf(samples: List<GpsSample>, latC: Double): DoubleArray {
        val out = DoubleArray(samples.size)
        for (i in samples.indices) out[i] = (samples[i].lat - latC) * M_PER_DEG_LAT
        return out
    }

    private fun eastOfDots(dots: List<Dot>, lonC: Double): DoubleArray {
        val out = DoubleArray(dots.size)
        for (i in dots.indices) out[i] = (dots[i].lon - lonC) * M_PER_DEG_LON
        return out
    }

    private fun northOfDots(dots: List<Dot>, latC: Double): DoubleArray {
        val out = DoubleArray(dots.size)
        for (i in dots.indices) out[i] = (dots[i].lat - latC) * M_PER_DEG_LAT
        return out
    }

    /** Same sum order as averaging the phone samples, then the frame samples. */
    private fun meanLatLon(phone: List<GpsSample>, frame: List<GpsSample>): Pair<Double, Double> {
        var lat = 0.0
        var lon = 0.0
        var n = 0
        for (sample in phone) {
            lat += sample.lat
            lon += sample.lon
            n += 1
        }
        for (sample in frame) {
            lat += sample.lat
            lon += sample.lon
            n += 1
        }
        return lat / n to lon / n
    }

    private fun nearCoord(x1: Float, y1: Float, x2: Float, y2: Float): Boolean =
        abs(x1 - x2) <= 0.05f && abs(y1 - y2) <= 0.05f

    private fun segmentOutside(x0: Double, y0: Double, x1: Double, y1: Double, box: DoubleArray): Boolean {
        val loX = min(x0, x1)
        val hiX = max(x0, x1)
        if (hiX < box[0] || loX > box[1]) return true
        val loY = min(y0, y1)
        val hiY = max(y0, y1)
        return hiY < box[2] || loY > box[3]
    }

    /**
     * Extra pixels a turn of [turnDeg] needs so the screen corners stay filled.
     * The pad is the sweep of the rectangle, plus the route cap.
     */
    fun rotationPadPx(turnDeg: Double, halfW: Float, halfH: Float): Float {
        val angle = abs(turnDeg)
        if (angle < 1.0 || halfW <= 0f || halfH <= 0f) return 0f
        val rad = Math.toRadians(angle)
        val c = abs(cos(rad)).toFloat()
        val s = abs(sin(rad)).toFloat()
        val padX = halfW * c + halfH * s - halfW
        val padY = halfW * s + halfH * c - halfH
        return max(padX, padY).coerceAtLeast(0f) + 12f
    }

    /**
     * Screen point for a picture laid out at one heading, then turned and moved.
     * [turnDeg] is clockwise degrees around [pivotX], [pivotY]. Identity leaves the point.
     */
    fun picturePoint(
        x: Float,
        y: Float,
        originX: Float,
        originY: Float,
        centerX: Float,
        centerY: Float,
        scale: Float,
        turnDeg: Float,
        pivotX: Float,
        pivotY: Float,
    ): Pt {
        val rad = Math.toRadians(turnDeg.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val dx = x - pivotX
        val dy = y - pivotY
        val rx = pivotX + dx * c - dy * s
        val ry = pivotY + dx * s + dy * c
        val k = if (scale > 0f) scale else 1f
        return Pt(centerX + (rx - originX) * k, centerY + (ry - originY) * k)
    }

    /** Inverse of [picturePoint]. A tap during a turn or a drag lands on the laid-out mark. */
    fun pictureSource(
        x: Float,
        y: Float,
        originX: Float,
        originY: Float,
        centerX: Float,
        centerY: Float,
        scale: Float,
        turnDeg: Float,
        pivotX: Float,
        pivotY: Float,
    ): Pt {
        val k = if (scale > 0f) scale else 1f
        val rx = originX + (x - centerX) / k
        val ry = originY + (y - centerY) / k
        val rad = Math.toRadians(-turnDeg.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val dx = rx - pivotX
        val dy = ry - pivotY
        return Pt(pivotX + dx * c - dy * s, pivotY + dx * s + dy * c)
    }

    private const val M_PER_DEG_LON = 111_320.0
    private const val M_PER_DEG_LAT = 110_540.0

    /**
     * Pieces of [points] that sit on the map. A point far past the edge is cut
     * at the edge, so the stroke cannot jump the clip.
     */
    fun clipPolyline(
        points: List<Pt>,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): List<List<Pt>> {
        if (points.size < 2 || !(right > left && bottom > top)) return emptyList()
        val runs = ArrayList<ArrayList<Pt>>()
        for (i in 1 until points.size) {
            val clipped = clipSegment(points[i - 1], points[i], left, top, right, bottom) ?: continue
            val open = runs.lastOrNull()
            val start = clipped.first
            if (open != null && nearPt(open.last(), start)) open.add(clipped.second)
            else runs.add(arrayListOf(start, clipped.second))
        }
        return runs
    }

    /** The part of the segment inside the rectangle, or null when it misses. */
    fun clipSegment(
        a: Pt,
        b: Pt,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): Pair<Pt, Pt>? {
        val out = FloatArray(4)
        if (!clipSegmentTo(a.x, a.y, b.x, b.y, left, top, right, bottom, out)) return null
        return Pt(out[0], out[1]) to Pt(out[2], out[3])
    }

    /** Writes x1, y1, x2, y2 into [out]. False when the segment misses the rectangle. */
    private fun clipSegmentTo(
        ax0: Float,
        ay0: Float,
        bx0: Float,
        by0: Float,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        out: FloatArray,
    ): Boolean {
        if (!ax0.isFinite() || !ay0.isFinite() || !bx0.isFinite() || !by0.isFinite()) return false
        if (!(right > left && bottom > top)) return false
        var ax = ax0
        var ay = ay0
        var bx = bx0
        var by = by0
        var codeA = outCode(ax, ay, left, top, right, bottom)
        var codeB = outCode(bx, by, left, top, right, bottom)
        var guard = 0
        while (guard++ < 8) {
            if (codeA or codeB == 0) {
                out[0] = ax
                out[1] = ay
                out[2] = bx
                out[3] = by
                return true
            }
            if (codeA and codeB != 0) return false
            val code = if (codeA != 0) codeA else codeB
            val x: Float
            val y: Float
            when {
                code and CLIP_TOP != 0 -> {
                    if (by == ay) return false
                    x = ax + (bx - ax) * (top - ay) / (by - ay)
                    y = top
                }
                code and CLIP_BOTTOM != 0 -> {
                    if (by == ay) return false
                    x = ax + (bx - ax) * (bottom - ay) / (by - ay)
                    y = bottom
                }
                code and CLIP_RIGHT != 0 -> {
                    if (bx == ax) return false
                    y = ay + (by - ay) * (right - ax) / (bx - ax)
                    x = right
                }
                else -> {
                    if (bx == ax) return false
                    y = ay + (by - ay) * (left - ax) / (bx - ax)
                    x = left
                }
            }
            if (!x.isFinite() || !y.isFinite()) return false
            if (code == codeA) {
                ax = x
                ay = y
                codeA = outCode(ax, ay, left, top, right, bottom)
            } else {
                bx = x
                by = y
                codeB = outCode(bx, by, left, top, right, bottom)
            }
        }
        out[0] = ax.coerceIn(left, right)
        out[1] = ay.coerceIn(top, bottom)
        out[2] = bx.coerceIn(left, right)
        out[3] = by.coerceIn(top, bottom)
        return true
    }

    private fun nearPt(a: Pt, b: Pt) = abs(a.x - b.x) <= 0.05f && abs(a.y - b.y) <= 0.05f

    private fun outCode(x: Float, y: Float, left: Float, top: Float, right: Float, bottom: Float): Int {
        var code = 0
        if (x < left) code = code or CLIP_LEFT
        else if (x > right) code = code or CLIP_RIGHT
        if (y < top) code = code or CLIP_TOP
        else if (y > bottom) code = code or CLIP_BOTTOM
        return code
    }

    private const val CLIP_LEFT = 1
    private const val CLIP_RIGHT = 2
    private const val CLIP_BOTTOM = 4
    private const val CLIP_TOP = 8

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
                            opKind = d.payloadOpKind,
                            fixAt = d.payloadAt,
                            phoneLat = d.payloadPhoneLat,
                            phoneLon = d.payloadPhoneLon,
                            opAt = d.payloadOpAt,
                            opPhoneLat = d.payloadOpPhoneLat,
                            opPhoneLon = d.payloadOpPhoneLon,
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
     * (last 15 minutes, or a sit from before the trail). Operator coordinates are not a pin.
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

    fun loudestFix(d: Sighting, since: Long = Long.MIN_VALUE): GpsSample? {
        val trail = if (since == Long.MIN_VALUE) d.gpsTrail else d.gpsTrail.filter { it.at >= since }
        loudestFix(trail)?.let { return it }
        if (d.lastSeen < since) return null
        val lat = d.latitude
        val lon = d.longitude
        if (lat != null && lon != null) return GpsSample(d.lastSeen, lat, lon, d.rssi)
        val last = d.gpsTrail.lastOrNull() ?: return null
        return GpsSample(d.lastSeen, last.lat, last.lon, d.rssi)
    }

    /**
     * No sit: the later of when Show live path turned on and 15 minutes ago.
     * An open sit replaces that with the sit start. [sitStartAt] null means no sit.
     */
    fun livePathSince(now: Long, armedAt: Long, sitStartAt: Long? = null): Long {
        if (sitStartAt != null) return sitStartAt
        return maxOf(armedAt, now - DebriefPrompt.WINDOW_MS)
    }

    /**
     * Walk samples from [since] on. [mark] is where the phone was when this
     * live-path window opened. The driven line keeps its earlier corner and only
     * moves the tip, so the window would otherwise hold the dot and no line until
     * the next bend. Once [mark] ages out, the stroke still starts where the
     * current segment crosses [since], so a straight road keeps its last stretch.
     */
    fun livePathSamples(
        path: List<GpsSample>,
        since: Long,
        mark: GpsSample? = null,
    ): List<GpsSample> {
        if (since == Long.MIN_VALUE) return path
        val kept = path.filter { it.at >= since }
        val tip = path.lastOrNull()
        val origin = mark?.takeIf { it.at >= since }
            ?: return if (mark == null) kept else withWindowEdge(path, since, kept, tip)
        val line = ArrayList<GpsSample>(kept.size + 2)
        line += origin
        for (sample in kept) {
            if (sample.at < origin.at) continue
            addUnlessSamePlace(line, sample)
        }
        if (tip != null && tip.at >= since) addUnlessSamePlace(line, tip)
        return singleDotIfStill(line, tip, since)
    }

    /** A mark older than [since] still anchors the visible end of a straight segment. */
    private fun withWindowEdge(
        path: List<GpsSample>,
        since: Long,
        kept: List<GpsSample>,
        tip: GpsSample?,
    ): List<GpsSample> {
        val edge = windowEdge(path, since) ?: return kept
        val line = ArrayList<GpsSample>(kept.size + 1)
        line += edge
        for (sample in kept) addUnlessSamePlace(line, sample)
        return singleDotIfStill(line, tip, since)
    }

    /** Position on the segment that crosses [since]. Null when the path already starts there. */
    private fun windowEdge(path: List<GpsSample>, since: Long): GpsSample? {
        var prior: GpsSample? = null
        for (sample in path) {
            if (sample.at < since) {
                prior = sample
                continue
            }
            val from = prior ?: return null
            if (sample.at == since) return null
            val span = (sample.at - from.at).toDouble()
            if (span <= 0.0) return null
            val fraction = ((since - from.at).toDouble() / span).coerceIn(0.0, 1.0)
            return GpsSample(
                since,
                from.lat + (sample.lat - from.lat) * fraction,
                from.lon + (sample.lon - from.lon) * fraction,
            )
        }
        return null
    }

    private fun addUnlessSamePlace(line: MutableList<GpsSample>, sample: GpsSample) {
        val end = line.last()
        if (sample.lat == end.lat && sample.lon == end.lon) return
        line += sample
    }

    /** A phone that has not moved is the current dot, not a zero-length stroke. */
    private fun singleDotIfStill(line: List<GpsSample>, tip: GpsSample?, since: Long): List<GpsSample> {
        if (line.isEmpty()) return line
        val here = line.first()
        if (line.any { it.lat != here.lat || it.lon != here.lon }) return line
        val dot = tip?.takeIf { it.at >= since && it.lat == here.lat && it.lon == here.lon }
        return listOf(dot ?: line.last())
    }

    /**
     * Watchlist alerts heard at or after [since]. A hidden burst stays off the map.
     * Extra attention alone does not qualify.
     */
    fun livePathRadios(
        devices: List<Sighting>,
        since: Long,
        alertKeys: Set<String>,
        watchedFleetIds: Set<String>,
        hiddenKeys: Set<String> = emptySet(),
    ): List<Sighting> = devices.filter { device ->
        device.key !in hiddenKeys &&
            device.lastSeen >= since &&
            (device.key in alertKeys || device.fleetIds.any { it in watchedFleetIds })
    }

    /**
     * One dot per radio, at its advertised fix or its loudest hear inside [since].
     * Callers pass the radios that belong on the map. No report alert cap.
     */
    fun liveDots(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        customNames: Map<String, String> = emptyMap(),
        since: Long = Long.MIN_VALUE,
    ): List<Dot> {
        val points = ArrayList<Dot>(devices.size)
        for (d in devices) {
            val pin = advertisedFix(d)
            val fix = pin ?: loudestFix(d, since) ?: continue
            val fleet = d.fleetIds.firstNotNullOfOrNull { id -> fleets.firstOrNull { it.id == id } }
            points += Dot(
                key = d.key,
                lat = fix.lat,
                lon = fix.lon,
                label = d.reportName(customNames).ifBlank { d.mac },
                extraAttention = d.attentionNotes(fleets).isNotEmpty(),
                named = d.key in customNames,
                kind = d.kind,
                mac = d.mac,
                fleetNames = d.fleetIds.mapNotNull { id -> fleets.firstOrNull { it.id == id }?.name },
                rssiMin = d.rssiMin,
                rssiMax = d.rssiMax,
                classKind = fleet?.kind,
                accentArgb = fleet?.let { Palette.color(it.colorIndex) } ?: 0,
                advertised = pin != null,
            )
        }
        return points
    }
}
