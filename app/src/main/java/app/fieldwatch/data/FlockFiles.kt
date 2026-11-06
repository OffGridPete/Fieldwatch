package app.fieldwatch.data

import app.fieldwatch.BuildConfig
import app.fieldwatch.domain.FlockApproach
import app.fieldwatch.domain.FlockCamera
import app.fieldwatch.domain.FlockParseException
import app.fieldwatch.domain.SensorKind
import app.fieldwatch.domain.parseFlock
import kotlinx.coroutines.CancellationException
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.zip.GZIPInputStream
import javax.net.ssl.SSLException

/**
 * One downloaded list. Plate readers and the other sensors are separate files.
 * A published name may end in .gz. The bytes are often plain JSON.
 * A real gzip stream is unpacked. Neither list ships inside the app.
 * A bad parse never replaces a list that already passed the check.
 */
enum class SensorList(
    val url: String,
    val fileName: String,
    val expectedBytes: Long,
    /** Version 1 of the plate file saved Flock only and must be fetched again. */
    val refillFlockOnly: Boolean,
) {
    PLATES(
        "https://data.dontgetflocked.com/cameras.geojson.gz",
        "flock-cameras.bin",
        37_748_018L,
        true,
    ),
    /**
     * Speed cameras, red lights, gunshot detectors, and other cameras.
     * Published from dist/ on GitHub. Plate readers stay on [PLATES].
     */
    OTHER(
        "https://raw.githubusercontent.com/OffGridPete/Fieldwatch/main/dist/fieldwatch-sensors.geojson.gz",
        "road-sensors.bin",
        623_103L,
        false,
    ),
}

object FlockFiles {
    val URL_ADDRESS = SensorList.PLATES.url
    val NAME = SensorList.PLATES.fileName
    /** Version 1 saved Flock only. Version 2 and later saved every plate reader. */
    const val ALL_PLATES: Int = 2
    const val MAX_BYTES = 80L * 1024L * 1024L
    /**
     * Version 1 is Flock only. Version 2 is every plate reader.
     * Version 3 adds a type byte after the brand.
     */
    const val FORMAT: Int = 3
    private const val VERSION_FLOCK_ONLY: Short = 1
    private val MAGIC = byteArrayOf('F'.code.toByte(), 'W'.code.toByte(), 'F'.code.toByte(), 'C'.code.toByte())

    data class Header(val count: Int, val savedAt: Long, val etag: String, val version: Int = FORMAT)

    data class Saved(val header: Header, val cameras: List<FlockCamera>)

    /** Why a download or an update check stopped. The settings row names this. */
    enum class Reason {
        REACH,
        TRUST,
        SERVER,
        SPACE,
        LARGE,
        EMPTY,
        READ,
        SAVE,
        OTHER,
    }

    sealed class Probe {
        data object Same : Probe()
        data object Changed : Probe()
        data class Failed(val reason: Reason, val code: Int = 0) : Probe()
    }

    sealed class Fetch {
        data class Saved(val savedAt: Long) : Fetch()
        data object Current : Fetch()
        data class Failed(val reason: Reason, val code: Int = 0) : Fetch()
    }

    fun failReason(error: Exception): Reason = when (error) {
        is SSLException -> Reason.TRUST
        is UnknownHostException, is SocketTimeoutException, is SocketException -> Reason.REACH
        else -> Reason.OTHER
    }

    fun file(dir: File, list: SensorList = SensorList.PLATES): File = File(dir, list.fileName)

    fun peek(dir: File, list: SensorList = SensorList.PLATES): Header? = peekFile(file(dir, list))

    fun camerasFrom(bytes: ByteArray): List<FlockCamera> = parseStream(bytes)

    fun read(dir: File, list: SensorList = SensorList.PLATES): Saved? =
        runCatching { readFile(file(dir, list)) }.getOrNull()

    /**
     * Writes [cameras] only when there are at least [FlockApproach.MIN_COUNT].
     * Returns false and leaves any existing file in place otherwise.
     */
    fun install(
        dir: File,
        cameras: List<FlockCamera>,
        etag: String,
        savedAt: Long,
        list: SensorList = SensorList.PLATES,
    ): Boolean {
        if (cameras.size < FlockApproach.MIN_COUNT || cameras.size > 400_000) return false
        val dest = file(dir, list)
        val tmp = File(dir, "${list.fileName}.write")
        return try {
            write(tmp, cameras, etag, savedAt)
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            true
        } catch (e: CancellationException) {
            tmp.delete()
            throw e
        } catch (_: Exception) {
            tmp.delete()
            false
        }
    }

    /**
     * Parse [bytes] and install. A short list, or a parse error, leaves the saved file alone.
     */
    fun commitParsed(
        dir: File,
        bytes: ByteArray,
        etag: String,
        savedAt: Long,
        list: SensorList = SensorList.PLATES,
    ): Boolean {
        val cameras = try {
            parseStream(bytes)
        } catch (_: FlockParseException) {
            return false
        } catch (_: Exception) {
            return false
        }
        if (cameras.size < FlockApproach.MIN_COUNT) return false
        return install(dir, cameras, etag, savedAt, list)
    }

    fun probe(dir: File, list: SensorList = SensorList.PLATES): Probe {
        if (list.url.isBlank()) return Probe.Failed(Reason.OTHER)
        val saved = peek(dir, list)?.etag.orEmpty()
        var conn: HttpURLConnection? = null
        return try {
            val connection = open(list, "HEAD", ifNoneMatch = null)
            conn = connection
            val code = connection.responseCode
            if (code == 304) return Probe.Same
            if (code != 200) return Probe.Failed(Reason.SERVER, code)
            val remote = connection.getHeaderField("ETag").orEmpty()
            if (saved.isNotEmpty() && remote.isNotEmpty() && remote == saved) Probe.Same else Probe.Changed
        } catch (e: CancellationException) {
            throw e
        } catch (error: Exception) {
            Probe.Failed(failReason(error))
        } finally {
            conn?.disconnect()
        }
    }

    fun download(
        dir: File,
        list: SensorList = SensorList.PLATES,
        onPercent: (Int) -> Unit,
    ): Fetch {
        if (list.url.isBlank()) return Fetch.Failed(Reason.OTHER)
        val header = peek(dir, list)
        // Version 1 of the plate file saved Flock only. Skip If-None-Match so that list is fetched again.
        val savedEtag = header?.etag?.takeIf {
            it.isNotEmpty() && (!list.refillFlockOnly || header.version >= ALL_PLATES)
        }
        var conn: HttpURLConnection? = null
        val tmp = File(dir, "${list.fileName}.download")
        return try {
            tmp.delete()
            val connection = open(list, "GET", savedEtag)
            conn = connection
            val code = connection.responseCode
            if (code == 304) return Fetch.Current
            if (code != 200) return Fetch.Failed(Reason.SERVER, code)
            val length = connection.contentLengthLong
            if (length > MAX_BYTES) return Fetch.Failed(Reason.LARGE)
            val need = if (length > 0L) length else list.expectedBytes
            val room = dir.usableSpace
            if (room > 0L && room < need + 8L * 1024L * 1024L) return Fetch.Failed(Reason.SPACE)
            val total = if (length > 0L) length else list.expectedBytes
            if (total <= 0L) return Fetch.Failed(Reason.EMPTY)
            var read = 0L
            var lastPct = -1
            connection.inputStream.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        read += n
                        if (read > MAX_BYTES) return Fetch.Failed(Reason.LARGE)
                        out.write(buf, 0, n)
                        val pct = ((read * 100L) / total).toInt().coerceIn(0, 99)
                        if (pct != lastPct) {
                            lastPct = pct
                            runCatching { onPercent(pct) }
                        }
                    }
                }
            }
            runCatching { onPercent(100) }
            val etag = connection.getHeaderField("ETag").orEmpty().ifEmpty { savedEtag.orEmpty() }
            val savedAt = System.currentTimeMillis()
            val plates = try {
                parseFile(tmp)
            } catch (_: Exception) {
                return Fetch.Failed(Reason.READ)
            }
            if (plates.isEmpty()) return Fetch.Failed(Reason.EMPTY)
            if (plates.size < FlockApproach.MIN_COUNT) return Fetch.Failed(Reason.READ)
            if (!install(dir, plates, etag, savedAt, list)) return Fetch.Failed(Reason.SAVE)
            runCatching { onPercent(100) }
            Fetch.Saved(savedAt)
        } catch (e: CancellationException) {
            throw e
        } catch (error: Exception) {
            Fetch.Failed(failReason(error))
        } finally {
            tmp.delete()
            conn?.disconnect()
        }
    }

    private fun open(list: SensorList, method: String, ifNoneMatch: String?): HttpURLConnection {
        return (URL(list.url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = method
            setRequestProperty("Accept", "application/geo+json, application/json, application/gzip")
            setRequestProperty(
                "User-Agent",
                "Fieldwatch/${BuildConfig.VERSION_NAME} (https://github.com/OffGridPete/Fieldwatch)",
            )
            if (!ifNoneMatch.isNullOrEmpty()) setRequestProperty("If-None-Match", ifNoneMatch)
        }
    }

    private fun parseFile(file: File): List<FlockCamera> {
        return reader(FileInputStream(file)).use { parseFlock(it) }
    }

    private fun parseStream(bytes: ByteArray): List<FlockCamera> {
        return reader(bytes.inputStream()).use { parseFlock(it) }
    }

    private fun reader(raw: java.io.InputStream): InputStreamReader {
        val input = BufferedInputStream(raw)
        input.mark(4)
        val b0 = input.read()
        val b1 = input.read()
        input.reset()
        val stream = if (b0 == 0x1f && b1 == 0x8b) GZIPInputStream(input) else input
        return InputStreamReader(stream, Charsets.UTF_8)
    }

    private fun peekFile(file: File): Header? {
        if (!file.isFile || file.length() < 20) return null
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                val magic = ByteArray(4)
                input.readFully(magic)
                if (!magic.contentEquals(MAGIC)) return null
                val version = input.readShort()
                if (!knownVersion(version)) return null
                val count = input.readInt()
                if (count < FlockApproach.MIN_COUNT || count > 400_000) return null
                val savedAt = input.readLong()
                val etagLen = input.readUnsignedShort()
                if (etagLen > 512) return null
                val etagBytes = ByteArray(etagLen)
                input.readFully(etagBytes)
                Header(count, savedAt, etagBytes.toString(Charsets.UTF_8), version.toInt())
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readFile(file: File): Saved {
        DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
            val magic = ByteArray(4)
            input.readFully(magic)
            if (!magic.contentEquals(MAGIC)) error("bad flock file")
            val version = input.readShort()
            if (!knownVersion(version)) error("bad flock version")
            val count = input.readInt()
            if (count < FlockApproach.MIN_COUNT || count > 400_000) error("bad flock count")
            val savedAt = input.readLong()
            val etagLen = input.readUnsignedShort()
            if (etagLen > 512) error("bad flock etag")
            val etagBytes = ByteArray(etagLen)
            input.readFully(etagBytes)
            val cameras = ArrayList<FlockCamera>(count)
            repeat(count) {
                val key = input.readLong()
                val lat = input.readInt() / 1_000_000.0
                val lon = input.readInt() / 1_000_000.0
                val versionInt = version.toInt()
                val legacy = versionInt == VERSION_FLOCK_ONLY.toInt()
                val flock = if (legacy) true else input.readUnsignedByte() == 1
                val brand = if (legacy) {
                    "Flock"
                } else {
                    val len = input.readUnsignedByte()
                    if (len > 48) error("bad flock brand")
                    val bytes = ByteArray(len)
                    if (len > 0) input.readFully(bytes)
                    bytes.toString(Charsets.UTF_8)
                }
                val kind = if (versionInt >= FORMAT) {
                    val raw = input.readUnsignedByte()
                    if (raw >= SensorKind.entries.size) error("bad sensor kind")
                    SensorKind.entries[raw]
                } else {
                    SensorKind.PLATE
                }
                val n = input.readUnsignedByte()
                if (n > FlockApproach.MAX_FACINGS) error("bad flock facing")
                val facings = List(n) {
                    val deg = input.readUnsignedShort()
                    if (deg > 359) error("bad flock degree")
                    deg
                }
                cameras.add(FlockCamera(key, lat, lon, facings, brand, flock, kind).forStore())
            }
            return Saved(Header(count, savedAt, etagBytes.toString(Charsets.UTF_8), version.toInt()), cameras)
        }
    }

    private fun knownVersion(version: Short): Boolean = version.toInt() in 1..FORMAT

    private fun write(file: File, cameras: List<FlockCamera>, etag: String, savedAt: Long) {
        val sorted = cameras.map { it.forStore() }.sortedWith(compareBy({ it.lat }, { it.lon }, { it.key }))
        val etagBytes = etag.encodeToByteArray().let { if (it.size > 512) it.copyOf(512) else it }
        DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
            out.write(MAGIC)
            out.writeShort(FORMAT)
            out.writeInt(sorted.size)
            out.writeLong(savedAt)
            out.writeShort(etagBytes.size)
            out.write(etagBytes)
            for (cam in sorted) {
                out.writeLong(cam.key)
                out.writeInt(micro(cam.lat))
                out.writeInt(micro(cam.lon))
                val faces = cam.facings.take(FlockApproach.MAX_FACINGS)
                val brandBytes = cam.brand.encodeToByteArray().let { if (it.size > 48) it.copyOf(48) else it }
                out.writeByte(if (cam.flock) 1 else 0)
                out.writeByte(brandBytes.size)
                out.write(brandBytes)
                out.writeByte(cam.kind.ordinal)
                out.writeByte(faces.size)
                for (deg in faces) out.writeShort(deg.coerceIn(0, 359))
            }
        }
    }

    private fun micro(deg: Double): Int = kotlin.math.round(deg * 1_000_000.0).toInt()
}
