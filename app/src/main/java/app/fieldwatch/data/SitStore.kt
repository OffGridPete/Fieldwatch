package app.fieldwatch.data

import android.app.ActivityManager
import android.content.Context
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.GpsSample
import app.fieldwatch.domain.RadioBookmarks
import app.fieldwatch.domain.Sit
import app.fieldwatch.domain.SitDebrief
import app.fieldwatch.domain.SitFile
import app.fieldwatch.domain.SitSession
import app.fieldwatch.domain.SitSummary
import app.fieldwatch.domain.SitUi
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.WatchTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SitStore(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val dir = File(context.filesDir, "sits").apply { mkdirs() }
    private var memoryTight = false
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutex = Mutex()
    private val lock = Any()
    private var open: SitSession? = null
    private var closed: List<SitSummary> = emptyList()
    private var selectedId: String? = null
    private val _ui = MutableStateFlow(SitUi())
    val ui: StateFlow<SitUi> = _ui.asStateFlow()

    fun startFlusher() {
        scope.launch {
            while (true) {
                delay(Sit.FLUSH_MS)
                runCatching { flushOpen() }
            }
        }
    }

    suspend fun load() = mutex.withLock {
        withContext(Dispatchers.IO) {
            val files = sitFiles()
            val opened = ArrayList<SitFile>()
            val closedAcc = ArrayList<SitSummary>()
            for (file in files) {
                val parsed = runCatching {
                    json.decodeFromString<SitFile>(file.readText())
                }.getOrNull() ?: continue
                if (parsed.format != Sit.FORMAT && parsed.format.isNotBlank()) continue
                if (parsed.summary.open) opened += parsed
                else closedAcc += parsed.summary
            }
            val keepOpen = opened.maxByOrNull { it.summary.startAt }
            for (extra in opened.filter { it.summary.id != keepOpen?.summary?.id }) {
                runCatching { File(dir, "${extra.summary.id}.json").delete() }
            }
            open = keepOpen?.let {
                SitSession(it.summary, it.radios, it.operatorPath)
            }
            closed = closedAcc.sortedByDescending { it.startAt }
            pruneClosedLocked()
            publishLocked()
        }
    }

    fun ingest(seen: List<Sighting>, fleets: List<Fleet>, watchlist: List<WatchTarget>) {
        if (seen.isEmpty()) return
        val session = synchronized(lock) { open } ?: return
        val watchKeys = RadioBookmarks.radios(watchlist).mapNotNull { it.deviceKey }.toSet()
        val watchedFleets = RadioBookmarks.watchedFleetIds(watchlist)
        val tight = memoryTight()
        synchronized(lock) {
            if (open !== session) return
            memoryTight = tight
            for (device in seen) {
                session.ingest(device, fleets, watchKeys, watchedFleets, tight = tight)
            }
            publishLocked()
        }
    }

    fun recordPath(lat: Double, lon: Double, at: Long) {
        synchronized(lock) {
            open?.recordPath(lat, lon, at)
        }
    }

    suspend fun start(
        name: String,
        heard: List<Sighting>,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
        now: Long = System.currentTimeMillis(),
    ): SitSummary? {
        mutex.withLock {
            if (synchronized(lock) { open } != null) return null
            val watchKeys = RadioBookmarks.radios(watchlist).mapNotNull { it.deviceKey }.toSet()
            val watchedFleets = RadioBookmarks.watchedFleetIds(watchlist)
            val session = SitSession.start(
                name = name,
                now = now,
                heard = heard,
                fleets = fleets,
                watchDeviceKeys = watchKeys,
                watchedFleetIds = watchedFleets,
            )
            synchronized(lock) {
                open = session
                selectedId = null
                publishLocked()
            }
            writeSession(session, fleets)
            return session.summary
        }
    }

    suspend fun end(
        fleets: List<Fleet>,
        now: Long = System.currentTimeMillis(),
    ): SitSummary? {
        mutex.withLock {
            val session = synchronized(lock) { open } ?: return null
            val file = session.end(now, fleets)
            writeFile(file)
            val dropped = synchronized(lock) {
                open = null
                closed = (listOf(file.summary) + closed).sortedByDescending { it.startAt }
                selectedId = file.summary.id
                pruneClosedLocked()
            }
            val notice = dropped?.let { "Dropped oldest sit “$it” (keep ${Sit.CLOSED_CAP})." }
            publishLocked(notice)
            return file.summary
        }
    }

    suspend fun rename(id: String, raw: String): Boolean {
        mutex.withLock {
            val next = Sit.clipName(raw)
            if (next.isEmpty()) return false
            val session = synchronized(lock) { open }
            if (session != null && session.summary.id == id) {
                if (!session.rename(raw)) return false
                writeSession(session, emptyList())
                synchronized(lock) { publishLocked() }
                return true
            }
            val row = closed.firstOrNull { it.id == id } ?: return false
            if (row.name == next) return false
            val file = readFile(id) ?: return false
            val updated = file.copy(summary = file.summary.copy(name = next))
            writeFile(updated)
            synchronized(lock) {
                closed = closed.map { if (it.id == id) it.copy(name = next) else it }
                publishLocked()
            }
            return true
        }
    }

    suspend fun delete(id: String) {
        mutex.withLock {
            val session = synchronized(lock) { open }
            if (session != null && session.summary.id == id) return
            withContext(Dispatchers.IO) {
                File(dir, "$id.json").delete()
            }
            synchronized(lock) {
                closed = closed.filterNot { it.id == id }
                if (selectedId == id) selectedId = null
                publishLocked()
            }
        }
    }

    suspend fun deleteAllClosed() {
        mutex.withLock {
            val ids = synchronized(lock) { closed.map { it.id } }
            withContext(Dispatchers.IO) {
                ids.forEach { File(dir, "$it.json").delete() }
            }
            synchronized(lock) {
                closed = emptyList()
                if (selectedId != null && open?.summary?.id != selectedId) selectedId = null
                publishLocked()
            }
        }
    }

    /** Full snapshot for one sit — live session or saved file. Null if gone. */
    suspend fun sitFile(id: String): SitFile? {
        synchronized(lock) {
            val session = open
            if (session != null && session.summary.id == id) {
                return session.snapshot()
            }
        }
        return mutex.withLock { readFile(id) }
    }

    fun select(id: String?) {
        synchronized(lock) {
            selectedId = when {
                id == null -> null
                open?.summary?.id == id -> id
                closed.any { it.id == id } -> id
                else -> null
            }
            publishLocked()
        }
    }

    fun consumeNotice() {
        synchronized(lock) { publishLocked(notice = null) }
    }

    fun debriefSource(now: Long = System.currentTimeMillis()): SitDebrief? {
        synchronized(lock) {
            val session = open
            if (session != null) {
                val snap = session.snapshot()
                return SitDebrief(
                    name = snap.summary.name,
                    startAt = snap.summary.startAt,
                    endAt = now,
                    devices = snap.radios.map { it.toSighting() },
                    operatorPath = snap.operatorPath,
                )
            }
        }
        val id = synchronized(lock) { selectedId } ?: return null
        val file = readFileBlocking(id) ?: return null
        val end = file.summary.endAt ?: now
        return SitDebrief(
            name = file.summary.name,
            startAt = file.summary.startAt,
            endAt = end,
            devices = file.radios.map { it.toSighting() },
            operatorPath = file.operatorPath,
        )
    }

    suspend fun flushOpen() {
        mutex.withLock {
            val session = synchronized(lock) { open } ?: return
            if (!session.dirty) return
            writeSession(session, emptyList())
        }
    }

    private suspend fun writeSession(session: SitSession, fleets: List<Fleet>) {
        val file = synchronized(lock) { session.snapshot(fleets) }
        writeFile(file)
        synchronized(lock) { session.markClean() }
    }

    private suspend fun writeFile(file: SitFile) = withContext(Dispatchers.IO) {
        val dest = File(dir, "${file.summary.id}.json")
        dest.writeText(json.encodeToString(file))
    }

    private suspend fun readFile(id: String): SitFile? = withContext(Dispatchers.IO) {
        readFileBlocking(id)
    }

    private fun readFileBlocking(id: String): SitFile? {
        val dest = File(dir, "$id.json")
        if (!dest.exists()) return null
        return runCatching { json.decodeFromString<SitFile>(dest.readText()) }.getOrNull()
    }

    private fun sitFiles(): List<File> =
        dir.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }.orEmpty()

    private fun pruneClosedLocked(): String? {
        if (closed.size <= Sit.CLOSED_CAP) return null
        val drop = closed.drop(Sit.CLOSED_CAP)
        closed = closed.take(Sit.CLOSED_CAP)
        drop.forEach { row ->
            runCatching { File(dir, "${row.id}.json").delete() }
            if (selectedId == row.id) selectedId = null
        }
        return drop.lastOrNull()?.name
    }

    private fun publishLocked(notice: String? = _ui.value.notice) {
        val session = open
        _ui.value = SitUi(
            open = session?.summary,
            radioCount = session?.radioCount ?: 0,
            atCap = session?.atCap ?: false,
            memoryTight = memoryTight,
            closed = closed,
            selectedId = selectedId,
            notice = notice,
        )
    }

    private fun memoryTight(): Boolean {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        val info = ActivityManager.MemoryInfo()
        return runCatching {
            am.getMemoryInfo(info)
            info.lowMemory || info.availMem < info.threshold
        }.getOrDefault(false)
    }
}
