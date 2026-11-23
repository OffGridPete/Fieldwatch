package app.fieldwatch.data

import app.fieldwatch.domain.AircraftHistory
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.Sit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Local Remote ID history. One update per heard aircraft, flushed on the sit cadence.
 * Coordinates stay in this file. Settings export does not include it.
 */
class AircraftStore(private val file: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    private val lock = Any()
    private val byId = HashMap<String, AircraftHistory.Record>()
    private var dirty = false
    private val _records = MutableStateFlow<List<AircraftHistory.Record>>(emptyList())
    val records: StateFlow<List<AircraftHistory.Record>> = _records.asStateFlow()

    fun load() {
        val text = runCatching { file.readText() }.getOrNull() ?: return
        val parsed = runCatching { json.decodeFromString<AircraftFile>(text) }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            byId.clear()
            for (record in parsed.records) {
                val kept = AircraftHistory.prune(record, now) ?: continue
                byId[kept.id] = kept
            }
            trimLocked(now)
            publishLocked()
        }
    }

    fun startFlusher(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                delay(Sit.FLUSH_MS)
                runCatching { flush() }
            }
        }
    }

    fun hear(devices: List<Sighting>) {
        if (devices.isEmpty()) return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            var changed = false
            for (device in devices) {
                val at = if (device.lastSeen > 0L) device.lastSeen else now
                val hear = AircraftHistory.hearOf(device, at) ?: continue
                val next = AircraftHistory.note(byId[hear.id], hear, now)
                if (next == null) {
                    if (byId.remove(hear.id) != null) changed = true
                    continue
                }
                if (byId[hear.id] != next) {
                    byId[hear.id] = next
                    changed = true
                }
            }
            if (!changed) return
            trimLocked(now)
            dirty = true
            publishLocked()
        }
    }

    fun flush() {
        val copy = synchronized(lock) {
            if (!dirty) return
            dirty = false
            byId.values.sortedBy { it.id }
        }
        try {
            val text = json.encodeToString(AircraftFile(records = copy))
            val dir = file.parentFile
            if (dir != null && !dir.isDirectory) dir.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        } catch (err: Exception) {
            synchronized(lock) { dirty = true }
            throw err
        }
    }

    private fun trimLocked(now: Long) {
        val stale = byId.keys.mapNotNull { id ->
            val kept = AircraftHistory.prune(byId[id], now)
            if (kept == null) id else {
                byId[id] = kept
                null
            }
        }
        for (id in stale) byId.remove(id)
        if (byId.size <= AircraftHistory.MAX_RECORDS) return
        val drop = byId.values
            .sortedBy { it.passes.lastOrNull()?.end ?: 0L }
            .take(byId.size - AircraftHistory.MAX_RECORDS)
        for (record in drop) byId.remove(record.id)
    }

    private fun publishLocked() {
        _records.value = byId.values.sortedByDescending { it.passes.lastOrNull()?.end ?: 0L }
    }
}

@Serializable
private data class AircraftFile(
    val format: String = AircraftHistory.FORMAT,
    val records: List<AircraftHistory.Record> = emptyList(),
)
