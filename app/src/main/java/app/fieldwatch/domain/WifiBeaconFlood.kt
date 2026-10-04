package app.fieldwatch.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.abs

/**
 * Many brand-new Wi-Fi names in one full scan, about the same loudness, on one channel,
 * then gone on the next full scan while some other already-heard network is still there.
 * The first full scan of this process stays quiet. A repeated name, a mesh, an extender,
 * or a guest network is not counted. A partial or cached scan is not a scan.
 * The advertisement does not name the tool.
 */
class WifiBeaconFlood {
    private val lock = Any()
    private val seenMacs = HashSet<String>()
    private val seenNames = HashSet<String>()
    private val bursts = ArrayList<FloodBurst>()
    private val episodeKeys = LinkedHashSet<String>()
    private val reportKeys = LinkedHashSet<String>()
    private val hiddenKeys = LinkedHashSet<String>()
    private var sawLive = emptySet<String>()
    private var pending: Pending? = null
    private var primed = false
    private var lastAcceptedAt = 0L
    private var episode = false
    private var episodeAt = 0L
    private var hidingEpisode = false
    private var acknowledged = false
    /** Open sit. Continue and Hide these then hold until the sit ends. */
    private var sitOpen = false
    private var sitHold: SitHold? = null
    /** Clock time when a no-sit hold ends. A sit hold uses [Long.MAX_VALUE]. */
    private var holdUntil = 0L
    private var lastNow = 0L
    private var medianRssi: Int? = null
    private val _notice = MutableStateFlow<PairingFlood.Notice?>(null)
    val notice: StateFlow<PairingFlood.Notice?> = _notice.asStateFlow()
    private val _hide = MutableStateFlow(PairingFlood.FloodHide())
    val hide: StateFlow<PairingFlood.FloodHide> = _hide.asStateFlow()

    fun bursts(): List<FloodBurst> = synchronized(lock) { bursts.toList() }

    /** One full fresh scan. Empty, cached, and back-to-back callbacks do nothing. */
    fun scan(observations: List<Observation>, now: Long) {
        val rows = parse(observations)
        if (rows.isEmpty()) return
        synchronized(lock) {
            if (lastAcceptedAt != 0L && now - lastAcceptedAt < SCAN_GAP_MS) return
            lastAcceptedAt = now
            lastNow = now
            expireHold(now)
            if (!primed) {
                absorb(rows)
                primed = true
                return
            }
            val open = pending
            var confirmed = false
            if (open != null) {
                val here = HashSet<String>(rows.size)
                for (row in rows) here.add(row.mac)
                val anchor = rows.any { it.mac in seenMacs && it.mac !in open.macs }
                if (!anchor) {
                    replaceBaseline(rows)
                    pending = null
                    finishEpisode()
                    return
                }
                val stayed = open.macs.any { it in here }
                pending = null
                if (!stayed) {
                    confirm(open)
                    confirmed = true
                }
            }
            val next = cluster(rows)
            if (next != null) {
                pending = next
            } else if (!confirmed) {
                finishEpisode()
            }
            absorb(rows)
        }
    }

    /**
     * An open sit keeps the answer until [setSitOpen] goes off.
     * A running 15-minute answer carries into a sit that starts while it still holds.
     * With no sit, the answer holds for [HOLD_MS] from the tap and does not slide.
     * The burst already on screen stays quiet when that time ends. The next one asks.
     */
    fun setSitOpen(open: Boolean) {
        synchronized(lock) {
            if (sitOpen == open) return
            if (open) expireHold(lastNow)
            sitOpen = open
            if (open) {
                if (sitHold != null) holdUntil = Long.MAX_VALUE
            } else {
                sitHold = null
                holdUntil = 0L
            }
        }
    }

    fun dismiss() {
        synchronized(lock) {
            val cur = _notice.value ?: return
            acknowledged = true
            sitHold = if (hidingEpisode) SitHold.HIDE else SitHold.LEAVE
            holdUntil = if (sitOpen) Long.MAX_VALUE else lastNow + HOLD_MS
            if (cur.showDialog) _notice.value = cur.copy(showDialog = false)
        }
    }

    /** Hide the addresses counted in the open burst. Off brings those addresses back. */
    fun setHideBurst(on: Boolean) {
        synchronized(lock) {
            if (on) {
                if (episodeKeys.isEmpty()) return
                hidingEpisode = true
                hiddenKeys.addAll(episodeKeys)
            } else if (episode) {
                hidingEpisode = false
                hiddenKeys.removeAll(episodeKeys)
                if (sitHold == SitHold.HIDE) sitHold = SitHold.LEAVE
            } else {
                hidingEpisode = false
                hiddenKeys.clear()
                if (sitHold == SitHold.HIDE) sitHold = SitHold.LEAVE
            }
            publishHide()
        }
    }

    /** Bring every hidden flood address back. The open burst's switch goes off too. */
    fun clearHidden() {
        synchronized(lock) {
            hidingEpisode = false
            if (sitHold == SitHold.HIDE) sitHold = SitHold.LEAVE
            if (hiddenKeys.isEmpty() && _hide.value.keys.isEmpty() && !_hide.value.episodeOn) return
            hiddenKeys.clear()
            publishHide()
        }
    }

    /**
     * Drop hidden addresses that were on the live map and have since left it.
     * A key that has not been published yet stays, so a burst can still be hidden
     * in the moment before the list refreshes.
     */
    fun prune(liveKeys: Set<String>) {
        synchronized(lock) {
            if (episodeKeys.isEmpty() && hiddenKeys.isEmpty()) {
                sawLive = liveKeys
                return
            }
            val gone = HashSet<String>()
            for (key in episodeKeys) if (key in sawLive && key !in liveKeys) gone.add(key)
            for (key in hiddenKeys) if (key in sawLive && key !in liveKeys) gone.add(key)
            sawLive = liveKeys
            if (gone.isEmpty()) return
            episodeKeys.removeAll(gone)
            val hiddenChanged = hiddenKeys.removeAll(gone)
            val wasOn = hidingEpisode
            if (episodeKeys.isEmpty()) hidingEpisode = false
            if (hiddenChanged || wasOn != hidingEpisode) publishHide()
        }
    }

    private fun expireHold(now: Long) {
        if (sitHold != null && now >= holdUntil) {
            sitHold = null
            holdUntil = 0L
        }
    }

    private fun confirm(open: Pending) {
        val starting = !episode || bursts.isEmpty()
        if (starting) {
            episodeKeys.clear()
            reportKeys.clear()
            hidingEpisode = sitHold == SitHold.HIDE
            episodeAt = lastAcceptedAt
            medianRssi = open.median
        }
        val grew = remember(open.keys)
        if (open.median != null && (starting || reportKeys.size > (bursts.lastOrNull()?.popupCount ?: 0))) {
            medianRssi = open.median
        }
        val next = FloodBurst(
            at = episodeAt,
            popupCount = reportKeys.size,
            nameCount = 0,
            medianRssi = medianRssi,
            keys = reportKeys.toList(),
            wifi = true,
        )
        if (starting) {
            bursts += next
            episode = true
            while (bursts.size > BURST_CAP) bursts.removeAt(0)
        } else {
            bursts[bursts.lastIndex] = next
        }
        if (grew && hidingEpisode) publishHide()
        _notice.value = PairingFlood.Notice(
            showDialog = sitHold == null && !acknowledged,
            popupCount = reportKeys.size,
            nameCount = 0,
            families = emptyList(),
            medianRssi = medianRssi,
            wifi = true,
            duringSit = sitOpen,
        )
    }

    private fun remember(keys: List<String>): Boolean {
        var grew = false
        for (key in keys) {
            if (reportKeys.add(key)) {
                while (reportKeys.size > Sit.RADIO_CAP) {
                    val oldest = reportKeys.iterator().next()
                    reportKeys.remove(oldest)
                }
            }
            if (!episodeKeys.add(key)) continue
            grew = true
            if (hidingEpisode) hiddenKeys.add(key)
            while (episodeKeys.size > EPISODE_CAP) {
                val oldest = episodeKeys.iterator().next()
                episodeKeys.remove(oldest)
            }
        }
        return grew
    }

    private fun finishEpisode() {
        val wasOn = hidingEpisode
        episode = false
        hidingEpisode = false
        episodeKeys.clear()
        reportKeys.clear()
        acknowledged = false
        medianRssi = null
        pending = null
        if (_notice.value != null) _notice.value = null
        if (wasOn) publishHide()
    }

    private fun publishHide() {
        _hide.value = PairingFlood.FloodHide(episodeOn = hidingEpisode, keys = hiddenKeys.toSet())
    }

    /** Largest single-channel group of brand-new names, kept within [RSSI_BAND_DB]. */
    private fun cluster(rows: List<Row>): Pending? {
        val fresh = ArrayList<Row>()
        for (row in rows) {
            if (row.mac in seenMacs) continue
            if (row.base.isEmpty() || row.base in seenNames) continue
            if (row.channel <= 0 || !Rssi.measured(row.rssi)) continue
            fresh += row
        }
        if (fresh.size < MIN_NAMES) return null
        val byBase = HashMap<String, MutableList<Row>>()
        for (row in fresh) byBase.getOrPut(row.base) { ArrayList(1) }.add(row)
        val singles = ArrayList<Row>()
        for (group in byBase.values) {
            if (group.size == 1) singles += group[0]
        }
        if (singles.size < MIN_NAMES) return null
        var best: List<Row>? = null
        var bestChannel = Int.MAX_VALUE
        val byChannel = HashMap<Int, MutableList<Row>>()
        for (row in singles) byChannel.getOrPut(row.channel) { ArrayList() }.add(row)
        for ((channel, group) in byChannel) {
            val current = best
            val bigger = current == null || group.size > current.size ||
                (group.size == current.size && channel < bestChannel)
            if (bigger) {
                best = group
                bestChannel = channel
            }
        }
        val group = best ?: return null
        val values = IntArray(group.size) { group[it].rssi }
        values.sort()
        val median = values[values.size / 2]
        val kept = ArrayList<Row>(group.size)
        for (row in group) {
            if (abs(row.rssi - median) <= RSSI_BAND_DB) kept += row
        }
        if (kept.size < MIN_NAMES) return null
        val macs = HashSet<String>(kept.size)
        val keys = ArrayList<String>(kept.size)
        for (row in kept) {
            macs.add(row.mac)
            keys.add("WIFI:${row.mac}")
        }
        return Pending(macs, keys, median)
    }

    private fun absorb(rows: List<Row>) {
        for (row in rows) {
            seenMacs.add(row.mac)
            if (row.base.isNotEmpty()) seenNames.add(row.base)
        }
    }

    private fun replaceBaseline(rows: List<Row>) {
        seenMacs.clear()
        seenNames.clear()
        absorb(rows)
    }

    private fun parse(observations: List<Observation>): List<Row> {
        if (observations.isEmpty()) return emptyList()
        val byMac = LinkedHashMap<String, Row>()
        for (obs in observations) {
            if (obs.kind != RadioKind.WIFI || !obs.fresh) continue
            val mac = MacUtil.normalize(obs.mac)
            if (mac.isBlank() || !mac.contains(':')) continue
            val row = Row(mac, baseName(obs.name), obs.rssi, obs.channel)
            val prev = byMac[mac]
            if (prev == null || prefer(row, prev)) byMac[mac] = row
        }
        return byMac.values.toList()
    }

    private fun prefer(next: Row, prev: Row): Boolean {
        val nextOk = Rssi.measured(next.rssi)
        val prevOk = Rssi.measured(prev.rssi)
        if (nextOk != prevOk) return nextOk
        return next.rssi > prev.rssi
    }

    /** One trailing guest, extender, or band suffix. The whole name is kept when that is all it is. */
    private fun baseName(raw: String): String {
        val lower = raw.trim().lowercase(Locale.US)
        if (lower.isEmpty() || lower == "<unknown ssid>") return ""
        val cut = maxOf(lower.lastIndexOf('-'), lower.lastIndexOf('_'), lower.lastIndexOf(' '))
        if (cut <= 0 || cut >= lower.lastIndex) return lower
        val head = lower.substring(0, cut).trim()
        val tail = lower.substring(cut + 1).trim()
        if (head.isNotEmpty() && tail in SUFFIXES) return head
        return lower
    }

    private class Row(val mac: String, val base: String, val rssi: Int, val channel: Int)

    private class Pending(val macs: Set<String>, val keys: List<String>, val median: Int?)

    companion object {
        const val MIN_NAMES = 15
        const val RSSI_BAND_DB = 6
        const val SCAN_GAP_MS = 3_000L
        /** Same length as the last-15-minutes path. Starts at the answer and does not slide. */
        const val HOLD_MS = 15 * 60_000L

        /** What Continue or Hide these means for the rest of an open sit. */
        private enum class SitHold { LEAVE, HIDE }
        private const val BURST_CAP = 40
        private const val EPISODE_CAP = 900
        private val SUFFIXES = hashSetOf(
            "guest", "guests", "extender", "ext",
            "2.4ghz", "5ghz", "6ghz",
            "2.4g", "5g", "6g",
            "2.4",
        )
    }
}
