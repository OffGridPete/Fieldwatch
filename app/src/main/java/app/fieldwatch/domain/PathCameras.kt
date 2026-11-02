package app.fieldwatch.domain

import kotlin.math.cos
import kotlin.math.hypot

/**
 * Cameras from the lists on this phone that the sit path came near.
 * A stored direction counts only when it pointed toward the path.
 * No stored direction counts as on the path. A direction that pointed
 * away is left out. Gunshot detectors are not cameras and are left out.
 * The corridor is [FlockApproach.PATH_M]. The live warning looks farther ahead.
 */
object PathCameras {
    private const val STEP_M = 40.0
    private val searchM = FlockApproach.PATH_M + STEP_M / 2.0

    data class Report(
        val plates: Boolean,
        val others: Boolean,
        val near: (Double, Double, Double) -> List<FlockCamera>,
    )

    data class Count(val labelKey: String, val facing: Int, val near: Int)

    data class Tally(
        val plates: Boolean,
        val others: Boolean,
        val pathReady: Boolean,
        val counts: List<Count>,
        /** Facing the path, or on the path with no stored direction. */
        val cameras: List<FlockCamera> = emptyList(),
    )

    /**
     * Cameras already saved on a sit, plus the step the fold has finished.
     * [committed] is what the next fold keeps. [cameras] is that list plus
     * whatever is beside the current tip, which is what the report shows.
     */
    data class Fold(
        val kept: GpsSample?,
        val committed: List<FlockCamera>,
        val cameras: List<FlockCamera>,
    )

    fun tally(
        path: List<GpsSample>,
        plates: Boolean,
        others: Boolean,
        near: (Double, Double, Double) -> List<FlockCamera>,
    ): Tally {
        val kept = keptPoints(path)
        if (kept.isEmpty()) return Tally(plates, others, pathReady = false, counts = emptyList())
        val best = HashMap<Seen, Hit>()
        absorb(kept, 0, kept.lastIndex, best, near)
        return finish(plates, others, best)
    }

    /**
     * One more slice of a sit path. [points] starts at [anchor] when the sit
     * already has one, and is the whole path when [anchor] is null.
     * A camera the path already came near stays. The moving tip is counted
     * for the report and is not frozen, so the saved list matches [tally]
     * of the same points.
     */
    fun extend(
        anchor: GpsSample?,
        points: List<GpsSample>,
        committed: List<FlockCamera>,
        near: (Double, Double, Double) -> List<FlockCamera>,
    ): Fold {
        val chain = when {
            anchor == null -> points
            points.isEmpty() -> return Fold(anchor, committed.sortedBy { it.key }, committed.sortedBy { it.key })
            sameFix(points.first(), anchor) -> points
            else -> ArrayList<GpsSample>(points.size + 1).apply {
                add(anchor)
                addAll(points)
            }
        }
        val kept = keptPoints(chain)
        if (kept.isEmpty()) {
            val sorted = committed.sortedBy { it.key }
            return Fold(anchor, sorted, sorted)
        }
        val frozenLast = lastFrozenIndex(kept)
        val best = HashMap<Seen, Hit>()
        seed(best, committed)
        val searchFrom = if (anchor == null) 0 else 1
        if (searchFrom <= frozenLast) absorb(kept, searchFrom, frozenLast, best, near)
        val frozen = camerasOf(best)
        val show = HashMap(best)
        if (frozenLast < kept.lastIndex) absorb(kept, kept.lastIndex, kept.lastIndex, show, near)
        return Fold(kept[frozenLast], frozen, camerasOf(show))
    }

    /**
     * Counts for a list the sit already saved. Does not search the camera files.
     * No stored direction is on the path. A stored direction that was saved
     * was facing the path. Gunshot detectors are left out.
     */
    fun fromSaved(
        saved: List<FlockCamera>,
        plates: Boolean,
        others: Boolean,
        pathReady: Boolean,
    ): Tally {
        if (!pathReady) return Tally(plates, others, pathReady = false, counts = emptyList())
        val best = HashMap<Seen, Hit>()
        seed(best, saved)
        return finish(plates, others, best)
    }

    /**
     * Sit report. The same lines as Reports → Path.
     * A list that is not on the phone is still named while its switch is on.
     * Call [shownInReport] first so a switch that is off is left out.
     */
    fun text(tally: Tally): String {
        if (!tally.plates && !tally.others) return LanguageText.text("ui.cameras.lists.missing")
        if (!tally.pathReady) return LanguageText.text("ui.cameras.no.path")
        return buildString {
            appendLine(
                LanguageText.text(
                    "ui.cameras.within",
                    mapOf("meters" to FlockApproach.PATH_M.toInt().toString()),
                ),
            )
            if (!tally.plates) {
                appendLine()
                appendLine(LanguageText.text("ui.cameras.plates.missing"))
            }
            if (!tally.others) {
                appendLine()
                appendLine(LanguageText.text("ui.cameras.other.missing"))
            }
            if (tally.counts.isEmpty()) {
                appendLine()
                append(LanguageText.text("ui.cameras.none.along"))
            } else {
                for (row in tally.counts) {
                    appendLine()
                    appendLine(LanguageText.text(row.labelKey))
                    appendLine(
                        "  " + LanguageText.text(
                            "ui.cameras.facing.count",
                            mapOf("count" to row.facing.toString()),
                        ),
                    )
                    appendLine(
                        "  " + LanguageText.text(
                            "ui.cameras.near.count",
                            mapOf("count" to row.near.toString()),
                        ),
                    )
                }
            }
        }.trimEnd()
    }

    /**
     * Reports → Path. A list that is not on the phone, or whose layer switch is off, is left out.
     * Null means the heading, the rows, and the marks stay off.
     */
    fun shownOnPath(tally: Tally, platesOn: Boolean, othersOn: Boolean): Tally? {
        val plates = tally.plates && platesOn
        val others = tally.others && othersOn
        if (!plates && !others) return null
        val counts = tally.counts.filter { count ->
            if (count.labelKey == Row.FLOCK.labelKey || count.labelKey == Row.PLATE.labelKey) {
                plates
            } else {
                others
            }
        }
        val cameras = tally.cameras.filter { cam ->
            when (cam.kind) {
                SensorKind.PLATE -> plates
                SensorKind.GUNSHOT -> false
                else -> others
            }
        }
        return tally.copy(plates = plates, others = others, counts = counts, cameras = cameras)
    }

    /** Marks for the path maps. A layer switch that is off leaves that list out. Gunshot detectors stay off. */
    fun forMap(cameras: List<FlockCamera>, platesOn: Boolean, othersOn: Boolean): List<FlockCamera> {
        if (!platesOn && !othersOn) return emptyList()
        return cameras.filter { cam ->
            when (cam.kind) {
                SensorKind.PLATE -> platesOn
                SensorKind.GUNSHOT -> false
                else -> othersOn
            }
        }
    }

    /**
     * Sit report and the path printed with it.
     * A layer switch that is off leaves that list out of the counts and the marks.
     * A list that is not on the phone is still named while its switch is on.
     * Null means the section and the marks stay off.
     */
    fun shownInReport(tally: Tally, platesOn: Boolean, othersOn: Boolean): Tally? {
        if (!platesOn && !othersOn) return null
        val counts = tally.counts.filter { count ->
            val plateRow = count.labelKey == Row.FLOCK.labelKey || count.labelKey == Row.PLATE.labelKey
            if (plateRow) platesOn else othersOn
        }
        val cameras = tally.cameras.filter { cam ->
            when (cam.kind) {
                SensorKind.PLATE -> platesOn
                SensorKind.GUNSHOT -> false
                else -> othersOn
            }
        }
        return tally.copy(
            plates = if (platesOn) tally.plates else true,
            others = if (othersOn) tally.others else true,
            counts = counts,
            cameras = cameras,
        )
    }

    private data class Seen(val key: Long, val row: Row)

    private data class Hit(val rank: Int, val camera: FlockCamera)

    private fun seed(best: HashMap<Seen, Hit>, cameras: List<FlockCamera>) {
        for (cam in cameras) {
            val row = rowOf(cam) ?: continue
            val rank = if (cam.facings.isEmpty()) NEAR else FACING
            val id = Seen(cam.key, row)
            val old = best[id]
            if (old == null || rank > old.rank) best[id] = Hit(rank, cam)
        }
    }

    private fun absorb(
        kept: List<GpsSample>,
        from: Int,
        to: Int,
        best: HashMap<Seen, Hit>,
        near: (Double, Double, Double) -> List<FlockCamera>,
    ) {
        for (i in from..to) {
            val here = kept[i]
            val prev = if (i == 0) here else kept[i - 1]
            val found = near(here.lat, here.lon, searchM)
            for (cam in found) {
                val row = rowOf(cam) ?: continue
                val approach = approach(prev.lat, prev.lon, here.lat, here.lon, cam.lat, cam.lon)
                if (approach.meters > FlockApproach.PATH_M) continue
                val rank = if (cam.facings.isEmpty()) {
                    NEAR
                } else if (looking(cam, approach.lat, approach.lon)) {
                    FACING
                } else {
                    AWAY
                }
                val id = Seen(cam.key, row)
                val old = best[id]
                if (old == null || rank > old.rank) best[id] = Hit(rank, cam)
            }
        }
    }

    private fun finish(plates: Boolean, others: Boolean, best: Map<Seen, Hit>): Tally {
        val facing = IntArray(Row.entries.size)
        val onPath = IntArray(Row.entries.size)
        for ((id, hit) in best) {
            when (hit.rank) {
                FACING -> facing[id.row.ordinal]++
                NEAR -> onPath[id.row.ordinal]++
            }
        }
        return Tally(plates, others, pathReady = true, countsOf(facing, onPath), camerasOf(best))
    }

    private fun countsOf(facing: IntArray, onPath: IntArray): List<Count> {
        val counts = ArrayList<Count>()
        for (row in Row.entries) {
            val face = facing[row.ordinal]
            val nearCount = onPath[row.ordinal]
            if (face == 0 && nearCount == 0) continue
            counts.add(Count(row.labelKey, face, nearCount))
        }
        return counts
    }

    private fun camerasOf(best: Map<Seen, Hit>): List<FlockCamera> {
        val cameras = ArrayList<FlockCamera>()
        for (hit in best.values) {
            if (hit.rank == FACING || hit.rank == NEAR) cameras.add(hit.camera)
        }
        cameras.sortBy { it.key }
        return cameras
    }

    /** Last kept point that is a full step, not only the moving end. */
    private fun lastFrozenIndex(kept: List<GpsSample>): Int {
        var frozen = 0
        for (i in 1 until kept.size) {
            if (Geo.meters(kept[frozen].lat, kept[frozen].lon, kept[i].lat, kept[i].lon) >= STEP_M) {
                frozen = i
            }
        }
        return frozen
    }

    private fun sameFix(a: GpsSample, b: GpsSample): Boolean =
        a.at == b.at && a.lat == b.lat && a.lon == b.lon

    private enum class Row(val labelKey: String) {
        FLOCK("ui.cameras.kind.flock"),
        PLATE("ui.cameras.kind.plate"),
        SPEED("ui.cameras.kind.speed"),
        RED("ui.cameras.kind.red"),
        CAMERA("ui.cameras.kind.other"),
    }

    private fun rowOf(cam: FlockCamera): Row? = when (cam.kind) {
        SensorKind.GUNSHOT -> null
        SensorKind.PLATE -> if (cam.flock) Row.FLOCK else Row.PLATE
        SensorKind.SPEED -> Row.SPEED
        SensorKind.RED -> Row.RED
        SensorKind.CAMERA -> Row.CAMERA
    }

    private fun looking(cam: FlockCamera, lat: Double, lon: Double): Boolean {
        val toPath = Geo.bearingDegrees(cam.lat, cam.lon, lat, lon)
        return cam.facings.any { FlockApproach.delta(it.toDouble(), toPath) <= FlockApproach.LOOK_DEG }
    }

    private fun keptPoints(path: List<GpsSample>): List<GpsSample> {
        val valid = path.filter { PayloadLocation.validCoord(it.lat, it.lon) }
        if (valid.isEmpty()) return emptyList()
        val out = ArrayList<GpsSample>()
        var last = valid.first()
        out.add(last)
        for (i in 1 until valid.size) {
            val sample = valid[i]
            if (Geo.meters(last.lat, last.lon, sample.lat, sample.lon) >= STEP_M) {
                out.add(sample)
                last = sample
            }
        }
        val end = valid.last()
        if (out.last() !== end && Geo.meters(out.last().lat, out.last().lon, end.lat, end.lon) > 0.5) {
            out.add(end)
        }
        return out
    }

    private data class Approach(val meters: Double, val lat: Double, val lon: Double)

    /** Closest point on the segment from [a] to [b], in local meters. */
    private fun approach(
        aLat: Double,
        aLon: Double,
        bLat: Double,
        bLon: Double,
        pLat: Double,
        pLon: Double,
    ): Approach {
        val eastScale = 111_320.0 * cos(Math.toRadians(aLat)).coerceAtLeast(0.2)
        val bx = (bLon - aLon) * eastScale
        val by = (bLat - aLat) * 111_320.0
        val px = (pLon - aLon) * eastScale
        val py = (pLat - aLat) * 111_320.0
        val len2 = bx * bx + by * by
        val t = if (len2 < 1.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        val cx = bx * t
        val cy = by * t
        return Approach(
            hypot(px - cx, py - cy),
            aLat + cy / 111_320.0,
            aLon + cx / eastScale,
        )
    }

    private const val AWAY = 0
    private const val NEAR = 1
    private const val FACING = 2
}
