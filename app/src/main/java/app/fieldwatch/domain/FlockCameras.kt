package app.fieldwatch.domain

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One saved roadside sensor. The order is the compact-file byte. Do not reorder it.
 * [PLATE] is a license plate reader. [CAMERA] is every other mapped camera.
 */
@Serializable
enum class SensorKind {
    PLATE,
    SPEED,
    RED,
    GUNSHOT,
    CAMERA,
}

/**
 * One sensor from the saved list.
 * [key] is the OSM id. A way uses the negative id. A relation uses a different negative range.
 * [facings] are degrees clockwise from true north. Empty means the file had no direction.
 * [flock] is true when the brand or operator says Flock. [brand] is the company name to show.
 * [kind] says which mark and which alert switch apply. A gunshot detector stores no direction.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class FlockCamera(
    val key: Long,
    val lat: Double,
    val lon: Double,
    val facings: List<Int>,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val brand: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val flock: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val kind: SensorKind = SensorKind.PLATE,
) {
    fun forStore(): FlockCamera {
        if (kind != SensorKind.GUNSHOT || facings.isEmpty()) return this
        return copy(facings = emptyList())
    }
}

/** A mark on the map. [count] above 1 is a crowd drawn as a number. */
data class FlockMark(
    val lat: Double,
    val lon: Double,
    val count: Int,
    val facings: List<Int>,
    val flock: Boolean = false,
    val kind: SensorKind = SensorKind.PLATE,
)

data class FlockHit(
    val key: Long,
    val looking: Boolean,
    val flock: Boolean = false,
    val brand: String = "",
    val kind: SensorKind = SensorKind.PLATE,
)

/** Which alert sentence to show. Plate readers keep the company name when the file has one. */
fun sensorNotice(hit: FlockHit): Pair<String, Map<String, String>> = when (hit.kind) {
    SensorKind.SPEED ->
        (if (hit.looking) "alert.speed.looking" else "alert.speed.ahead") to emptyMap()
    SensorKind.RED ->
        (if (hit.looking) "alert.red.looking" else "alert.red.ahead") to emptyMap()
    SensorKind.GUNSHOT -> "alert.gun.ahead" to emptyMap()
    SensorKind.CAMERA ->
        (if (hit.looking) "alert.camera.looking" else "alert.camera.ahead") to emptyMap()
    SensorKind.PLATE -> when {
        hit.flock && hit.looking -> "alert.flock.looking" to emptyMap()
        hit.flock -> "alert.flock.ahead" to emptyMap()
        hit.brand.isNotBlank() && hit.looking ->
            "alert.plate.looking" to mapOf("brand" to hit.brand)
        hit.brand.isNotBlank() -> "alert.plate.ahead" to mapOf("brand" to hit.brand)
        hit.looking -> "alert.plate.unknown.looking" to emptyMap()
        else -> "alert.plate.unknown.ahead" to emptyMap()
    }
}

fun AppSettings.alertsFor(kind: SensorKind): Boolean = when (kind) {
    SensorKind.PLATE -> flockLayer && flockAlert
    SensorKind.SPEED -> sensorLayer && speedAlert
    SensorKind.RED -> sensorLayer && redAlert
    SensorKind.GUNSHOT -> sensorLayer && gunshotAlert
    SensorKind.CAMERA -> sensorLayer && cameraAlert
}

fun AppSettings.anySensorAlert(): Boolean =
    (flockLayer && flockAlert) ||
        (sensorLayer && (speedAlert || redAlert || gunshotAlert || cameraAlert))

/**
 * Ahead and looking-your-way. Fixed distances. The settings page does not ask for a range.
 * A camera with no facing can still be ahead. A known facing that looks elsewhere stays quiet.
 * The spoken warning is farther out than the path-report corridor.
 */
object FlockApproach {
    /** Speak while a listed camera is still this far ahead. */
    const val AHEAD_M = 500.0
    /** Stay quiet until the phone is this far away, then a return can speak again. */
    const val REARM_M = 1_000.0
    /** Cameras counted along a saved path. Not the live warning. */
    const val PATH_M = 150.0
    const val MIN_SPEED_MPS = 2.0
    const val AHEAD_DEG = 35.0
    const val LOOK_DEG = 60.0
    const val MIN_COUNT = 1_000
    const val MAX_FACINGS = 4
    const val MARK_CAP = 250

    enum class Kind { AHEAD, LOOKING }

    fun delta(a: Double, b: Double): Double {
        var d = abs(a - b) % 360.0
        if (d > 180.0) d = 360.0 - d
        return d
    }

    fun norm(deg: Double): Int? {
        if (!deg.isFinite()) return null
        var turned = deg % 360.0
        if (turned < 0.0) turned += 360.0
        val rounded = (turned + 0.5).toInt() % 360
        return if (rounded < 0) rounded + 360 else rounded
    }

    /** A point [meters] from [lat], [lon], along [bearingDeg] clockwise from true north. */
    fun destination(lat: Double, lon: Double, bearingDeg: Double, meters: Double): Pair<Double, Double> {
        val radius = 6_371_000.0
        val step = meters / radius
        val bearing = Math.toRadians(bearingDeg)
        val p1 = Math.toRadians(lat)
        val l1 = Math.toRadians(lon)
        val p2 = asin(sin(p1) * cos(step) + cos(p1) * sin(step) * cos(bearing))
        val l2 = l1 + atan2(
            sin(bearing) * sin(step) * cos(p1),
            cos(step) - sin(p1) * sin(p2),
        )
        return Math.toDegrees(p2) to Math.toDegrees(l2)
    }

    fun classify(
        userLat: Double,
        userLon: Double,
        courseDeg: Double,
        speedMps: Double?,
        camLat: Double,
        camLon: Double,
        facings: List<Int>,
    ): Kind? {
        if (speedMps == null || !speedMps.isFinite() || speedMps < MIN_SPEED_MPS) return null
        if (!courseDeg.isFinite()) return null
        val dist = Geo.meters(userLat, userLon, camLat, camLon)
        if (dist > AHEAD_M || dist < 1.0) return null
        val toCam = Geo.bearingDegrees(userLat, userLon, camLat, camLon)
        if (delta(courseDeg, toCam) > AHEAD_DEG) return null
        if (facings.isEmpty()) return Kind.AHEAD
        val toUser = Geo.bearingDegrees(camLat, camLon, userLat, userLon)
        val looking = facings.any { delta(it.toDouble(), toUser) <= LOOK_DEG }
        return if (looking) Kind.LOOKING else null
    }
}

/**
 * One notice per camera. Quiet until this phone has left [FlockApproach.REARM_M]
 * and comes back. Standing still does not rearm a camera that is still close.
 */
class FlockLatch {
    private val quiet = HashSet<Long>()

    fun clear() {
        quiet.clear()
    }

    fun consider(
        lat: Double,
        lon: Double,
        courseDeg: Double,
        speedMps: Double?,
        nearby: List<FlockCamera>,
    ): FlockHit? {
        val stay = HashSet<Long>()
        var best: FlockCamera? = null
        var bestLooking = false
        var bestDist = Double.POSITIVE_INFINITY
        for (cam in nearby) {
            val dist = Geo.meters(lat, lon, cam.lat, cam.lon)
            if (dist > FlockApproach.REARM_M) continue
            stay.add(cam.key)
            if (cam.key in quiet) continue
            val kind = FlockApproach.classify(
                lat, lon, courseDeg, speedMps, cam.lat, cam.lon, cam.facings,
            ) ?: continue
            if (dist < bestDist) {
                best = cam
                bestDist = dist
                bestLooking = kind == FlockApproach.Kind.LOOKING
            }
        }
        quiet.retainAll(stay)
        val hit = best ?: return null
        quiet.add(hit.key)
        return FlockHit(hit.key, bestLooking, hit.flock, hit.brand, hit.kind)
    }
}

/** Compiled credit. The camera locations are OpenStreetMap data. Not a language-pack string. */
object FlockCredit {
    const val LINE = "© OpenStreetMap contributors"
    const val URL = "https://www.openstreetmap.org/copyright"

    fun append(base: String, on: Boolean): String {
        if (!on || base.contains("OpenStreetMap")) return base
        return "$base · $LINE"
    }
}

/**
 * Cameras sorted by latitude, plus a coarse grid for a wide view.
 * The map draws at most about [FlockApproach.MARK_CAP] marks of each type and counts the rest.
 * A counted group keeps that type, so a crowd of speed cameras stays teal.
 */
class FlockIndex {
    @Volatile private var snap: Snap = Snap.EMPTY
    @Volatile var revision: Int = 0
        private set
    @Volatile var loaded: Boolean = false
        private set

    fun replace(cameras: List<FlockCamera>) {
        snap = Snap.build(cameras)
        loaded = true
        revision += 1
    }

    fun clear() {
        snap = Snap.EMPTY
        loaded = false
        revision += 1
    }

    fun markMissing() {
        snap = Snap.EMPTY
        loaded = true
        revision += 1
    }

    /** A list with cameras is in memory. Off, missing, or still loading stays false. */
    fun hasCameras(): Boolean = loaded && snap.lats.isNotEmpty()

    fun marks(
        south: Double,
        north: Double,
        west: Double,
        east: Double,
        project: (Double, Double) -> Pair<Float, Float>,
        inside: (Float, Float) -> Boolean,
    ): List<FlockMark> = pack(snap.indexed(south, north, west, east).marks, project, inside)

    /**
     * Cameras for a box around [south]/[north]/[west]/[east]. A later picture
     * inside that box reuses [previous] when the slice-or-cell choice matches.
     * The caller keeps [previous]. This index does not.
     */
    internal fun gatherFor(
        previous: CameraGather?,
        south: Double,
        north: Double,
        west: Double,
        east: Double,
    ): CameraGather {
        val wide = snap.wide(south, north)
        if (
            previous != null &&
            previous.revision == revision &&
            previous.cells == wide &&
            previous.covers(south, north, west, east)
        ) {
            return previous
        }
        var boxSouth = south
        var boxNorth = north
        var boxWest = west
        var boxEast = east
        if (west <= east) {
            val padLat = (0.5 * (north - south)).coerceAtLeast(CELL_DEG)
            val padLon = (0.5 * (east - west)).coerceAtLeast(CELL_DEG)
            val padSouth = south - padLat
            val padNorth = north + padLat
            if (snap.wide(padSouth, padNorth) == wide) {
                boxSouth = padSouth
                boxNorth = padNorth
                boxWest = west - padLon
                boxEast = east + padLon
            }
        }
        val indexed = snap.indexed(boxSouth, boxNorth, boxWest, boxEast)
        return CameraGather(
            revision,
            indexed.cells,
            boxSouth,
            boxNorth,
            boxWest,
            boxEast,
            indexed.marks,
            indexed.band,
            indexed.col,
        )
    }

    fun near(lat: Double, lon: Double, radiusM: Double): List<FlockCamera> = snap.near(lat, lon, radiusM)

    private class Snap(
        val lats: DoubleArray,
        val lons: DoubleArray,
        val keys: LongArray,
        val facings: Array<List<Int>>,
        val brands: Array<String>,
        val flocks: BooleanArray,
        val kinds: ByteArray,
        val cells: Array<Cell>,
        val bands: IntArray,
    ) {
        fun wide(south: Double, north: Double): Boolean {
            if (lats.isEmpty()) return false
            val lo = lower(lats, south)
            val hi = upper(lats, north)
            return hi - lo > SLICE_CAP
        }

        fun indexed(south: Double, north: Double, west: Double, east: Double): IndexedMarks {
            if (lats.isEmpty()) return IndexedMarks.NONE
            val lo = lower(lats, south)
            val hi = upper(lats, north)
            if (hi <= lo) return IndexedMarks.NONE
            if (hi - lo > SLICE_CAP) return cellsIndexed(south, north, west, east)
            val list = ArrayList<FlockMark>(hi - lo)
            for (i in lo until hi) {
                if (!lonIn(lons[i], west, east)) continue
                list.add(
                    FlockMark(
                        lats[i],
                        lons[i],
                        1,
                        facings[i],
                        flocks[i],
                        SensorKind.entries[kinds[i].toInt()],
                    ),
                )
            }
            return IndexedMarks(false, list, IntArray(0), IntArray(0))
        }

        fun near(lat: Double, lon: Double, radiusM: Double): List<FlockCamera> {
            if (lats.isEmpty() || !lat.isFinite() || !lon.isFinite() || radiusM <= 0.0) return emptyList()
            val dLat = radiusM / 111_320.0
            val scale = cos(Math.toRadians(lat)).coerceAtLeast(0.2)
            val dLon = radiusM / (111_320.0 * scale)
            val lo = lower(lats, lat - dLat)
            val hi = upper(lats, lat + dLat)
            val out = ArrayList<FlockCamera>()
            for (i in lo until hi) {
                if (lons[i] < lon - dLon || lons[i] > lon + dLon) continue
                if (Geo.meters(lat, lon, lats[i], lons[i]) > radiusM) continue
                out.add(
                    FlockCamera(
                        keys[i],
                        lats[i],
                        lons[i],
                        facings[i],
                        brands[i],
                        flocks[i],
                        SensorKind.entries[kinds[i].toInt()],
                    ),
                )
            }
            return out
        }

        private fun cellsIndexed(south: Double, north: Double, west: Double, east: Double): IndexedMarks {
            if (cells.isEmpty()) return IndexedMarks(true, emptyList(), IntArray(0), IntArray(0))
            val band0 = floor(south / CELL_DEG).toInt()
            val band1 = floor(north / CELL_DEG).toInt()
            val col0 = floor(west / CELL_DEG).toInt()
            val col1 = floor(east / CELL_DEG).toInt()
            val lo = lowerInt(bands, band0)
            val hi = upperInt(bands, band1)
            val marks = ArrayList<FlockMark>()
            val band = ArrayList<Int>()
            val col = ArrayList<Int>()
            for (i in lo until hi) {
                val cell = cells[i]
                if (!colHits(cell.col, west, east, col0, col1)) continue
                marks.add(cell.mark)
                band.add(cell.band)
                col.add(cell.col)
            }
            return IndexedMarks(true, marks, band.toIntArray(), col.toIntArray())
        }

        companion object {
            val EMPTY = Snap(
                DoubleArray(0),
                DoubleArray(0),
                LongArray(0),
                emptyArray(),
                emptyArray(),
                BooleanArray(0),
                ByteArray(0),
                emptyArray(),
                IntArray(0),
            )

            fun build(cameras: List<FlockCamera>): Snap {
                if (cameras.isEmpty()) return EMPTY
                val sorted = cameras.sortedWith(compareBy({ it.lat }, { it.lon }, { it.key }))
                val n = sorted.size
                val lats = DoubleArray(n)
                val lons = DoubleArray(n)
                val keys = LongArray(n)
                val facings = Array(n) { sorted[it].facings }
                val brands = Array(n) { sorted[it].brand }
                val flocks = BooleanArray(n) { sorted[it].flock }
                val kinds = ByteArray(n) { sorted[it].kind.ordinal.toByte() }
                val acc = HashMap<Long, CellAcc>()
                for (i in 0 until n) {
                    val cam = sorted[i]
                    lats[i] = cam.lat
                    lons[i] = cam.lon
                    keys[i] = cam.key
                    val band = floor(cam.lat / CELL_DEG).toInt()
                    val col = floor(cam.lon / CELL_DEG).toInt()
                    val id = cellId(band, col, cam.kind.ordinal)
                    acc.getOrPut(id) { CellAcc(band, col, cam.kind) }.add(cam)
                }
                val cells = acc.values.map { it.seal() }.sortedBy { it.band }.toTypedArray()
                val bands = IntArray(cells.size) { cells[it].band }
                return Snap(lats, lons, keys, facings, brands, flocks, kinds, cells, bands)
            }
        }
    }

    private class CellAcc(val band: Int, val col: Int, val kind: SensorKind) {
        var count = 0
        var lat = 0.0
        var lon = 0.0
        var facings: List<Int> = emptyList()
        var flock = false

        fun add(cam: FlockCamera) {
            if (count == 0) {
                facings = cam.facings
                flock = cam.flock
            }
            count += 1
            lat += cam.lat
            lon += cam.lon
        }

        fun seal(): Cell {
            val n = count.coerceAtLeast(1)
            val one = count == 1
            return Cell(
                band,
                col,
                FlockMark(
                    lat / n,
                    lon / n,
                    count,
                    if (one) facings else emptyList(),
                    one && flock,
                    kind,
                ),
            )
        }
    }

    private class IndexedMarks(
        val cells: Boolean,
        val marks: List<FlockMark>,
        val band: IntArray,
        val col: IntArray,
    ) {
        companion object {
            val NONE = IndexedMarks(false, emptyList(), IntArray(0), IntArray(0))
        }
    }

    private data class Cell(val band: Int, val col: Int, val mark: FlockMark)

    companion object {
        internal const val CELL_DEG = 0.02
        internal const val SLICE_CAP = 8_000

        /** One wide-view cell per type, so a speed camera is not counted as a plate reader. */
        internal fun cellId(band: Int, col: Int, kind: Int): Long {
            return (kind.toLong() shl 40) or
                ((band.toLong() and 0xfffffL) shl 20) or
                (col.toLong() and 0xfffffL)
        }

        internal fun lonIn(lon: Double, west: Double, east: Double): Boolean {
            return if (west <= east) lon in west..east else lon >= west || lon <= east
        }

        internal fun colHits(col: Int, west: Double, east: Double, col0: Int, col1: Int): Boolean {
            return if (west <= east) col in col0..col1 else col >= col0 || col <= col1
        }

        internal fun pack(
            raw: List<FlockMark>,
            project: (Double, Double) -> Pair<Float, Float>,
            inside: (Float, Float) -> Boolean,
        ): List<FlockMark> {
            if (raw.isEmpty()) return emptyList()
            if (raw.size <= FlockApproach.MARK_CAP) {
                return raw.filter { mark ->
                    val (x, y) = project(mark.lat, mark.lon)
                    x.isFinite() && y.isFinite() && inside(x, y)
                }
            }
            val out = ArrayList<FlockMark>()
            for (group in raw.groupBy { it.kind }.values) {
                if (group.size <= FlockApproach.MARK_CAP) {
                    for (mark in group) {
                        val (x, y) = project(mark.lat, mark.lon)
                        if (x.isFinite() && y.isFinite() && inside(x, y)) out.add(mark)
                    }
                } else {
                    out.addAll(packKind(group, project, inside))
                }
            }
            return out
        }

        private fun packKind(
            raw: List<FlockMark>,
            project: (Double, Double) -> Pair<Float, Float>,
            inside: (Float, Float) -> Boolean,
        ): List<FlockMark> {
            var cell = 28f
            var grouped = bucket(raw, project, inside, cell)
            var guard = 0
            while (grouped.size > FlockApproach.MARK_CAP && guard < 8) {
                cell *= 1.6f
                grouped = bucket(raw, project, inside, cell)
                guard += 1
            }
            return grouped
        }

        internal fun bucket(
            raw: List<FlockMark>,
            project: (Double, Double) -> Pair<Float, Float>,
            inside: (Float, Float) -> Boolean,
            cell: Float,
        ): List<FlockMark> {
            val buckets = HashMap<Long, Bucket>()
            for (mark in raw) {
                val (x, y) = project(mark.lat, mark.lon)
                if (!x.isFinite() || !y.isFinite() || !inside(x, y)) continue
                val cx = floor(x / cell).toInt()
                val cy = floor(y / cell).toInt()
                val id = (cx.toLong() shl 32) xor (cy.toLong() and 0xffffffffL)
                buckets.getOrPut(id) { Bucket() }.add(mark)
            }
            return buckets.values.map { it.toMark() }
        }

        internal class Bucket {
            var count = 0
            var lat = 0.0
            var lon = 0.0
            var facings: List<Int> = emptyList()
            var flock = false
            var kind = SensorKind.PLATE
            var single = true

            fun add(mark: FlockMark) {
                if (count == 0) {
                    facings = mark.facings
                    flock = mark.flock
                    kind = mark.kind
                    single = mark.count == 1
                } else {
                    single = false
                }
                if (mark.count != 1) single = false
                count += mark.count
                lat += mark.lat * mark.count
                lon += mark.lon * mark.count
            }

            fun toMark(): FlockMark {
                val n = count.coerceAtLeast(1)
                val one = single && count == 1
                val faces = if (one) facings else emptyList()
                return FlockMark(lat / n, lon / n, count, faces, one && flock, kind)
            }
        }

        internal fun lower(a: DoubleArray, v: Double): Int {
            var lo = 0
            var hi = a.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (a[mid] < v) lo = mid + 1 else hi = mid
            }
            return lo
        }

        internal fun upper(a: DoubleArray, v: Double): Int {
            var lo = 0
            var hi = a.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (a[mid] <= v) lo = mid + 1 else hi = mid
            }
            return lo
        }

        internal fun lowerInt(a: IntArray, v: Int): Int {
            var lo = 0
            var hi = a.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (a[mid] < v) lo = mid + 1 else hi = mid
            }
            return lo
        }

        internal fun upperInt(a: IntArray, v: Int): Int {
            var lo = 0
            var hi = a.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (a[mid] <= v) lo = mid + 1 else hi = mid
            }
            return lo
        }
    }
}

/**
 * Cameras read for one geographic box. The map keeps this and cuts it back to
 * the picture. A date-line box is stored exact. A cell keeps the band it was
 * built with, because the averaged center can sit just outside the view.
 */
internal class CameraGather(
    val revision: Int,
    val cells: Boolean,
    val south: Double,
    val north: Double,
    val west: Double,
    val east: Double,
    val marks: List<FlockMark>,
    private val band: IntArray,
    private val col: IntArray,
) {
    fun covers(south: Double, north: Double, west: Double, east: Double): Boolean {
        if (west > east || this.west > this.east) return sameBox(south, north, west, east)
        return south >= this.south && north <= this.north && west >= this.west && east <= this.east
    }

    /** The marks a fresh read of this box would return, in the same order. */
    fun visible(south: Double, north: Double, west: Double, east: Double): List<FlockMark> {
        if (sameBox(south, north, west, east)) return marks
        if (cells) {
            val band0 = floor(south / FlockIndex.CELL_DEG).toInt()
            val band1 = floor(north / FlockIndex.CELL_DEG).toInt()
            val col0 = floor(west / FlockIndex.CELL_DEG).toInt()
            val col1 = floor(east / FlockIndex.CELL_DEG).toInt()
            val out = ArrayList<FlockMark>()
            for (i in marks.indices) {
                if (band[i] !in band0..band1) continue
                if (!FlockIndex.colHits(col[i], west, east, col0, col1)) continue
                out.add(marks[i])
            }
            return out
        }
        val out = ArrayList<FlockMark>()
        for (mark in marks) {
            if (mark.lat in south..north && FlockIndex.lonIn(mark.lon, west, east)) out.add(mark)
        }
        return out
    }

    private fun sameBox(south: Double, north: Double, west: Double, east: Double): Boolean =
        south == this.south && north == this.north && west == this.west && east == this.east
}

/** Lines for the map card. A crowd names the kind and the count only. */
data class CameraCard(
    val kindKey: String,
    val count: Int,
    val brand: String,
    val facing: String,
)

fun cameraKindKey(mark: FlockMark): String = when (mark.kind) {
    SensorKind.PLATE -> when {
        mark.count == 1 && mark.flock -> "ui.cameras.kind.flock"
        mark.count == 1 -> "ui.cameras.kind.plate"
        else -> "settings.layer.flock"
    }
    SensorKind.SPEED -> "ui.cameras.kind.speed"
    SensorKind.RED -> "ui.cameras.kind.red"
    SensorKind.GUNSHOT -> "ui.cameras.kind.gun"
    SensorKind.CAMERA -> "ui.cameras.kind.other"
}

/** Compass letters. The map button uses the same N, and these stay untranslated. */
fun compassPoint(deg: Int): String {
    val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    var turned = deg % 360
    if (turned < 0) turned += 360
    return names[((turned + 22) / 45) % 8]
}

fun cameraFacing(mark: FlockMark): String {
    if (mark.count != 1 || mark.facings.isEmpty()) return ""
    return mark.facings.map { compassPoint(it) }.distinct().joinToString(", ")
}

fun cameraCard(mark: FlockMark, brand: String): CameraCard = CameraCard(
    cameraKindKey(mark),
    mark.count,
    if (mark.count == 1) brand.trim() else "",
    cameraFacing(mark),
)

/**
 * Company on a single mark. The closest saved sensor of that kind within 15 m.
 * A crowd has no one company.
 */
fun cameraBrand(index: FlockIndex, mark: FlockMark): String {
    if (mark.count != 1) return ""
    val same = index.near(mark.lat, mark.lon, 15.0)
        .filter { it.kind == mark.kind && it.flock == mark.flock }
    val faced = same.filter { it.facings == mark.facings }
    val pool = if (faced.isNotEmpty()) faced else same
    val pick = pool.minByOrNull { Geo.meters(mark.lat, mark.lon, it.lat, it.lon) } ?: return ""
    return pick.brand.trim()
}

/**
 * Marks that share [anchor]'s screen spot.
 * [stackPx] matches a stacked detection, so a line of cameras along a road stays separate.
 */
fun camerasOnSpot(
    marks: List<FlockMark>,
    anchor: FlockMark,
    stackPx: Float = 28f,
    project: (Double, Double) -> Pair<Float, Float>,
): List<FlockMark> {
    val (ax, ay) = project(anchor.lat, anchor.lon)
    if (!ax.isFinite() || !ay.isFinite()) return listOf(anchor)
    val thresh2 = stackPx * stackPx
    val near = marks.filter { mark ->
        val (x, y) = project(mark.lat, mark.lon)
        if (!x.isFinite() || !y.isFinite()) return@filter false
        val dx = x - ax
        val dy = y - ay
        dx * dx + dy * dy <= thresh2
    }
    if (near.isEmpty()) return listOf(anchor)
    return near.sortedWith(compareBy({ it.kind.ordinal }, { it.lat }, { it.lon }, { it.flock }))
}

/** Where a leader meets a card. The point is on the card edge nearest the mark. */
fun cardAnchor(
    x: Float,
    y: Float,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
): Pair<Float, Float> {
    if (right < left || bottom < top) return x to y
    return x.coerceIn(left, right) to y.coerceIn(top, bottom)
}

/** Closest mark center within [slopPx]. The view cone is not part of the hit. */
fun cameraAt(
    marks: List<FlockMark>,
    x: Float,
    y: Float,
    slopPx: Float = 40f,
    project: (Double, Double) -> Pair<Float, Float>,
): FlockMark? {
    var best: FlockMark? = null
    var bestD = Double.MAX_VALUE
    for (mark in marks) {
        val (px, py) = project(mark.lat, mark.lon)
        if (!px.isFinite() || !py.isFinite()) continue
        val d = hypot((px - x).toDouble(), (py - y).toDouble())
        if (d <= slopPx.toDouble() && d < bestD) {
            best = mark
            bestD = d
        }
    }
    return best
}

/**
 * A camera loses a tie, and it loses when a GNSS diamond or a detection is
 * within about half a disc. A mark drawn under a badge does not take that tap.
 */
fun cameraWinsTap(cameraD: Float, gnssD: Float?, radioD: Float?): Boolean {
    if (gnssD != null && cameraD + 8f >= gnssD) return false
    if (radioD != null && cameraD + 8f >= radioD) return false
    return true
}

fun cameraId(mark: FlockMark): String {
    val lat = (mark.lat * 1_000_000.0).roundToInt() / 1_000_000.0
    val lon = (mark.lon * 1_000_000.0).roundToInt() / 1_000_000.0
    return "${mark.kind.name}:${if (mark.flock) 1 else 0}:${mark.count}:$lat:$lon"
}
