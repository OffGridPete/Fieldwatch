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

/** Places and episodes for this phone's cell. Separate from settings. */
class CellStore(private val file: File, private val scope: CoroutineScope) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val lock = Any()
    private var book = CellBook()
    private var dirty = false
    private val _book = MutableStateFlow(CellBook())
    val bookFlow: StateFlow<CellBook> = _book.asStateFlow()
    private val _reading = MutableStateFlow(CellReading())
    val reading: StateFlow<CellReading> = _reading.asStateFlow()

    fun load() {
        val text = runCatching { file.readText() }.getOrNull() ?: return
        val parsed = runCatching { json.decodeFromString(CellBook.serializer(), text) }.getOrNull() ?: return
        synchronized(lock) {
            book = parsed
            _book.value = book
            dirty = false
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

    private fun flush(): Boolean {
        val snap: CellBook
        synchronized(lock) {
            if (!dirty) return true
            snap = book
            dirty = false
        }
        val ok = runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(CellBook.serializer(), snap))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }.isSuccess
        if (!ok) synchronized(lock) { dirty = true }
        return ok
    }

    companion object {
        private const val FLUSH_MS = 10_000L
    }
}
