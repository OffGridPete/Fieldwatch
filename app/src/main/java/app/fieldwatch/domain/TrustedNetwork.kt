package app.fieldwatch.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Watches Wi-Fi names the user has marked trusted.
 * A sighting alerts only when it looks unlike the access points and places already learned.
 * The phone stays receive-only. This type does not scan or connect.
 */
object TrustedSecurity {
    @Serializable
    data class Mark(val rank: Int, val auth: String)

    fun parse(text: String?): Set<Mark> {
        if (text.isNullOrBlank()) return emptySet()
        val u = text.uppercase()
        val out = LinkedHashSet<Mark>()
        val wpa3 = u.contains("WPA3") || u.contains("SAE") || u.contains("OWE") || u.contains("SUITE-B")
        val wpa2 = u.contains("WPA2") || u.contains("RSN")
        val wpaOnly = u.contains("WPA") && !wpa2 && !wpa3
        if (u.contains("SAE")) out += Mark(4, AUTH_PERSONAL)
        if (u.contains("SUITE-B")) out += Mark(4, AUTH_ENTERPRISE)
        if (u.contains("OWE")) out += Mark(4, AUTH_OWE)
        if (u.contains("802.1X") || u.contains("EAP")) {
            val rank = when {
                wpa3 && !wpa2 -> 4
                wpa2 -> 3
                wpaOnly -> 2
                else -> 3
            }
            out += Mark(rank, AUTH_ENTERPRISE)
        }
        if (u.contains("PSK")) {
            val rank = when {
                wpa3 && !wpa2 -> 4
                wpa2 -> 3
                wpaOnly || u.contains("WPA") -> 2
                else -> 3
            }
            out += Mark(rank, AUTH_PERSONAL)
        }
        if (u.contains("WEP")) out += Mark(1, AUTH_WEP)
        val crypto = u.contains("WPA") || u.contains("RSN") || u.contains("WEP") ||
            u.contains("SAE") || u.contains("OWE") || u.contains("EAP") || u.contains("PSK") ||
            u.contains("SUITE-B")
        if (!crypto && (u.contains("OPEN") || u.contains("NONE") || u.contains("ESS") || u.contains("IBSS"))) {
            out += Mark(0, AUTH_OPEN)
        }
        return out
    }

    /** Null when the sighting is blank or is not weaker than the learned set. */
    fun downgrade(sighting: Set<Mark>, baseline: Set<Mark>): String? {
        if (sighting.isEmpty() || baseline.isEmpty()) return null
        val best = sighting.maxOf { it.rank }
        val openish = sighting.filter { it.rank <= 1 }
        val baselineOpen = baseline.any { it.rank <= 1 }
        if (openish.isNotEmpty() && !baselineOpen && sighting.none { it.rank >= 2 }) {
            return if (openish.any { it.rank == 0 }) REASON_OPEN else REASON_WEAKER
        }
        if (best < baseline.minOf { it.rank }) {
            return if (best == 0) REASON_OPEN else REASON_WEAKER
        }
        val personal = sighting.any { it.auth == AUTH_PERSONAL }
        val baselinePersonal = baseline.any { it.auth == AUTH_PERSONAL }
        val baselineAllEnterprise = baseline.all { it.auth == AUTH_ENTERPRISE }
        if (baselineAllEnterprise && personal && !baselinePersonal) return REASON_WEAKER
        return null
    }

    const val AUTH_OPEN = "open"
    const val AUTH_WEP = "wep"
    const val AUTH_PERSONAL = "personal"
    const val AUTH_ENTERPRISE = "enterprise"
    const val AUTH_OWE = "owe"
    const val REASON_OPEN = "open"
    const val REASON_WEAKER = "weaker"
    const val REASON_UNKNOWN = "unknown"
    const val REASON_AWAY = "away"
}

@Serializable
data class TrustedAp(
    val bssid: String,
    val vendor: String? = null,
    val oui: String? = null,
    val band: String = "",
    val channel: Int = 0,
    val security: List<TrustedSecurity.Mark> = emptyList(),
    val securityText: String? = null,
    val firstSeen: Long = 0L,
    val lastSeen: Long = 0L,
    val loudestRssi: Int = -127,
    val loudLat: Double? = null,
    val loudLon: Double? = null,
)

@Serializable
data class UsualPlace(
    val lat: Double,
    val lon: Double,
    val updatedAt: Long,
)

@Serializable
data class TwinEpisode(
    val bssid: String,
    val reasons: List<String>,
    val at: Long,
    val lastHeard: Long,
    val open: Boolean,
    val confidence: Int,
    val notified: Int = -1,
    val rssi: Int = -127,
    val lat: Double? = null,
    val lon: Double? = null,
    val securityText: String? = null,
    val channel: Int = 0,
    val frequencyMhz: Int = 0,
    val vendor: String? = null,
    val ouiNote: Boolean = false,
    /** Dismissed episodes stay closed until the address has been gone for 10 minutes. */
    val dismissed: Boolean = false,
)

@Serializable
data class TrustedNet(
    val ssid: String,
    val label: String = "",
    val trustedAt: Long,
    val learningPaused: Boolean = false,
    val travels: Boolean = false,
    val aps: List<TrustedAp> = emptyList(),
    val places: List<UsualPlace> = emptyList(),
    val ignored: List<String> = emptyList(),
    val episodes: List<TwinEpisode> = emptyList(),
    val pending: Map<String, Int> = emptyMap(),
    val learnPending: Map<String, Int> = emptyMap(),
    /** Pins already sent. Left out of the export. */
    val pins: List<TwinPin> = emptyList(),
)

@Serializable
data class TrustedBook(val nets: List<TrustedNet> = emptyList())

@Serializable
data class TwinPin(
    val uid: String,
    val ssid: String,
    val bssid: String,
    val lat: Double,
    val lon: Double,
    val rssi: Int,
    val lastSentAt: Long = 0L,
    val vendor: String? = null,
    val reasons: List<String> = emptyList(),
    val confidence: Int = 1,
)

/** A tombstone waiting for the feed. Not part of the export. */
@Serializable
data class TwinTomb(
    val uid: String,
    val lat: Double,
    val lon: Double,
    val callsign: String,
)

/** One sit-report row. The block prints the name, the reason, the time, and the address. */
data class TrustedSitLine(
    val ssid: String,
    val label: String,
    val bssid: String,
    val reasons: List<String>,
    val at: Long,
    val until: Long,
)

data class PinMove(
    val pin: TwinPin,
    val action: TakHeardHere,
)

data class TrustedOpt(
    val enabled: Boolean = false,
    val downgrade: Boolean = true,
    val unknownVendor: Boolean = true,
    val away: Boolean = true,
    val awayMeters: Int = 1_000,
    val loudnessFloor: Int = -85,
    val learn: Boolean = true,
    /** Days after the name was trusted. 0 is 1 hour. */
    val learnDays: Int = 3,
    val beep: Boolean = true,
    val voice: Boolean = true,
    val shade: Boolean = false,
    val floor: GnssConfidence = GnssConfidence.MEDIUM,
    val takPin: Boolean = false,
    val takHold: TakGnssHold = TakGnssHold.HOUR,
)

data class TwinHear(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val fresh: Boolean,
    val securityText: String?,
    val channel: Int,
    val frequencyMhz: Int,
    val vendor: String?,
    val lat: Double?,
    val lon: Double?,
    val accuracyM: Float?,
    val locationPaused: Boolean,
)

data class TrustedAlert(
    val ssid: String,
    val bssid: String,
    val reasons: List<String>,
    val confidence: Int,
    val ouiNote: Boolean,
    val offerLearn: Boolean,
    val offerIgnore: Boolean,
    val offerRemember: Boolean,
    val beep: Boolean,
    val voice: Boolean,
    val shade: Boolean,
)

data class TrustedStep(
    val book: TrustedBook,
    val alert: TrustedAlert? = null,
)

object TrustedNets {
    const val NEAR_M = 300.0
    const val ABSENT_MS = 10 * 60_000L
    const val PLACE_CAP = 8
    const val EPISODE_CAP = 40
    private const val LOUDER_DB = 15
    private val TOOL_OUI = "00:13:37"

    fun bandOf(mhz: Int): String = when {
        mhz in 2400..2500 -> "2.4 GHz"
        mhz in 4900..5900 -> "5 GHz"
        mhz in 5925..7125 -> "6 GHz"
        else -> ""
    }

    fun factoryOui(mac: String): String? {
        if (MacUtil.isLocallyAdministered(mac)) return null
        val prefix = MacUtil.prefixBytes(mac, 3)
        return prefix.takeIf { it.count { ch -> ch == ':' } == 2 }
    }

    fun canTrust(name: String, hidden: Boolean): Boolean =
        !hidden && name.isNotBlank() && !name.equals("<unknown ssid>", ignoreCase = true)

    fun display(net: TrustedNet): String = net.label.ifBlank { net.ssid }

    fun reasonLine(reasons: List<String>): String =
        reasons.joinToString(", ") { code ->
            when (code) {
                TrustedSecurity.REASON_OPEN -> LanguageText.text("trusted.reason.open")
                TrustedSecurity.REASON_WEAKER -> LanguageText.text("trusted.reason.weaker")
                TrustedSecurity.REASON_UNKNOWN -> LanguageText.text("trusted.reason.unknown")
                TrustedSecurity.REASON_AWAY -> LanguageText.text("trusted.reason.away")
                else -> code
            }
        }

    fun sentence(ssid: String, reasons: List<String>): String =
        LanguageText.text(
            "trusted.alert",
            mapOf("ssid" to ssid, "reason" to reasonLine(reasons)),
        )

    fun trust(book: TrustedBook, ssid: String, picks: List<TwinHear>, now: Long): TrustedBook {
        if (ssid.isBlank() || picks.isEmpty()) return book
        val aps = picks.map { hear -> apFrom(hear, now) }
        val net = TrustedNet(ssid = ssid, trustedAt = now, aps = aps)
        return book.copy(nets = book.nets.filterNot { it.ssid == ssid } + net)
    }

    fun forget(book: TrustedBook, ssid: String): TrustedBook =
        book.copy(nets = book.nets.filterNot { it.ssid == ssid })

    fun rename(book: TrustedBook, ssid: String, label: String): TrustedBook =
        update(book, ssid) { it.copy(label = label.trim()) }

    fun setPaused(book: TrustedBook, ssid: String, paused: Boolean): TrustedBook =
        update(book, ssid) { it.copy(learningPaused = paused) }

    fun setTravels(book: TrustedBook, ssid: String, travels: Boolean): TrustedBook =
        update(book, ssid) { it.copy(travels = travels) }

    fun removeAp(book: TrustedBook, ssid: String, bssid: String): TrustedBook =
        update(book, ssid) { it.copy(aps = it.aps.filterNot { ap -> ap.bssid.equals(bssid, true) }) }

    fun removePlace(book: TrustedBook, ssid: String, index: Int): TrustedBook =
        update(book, ssid) { net ->
            if (index !in net.places.indices) net else net.copy(places = net.places.filterIndexed { i, _ -> i != index })
        }

    fun stopIgnore(book: TrustedBook, ssid: String, bssid: String): TrustedBook =
        update(book, ssid) { it.copy(ignored = it.ignored.filterNot { mac -> mac.equals(bssid, true) }) }

    fun hear(book: TrustedBook, hear: TwinHear, opt: TrustedOpt, now: Long): TrustedStep {
        val aged = age(book, now)
        val net = aged.nets.find { it.ssid == hear.ssid } ?: return TrustedStep(aged)
        val mac = MacUtil.normalize(hear.bssid)
        if (net.ignored.any { MacUtil.normalize(it).equals(mac, true) }) return TrustedStep(aged)
        var next = learn(net, hear, mac, opt, now)
        val alert = detect(next, hear, mac, opt, now)
        next = alert.second
        val replaced = aged.nets.map { if (it.ssid == next.ssid) next else it }
        return TrustedStep(aged.copy(nets = replaced), alert.first)
    }

    fun dismiss(book: TrustedBook, ssid: String, bssid: String, now: Long): TrustedBook =
        update(book, ssid) { net ->
            net.copy(episodes = net.episodes.map { ep ->
                if (ep.open && ep.bssid.equals(bssid, true)) ep.copy(open = false, dismissed = true, lastHeard = now) else ep
            })
        }

    fun learnAp(book: TrustedBook, ssid: String, bssid: String, now: Long): TrustedBook =
        update(book, ssid) { net ->
            val ep = net.episodes.find { it.open && it.bssid.equals(bssid, true) } ?: return@update net
            val hear = hearFrom(ep, ssid)
            val ap = apFrom(hear, ep.at).copy(lastSeen = now)
            net.copy(
                aps = mergeAp(net.aps, ap),
                episodes = close(net.episodes, bssid),
                pending = net.pending - macKey(bssid),
            )
        }

    fun ignore(book: TrustedBook, ssid: String, bssid: String): TrustedBook =
        update(book, ssid) { net ->
            val mac = MacUtil.normalize(bssid)
            net.copy(
                ignored = (net.ignored + mac).distinct(),
                episodes = close(net.episodes, bssid),
                pending = net.pending - macKey(bssid),
            )
        }

    fun remember(book: TrustedBook, ssid: String, bssid: String, now: Long): TrustedBook =
        update(book, ssid) { net ->
            if (net.travels) return@update net
            val ep = net.episodes.find { it.bssid.equals(bssid, true) && it.lat != null && it.lon != null } ?: return@update net
            net.copy(
                places = addPlace(net.places, ep.lat!!, ep.lon!!, now),
                episodes = close(net.episodes, bssid),
            )
        }

    fun age(book: TrustedBook, now: Long): TrustedBook = book.copy(
        nets = book.nets.map { net ->
            net.copy(episodes = net.episodes.map { ep ->
                if (ep.open && now - ep.lastHeard >= ABSENT_MS) ep.copy(open = false) else ep
            })
        },
    )

    /** Notified episodes whose time overlaps the sit. Coordinates stay off this row. */
    fun linesIn(book: TrustedBook, start: Long, end: Long): List<TrustedSitLine> =
        book.nets.flatMap { net ->
            net.episodes.filter { ep ->
                ep.notified >= 0 && ep.at <= end && ep.lastHeard >= start
            }.map { ep ->
                TrustedSitLine(net.ssid, display(net), ep.bssid, ep.reasons, ep.at, ep.lastHeard)
            }
        }.sortedBy { it.at }

    /** Export keeps names, access points, security, the travels switch, and ignored addresses. */
    fun exported(book: TrustedBook): TrustedBook = book.copy(
        nets = book.nets.map { net ->
            net.copy(
                places = emptyList(),
                episodes = emptyList(),
                pending = emptyMap(),
                learnPending = emptyMap(),
                pins = emptyList(),
                aps = net.aps.map { it.copy(loudLat = null, loudLon = null) },
            )
        },
    )

    /**
     * A broken file returns null and the caller keeps [current].
     * A name already on the phone keeps its places. New access points and ignored addresses are added.
     */
    fun mergeImport(current: TrustedBook, incoming: TrustedBook): TrustedBook {
        var nets = current.nets
        for (add in incoming.nets) {
            if (add.ssid.isBlank()) continue
            val had = nets.find { it.ssid == add.ssid }
            nets = if (had == null) {
                nets + add.copy(
                    places = emptyList(),
                    episodes = emptyList(),
                    pending = emptyMap(),
                    learnPending = emptyMap(),
                    pins = emptyList(),
                )
            } else {
                val aps = add.aps.fold(had.aps) { acc, ap -> mergeAp(acc, ap.copy(loudLat = null, loudLon = null)) }
                val ignored = (had.ignored + add.ignored).distinct()
                nets.map {
                    if (it.ssid == had.ssid) {
                        had.copy(aps = aps, ignored = ignored, travels = had.travels || add.travels)
                    } else {
                        it
                    }
                }
            }
        }
        return current.copy(nets = nets)
    }

    fun severalMakers(hears: List<TwinHear>): Boolean =
        hears.mapNotNull { factoryOui(it.bssid) }.toSet().size > 1

    /** Other exact names that share a checked access point's OUI and security. None are trusted yet. */
    fun alsoNames(visible: List<TwinHear>, checked: List<TwinHear>, ssid: String): List<String> {
        if (checked.isEmpty()) return emptyList()
        val names = LinkedHashSet<String>()
        for (hear in visible) {
            if (hear.ssid.isBlank() || hear.ssid == ssid) continue
            if (matchesPick(hear, checked)) names += hear.ssid
        }
        return names.toList()
    }

    /** Access points on [ssid] that match a checked access point's OUI and security. */
    fun apsForName(visible: List<TwinHear>, checked: List<TwinHear>, ssid: String): List<TwinHear> =
        visible.filter { it.ssid == ssid && matchesPick(it, checked) }

    fun pinUid(bssid: String): String = "FW-TWIN-" + MacUtil.normalize(bssid).replace(":", "")

    fun twinCallsign(ssid: String): String = "Possible evil twin: $ssid".take(32)

    /**
     * Move only when this hear is louder. A weaker hear keeps the latitude and longitude
     * and refreshes after the usual interval.
     */
    fun movePin(
        prev: TwinPin?,
        ssid: String,
        bssid: String,
        lat: Double,
        lon: Double,
        rssi: Int,
        now: Long,
        vendor: String? = null,
        reasons: List<String> = emptyList(),
        confidence: Int = GnssConfidence.MEDIUM.ordinal,
    ): PinMove {
        val lastAt = prev?.lastSentAt?.takeIf { it > 0L }
        val lastRssi = if (lastAt == null) null else prev.rssi
        val action = TakPublish.heardHereAction(lastAt, lastRssi, now, rssi)
        val pin = when (action) {
            TakHeardHere.SKIP -> prev ?: pinAt(ssid, bssid, lat, lon, rssi, now, vendor, reasons, confidence)
            TakHeardHere.REFRESH -> prev!!.copy(lastSentAt = now)
            TakHeardHere.MOVE -> pinAt(ssid, bssid, lat, lon, rssi, now, vendor ?: prev?.vendor, reasons.ifEmpty { prev?.reasons.orEmpty() }, confidence)
        }
        return PinMove(pin, action)
    }

    /**
     * Packets for open episodes. Nothing is sent while the feed is paused.
     * The pin switch off tombstones pins already sent and sends no new one.
     */
    fun pinCalls(book: TrustedBook, opt: TrustedOpt, takRunning: Boolean, now: Long): Pair<TrustedBook, List<String>> {
        if (!takRunning) return book to emptyList()
        if (!opt.takPin) {
            val xml = ArrayList<String>()
            val nets = book.nets.map { net ->
                net.pins.forEach { xml += tombXml(it, now) }
                if (net.pins.isEmpty()) net else net.copy(pins = emptyList())
            }
            return book.copy(nets = nets) to xml
        }
        if (!opt.enabled) return book to emptyList()
        val xml = ArrayList<String>()
        val nets = book.nets.map { net ->
            var pins = net.pins
            for (ep in net.episodes) {
                val lat = ep.lat ?: continue
                val lon = ep.lon ?: continue
                if (!ep.open) continue
                val prev = pins.find { it.bssid.equals(ep.bssid, true) }
                val step = movePin(prev, net.ssid, ep.bssid, lat, lon, ep.rssi, now, ep.vendor, ep.reasons, ep.confidence)
                if (step.action == TakHeardHere.SKIP) continue
                xml += pinXml(step.pin, now, opt.takHold.staleMs())
                pins = pins.filterNot { it.uid == step.pin.uid } + step.pin
            }
            net.copy(pins = pins)
        }
        return book.copy(nets = nets) to xml
    }

    fun applyPins(current: TrustedBook, planned: TrustedBook): TrustedBook =
        current.copy(nets = current.nets.map { net ->
            val from = planned.nets.find { it.ssid == net.ssid } ?: return@map net
            net.copy(pins = from.pins)
        })

    fun pinXml(pin: TwinPin, now: Long, staleMs: Long): String = CotEvent.twinXml(
        uid = pin.uid,
        lat = pin.lat,
        lon = pin.lon,
        now = now,
        callsign = twinCallsign(pin.ssid),
        remarks = twinRemarks(pin, now),
        staleMs = staleMs,
    )

    fun tombXml(pin: TwinPin, now: Long): String = CotEvent.tombstoneXml(
        uid = pin.uid,
        lat = pin.lat,
        lon = pin.lon,
        now = now,
        callsign = twinCallsign(pin.ssid),
        remarks = "Fieldwatch · gone",
    )

    fun tombOf(pin: TwinPin): TwinTomb = TwinTomb(pin.uid, pin.lat, pin.lon, twinCallsign(pin.ssid))

    fun tombXml(tomb: TwinTomb, now: Long): String = CotEvent.tombstoneXml(
        uid = tomb.uid,
        lat = tomb.lat,
        lon = tomb.lon,
        now = now,
        callsign = tomb.callsign,
        remarks = "Fieldwatch · gone",
    )

    private fun pinAt(
        ssid: String,
        bssid: String,
        lat: Double,
        lon: Double,
        rssi: Int,
        now: Long,
        vendor: String?,
        reasons: List<String>,
        confidence: Int,
    ) = TwinPin(
        uid = pinUid(bssid),
        ssid = ssid,
        bssid = MacUtil.normalize(bssid),
        lat = lat,
        lon = lon,
        rssi = rssi,
        lastSentAt = now,
        vendor = vendor,
        reasons = reasons,
        confidence = confidence,
    )

    private fun twinRemarks(pin: TwinPin, now: Long): String = buildString {
        append(pin.ssid)
        append('\n')
        append(pin.bssid)
        val vendor = pin.vendor?.takeIf { it.isNotBlank() }
        if (vendor != null) {
            append(' ')
            append(vendor)
        }
        append('\n')
        append(reasonLine(pin.reasons))
        append('\n')
        append(GnssConfidence.entries.getOrNull(pin.confidence)?.label().orEmpty())
        append('\n')
        append(utc(now))
        append('\n')
        append("Where this phone was. Not the access point.")
    }.take(TakDefaults.REMARKS_MAX)

    private fun utc(ms: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(ms))
    }

    private fun matchesPick(hear: TwinHear, checked: List<TwinHear>): Boolean {
        val oui = factoryOui(hear.bssid) ?: return false
        val marks = TrustedSecurity.parse(hear.securityText)
        if (marks.isEmpty()) return false
        return checked.any { pick ->
            factoryOui(pick.bssid) == oui && TrustedSecurity.parse(pick.securityText) == marks
        }
    }

    private fun learn(net: TrustedNet, hear: TwinHear, mac: String, opt: TrustedOpt, now: Long): TrustedNet {
        val known = net.aps.find { it.bssid.equals(mac, true) }
        val sighting = TrustedSecurity.parse(hear.securityText)
        val window = learning(net, opt, now)
        val good = goodFix(hear)
        if (known != null) {
            var ap = known.copy(lastSeen = now)
            val down = TrustedSecurity.downgrade(sighting, baseline(net))
            val matches = sighting.isNotEmpty() && down == null
            if (matches) {
                ap = ap.copy(
                    security = (ap.security + sighting).distinct(),
                    securityText = hear.securityText ?: ap.securityText,
                )
            }
            if (hear.rssi > ap.loudestRssi && good) {
                ap = ap.copy(loudestRssi = hear.rssi, loudLat = hear.lat, loudLon = hear.lon)
            } else if (hear.rssi > ap.loudestRssi) {
                ap = ap.copy(loudestRssi = hear.rssi)
            }
            var places = net.places
            if (window && !net.travels && good && matches) {
                places = placeForKnown(places, hear, now, opt.awayMeters)
            }
            return net.copy(aps = net.aps.map { if (it.bssid.equals(mac, true)) ap else it }, places = places)
        }
        if (!window) return net
        val baseline = baseline(net)
        val securityOk = sighting.isNotEmpty() && TrustedSecurity.downgrade(sighting, baseline) == null
        if (!securityOk || !good) return net
        if (!nearAny(net.places, hear)) return net
        val quiet = hear.rssi < opt.loudnessFloor
        val sibling = isSibling(net, mac, securityOk)
        val local = MacUtil.isLocallyAdministered(mac)
        val sameOui = sameOui(net, mac)
        if (sibling || local) {
            return net.copy(aps = mergeAp(net.aps, apFrom(hear, now)), learnPending = net.learnPending - macKey(mac))
        }
        if (!sameOui) return net
        if (quiet && !hear.fresh) return net
        val count = if (hear.fresh) (net.learnPending[macKey(mac)] ?: 0) + 1 else net.learnPending[macKey(mac)] ?: 0
        return if (count >= 2) {
            net.copy(aps = mergeAp(net.aps, apFrom(hear, now)), learnPending = net.learnPending - macKey(mac))
        } else {
            net.copy(learnPending = net.learnPending + (macKey(mac) to count))
        }
    }

    private fun detect(
        net: TrustedNet,
        hear: TwinHear,
        mac: String,
        opt: TrustedOpt,
        now: Long,
    ): Pair<TrustedAlert?, TrustedNet> {
        val held = net.episodes
            .filter { it.bssid.equals(mac, true) && it.dismissed && !it.open }
            .maxByOrNull { it.lastHeard }
        if (held != null && now - held.lastHeard < ABSENT_MS) {
            val bumped = net.copy(episodes = net.episodes.map { ep ->
                if (ep.bssid.equals(mac, true) && ep.dismissed && !ep.open && ep.at == held.at) {
                    ep.copy(lastHeard = now)
                } else {
                    ep
                }
            })
            return null to bumped
        }
        if (hear.rssi < opt.loudnessFloor || !hear.fresh) {
            return null to touchHeard(net, mac, now, hear.rssi)
        }
        val reasons = reasons(net, hear, mac, opt)
        if (reasons.isEmpty() || !opt.enabled) return null to touchHeard(net, mac, now, hear.rssi)
        val count = (net.pending[macKey(mac)] ?: 0) + 1
        var pending = net.pending + (macKey(mac) to count)
        val already = net.episodes.any { it.open && it.bssid.equals(mac, true) }
        if (count < 2 && !already) {
            return null to net.copy(pending = pending)
        }
        val confidence = confidence(net, hear, reasons)
        if (confidence < opt.floor.ordinal) return null to net.copy(pending = pending)
        val known = net.aps.any { it.bssid.equals(mac, true) }
        val onlyAway = reasons == listOf(TrustedSecurity.REASON_AWAY)
        val existing = net.episodes.find { it.open && it.bssid.equals(mac, true) }
        val notify = existing == null || confidence > existing.notified
        val episode = (existing ?: TwinEpisode(
            bssid = mac,
            reasons = reasons,
            at = now,
            lastHeard = now,
            open = true,
            confidence = confidence,
            rssi = hear.rssi,
        )).copy(
            reasons = reasons,
            lastHeard = now,
            open = true,
            dismissed = false,
            confidence = maxOf(existing?.confidence ?: confidence, confidence),
            notified = if (notify) confidence else existing?.notified ?: confidence,
            rssi = maxOf(existing?.rssi ?: hear.rssi, hear.rssi),
            lat = if (goodFix(hear) && (existing?.lat == null || hear.rssi >= (existing.rssi))) hear.lat else existing?.lat,
            lon = if (goodFix(hear) && (existing?.lat == null || hear.rssi >= (existing.rssi))) hear.lon else existing?.lon,
            securityText = hear.securityText ?: existing?.securityText,
            channel = hear.channel,
            frequencyMhz = hear.frequencyMhz,
            vendor = hear.vendor ?: existing?.vendor,
            ouiNote = factoryOui(mac) == TOOL_OUI,
        )
        val episodes = trim(net.episodes.filterNot { it.bssid.equals(mac, true) && it.open } + episode)
        val alert = if (!notify) null else TrustedAlert(
            ssid = net.ssid,
            bssid = mac,
            reasons = reasons,
            confidence = confidence,
            ouiNote = episode.ouiNote,
            offerLearn = !known,
            offerIgnore = !known,
            offerRemember = known && onlyAway && !net.travels && episode.lat != null,
            beep = opt.beep,
            voice = opt.voice,
            shade = opt.shade,
        )
        pending = pending - macKey(mac)
        return alert to net.copy(episodes = episodes, pending = pending)
    }

    private fun reasons(net: TrustedNet, hear: TwinHear, mac: String, opt: TrustedOpt): List<String> {
        val sighting = TrustedSecurity.parse(hear.securityText)
        if (sighting.isEmpty()) return emptyList()
        val base = baseline(net)
        val out = ArrayList<String>(3)
        if (opt.downgrade) {
            TrustedSecurity.downgrade(sighting, base)?.let { out += it }
        }
        val oui = factoryOui(mac)
        val learnedOui = net.aps.mapNotNull { it.oui }.toSet()
        val known = net.aps.any { it.bssid.equals(mac, true) }
        if (opt.unknownVendor && !known && oui != null && oui != TOOL_OUI && oui !in learnedOui) {
            out += TrustedSecurity.REASON_UNKNOWN
        }
        if (opt.away && !net.travels && net.places.isNotEmpty() && goodFix(hear) && far(net, hear, opt)) {
            out += TrustedSecurity.REASON_AWAY
        }
        return out
    }

    private fun confidence(net: TrustedNet, hear: TwinHear, reasons: List<String>): Int {
        var level = GnssConfidence.MEDIUM.ordinal
        val sighting = TrustedSecurity.parse(hear.securityText)
        val base = baseline(net)
        if (TrustedSecurity.REASON_OPEN in reasons ||
            (TrustedSecurity.REASON_WEAKER in reasons && sighting.any { it.rank <= 1 } && base.none { it.rank <= 1 })
        ) {
            level = GnssConfidence.HIGH.ordinal
        }
        if (louder(net, hear)) level = (level + 1).coerceAtMost(GnssConfidence.HIGH.ordinal)
        return level
    }

    private fun louder(net: TrustedNet, hear: TwinHear): Boolean {
        if (!goodFix(hear)) return false
        val place = nearest(net.places, hear.lat!!, hear.lon!!) ?: return false
        if (Geo.meters(place.lat, place.lon, hear.lat, hear.lon) > NEAR_M) return false
        val stored = net.aps.mapNotNull { ap ->
            val lat = ap.loudLat ?: return@mapNotNull null
            val lon = ap.loudLon ?: return@mapNotNull null
            if (Geo.meters(place.lat, place.lon, lat, lon) > NEAR_M) null else ap.loudestRssi
        }
        val loudest = stored.maxOrNull() ?: return false
        return hear.rssi >= loudest + LOUDER_DB
    }

    private fun baseline(net: TrustedNet): Set<TrustedSecurity.Mark> =
        net.aps.flatMap { it.security }.toSet()

    private fun learning(net: TrustedNet, opt: TrustedOpt, now: Long): Boolean {
        if (!opt.learn || net.learningPaused) return false
        val window = LearnLeft.trustedWindowMs(opt.learnDays)
        return now - net.trustedAt < window
    }

    private fun goodFix(hear: TwinHear): Boolean {
        val lat = hear.lat ?: return false
        val lon = hear.lon ?: return false
        val acc = hear.accuracyM ?: return false
        return !hear.locationPaused && acc < 100f && lat.isFinite() && lon.isFinite() && abs(lat) <= 90 && abs(lon) <= 180
    }

    private fun nearAny(places: List<UsualPlace>, hear: TwinHear): Boolean {
        if (!goodFix(hear) || places.isEmpty()) return false
        return places.any { Geo.meters(it.lat, it.lon, hear.lat!!, hear.lon!!) <= NEAR_M }
    }

    private fun far(net: TrustedNet, hear: TwinHear, opt: TrustedOpt): Boolean {
        if (!goodFix(hear) || net.places.isEmpty()) return false
        val nearest = net.places.minOf { Geo.meters(it.lat, it.lon, hear.lat!!, hear.lon!!) }
        return nearest > opt.awayMeters
    }

    private fun placeForKnown(places: List<UsualPlace>, hear: TwinHear, now: Long, awayMeters: Int): List<UsualPlace> {
        if (!goodFix(hear)) return places
        if (places.isEmpty()) return addPlace(places, hear.lat!!, hear.lon!!, now)
        val near = places.indexOfFirst { Geo.meters(it.lat, it.lon, hear.lat!!, hear.lon!!) <= NEAR_M }
        if (near >= 0) {
            return places.mapIndexed { i, place ->
                if (i == near) place.copy(lat = hear.lat!!, lon = hear.lon!!, updatedAt = now) else place
            }
        }
        val nearest = places.minOf { Geo.meters(it.lat, it.lon, hear.lat!!, hear.lon!!) }
        if (nearest > awayMeters) return addPlace(places, hear.lat!!, hear.lon!!, now)
        return places
    }

    private fun addPlace(places: List<UsualPlace>, lat: Double, lon: Double, now: Long): List<UsualPlace> {
        val near = places.indexOfFirst { Geo.meters(it.lat, it.lon, lat, lon) <= NEAR_M }
        if (near >= 0) {
            return places.mapIndexed { i, place -> if (i == near) place.copy(lat = lat, lon = lon, updatedAt = now) else place }
        }
        val next = UsualPlace(lat, lon, now)
        if (places.size < PLACE_CAP) return places + next
        val oldest = places.minBy { it.updatedAt }
        return places.map { if (it == oldest) next else it }
    }

    private fun isSibling(net: TrustedNet, mac: String, securityOk: Boolean): Boolean {
        if (!securityOk || MacUtil.isLocallyAdministered(mac)) return false
        val parts = MacUtil.normalize(mac).split(":")
        if (parts.size != 6) return false
        val head = parts.take(5).joinToString(":")
        return net.aps.any { ap ->
            val other = MacUtil.normalize(ap.bssid).split(":")
            other.size == 6 && other.take(5).joinToString(":") == head && !ap.bssid.equals(mac, true)
        }
    }

    private fun sameOui(net: TrustedNet, mac: String): Boolean {
        val oui = factoryOui(mac) ?: return false
        return net.aps.any { it.oui == oui }
    }

    private fun apFrom(hear: TwinHear, now: Long): TrustedAp {
        val mac = MacUtil.normalize(hear.bssid)
        val good = goodFix(hear)
        return TrustedAp(
            bssid = mac,
            vendor = if (factoryOui(mac) == null) null else hear.vendor,
            oui = factoryOui(mac),
            band = bandOf(hear.frequencyMhz),
            channel = hear.channel,
            security = TrustedSecurity.parse(hear.securityText).toList(),
            securityText = hear.securityText?.takeIf { it.isNotBlank() },
            firstSeen = now,
            lastSeen = now,
            loudestRssi = hear.rssi,
            loudLat = if (good) hear.lat else null,
            loudLon = if (good) hear.lon else null,
        )
    }

    private fun mergeAp(aps: List<TrustedAp>, ap: TrustedAp): List<TrustedAp> {
        val old = aps.find { it.bssid.equals(ap.bssid, true) }
        if (old == null) return aps + ap
        return aps.map { if (it.bssid.equals(ap.bssid, true)) {
            it.copy(
                security = (it.security + ap.security).distinct(),
                securityText = ap.securityText ?: it.securityText,
                lastSeen = maxOf(it.lastSeen, ap.lastSeen),
                loudestRssi = maxOf(it.loudestRssi, ap.loudestRssi),
                loudLat = ap.loudLat ?: it.loudLat,
                loudLon = ap.loudLon ?: it.loudLon,
                vendor = it.vendor ?: ap.vendor,
            )
        } else it }
    }

    private fun hearFrom(ep: TwinEpisode, ssid: String) = TwinHear(
        ssid = ssid,
        bssid = ep.bssid,
        rssi = ep.rssi,
        fresh = true,
        securityText = ep.securityText,
        channel = ep.channel,
        frequencyMhz = ep.frequencyMhz,
        vendor = ep.vendor,
        lat = ep.lat,
        lon = ep.lon,
        accuracyM = if (ep.lat != null) 10f else null,
        locationPaused = false,
    )

    private fun close(episodes: List<TwinEpisode>, bssid: String): List<TwinEpisode> =
        episodes.map { if (it.bssid.equals(bssid, true)) it.copy(open = false, dismissed = false) else it }

    private fun touchHeard(net: TrustedNet, mac: String, now: Long, rssi: Int): TrustedNet =
        net.copy(episodes = net.episodes.map { ep ->
            if (ep.open && ep.bssid.equals(mac, true)) ep.copy(lastHeard = now, rssi = maxOf(ep.rssi, rssi)) else ep
        })

    private fun trim(episodes: List<TwinEpisode>): List<TwinEpisode> {
        if (episodes.size <= EPISODE_CAP) return episodes
        val closed = episodes.filter { !it.open }.sortedBy { it.at }
        val drop = episodes.size - EPISODE_CAP
        val doomed = closed.take(drop).map { it.bssid to it.at }.toSet()
        return episodes.filter { (it.bssid to it.at) !in doomed }
    }

    private fun macKey(bssid: String) = MacUtil.normalize(bssid)

    private fun nearest(places: List<UsualPlace>, lat: Double, lon: Double): UsualPlace? =
        places.minByOrNull { Geo.meters(it.lat, it.lon, lat, lon) }

    private fun update(book: TrustedBook, ssid: String, edit: (TrustedNet) -> TrustedNet): TrustedBook =
        book.copy(nets = book.nets.map { if (it.ssid == ssid) edit(it) else it })
}

fun AppSettings.trustedOpt() = TrustedOpt(
    enabled = trustedWatch,
    downgrade = trustedDowngrade,
    unknownVendor = trustedVendor,
    away = trustedAway,
    awayMeters = trustedAwayMeters,
    loudnessFloor = trustedFloorDbm,
    learn = trustedLearn,
    learnDays = trustedLearnDays,
    beep = trustedBeep,
    voice = trustedVoice,
    shade = trustedShade,
    floor = trustedAlertFloor.toConfidence(),
    takPin = trustedTak,
    takHold = trustedTakHold,
)

@Serializable
data class TrustedExportFile(
    val format: String = FORMAT,
    val nets: List<TrustedNet> = emptyList(),
) {
    companion object {
        const val FORMAT = "fieldwatch-trusted"
    }
}

object TrustedExchange {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(book: TrustedBook): String = json.encodeToString(
        TrustedExportFile.serializer(),
        TrustedExportFile(nets = TrustedNets.exported(book).nets),
    )

    /** Null when the file is not a trusted-network export. The caller keeps the list it has. */
    fun parse(text: String): TrustedBook? {
        val file = runCatching { json.decodeFromString(TrustedExportFile.serializer(), text) }.getOrNull() ?: return null
        if (file.format != TrustedExportFile.FORMAT) return null
        return TrustedBook(file.nets)
    }
}
