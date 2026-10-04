package app.fieldwatch.domain

import app.fieldwatch.i18n.localized

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
        val pairing = !wifi && popupCount >= PairingFlood.POPUP_MIN
        val head = when {
            wifi -> localized("pairing_flood_wi_fi_beacon_flood", "Wi-Fi beacon flood")
            pairing -> localized("pairing_flood_pairing_flood", "Pairing flood")
            else -> localized("pairing_flood_name_flood", "Name flood")
        }
        val count = when {
            wifi -> popupCount
            pairing -> popupCount
            else -> nameCount
        }
        val unit = if (wifi) localized("pairing_flood_new_names", "new names") else localized("pairing_flood_new_addresses", "new addresses")
        val fam = if (pairing && families.isNotEmpty()) ": ${families.joinToString(", ")}" else ""
        val extra = if (pairing && nameCount >= PairingFlood.NAME_MIN) localized("pairing_flood_named", " · %1\$s named", nameCount) else ""
        val loud = if (medianRssi != null) localized("pairing_flood_about_dbm", " · about %1\$s dBm", medianRssi) else ""
        val aside = when (keys.size) {
            0 -> ""
            1 -> localized("pairing_flood_1_address_from_this_burst_is_left", " 1 address from this burst is left out of the counts and lists below.")
            else -> localized("pairing_flood_addresses_from_this_burst_are_left_out", " %1\$s addresses from this burst are left out of the counts and lists below.", keys.size)
        }
        return "$clock UTC. $head. $count $unit$fam$extra$loud.$aside"
    }

    companion object {
        val INTRO: String
            get() =
            localized("pairing_flood_a_burst_of_new_bluetooth_addresses_in", "A burst of new Bluetooth addresses in a few seconds. A handheld can do this by advertising a pairing request or a new name and changing the address every packet. The advertisement does not name the tool.")

        val WIFI_INTRO: String
            get() =
            localized("pairing_flood_a_burst_of_new_wi_fi_names", "A burst of new Wi-Fi names in one scan, about the same loudness, gone by the next scan. A repeated name, a mesh, an extender, or a guest network is not counted. The advertisement does not name the tool.")

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
 * Nearby Action (0x0F), a 3-byte Fast Pair model, a Swift Pair beacon, a
 * Nearby Sharing scenario, Samsung Easy Setup buds or watch, and a LoveSpouse
 * advertisement (company 0x00FF plus its fixed prefix).
 * A separate counter is a new address that only advertises a name.
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
    ) {
        fun title(): String = when {
            wifi -> localized("pairing_flood_wi_fi_beacon_flood", "Wi-Fi beacon flood")
            popupCount >= POPUP_MIN -> localized("pairing_flood_pairing_flood", "Pairing flood")
            else -> localized("pairing_flood_name_flood", "Name flood")
        }

        fun line(): String {
            if (wifi) {
                val loud = if (medianRssi != null) localized("pairing_flood_about_dbm", " · about %1\$s dBm", medianRssi) else ""
                return localized("pairing_flood_wi_fi_beacon_flood_new_names", "Wi-Fi beacon flood · %1\$s new names%2\$s", popupCount, loud)
            }
            val popupHot = popupCount >= POPUP_MIN
            val nameHot = nameCount >= NAME_MIN
            val head = if (popupHot) localized("pairing_flood_pairing_flood", "Pairing flood") else localized("pairing_flood_name_flood", "Name flood")
            val count = if (popupHot) popupCount else nameCount
            val extra = if (popupHot && nameHot) localized("pairing_flood_named", " · %1\$s named", nameCount) else ""
            val loud = if (medianRssi != null) localized("pairing_flood_about_dbm", " · about %1\$s dBm", medianRssi) else ""
            return localized("pairing_flood_new_addresses_2", "%1\$s · %2\$s new addresses%3\$s%4\$s", head, count, extra, loud)
        }

        fun body(): String {
            if (wifi) {
                val loud = if (medianRssi != null) {
                    localized("pairing_flood_about_the_same_loudness_about_dbm", ", about the same loudness, about %1\$s dBm,", medianRssi)
                } else {
                    ""
                }
                val what = localized("pairing_flood_new_wi_fi_names_showed_up_in", "%1\$s new Wi-Fi names showed up in one scan%2\$s and they were gone on the next scan. ", popupCount, loud) +
                    localized("pairing_flood_a_repeated_name_a_mesh_an_extender", "A repeated name, a mesh, an extender, or a guest network is not counted.")
                val how = localized("pairing_flood_a_handheld_such_as_a_flipper_zero", "A handheld such as a Flipper Zero, or an ESP32 running Marauder or Bruce, does this by advertising many network names. The advertisement does not name the tool.")
                return what + "\n\n" + how
            }
            val popupHot = popupCount >= POPUP_MIN
            val nameHot = nameCount >= NAME_MIN
            val what = buildString {
                if (popupHot) {
                    append(popupCount)
                    append(localized("pairing_flood_new_addresses_sent_pairing_advertisements_in_the", " new addresses sent pairing advertisements in the last few seconds"))
                    if (families.isNotEmpty()) {
                        append(": ")
                        append(families.joinToString(", "))
                    }
                    append('.')
                    if (nameHot) {
                        append(' ')
                        append(nameCount)
                        append(localized("pairing_flood_more_each_advertised_a_bluetooth_name", " more each advertised a Bluetooth name."))
                    }
                } else {
                    append(nameCount)
                    append(localized("pairing_flood_new_addresses_each_advertised_a_bluetooth_name", " new addresses each advertised a Bluetooth name in the last few seconds."))
                }
                if (medianRssi != null) {
                    append(localized("pairing_flood_they_are_about_the_same_loudness_about", " They are about the same loudness, about "))
                    append(medianRssi)
                    append(localized("pairing_flood_dbm_which_reads_as_one_nearby_radio", " dBm, which reads as one nearby radio."))
                }
            }
            val how = if (popupHot) {
                localized("pairing_flood_a_handheld_such_as_a_flipper_zero_2", "A handheld such as a Flipper Zero, or an ESP32 running Marauder or Bruce, does this by advertising a pairing request and changing the address every packet. The advertisement does not name the tool.")
            } else {
                localized("pairing_flood_a_handheld_such_as_a_flipper_zero_3", "A handheld such as a Flipper Zero, or an ESP32 running Marauder or Bruce, does this by advertising a new name and changing the address every packet. The advertisement does not name the tool.")
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
    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice.asStateFlow()
    private val _hide = MutableStateFlow(FloodHide())
    val hide: StateFlow<FloodHide> = _hide.asStateFlow()

    fun bursts(): List<FloodBurst> = synchronized(lock) { bursts.toList() }

    /** First sighting of an address. Repeat advertisements are not passed in. */
    fun consider(obs: Observation, now: Long) {
        if (obs.kind != RadioKind.BLE) return
        val kind = classify(obs) ?: return
        synchronized(lock) {
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
    fun tick(now: Long) {
        synchronized(lock) {
            if (window.isEmpty()) {
                finishEpisode()
                return
            }
            if (!evict(now)) return
            publish(now)
        }
    }

    fun dismiss() {
        synchronized(lock) {
            val cur = _notice.value ?: return
            acknowledged = true
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
            } else {
                hidingEpisode = false
                hiddenKeys.clear()
            }
            publishHide()
        }
    }

    /** Bring every hidden flood address back. The open burst's switch goes off too. */
    fun clearHidden() {
        synchronized(lock) {
            hidingEpisode = false
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
        val popupHot = pop.count >= POPUP_MIN
        val nameHot = nam.count >= NAME_MIN
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
            hidingEpisode = false
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
            showDialog = !acknowledged,
            popupCount = pop.count,
            nameCount = nam.count,
            families = families,
            medianRssi = median,
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
        if (obs.name.isNotBlank()) return Kind(popup = false, families = 0)
        return null
    }

    private fun mfgOf(obs: Observation): List<MfgRecord> {
        if (obs.facts.mfgRecords.isNotEmpty()) return obs.facts.mfgRecords
        val id = obs.manufacturerId ?: return emptyList()
        if (obs.manufacturerDataHex.isBlank()) return emptyList()
        return listOf(MfgRecord(id, obs.manufacturerDataHex))
    }

    /** Apple Continuity TLVs. 0x10 Nearby Info and 0x12 Find My are not popups. */
    private fun appleBits(hex: String): Int {
        val bytes = hexBytes(hex) ?: return 0
        var i = 0
        var bits = 0
        while (i + 2 <= bytes.size) {
            val type = bytes[i].toInt() and 0xFF
            val len = bytes[i + 1].toInt() and 0xFF
            if (len <= 0 || i + 2 + len > bytes.size) break
            when (type) {
                0x07 -> bits = bits or BIT_PROX
                0x0F -> bits = bits or BIT_ACTION
            }
            i += 2 + len
        }
        return bits
    }

    /**
     * Nearby Sharing scenario 0x01, or a Swift Pair beacon: id 0x03,
     * sub-scenario 0x00–0x02, reserved byte 0x80. Other 0x0006 payloads stay out.
     */
    private fun swiftPair(hex: String): Boolean {
        val bytes = hexBytes(hex) ?: return false
        if (bytes.size >= 2 && (bytes[0].toInt() and 0xFF) == 0x01) return true
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

    companion object {
        const val WINDOW_MS = 10_000L
        const val POPUP_MIN = 6
        const val NAME_MIN = 15
        const val RSSI_BAND_DB = 12
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
