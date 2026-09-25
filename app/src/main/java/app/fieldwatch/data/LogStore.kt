package app.fieldwatch.data

import android.content.Context
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.LogExportRadios
import app.fieldwatch.domain.LogFormat
import app.fieldwatch.domain.LogRadio
import app.fieldwatch.domain.LogReplay
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.Sighting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class LogStore(context: Context) {
    private val dir = File(context.filesDir, "logs").apply { mkdirs() }
    private val mutex = Mutex()
    private val generation = AtomicInteger(0)
    private val paused = AtomicBoolean(false)
    private var writer: BufferedWriter? = null
    private var writerPath: String? = null
    private var unflushed = 0
    private var index = 0
    private var format = LogFormat.CSV
    private var rotateBytes = 1024 * 1024
    private var lines = 0L
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    val lineCount: Long get() = lines
    val isPaused: Boolean get() = paused.get()

    @Volatile
    private var enabled = true

    fun configure(@Suppress("UNUSED_PARAMETER") format: LogFormat, rotateKb: Int, loggingOn: Boolean = enabled) {
        this.format = LogFormat.JSON
        this.rotateBytes = rotateKb.coerceAtLeast(64) * 1024
        enabled = loggingOn
    }

    suspend fun append(device: Sighting, fleets: List<Fleet>): Long {
        if (!enabled || paused.get()) return lines
        val gen = generation.get()
        val names = fleets.filter { it.id in device.fleetIds }.joinToString("+") { it.name }
        val row = jsonLine(device, names)
        return mutex.withLock {
            if (paused.get() || generation.get() != gen) return@withLock lines
            withContext(Dispatchers.IO) {
                val out = ensureWriter()
                out.write(row)
                unflushed++
                if (unflushed >= 16) {
                    out.flush()
                    unflushed = 0
                }
                lines++
                rotateIfNeeded()
                lines
            }
        }
    }

    fun currentFile(): File = File(dir, "fieldwatch-%03d.jsonl".format(index))

    fun logParts(): List<File> = dir.listFiles()
        ?.filter { it.isFile && it.name.startsWith("fieldwatch-") && !it.name.contains("export") }
        ?.sortedBy { it.name }
        ?: emptyList()

    fun allFiles(): List<File> = logParts()

    fun totalBytes(): Long = logParts().sumOf { it.length() }

    /** Unique radios from every rotating part. Re-match; ignore write-time fleets. */
    suspend fun readRadios(
        onProgress: suspend (copied: Long, total: Long) -> Unit = { _, _ -> },
    ): List<LogRadio> = mutex.withLock {
        withContext(Dispatchers.IO) {
            flushWriter()
            val parts = logParts()
            val total = parts.sumOf { it.length() }.coerceAtLeast(1L)
            var copied = 0L
            var lastEmit = 0L
            val acc = LinkedHashMap<String, LogRadio>()
            onProgress(0L, total)
            parts.forEach { file ->
                if (!file.exists() || file.length() == 0L) return@forEach
                val json = file.name.endsWith(".jsonl") || file.name.endsWith(".json")
                file.bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.lineSequence().forEach { line ->
                        copied += line.length + 1L
                        LogReplay.ingest(sequenceOf(line), json, acc)
                        if (copied - lastEmit >= 48 * 1024) {
                            lastEmit = copied
                            onProgress(copied.coerceAtMost(total), total)
                        }
                    }
                }
            }
            onProgress(total, total)
            acc.values.toList()
        }
    }

    fun exportExtension(asJsonl: Boolean = false): String = if (asJsonl) "jsonl" else "csv"

    fun exportMime(asJsonl: Boolean = false): String =
        if (asJsonl) "application/json" else "text/csv"

    fun suggestedExportName(asJsonl: Boolean = false): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "fieldwatch-log-$stamp.${exportExtension(asJsonl)}"
    }

    suspend fun exportBundle(
        asJsonl: Boolean = false,
        radios: LogExportRadios = LogExportRadios.BOTH,
        onProgress: suspend (copied: Long, total: Long) -> Unit = { _, _ -> },
    ): File =
        withLoggingPaused {
            mutex.withLock {
                withContext(Dispatchers.IO) {
                    flushWriter()
                    val out = File(dir, "fieldwatch-export-${System.currentTimeMillis()}.${exportExtension(asJsonl)}")
                    out.outputStream().buffered(64 * 1024).use { dest ->
                        writeExport(dest, asJsonl, radios, onProgress)
                    }
                    out
                }
            }
        }

    suspend fun exportToUri(
        resolver: ContentResolver,
        uri: Uri,
        asJsonl: Boolean = false,
        radios: LogExportRadios = LogExportRadios.BOTH,
        onProgress: suspend (copied: Long, total: Long) -> Unit = { _, _ -> },
    ) = withLoggingPaused {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                flushWriter()
                val stream = resolver.openOutputStream(uri)
                    ?: error("Could not open the selected location")
                stream.buffered(64 * 1024).use { dest ->
                    writeExport(dest, asJsonl, radios, onProgress)
                }
            }
        }
    }

    private suspend fun <T> withLoggingPaused(block: suspend () -> T): T {
        paused.set(true)
        try {
            return block()
        } finally {
            paused.set(false)
        }
    }

    private suspend fun writeExport(
        dest: OutputStream,
        asJsonl: Boolean,
        radios: LogExportRadios,
        onProgress: suspend (copied: Long, total: Long) -> Unit,
    ) {
        val parts = logParts()
        val total = parts.sumOf { it.length() }.coerceAtLeast(1L)
        var copied = 0L
        var lastEmit = 0L
        suspend fun emit(force: Boolean = false) {
            if (force || copied - lastEmit >= 48 * 1024) {
                lastEmit = copied
                onProgress(copied.coerceAtMost(total), total)
            }
        }
        onProgress(0L, total)
        val writer = OutputStreamWriter(dest, Charsets.UTF_8)
        if (!asJsonl) writer.write(CSV_HEADER)
        parts.forEach { src ->
            if (!src.exists() || src.length() == 0L) return@forEach
            val fromJson = src.name.endsWith(".jsonl") || src.name.endsWith(".json")
            src.bufferedReader(Charsets.UTF_8).use { reader ->
                var header = LogReplay.DEFAULT_CSV_HEADER
                reader.lineSequence().forEach { raw ->
                    val line = raw.trimEnd('\r')
                    if (line.isBlank()) return@forEach
                    copied += line.length + 1L
                    emit()
                    if (!fromJson && line.startsWith("timestamp")) {
                        header = line.split(',')
                        return@forEach
                    }
                    val kind = LogReplay.lineKind(line, fromJson, header)
                    if (kind == null || !radios.matches(kind)) return@forEach
                    val out = when {
                        asJsonl && fromJson -> line
                        asJsonl && !fromJson -> LogReplay.csvRowToJson(line, header) ?: return@forEach
                        !asJsonl && fromJson -> LogReplay.jsonRowToCsv(line) ?: return@forEach
                        else -> line
                    }
                    writer.write(out)
                    writer.write("\n")
                }
            }
        }
        writer.flush()
        emit(force = true)
        onProgress(total, total)
    }

    suspend fun clear(): Long {
        generation.incrementAndGet()
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                closeWriter()
                dir.listFiles()?.forEach { runCatching { it.delete() } }
                index = 0
                lines = 0
                generation.incrementAndGet()
                0L
            }
        }
    }

    private fun ensureWriter(): BufferedWriter {
        val path = currentFile().absolutePath
        val open = writer
        if (open != null && writerPath == path) return open
        closeWriter()
        val file = currentFile()
        val next = BufferedWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8), 32 * 1024)
        writer = next
        writerPath = path
        return next
    }

    private fun flushWriter() {
        runCatching { writer?.flush() }
        unflushed = 0
    }

    private fun closeWriter() {
        runCatching { writer?.flush() }
        runCatching { writer?.close() }
        writer = null
        writerPath = null
        unflushed = 0
    }

    private fun rotateIfNeeded() {
        val file = currentFile()
        if (file.length() < rotateBytes) return
        closeWriter()
        index = (index + 1) % 12
        val next = currentFile()
        if (next.exists()) next.delete()
    }

    private fun csvLine(device: Sighting, names: String): String {
        fun esc(v: String) = v.replace(',', ' ').replace('\n', ' ').replace('"', ' ')
        return listOf(
            device.lastSeen.toString(),
            iso.format(Date(device.lastSeen)),
            device.kind.name,
            device.mac,
            esc(device.name),
            device.rssi.toString(),
            device.channel.toString(),
            device.frequencyMhz.toString(),
            device.oui,
            esc(device.vendor.orEmpty()),
            esc(names),
            device.manufacturerId?.toString(16)?.uppercase().orEmpty(),
            device.serviceUuids.joinToString("|"),
            buildString {
                if (device.randomized) append("RAND ")
                if (device.hiddenSsid) append("HIDDEN ")
            }.trim(),
            device.manufacturerDataHex.ifBlank { device.rawHex }.take(80),
            device.latitude?.let { String.format(Locale.US, "%.6f", it) }.orEmpty(),
            device.longitude?.let { String.format(Locale.US, "%.6f", it) }.orEmpty(),
            device.vendorIeOuis.take(8).joinToString("|") { MacUtil.normalize(it) },
        ).joinToString(",") + "\n"
    }

    private fun jsonLine(device: Sighting, names: String): String {
        val obj = JSONObject()
            .put("ts", device.lastSeen)
            .put("iso", iso.format(Date(device.lastSeen)))
            .put("kind", device.kind.name)
            .put("mac", device.mac)
            .put("name", device.name)
            .put("rssi", device.rssi)
            .put("channel", device.channel)
            .put("freq", device.frequencyMhz)
            .put("oui", device.oui)
            .put("vendor", device.vendor)
            .put("fleets", names)
            .put("mfg", device.manufacturerId)
            .put("uuids", device.serviceUuids.joinToString(","))
            .put("raw", device.manufacturerDataHex.ifBlank { device.rawHex }.take(160))
            .put("vendor_ie", device.vendorIeOuis.take(8).joinToString("|") { MacUtil.normalize(it) })
            .put("rand", device.randomized)
            .put("hidden", device.hiddenSsid)
            .put("lat", device.latitude ?: JSONObject.NULL)
            .put("lon", device.longitude ?: JSONObject.NULL)
        return obj.toString() + "\n"
    }

    companion object {
        private const val CSV_HEADER =
            "timestamp,iso,kind,mac,name,rssi,channel,freq,oui,vendor,fleets,mfg,uuids,flags,raw,lat,lon,vendor_ie\n"

        private fun skipCsvHeader(input: java.io.BufferedInputStream) {
            input.mark(512)
            val line = StringBuilder()
            while (true) {
                val b = input.read()
                if (b < 0 || b == '\n'.code) break
                if (b != '\r'.code) line.append(b.toChar())
                if (line.length > 400) break
            }
            if (!line.startsWith("timestamp")) {
                input.reset()
            }
        }
    }
}
