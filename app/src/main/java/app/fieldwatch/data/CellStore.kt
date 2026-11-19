package app.fieldwatch.data

import app.fieldwatch.domain.CellBook
import app.fieldwatch.domain.CellFix
import app.fieldwatch.domain.CellMark
import app.fieldwatch.domain.CellReading
import app.fieldwatch.domain.CellStep
import app.fieldwatch.domain.CellSwitches
import app.fieldwatch.domain.CellWatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Places and episodes for this phone's cell. Separate from settings. */
class CellStore(private val file: File, private val scope: CoroutineScope) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val lock = Any()
    private var book = CellBook()
    private var dirty = false
    /** False when the file on disk could not be read. A later save must not replace it. */
    private var writable = true
    private val _book = MutableStateFlow(CellBook())
    val bookFlow: StateFlow<CellBook> = _book.asStateFlow()
    private val _reading = MutableStateFlow(CellReading())
    val reading: StateFlow<CellReading> = _reading.asStateFlow()

    fun load() {
        when (val main = readFile(file)) {
            is Read.Ok -> {
                adopt(main.book, allowWrite = true, save = false)
                if (readFile(backupFile()) !is Read.Ok) runCatching { mirrorBackup() }
            }
            is Read.Missing -> {
                val backup = readFile(backupFile())
                if (backup is Read.Ok) {
                    adopt(backup.book, allowWrite = true, save = true)
                    flush()
                } else {
                    adopt(CellBook(), allowWrite = true, save = false)
                }
            }
            is Read.Unreadable -> {
                val backup = readFile(backupFile())
                val shown = if (backup is Read.Ok) backup.book else CellBook()
                adopt(shown, allowWrite = false, save = false)
            }
            is Read.Invalid -> {
                val backup = readFile(backupFile())
                if (backup is Read.Ok) {
                    adopt(backup.book, allowWrite = true, save = true)
                    flush()
                } else {
                    val cleared = park(file)
                    adopt(CellBook(), allowWrite = cleared, save = false)
                }
            }
        }
    }

    fun startFlusher() {
        scope.launch {
            while (true) {
                delay(FLUSH_MS)
                runCatching { flush() }
            }
        }
    }

    fun tick(reading: CellReading, fix: CellFix, switches: CellSwitches, now: Long): CellStep = synchronized(lock) {
        val step = CellWatch.tick(book, reading, fix, switches, now)
        if (step.book != book) {
            book = step.book
            dirty = true
            _book.value = book
        }
        _reading.value = reading
        step
    }

    fun showing() = synchronized(lock) { CellWatch.showing(book) }

    fun marks(): List<CellMark> = synchronized(lock) { CellWatch.marks(book) }

    fun learn(episodeId: Long, now: Long) = edit { CellWatch.learn(book, episodeId, now) }

    fun dismiss(episodeId: Long, now: Long) = edit { CellWatch.dismiss(book, episodeId, now) }

    fun removeCell(placeId: String, key: String) = edit { CellWatch.removeCell(book, placeId, key) }

    fun forget(placeId: String, now: Long) = edit { CellWatch.forget(book, placeId, now) }

    fun applyNames(namesByCell: Map<String, String>) = edit { CellWatch.applyNames(book, namesByCell) }

    private fun edit(block: () -> CellBook) {
        synchronized(lock) {
            val next = block()
            if (next == book) return
            book = next
            dirty = true
            _book.value = book
        }
    }

    internal fun flushForTest(): Boolean = flush()

    private fun flush(): Boolean {
        val snap: CellBook
        synchronized(lock) {
            if (!dirty) return true
            if (!writable) return false
            snap = book
            dirty = false
        }
        val ok = runCatching {
            file.parentFile?.mkdirs()
            publish(file, json.encodeToString(CellBook.serializer(), snap))
            mirrorBackup()
        }.isSuccess
        if (!ok) synchronized(lock) { dirty = true }
        return ok
    }

    /** Last file that parsed. A torn write of the live file loads this instead. */
    internal fun backupFile(): File = File(file.parentFile, file.name + ".bak")

    private fun adopt(next: CellBook, allowWrite: Boolean, save: Boolean) {
        synchronized(lock) {
            book = next
            _book.value = book
            dirty = save
            writable = allowWrite
        }
    }

    /**
     * Moves a file that will not parse out of the way. The bytes stay on disk.
     * A failed rename is copied and synced before the live name is removed.
     */
    private fun park(source: File): Boolean {
        val parked = File(source.parentFile, source.name + ".corrupt")
        if (!parked.exists()) {
            if (source.renameTo(parked)) return true
            val copied = runCatching {
                source.copyTo(parked, overwrite = false)
                FileOutputStream(parked, true).use { it.fd.sync() }
                parked.length() == source.length() && parked.length() > 0L
            }.getOrDefault(false)
            if (!copied) {
                if (parked.exists()) parked.delete()
                return !source.exists()
            }
        }
        return source.delete() || !source.exists()
    }

    /**
     * The new bytes are on disk before the name changes. The previous file stays
     * until that moment, so a kill cannot leave a short file in its place.
     */
    private fun publish(dest: File, text: String) {
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        moveReplacing(tmp, dest)
    }

    private fun mirrorBackup() {
        if (readFile(file) !is Read.Ok) return
        val bak = backupFile()
        val tmp = File(file.parentFile, file.name + ".bak.tmp")
        file.copyTo(tmp, overwrite = true)
        FileOutputStream(tmp, true).use { it.fd.sync() }
        moveReplacing(tmp, bak)
    }

    private fun moveReplacing(from: File, to: File) {
        try {
            Files.move(
                from.toPath(),
                to.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun readFile(source: File): Read {
        if (!source.exists() || source.length() == 0L) return Read.Missing
        val text = runCatching { source.readText() }.getOrElse { return Read.Unreadable }
        if (text.isBlank()) return Read.Missing
        val parsed = runCatching { json.decodeFromString(CellBook.serializer(), text) }.getOrNull()
            ?: return Read.Invalid
        return Read.Ok(parsed)
    }

    private sealed class Read {
        data class Ok(val book: CellBook) : Read()
        data object Missing : Read()
        data object Unreadable : Read()
        data object Invalid : Read()
    }

    companion object {
        private const val FLUSH_MS = 10_000L
    }
}
