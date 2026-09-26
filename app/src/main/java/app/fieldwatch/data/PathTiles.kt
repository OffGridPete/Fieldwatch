package app.fieldwatch.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.fieldwatch.domain.GpsSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Optional OSM raster under Reports → Path. Same switch as Online place names.
 * Never throws; offline / Privacy / fetch fail → empty list (plain trace).
 */
object PathTiles {
    data class Tile(
        val bitmap: Bitmap,
        val north: Double,
        val south: Double,
        val west: Double,
        val east: Double,
    )

    suspend fun load(
        context: Context,
        samples: List<GpsSample>,
        privacy: Boolean,
        onlineLookup: Boolean,
    ): List<Tile> = withContext(Dispatchers.IO) {
        runCatching {
            if (privacy || !onlineLookup) return@withContext emptyList()
            if (samples.size < 2) return@withContext emptyList()
            if (!PlaceLookup.online(context)) return@withContext emptyList()
            loadInner(context, samples)
        }.getOrDefault(emptyList())
    }

    private fun loadInner(context: Context, samples: List<GpsSample>): List<Tile> {
        val rawMinLat = samples.minOf { it.lat }
        val rawMaxLat = samples.maxOf { it.lat }
        val rawMinLon = samples.minOf { it.lon }
        val rawMaxLon = samples.maxOf { it.lon }
        val expanded = expandToPlot(rawMinLat, rawMaxLat, rawMinLon, rawMaxLon)
        val minLat = expanded[0]
        val maxLat = expanded[1]
        val minLon = expanded[2]
        val maxLon = expanded[3]
        val midLat = (minLat + maxLat) / 2.0
        val spanM = app.fieldwatch.domain.Geo.meters(minLat, minLon, maxLat, maxLon).coerceAtLeast(40.0)
        var z = zoomFor(spanM, midLat)
        var box = tileBox(minLat, maxLat, minLon, maxLon, z)
        while (box.size > 20 && z > 8) {
            z--
            box = tileBox(minLat, maxLat, minLon, maxLon, z)
        }
        if (box.isEmpty()) return emptyList()
        val cache = File(context.cacheDir, "osm").apply { mkdirs() }
        val out = ArrayList<Tile>(box.size)
        for ((x, y) in box) {
            val bmp = tileBitmap(cache, z, x, y) ?: continue
            val n = tileLat(y, z)
            val s = tileLat(y + 1, z)
            val w = tileLon(x, z)
            val e = tileLon(x + 1, z)
            out += Tile(bmp, n, s, w, e)
        }
        return out
    }

    /** Grow the path bbox to the Path plot aspect so tiles fill the allotted box, not a skinny strip. */
    internal fun expandToPlot(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        targetAspect: Double = 1.7,
    ): DoubleArray {
        val midLat = (minLat + maxLat) / 2.0
        val midLon = (minLon + maxLon) / 2.0
        val cos = cos(Math.toRadians(midLat)).absoluteValue.coerceAtLeast(0.2)
        var yM = ((maxLat - minLat) * 110_540.0).coerceAtLeast(40.0)
        var xM = ((maxLon - minLon) * 111_320.0 * cos).coerceAtLeast(40.0)
        if (xM / yM < targetAspect) {
            xM = yM * targetAspect
        } else {
            yM = xM / targetAspect
        }
        xM *= 1.35
        yM *= 1.35
        val dLat = yM / 2.0 / 110_540.0
        val dLon = xM / 2.0 / (111_320.0 * cos)
        return doubleArrayOf(midLat - dLat, midLat + dLat, midLon - dLon, midLon + dLon)
    }

    internal fun zoomFor(spanM: Double, lat: Double): Int {
        val earth = 40_075_016.686 * cos(Math.toRadians(lat)).absoluteValue.coerceAtLeast(0.2)
        for (z in 17 downTo 8) {
            val tileM = earth / (1 shl z)
            if (tileM * 2.2 >= spanM) return z
        }
        return 8
    }

    private fun tileBox(
        minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, z: Int,
    ): List<Pair<Int, Int>> {
        val x0 = lon2tile(minLon, z)
        val x1 = lon2tile(maxLon, z)
        val y0 = lat2tile(maxLat, z)
        val y1 = lat2tile(minLat, z)
        val n = 1 shl z
        val xs = (minOf(x0, x1).coerceIn(0, n - 1)..maxOf(x0, x1).coerceIn(0, n - 1))
        val ys = (minOf(y0, y1).coerceIn(0, n - 1)..maxOf(y0, y1).coerceIn(0, n - 1))
        val out = ArrayList<Pair<Int, Int>>()
        for (x in xs) for (y in ys) out += x to y
        return out
    }

    private fun lon2tile(lon: Double, z: Int): Int {
        val n = 1 shl z
        return floor((lon + 180.0) / 360.0 * n).toInt()
    }

    private fun lat2tile(lat: Double, z: Int): Int {
        val n = 1 shl z
        val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        return floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()
    }

    private fun tileLon(x: Int, z: Int): Double {
        val n = 1 shl z
        return x.toDouble() / n * 360.0 - 180.0
    }

    private fun tileLat(y: Int, z: Int): Double {
        val n = 1 shl z
        val t = PI - 2.0 * PI * y / n
        return Math.toDegrees(atan(sinh(t)))
    }

    private fun tileBitmap(cache: File, z: Int, x: Int, y: Int): Bitmap? {
        val file = File(cache, "${z}_${x}_$y.png")
        if (file.isFile && file.length() > 64) {
            return BitmapFactory.decodeFile(file.absolutePath)
        }
        val url = URL("https://tile.openstreetmap.org/$z/$x/$y.png")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 4000
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "Fieldwatch/${app.fieldwatch.BuildConfig.VERSION_NAME} (https://github.com/OffGridPete/Fieldwatch)",
            )
        }
        return try {
            if (conn.responseCode != 200) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.size < 64) return null
            runCatching { file.writeBytes(bytes) }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
