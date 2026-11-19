package app.fieldwatch.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * Many different new Remote ID drones in a short window.
 * A drone counts when a drone-class signature matches and a UAS ID sticks.
 * The same ID on another MAC, or on Bluetooth and Wi-Fi, counts once.
 * Distance and loudness are not inputs. A blank ID stays out.
 * IDs already known when this scan started stay out.
 * One warning covers a burst that keeps minting IDs. A new warning waits
 * until new IDs have stopped for the quiet time.
 */
class RemoteIdSwarm {
    /** One maker line. A blank [name] means the plate did not name a maker. */
    data class TypeCount(val name: String, val count: Int)

    data class Notice(
        val showDialog: Boolean,
        val at: Long,
        val count: Int,
        val windowSec: Int,
        val ble: Int,
        val wifi: Int,
        val types: List<TypeCount> = emptyList(),
    ) {
        fun title(): String = LanguageText.text("ui.swarm.title")

        fun body(): String {
            val lead = LanguageText.text(
                "ui.swarm.body",
                mapOf("n" to count.toString(), "s" to windowSec.toString()),
            )
            if (types.isEmpty()) return lead
            val lines = types.joinToString("\n") { row ->
                val label = row.name.ifBlank { LanguageText.text("ui.swarm.unknown") }
                "${row.count} $label"
            }
            return "$lead\n\n$lines"
        }

        fun details(): String = LanguageText.text(
            "ui.swarm.details",
            mapOf(
                "n" to count.toString(),
                "s" to windowSec.toString(),
                "b" to ble.toString(),
                "w" to wifi.toString(),
            ),
        )
    }

    data class Outcome(
        val beep: Boolean = false,
        val notify: Boolean = false,
        val clearNote: Boolean = false,
        val count: Int = 0,
        val windowSec: Int = 0,
        val ble: Int = 0,
        val wifi: Int = 0,
        /** Drone-class watch rows stay quiet while this is true. A named radio does not. */
        val quiet: Boolean = false,
    )

    private class Episode(
        val at: Long,
        val minIds: Int,
        val windowMs: Long,
        var quietMs: Long,
        val members: LinkedHashSet<String>,
        var lastNewAt: Long,
        var showDialog: Boolean,
        var announced: Int,
    )

    private val lock = Any()
    private val firstHeard = LinkedHashMap<String, Long>()
    private val excluded = HashSet<String>()
    private val kindBits = HashMap<String, Int>()
    /** UAS ID to the maker on the plate, or a drone signature other than Remote ID. */
    private val typeOf = HashMap<String, String>()
    /** UAS ID to the swarm time. Stays after the quiet time so the radio page can still say so. */
    private val memberAt = HashMap<String, Long>()
    private val bursts = ArrayList<SwarmBurst>()
    private var episode: Episode? = null
    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice.asStateFlow()

    /** [alreadyKnown] UAS IDs were on the air before this scan. They do not count. */
    fun beginSession(alreadyKnown: Collection<String>) = synchronized(lock) {
        clearLocked()
        for (raw in alreadyKnown) {
            val id = raw.trim()
            if (id.isNotEmpty()) excluded.add(id)
        }
    }

    fun endSession() = synchronized(lock) {
        clearLocked()
    }

    /** Hide the dialog. The episode, the quiet radios, and the notification stay. */
    fun dismiss() = synchronized(lock) {
        val ep = episode
        if (ep == null) {
            _notice.value = null
            return
        }
        if (!ep.showDialog) return
        ep.showDialog = false
        publish(ep)
    }

    /** Temporary. Remove after the swarm dialog review. */
    fun previewSample() = synchronized(lock) {
        _notice.value = Notice(
            showDialog = true,
            at = System.currentTimeMillis(),
            count = 8,
            windowSec = 19,
            ble = 7,
            wifi = 3,
            types = listOf(
                TypeCount("DJI", 5),
                TypeCount("Autel", 2),
                TypeCount("Skydio", 1),
            ),
        )
    }

    fun quietsDroneClass(): Boolean = synchronized(lock) { episode != null }

    fun memberAt(uasId: String?): Long? = synchronized(lock) {
        val id = uasId?.trim().orEmpty()
        if (id.isEmpty()) null else memberAt[id]
    }

    fun memberLine(uasId: String?): String? {
        val at = memberAt(uasId) ?: return null
        return LanguageText.text(
            "ui.swarm.part",
            mapOf("time" to FloodBurst.clock(at) + " UTC"),
        )
    }

    fun bursts(): List<SwarmBurst> = synchronized(lock) { bursts.toList() }

    /**
     * [devices] is the live list, watched or not.
     * [droneFleetIds] is every signature whose class is Drones, stock or custom.
     * Call again with no new packets so the quiet time can end.
     */
    fun pass(
        devices: List<Sighting>,
        droneFleetIds: Set<String>,
        now: Long,
        enabled: Boolean,
        minIds: Int,
        windowMs: Long,
        quietMs: Long,
        typeNames: Map<String, String> = emptyMap(),
    ): Outcome = synchronized(lock) {
        if (droneFleetIds.isNotEmpty()) {
            for (device in devices) {
                if (device.fleetIds.none { it in droneFleetIds }) continue
                val id = device.payloadUasId?.trim().orEmpty()
                if (id.isEmpty() || id in excluded) continue
                if (id !in firstHeard) firstHeard[id] = now
                noteType(id, device, typeNames)
                val bit = if (device.kind == RadioKind.WIFI) WIFI else BLE
                kindBits[id] = (kindBits[id] ?: 0) or bit
            }
        }
        val active = episode
        if (!enabled) {
            if (active == null) return Outcome()
            val clear = active.announced > 0
            episode = null
            _notice.value = null
            return Outcome(clearNote = clear)
        }
        if (active != null) return continueEpisode(active, now, quietMs)
        maybeStart(now, minIds, windowMs, quietMs)
    }

    private fun continueEpisode(ep: Episode, now: Long, quietMs: Long): Outcome {
        ep.quietMs = quietMs
        val floor = ep.at - ep.windowMs
        var joined = false
        for ((id, heard) in firstHeard) {
            if (id in ep.members || id in memberAt) continue
            if (heard < floor) continue
            ep.members.add(id)
            memberAt[id] = ep.at
            joined = true
        }
        if (joined) ep.lastNewAt = now
        remember(ep)
        val count = ep.members.size
        val beep = ep.announced == 0
        val notify = count != ep.announced
        if (notify) ep.announced = count
        val (ble, wifi) = tallies(ep.members)
        val windowSec = (ep.windowMs / 1000L).toInt()
        if (now - ep.lastNewAt >= ep.quietMs) {
            val clear = ep.announced > 0
            episode = null
            _notice.value = null
            return Outcome(
                beep = beep,
                notify = notify,
                clearNote = clear,
                count = count,
                windowSec = windowSec,
                ble = ble,
                wifi = wifi,
            )
        }
        publish(ep)
        return Outcome(
            beep = beep,
            notify = notify,
            count = count,
            windowSec = windowSec,
            ble = ble,
            wifi = wifi,
            quiet = true,
        )
    }

    private fun maybeStart(now: Long, minIds: Int, windowMs: Long, quietMs: Long): Outcome {
        if (minIds <= 0 || windowMs <= 0) return Outcome()
        val chosen = ArrayList<String>()
        var newest = Long.MIN_VALUE
        for ((id, heard) in firstHeard) {
            if (id in memberAt) continue
            if (heard > now || now - heard > windowMs) continue
            chosen.add(id)
            if (heard > newest) newest = heard
        }
        if (chosen.size < minIds) return Outcome()
        val members = LinkedHashSet(chosen)
        val ep = Episode(
            at = now,
            minIds = minIds,
            windowMs = windowMs,
            quietMs = quietMs,
            members = members,
            lastNewAt = newest,
            showDialog = true,
            announced = members.size,
        )
        for (id in members) memberAt[id] = now
        episode = ep
        remember(ep)
        publish(ep)
        val (ble, wifi) = tallies(members)
        return Outcome(
            beep = true,
            notify = true,
            count = members.size,
            windowSec = (windowMs / 1000L).toInt(),
            ble = ble,
            wifi = wifi,
            quiet = true,
        )
    }

    private fun remember(ep: Episode) {
        val (ble, wifi) = tallies(ep.members)
        val burst = SwarmBurst(
            at = ep.at,
            idCount = ep.members.size,
            uasIds = ep.members.toList(),
            wifi = wifi,
            ble = ble,
            minIds = ep.minIds,
            windowMs = ep.windowMs,
            quietMs = ep.quietMs,
        )
        val index = bursts.indexOfFirst { it.at == burst.at }
        if (index >= 0) bursts[index] = burst
        else bursts.add(burst)
        while (bursts.size > 40) bursts.removeAt(0)
    }

    private fun publish(ep: Episode) {
        val (ble, wifi) = tallies(ep.members)
        _notice.value = Notice(
            showDialog = ep.showDialog,
            at = ep.at,
            count = ep.members.size,
            windowSec = (ep.windowMs / 1000L).toInt(),
            ble = ble,
            wifi = wifi,
            types = typesFor(ep.members),
        )
    }

    /** Plate maker wins. A later packet with no maker leaves the name already stored. */
    private fun noteType(id: String, device: Sighting, typeNames: Map<String, String>) {
        val aircraft = device.payloadAircraft?.trim().orEmpty()
        if (aircraft.isNotEmpty()) {
            typeOf[id] = aircraft
            return
        }
        if (id in typeOf) return
        val hint = device.fleetIds.asSequence()
            .filter { it != REMOTE_ID_FLEET }
            .sorted()
            .mapNotNull { typeNames[it]?.trim()?.takeIf { name -> name.isNotEmpty() } }
            .firstOrNull()
        if (hint != null) typeOf[id] = hint
    }

    private fun typesFor(ids: Collection<String>): List<TypeCount> {
        val counts = HashMap<String, Int>()
        for (id in ids) {
            val name = typeOf[id] ?: continue
            counts[name] = (counts[name] ?: 0) + 1
        }
        if (counts.isEmpty()) return emptyList()
        val named = counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { TypeCount(it.key, it.value) }
        val unknown = ids.size - named.sumOf { it.count }
        if (unknown <= 0) return named
        return named + TypeCount("", unknown)
    }

    private fun tallies(ids: Collection<String>): Pair<Int, Int> {
        var ble = 0
        var wifi = 0
        for (id in ids) {
            val bits = kindBits[id] ?: 0
            if (bits and BLE != 0) ble++
            if (bits and WIFI != 0) wifi++
        }
        return ble to wifi
    }

    private fun clearLocked() {
        firstHeard.clear()
        excluded.clear()
        kindBits.clear()
        typeOf.clear()
        memberAt.clear()
        bursts.clear()
        episode = null
        _notice.value = null
    }

    companion object {
        const val DEFAULT_MIN_IDS = 5
        const val DEFAULT_WINDOW_SEC = 30
        const val DEFAULT_QUIET_MIN = 2
        const val MIN_IDS_LO = 3
        const val MIN_IDS_HI = 20
        const val WINDOW_LO = 10
        const val WINDOW_HI = 120
        const val WINDOW_STEP = 5
        const val QUIET_LO = 1
        const val QUIET_HI = 10

        private const val BLE = 1
        private const val WIFI = 2
        private const val REMOTE_ID_FLEET = "fleet-remote-id"

        /** Out-of-range trigger values move to the nearest end. The switch is left as saved. */
        fun clamp(settings: AppSettings): AppSettings {
            val minIds = settings.swarmMinIds.coerceIn(MIN_IDS_LO, MIN_IDS_HI)
            val windowSec = settings.swarmWindowSec.coerceIn(WINDOW_LO, WINDOW_HI)
            val quietMin = settings.swarmQuietMin.coerceIn(QUIET_LO, QUIET_HI)
            if (minIds == settings.swarmMinIds &&
                windowSec == settings.swarmWindowSec &&
                quietMin == settings.swarmQuietMin
            ) {
                return settings
            }
            return settings.copy(
                swarmMinIds = minIds,
                swarmWindowSec = windowSec,
                swarmQuietMin = quietMin,
            )
        }
    }
}

/** One Remote ID swarm saved on the sit. [uasIds] stay in the file and stay out of reports. */
@Serializable
data class SwarmBurst(
    val at: Long,
    val idCount: Int,
    val uasIds: List<String> = emptyList(),
    val wifi: Int = 0,
    val ble: Int = 0,
    val minIds: Int = RemoteIdSwarm.DEFAULT_MIN_IDS,
    val windowMs: Long = RemoteIdSwarm.DEFAULT_WINDOW_SEC * 1000L,
    val quietMs: Long = RemoteIdSwarm.DEFAULT_QUIET_MIN * 60_000L,
) {
    /** [windowMs] is the window in effect when this swarm fired, not the spread of the IDs. */
    fun reportLine(): String = LanguageText.text(
        "ui.swarm.debrief",
        mapOf(
            "clock" to FloodBurst.clock(at),
            "n" to idCount.toString(),
            "s" to (windowMs / 1000L).toString(),
        ),
    )
}

/**
 * A drone-class fleet row stays quiet for the whole swarm.
 * A named radio, device key set, still alerts.
 */
internal fun swarmQuietsFleetWatch(
    deviceKey: String?,
    fleetId: String?,
    quietDroneFleets: Set<String>,
): Boolean {
    if (quietDroneFleets.isEmpty() || deviceKey != null) return false
    val fleet = fleetId ?: return false
    return fleet in quietDroneFleets
}
