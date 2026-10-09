package app.fieldwatch.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/** One burst that crossed the flood line. Saved on the sit when it happens. */
@Serializable
data class FloodBurst(
    val at: Long,
    val popupCount: Int,
    val nameCount: Int,
    val families: List<String> = emptyList(),
    val medianRssi: Int? = null,
    /** Device keys counted in this burst. Empty on a sit saved before keys were stored. */
    val keys: List<String> = emptyList(),
    /** Wi-Fi beacon flood. A Bluetooth burst leaves this false. */
    val wifi: Boolean = false,
) {
    fun reportLine(clock: String): String {
        // Families are stored only when the live pairing line was crossed, so an older sit keeps that label.
        val pairing = !wifi && (families.isNotEmpty() || popupCount >= PairingFlood.POPUP_MIN)
        val head = when {
            wifi -> "Wi-Fi beacon flood"
            pairing -> "Pairing flood"
            else -> "Name flood"
        }
        val count = when {
            wifi -> popupCount
            pairing -> popupCount
            else -> nameCount
        }
        val unit = if (wifi) "new names" else "new addresses"
        val fam = if (pairing && families.isNotEmpty()) ": ${families.joinToString(", ")}" else ""
        val extra = if (pairing && nameCount >= PairingFlood.NAME_MIN) " · $nameCount named" else ""
        val loud = if (medianRssi != null) " · about $medianRssi dBm" else ""
        val aside = when (keys.size) {
            0 -> ""
            1 -> " 1 address from this burst is left out of the counts and lists below."
            else -> " ${keys.size} addresses from this burst are left out of the counts and lists below."
        }
        return "$clock UTC. $head. $count $unit$fam$extra$loud.$aside"
    }

    companion object {
        const val INTRO =
            "A burst of new Bluetooth addresses in a few seconds. A handheld can do this by advertising a pairing request or a new name and changing the address every packet. A name flood counts randomized addresses. A factory address with a stable name stays out of that count. The advertisement does not name the tool."

        const val WIFI_INTRO =
            "A burst of new Wi-Fi names in one scan, about the same loudness, gone by the next scan. A repeated name, a mesh, an extender, or a guest network is not counted. The advertisement does not name the tool."

        /** Device keys counted in these bursts. A burst with no keys contributes nothing. */
        fun keysOf(floods: List<FloodBurst>): Set<String> {
            if (floods.isEmpty()) return emptySet()
            var out: HashSet<String>? = null
            for (burst in floods) {
                if (burst.keys.isEmpty()) continue
                if (out == null) out = HashSet()
                out.addAll(burst.keys)
            }
            return out ?: emptySet()
        }

        /** Bluetooth sentence when any burst is Bluetooth, Wi-Fi sentence when any burst is Wi-Fi. */
        fun intro(floods: List<FloodBurst>): String = buildString {
            if (floods.any { !it.wifi }) append(INTRO)
            if (floods.any { it.wifi }) {
                if (isNotEmpty()) append("\n\n")
                append(WIFI_INTRO)
            }
        }

        /** Keys whose burst started inside the report window. */
        fun keysOf(floods: List<FloodBurst>, start: Long, end: Long): Set<String> {
            if (floods.isEmpty()) return emptySet()
            var out: HashSet<String>? = null
            for (burst in floods) {
                if (burst.keys.isEmpty() || burst.at !in start..end) continue
                if (out == null) out = HashSet()
                out.addAll(burst.keys)
            }
            return out ?: emptySet()
        }

        fun clock(ms: Long): String {
            val fmt = SimpleDateFormat("HH:mm", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            return fmt.format(Date(ms))
        }
    }
}

/**
 * Counts brand-new Bluetooth addresses over about ten seconds.
 * Pairing popups share one counter: Apple proximity pairing (Continuity 0x07),
 * Nearby Action (0x0F), a 3-byte Fast Pair model, a Swift Pair beacon,
 * Samsung Easy Setup buds or watch, and a LoveSpouse advertisement
 * (company 0x00FF plus its fixed prefix).
 * The Windows background beacon (company 0x0006 starting with 0x01) stays out.
 * Find My (0x12) stays out, including a later 0x07 in that same payload.
 * A separate counter is a new randomized address that only advertises a name.
 * A factory address with a stable name stays out of that count.
 * Pairing popups still count every address.
 * Similar loudness keeps a spread-out crowd from tripping it.
 * The advertisement does not name the tool.
 */
class PairingFlood {
    data class Notice(
        val showDialog: Boolean,
        val popupCount: Int,
        val nameCount: Int,
        val families: List<String>,
        val medianRssi: Int?,
        val wifi: Boolean = false,
        /** A sit is open, so Continue and Hide these hold until that sit ends. */
        val duringSit: Boolean = false,
    ) {
        fun title(): String = when {
            wifi -> "Wi-Fi beacon flood"
            popupCount >= POPUP_MIN -> "Pairing flood"
            else -> "Name flood"
        }

        fun line(): String {
            if (wifi) {
                val loud = if (medianRssi != null) " · about $medianRssi dBm" else ""
                return "Wi-Fi beacon flood · $popupCount new names$loud"
            }
            val popupHot = popupCount >= POPUP_MIN
            val nameHot = nameCount >= NAME_MIN
            val head = if (popupHot) "Pairing flood" else "Name flood"
            val count = if (popupHot) popupCount else nameCount
            val extra = if (popupHot && nameHot) " · $nameCount named" else ""
            val loud = if (medianRssi != null) " · about $medianRssi dBm" else ""
            return "$head · $count new addresses$extra$loud"
        }

        fun body(): String {
            val choice = if (duringSit) {
                "Continue leaves them on Live for the rest of this sit. Hide these takes this burst, and later bursts in this sit, off Live. The sit and the log still keep them."
            } else {
                "Continue leaves them on Live for about the next 15 minutes. Hide these takes this burst, and later bursts in that time, off Live. The sit and the log still keep them."
            }
            if (wifi) {
                val loud = if (medianRssi != null) {
                    ", about the same loudness, about $medianRssi dBm,"
                } else {
                    ""
                }
                val what = "$popupCount new Wi-Fi names showed up in one scan$loud and they were gone on the next scan. " +
                    "A repeated name, a mesh, an extender, or a guest network is not counted."
                val how = "A handheld such as a Flipper Zero, or an ESP32 running Marauder or Bruce, does this by advertising many network names. The advertisement does not name the tool."
                return what + "\n\n" + how + "\n\n" + choice
            }
            val popupHot = popupCount >= POPUP_MIN
            val nameHot = nameCount >= NAME_MIN
            val what = buildString {
                if (popupHot) {
                    append(popupCount)
                    append(" new addresses sent pairing advertisements in the last few seconds")
                    if (families.isNotEmpty()) {
                        append(": ")
                        append(families.joinToString(", "))
                    }
                    append('.')
                    if (nameHot) {
                        append(' ')
                        append(nameCount)
                        append(" more each advertised a Bluetooth name.")
                    }
                } else {
                    append(nameCount)
                    append(" new addresses each advertised a Bluetooth name in the last few seconds.")
                }
                if (medianRssi != null) {
                    append(" They are about the same loudness, about ")
                    append(medianRssi)
                    append(" dBm.")
                }
            }
            val how = if (popupHot) {
                "This can be many radios already advertising pairing, such as in a store, or one radio changing its address on every packet. A Flipper Zero, or an ESP32 running Marauder or Bruce, can do the second. The advertisement does not name the tool.\n\n$choice"
            } else {
                "This can be many radios already advertising a name, such as tags in a store, or one radio changing its name and address on every packet. A Flipper Zero, or an ESP32 running Marauder or Bruce, can do the second. The advertisement does not name the tool.\n\n$choice"
            }
            return what + "\n\n" + how
        }
    }

    /** Addresses from bursts the operator chose to hide, still on the live map. */
    data class FloodHide(
        val episodeOn: Boolean = false,
        val keys: Set<String> = emptySet(),
    )

    private val lock = Any()
    private val window = ArrayDeque<Hit>()
    private val macs = HashSet<String>()
    private val bursts = ArrayList<FloodBurst>()
    private val episodeKeys = LinkedHashSet<String>()
    /** Every address this episode counted, including ones past the live-hide cap. */
    private val reportKeys = LinkedHashSet<String>()
    private val hiddenKeys = LinkedHashSet<String>()
    private var sawLive = emptySet<String>()
    private var episode = false
    private var hidingEpisode = false
    private var acknowledged = false
    /** Open sit. Continue and Hide these then hold until the sit ends. */
    private var sitOpen = false
    private var sitHold: SitHold? = null
    /** Clock time when a no-sit hold ends. A sit hold uses [Long.MAX_VALUE]. */
    private var holdUntil = 0L
    private var lastNow = 0L
    /** Latest GPS speed from the scan loop. Null when the fix has no speed. */
    private var speedMps: Double? = null
    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice.asStateFlow()
    private val _hide = MutableStateFlow(FloodHide())
    val hide: StateFlow<FloodHide> = _hide.asStateFlow()

    fun bursts(): List<FloodBurst> = synchronized(lock) { bursts.toList() }

    /**
     * First sighting of an address. Repeat advertisements are not passed in.
     * [speedMps] is this phone's GPS speed, or null when it is unknown.
     * A weak burst while driving does not warn. Null does not hide one.
     */
    fun consider(obs: Observation, now: Long, speedMps: Double? = null) {
        if (obs.kind != RadioKind.BLE) return
        val kind = classify(obs) ?: return
        synchronized(lock) {
            this.speedMps = speedMps
            lastNow = now
            expireHold(now)
            val dropped = evict(now)
            val mac = MacUtil.normalize(obs.mac)
            if (mac.isBlank() || !macs.add(mac)) {
                if (dropped) publish(now)
                return
            }
            window.addLast(Hit(mac, now, obs.rssi, kind.popup, kind.families))
            while (window.size > CAP) macs.remove(window.removeFirst().mac)
            publish(now)
        }
    }

    /** Drops addresses that have left the window. Cheap when the window is empty. */
    fun tick(now: Long, speedMps: Double? = null) {
        synchronized(lock) {
            val wasDriving = driving(this.speedMps)
            this.speedMps = speedMps
            lastNow = now
            expireHold(now)
            if (window.isEmpty()) {
                finishEpisode()
                return
            }
            val dropped = evict(now)
            if (!dropped && wasDriving == driving(this.speedMps)) return
            publish(now)
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

    /** A weak cluster while this phone is driving is a pass-by, not a warning. */
    private fun warnsAtThisSpeed(median: Int?): Boolean {
        if (!driving(speedMps)) return true
        val rssi = median ?: return true
        return rssi > DRIVE_WEAK_DBM
    }

    private fun evict(now: Long): Boolean {
        var dropped = false
        while (window.isNotEmpty() && now - window.first().at > WINDOW_MS) {
            macs.remove(window.removeFirst().mac)
            dropped = true
        }
        return dropped
    }

    private fun publish(now: Long) {
        val popups = ArrayList<Hit>()
        val names = ArrayList<Hit>()
        for (hit in window) {
            if (hit.popup) popups += hit else names += hit
        }
        val pop = band(popups)
        val nam = band(names)
        val popupHot = pop.count >= POPUP_MIN && warnsAtThisSpeed(pop.median)
        val nameHot = nam.count >= NAME_MIN && warnsAtThisSpeed(nam.median)
        if (!popupHot && !nameHot) {
            finishEpisode()
            return
        }
        val families = if (popupHot) labels(pop.hits) else emptyList()
        val median = when {
            popupHot && pop.median != null -> pop.median
            nameHot && nam.median != null -> nam.median
            else -> null
        }
        val starting = !episode || bursts.isEmpty()
        if (starting) {
            episodeKeys.clear()
            reportKeys.clear()
            hidingEpisode = sitHold == SitHold.HIDE
        }
        var grew = false
        if (popupHot) grew = remember(pop.hits) || grew
        if (nameHot) grew = remember(nam.hits) || grew
        val next = FloodBurst(now, pop.count, nam.count, families, median, reportKeys.toList())
        if (starting) {
            bursts += next
            episode = true
            while (bursts.size > BURST_CAP) bursts.removeAt(0)
        } else {
            bursts[bursts.lastIndex] = peak(bursts.last(), next)
        }
        if (grew && hidingEpisode) publishHide()
        _notice.value = Notice(
            showDialog = sitHold == null && !acknowledged,
            popupCount = pop.count,
            nameCount = nam.count,
            families = families,
            medianRssi = median,
            duringSit = sitOpen,
        )
    }

    private fun remember(hits: List<Hit>): Boolean {
        var grew = false
        for (hit in hits) {
            val key = "BLE:${hit.mac}"
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

    private fun expireHold(now: Long) {
        if (sitHold != null && now >= holdUntil) {
            sitHold = null
            holdUntil = 0L
        }
    }

    private fun finishEpisode() {
        val wasOn = hidingEpisode
        episode = false
        hidingEpisode = false
        episodeKeys.clear()
        reportKeys.clear()
        acknowledged = false
        if (_notice.value != null) _notice.value = null
        if (wasOn) publishHide()
    }

    private fun publishHide() {
        _hide.value = FloodHide(episodeOn = hidingEpisode, keys = hiddenKeys.toSet())
    }

    /** Keep the first time and the high-water counts for this burst. */
    private fun peak(prev: FloodBurst, next: FloodBurst): FloodBurst {
        val louder = next.popupCount > prev.popupCount ||
            (next.popupCount == prev.popupCount && next.nameCount > prev.nameCount)
        return prev.copy(
            popupCount = maxOf(prev.popupCount, next.popupCount),
            nameCount = maxOf(prev.nameCount, next.nameCount),
            families = (prev.families + next.families).distinct(),
            medianRssi = if (louder) next.medianRssi ?: prev.medianRssi else prev.medianRssi ?: next.medianRssi,
            keys = next.keys,
        )
    }

    private fun band(hits: List<Hit>): Band {
        if (hits.isEmpty()) return Band(0, null, emptyList())
        val measured = ArrayList<Hit>(hits.size)
        for (hit in hits) if (Rssi.measured(hit.rssi)) measured += hit
        if (measured.size * 2 <= hits.size) return Band(hits.size, null, hits)
        val values = IntArray(measured.size) { measured[it].rssi }
        values.sort()
        val median = values[values.size / 2]
        val kept = ArrayList<Hit>(measured.size)
        for (hit in measured) {
            if (abs(hit.rssi - median) <= RSSI_BAND_DB) kept += hit
        }
        return Band(kept.size, median, kept)
    }

    private fun labels(hits: List<Hit>): List<String> {
        var bits = 0
        for (hit in hits) bits = bits or hit.families
        val out = ArrayList<String>(6)
        if (bits and BIT_PROX != 0) out += "Apple proximity pairing"
        if (bits and BIT_ACTION != 0) out += "Apple Nearby Action"
        if (bits and BIT_FAST != 0) out += "Fast Pair"
        if (bits and BIT_SWIFT != 0) out += "Swift Pair"
        if (bits and BIT_EASY != 0) out += "Samsung Easy Setup"
        if (bits and BIT_LOVE != 0) out += "LoveSpouse"
        return out
    }

    private fun classify(obs: Observation): Kind? {
        var bits = 0
        for (rec in mfgOf(obs)) {
            when (rec.companyId) {
                0x004C -> bits = bits or appleBits(rec.dataHex)
                0x0006 -> if (swiftPair(rec.dataHex)) bits = bits or BIT_SWIFT
                0x0075 -> if (easySetup(rec.dataHex)) bits = bits or BIT_EASY
                0x00FF -> if (loveSpouse(rec.dataHex)) bits = bits or BIT_LOVE
            }
        }
        if (FastPair.pairingAdvertised(obs.facts)) bits = bits or BIT_FAST
        if (bits != 0) return Kind(popup = true, families = bits)
        if (obs.name.isNotBlank() && MacUtil.isRandomized(obs.mac)) {
            return Kind(popup = false, families = 0)
        }
        return null
    }

    private fun mfgOf(obs: Observation): List<MfgRecord> {
        if (obs.facts.mfgRecords.isNotEmpty()) return obs.facts.mfgRecords
        val id = obs.manufacturerId ?: return emptyList()
        if (obs.manufacturerDataHex.isBlank()) return emptyList()
        return listOf(MfgRecord(id, obs.manufacturerDataHex))
    }

    /**
     * Apple Continuity TLVs. Nearby Info (0x10) and Find My (0x12) are not popups.
     * A 0x07 after a Find My TLV is part of that advertisement. A 0x07 that comes
     * first is still proximity pairing. Nearby Action (0x0F) still counts.
     */
    private fun appleBits(hex: String): Int {
        val bytes = hexBytes(hex) ?: return 0
        var i = 0
        var bits = 0
        var sawFindMy = false
        while (i + 2 <= bytes.size) {
            val type = bytes[i].toInt() and 0xFF
            val len = bytes[i + 1].toInt() and 0xFF
            if (len <= 0 || i + 2 + len > bytes.size) break
            when (type) {
                0x07 -> if (!sawFindMy) bits = bits or BIT_PROX
                0x0F -> bits = bits or BIT_ACTION
                0x12 -> sawFindMy = true
            }
            i += 2 + len
        }
        return bits
    }

    /**
     * Swift Pair beacon: id 0x03, sub-scenario 0x00–0x02, reserved byte 0x80.
     * The Windows background beacon starts with 0x01 and stays out.
     */
    private fun swiftPair(hex: String): Boolean {
        val bytes = hexBytes(hex) ?: return false
        if (bytes.size < 3 || (bytes[0].toInt() and 0xFF) != 0x03) return false
        val sub = bytes[1].toInt() and 0xFF
        val reserved = bytes[2].toInt() and 0xFF
        return sub <= 0x02 && reserved == 0x80
    }

    /** Samsung Easy Setup buds or watch. Other 0x0075 payloads, including a SmartTag, stay out. */
    private fun easySetup(hex: String): Boolean {
        val bytes = hexBytes(hex) ?: return false
        return startsWith(bytes, EASY_BUDS) || startsWith(bytes, EASY_WATCH)
    }

    /** LoveSpouse prefix under company 0x00FF. A shorter blob stays out. */
    private fun loveSpouse(hex: String): Boolean {
        val bytes = hexBytes(hex) ?: return false
        return startsWith(bytes, LOVE_SPOUSE)
    }

    private fun startsWith(bytes: ByteArray, prefix: ByteArray): Boolean {
        if (bytes.size < prefix.size) return false
        for (i in prefix.indices) if (bytes[i] != prefix[i]) return false
        return true
    }

    private fun hexBytes(hex: String): ByteArray? {
        var n = 0
        for (c in hex) if (nibble(c) >= 0) n++
        if (n == 0 || n % 2 != 0) return null
        val out = ByteArray(n / 2)
        var i = 0
        var hi = -1
        for (c in hex) {
            val v = nibble(c)
            if (v < 0) continue
            if (hi < 0) {
                hi = v
            } else {
                out[i++] = ((hi shl 4) or v).toByte()
                hi = -1
            }
        }
        return out
    }

    private fun nibble(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }

    private class Hit(
        val mac: String,
        val at: Long,
        val rssi: Int,
        val popup: Boolean,
        val families: Int,
    )

    private class Kind(val popup: Boolean, val families: Int)

    private class Band(val count: Int, val median: Int?, val hits: List<Hit>)

    /** What Continue or Hide these means for the rest of an open sit. */
    private enum class SitHold { LEAVE, HIDE }

    companion object {
        const val WINDOW_MS = 10_000L
        /** Same length as the last-15-minutes path. Starts at the answer and does not slide. */
        const val HOLD_MS = 15 * 60_000L
        const val POPUP_MIN = 10
        const val NAME_MIN = 15
        const val RSSI_BAND_DB = 12
        /** Above a walk. About 11 mph. A store aisle stays under this. */
        const val DRIVE_MPS = 5.0
        /** At or below this, a burst while driving stays quiet. Louder still warns. */
        const val DRIVE_WEAK_DBM = -80

        private fun driving(speed: Double?): Boolean = speed != null && speed >= DRIVE_MPS
        private const val CAP = 96
        private const val BURST_CAP = 40
        private const val EPISODE_CAP = 900
        private const val BIT_PROX = 1
        private const val BIT_ACTION = 2
        private const val BIT_FAST = 4
        private const val BIT_SWIFT = 8
        private const val BIT_EASY = 16
        private const val BIT_LOVE = 32
        private val LOVE_SPOUSE = byteArrayOf(
            0x6D, 0xB6.toByte(), 0x43, 0xCE.toByte(), 0x97.toByte(), 0xFE.toByte(), 0x42, 0x7C,
        )
        private val EASY_BUDS = byteArrayOf(
            0x42, 0x09, 0x81.toByte(), 0x02, 0x14, 0x15, 0x03, 0x21, 0x01, 0x09,
        )
        private val EASY_WATCH = byteArrayOf(
            0x01, 0x00, 0x02, 0x00, 0x01, 0x01, 0xFF.toByte(), 0x00, 0x00, 0x43,
        )
    }
}
