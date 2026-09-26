package app.fieldwatch.domain

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** ATAK CIV CoT input is the default port. SA multicast is 239.2.3.1:6969. UDP only. */
object TakDefaults {
    const val HOST = "239.2.3.1"
    const val PORT = 10011
    const val LOOPBACK = "127.0.0.1"
    const val SA_HOST = "239.2.3.1"
    const val SA_PORT = 6969
    const val MIN_INTERVAL_MS = 10_000L
    const val MOVE_M = 30.0
    const val STALE_MS = 120_000L
    const val MAX_PER_TICK = 24
    const val REMARKS_MAX = 800
}

enum class TakUdpPreset {
    THIS_PHONE,
    LAN_MULTICAST,
    CUSTOM,
}

data class TakFeedStatus(
    val at: Long = 0L,
    val sent: Int = 0,
    val gone: Int = 0,
    val onFeed: Int = 0,
    val dest: String = "",
    val error: String? = null,
    val paused: Boolean = false,
    val detail: String = "",
)

data class TakSent(
    val uid: String,
    val deviceKey: String,
    val at: Long,
    val lat: Double,
    val lon: Double,
    val rssi: Int = Int.MIN_VALUE,
)

enum class TakHeardHere {
    SKIP,
    MOVE,
    REFRESH,
}

data class TakMarker(
    val uid: String,
    val deviceKey: String,
    val lat: Double,
    val lon: Double,
    val advertised: Boolean,
    val pilot: Boolean,
    val device: Sighting,
)

/**
 * Who goes on the TAK/CoT feed and where the pin sits.
 * Pure so unit tests do not need Android.
 */
object TakPublish {
    fun selected(
        device: Sighting,
        settings: AppSettings,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
    ): Boolean {
        if (!settings.takEnabled) return false
        if (settings.demoMode) return false
        val extra = hasAttention(device, fleets)
        val payload = PayloadLocation.validCoord(device.payloadLat, device.payloadLon)
        val watched = onWatchlist(device, watchlist)
        val signed = device.fleetIds.isNotEmpty()
        if (extra && settings.takAttention) return true
        if (payload && settings.takPayloadFix) return true
        if (watched && settings.takWatchlist) return true
        if (signed && settings.takAllSignatures) return true
        return false
    }

    /**
     * Advertised decode lat/lon win. Otherwise this hear’s operator GPS,
     * and only while GPS tagging is on. GPS off + no payload = no pin.
     * Heard-here TAK holds the loudest of these (see [heardHereAction]).
     */
    fun pin(device: Sighting, settings: AppSettings): Pair<Double, Double>? {
        if (PayloadLocation.validCoord(device.payloadLat, device.payloadLon)) {
            return device.payloadLat!! to device.payloadLon!!
        }
        if (!settings.tagLocation) return null
        if (PayloadLocation.validCoord(device.latitude, device.longitude)) {
            return device.latitude!! to device.longitude!!
        }
        return null
    }

    fun pilotPin(device: Sighting): Pair<Double, Double>? {
        if (!PayloadLocation.validCoord(device.payloadOpLat, device.payloadOpLon)) return null
        return device.payloadOpLat!! to device.payloadOpLon!!
    }

    fun eligible(
        device: Sighting,
        settings: AppSettings,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
    ): Boolean = selected(device, settings, fleets, watchlist) && pin(device, settings) != null

    fun advertisedPin(device: Sighting): Boolean =
        PayloadLocation.validCoord(device.payloadLat, device.payloadLon)

    fun rank(
        device: Sighting,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
    ): Int {
        var n = 0
        if (hasAttention(device, fleets)) n += 8
        if (advertisedPin(device)) n += 4
        if (onWatchlist(device, watchlist)) n += 2
        if (device.fleetIds.isNotEmpty()) n += 1
        return n
    }

    fun shouldEmit(
        lastAt: Long?,
        lastLat: Double?,
        lastLon: Double?,
        now: Long,
        lat: Double,
        lon: Double,
        minIntervalMs: Long = TakDefaults.MIN_INTERVAL_MS,
        moveM: Double = TakDefaults.MOVE_M,
    ): Boolean {
        if (lastAt == null || lastLat == null || lastLon == null) return true
        if (now - lastAt >= minIntervalMs) return true
        return Geo.meters(lastLat, lastLon, lat, lon) >= moveM
    }

    /**
     * Heard-here pins sit on operator GPS. Move only when this hear is
     * louder than the last send (closer). Weaker hears still refresh the
     * same lat/lon after [minIntervalMs] so ATAK does not stale-drop.
     */
    fun heardHereAction(
        lastAt: Long?,
        lastRssi: Int?,
        now: Long,
        rssi: Int,
        minIntervalMs: Long = TakDefaults.MIN_INTERVAL_MS,
    ): TakHeardHere {
        if (lastAt == null || lastRssi == null || lastRssi == Int.MIN_VALUE) return TakHeardHere.MOVE
        if (rssi > lastRssi) return TakHeardHere.MOVE
        if (now - lastAt >= minIntervalMs) return TakHeardHere.REFRESH
        return TakHeardHere.SKIP
    }

    fun hasAttention(device: Sighting, fleets: List<Fleet>): Boolean {
        if (device.fleetIds.isEmpty()) return false
        val byId = fleets.associateBy { it.id }
        return device.fleetIds.any { id -> byId[id]?.attentionNote?.isNotBlank() == true }
    }

    fun onWatchlist(device: Sighting, watchlist: List<WatchTarget>): Boolean =
        watchlist.any { target ->
            when {
                target.deviceKey != null -> target.deviceKey == device.key && target.alert
                target.fleetId != null -> target.fleetId in device.fleetIds
                else -> false
            }
        }

    fun callsign(device: Sighting, fleets: List<Fleet>, watchlist: List<WatchTarget>): String {
        val advertised = advertisedPin(device)
        val base = callsignBase(device, fleets, watchlist, advertised)
        if (advertised) return base.take(32)
        val suffix = " (here)"
        return (base.take((32 - suffix.length).coerceAtLeast(1)) + suffix).take(32)
    }

    fun pilotCallsign(device: Sighting): String {
        val id = device.payloadUasId?.trim()?.takeIf { it.isNotEmpty() }
            ?: device.payloadSelfId?.trim()?.takeIf { it.isNotEmpty() }
        return if (id != null) "Pilot · ${id.take(20)}".take(32) else "Pilot".take(32)
    }

    fun markers(
        device: Sighting,
        settings: AppSettings,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
    ): List<TakMarker> {
        if (device.gone) return emptyList()
        if (!selected(device, settings, fleets, watchlist)) return emptyList()
        val aircraft = pin(device, settings) ?: return emptyList()
        val advertised = advertisedPin(device)
        val out = ArrayList<TakMarker>(2)
        out += TakMarker(
            uid = CotEvent.uid(device),
            deviceKey = device.key,
            lat = aircraft.first,
            lon = aircraft.second,
            advertised = advertised,
            pilot = false,
            device = device,
        )
        if (advertised) {
            val pilot = pilotPin(device)
            if (pilot != null) {
                out += TakMarker(
                    uid = CotEvent.pilotUid(device),
                    deviceKey = device.key,
                    lat = pilot.first,
                    lon = pilot.second,
                    advertised = true,
                    pilot = true,
                    device = device,
                )
            }
        }
        return out
    }

    /**
     * UIDs that must stay on ATAK this tick. Includes every eligible marker, not only
     * the 24 we send — missing the cap is not gone. Selected radios with no pin this
     * tick (GPS blip) keep whatever we already sent for that device key.
     */
    fun keepUids(
        devices: List<Sighting>,
        settings: AppSettings,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
        previous: Collection<TakSent>,
        selfUid: String? = null,
        selfOk: Boolean = false,
    ): Set<String> {
        val byKey = previous.groupBy { it.deviceKey }
        val keep = LinkedHashSet<String>()
        if (selfOk && selfUid != null) keep += selfUid
        for (device in devices) {
            if (device.gone) continue
            if (!selected(device, settings, fleets, watchlist)) continue
            val next = markers(device, settings, fleets, watchlist)
            if (next.isNotEmpty()) {
                next.forEach { keep += it.uid }
            } else if (settings.tagLocation) {
                byKey[device.key]?.forEach { keep += it.uid }
            }
        }
        return keep
    }

    fun udpPreset(host: String, port: Int): TakUdpPreset {
        val h = host.trim()
        return when {
            h == TakDefaults.LOOPBACK && port == TakDefaults.PORT -> TakUdpPreset.THIS_PHONE
            (h == TakDefaults.SA_HOST || h == TakDefaults.HOST) && port == TakDefaults.SA_PORT ->
                TakUdpPreset.LAN_MULTICAST
            else -> TakUdpPreset.CUSTOM
        }
    }

    fun applyPreset(preset: TakUdpPreset): Pair<String, Int> = when (preset) {
        TakUdpPreset.THIS_PHONE -> TakDefaults.LOOPBACK to TakDefaults.PORT
        TakUdpPreset.LAN_MULTICAST -> TakDefaults.SA_HOST to TakDefaults.SA_PORT
        TakUdpPreset.CUSTOM -> TakDefaults.HOST to TakDefaults.PORT
    }

    private fun callsignBase(
        device: Sighting,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
        advertised: Boolean,
    ): String {
        watchlist.firstOrNull { it.deviceKey == device.key }
            ?.label?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { return it.take(32) }
        if (advertised) {
            device.payloadSelfId?.trim()?.takeIf { it.isNotEmpty() }?.let { return it.take(32) }
            device.payloadUasId?.trim()?.takeIf { it.isNotEmpty() }?.let { return it.take(32) }
        }
        val byId = fleets.associateBy { it.id }
        device.fleetIds.firstOrNull { id -> byId[id]?.attentionNote?.isNotBlank() == true }
            ?.let { id -> byId[id]?.name?.trim()?.takeIf { it.isNotEmpty() }?.let { return it.take(32) } }
        device.fleetIds.firstOrNull()
            ?.let { id -> byId[id]?.name?.trim()?.takeIf { it.isNotEmpty() }?.let { return it.take(32) } }
        val advertisedName = device.name.trim()
        if (advertisedName.isNotEmpty() && !advertisedName.equals(device.mac, ignoreCase = true)) {
            return advertisedName.take(32)
        }
        return device.mac.takeLast(8)
    }
}

object CotEvent {
    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC)
    private const val SELF_UID = "FIELDWATCH-SELF"

    fun uid(device: Sighting): String {
        stableId(device.payloadUasId)?.let { return "FIELDWATCH-RID-$it" }
        val mac = MacUtil.normalize(device.mac).replace(":", "")
        return "FIELDWATCH-${device.kind.name}-$mac"
    }

    fun pilotUid(device: Sighting): String {
        stableId(device.payloadUasId)?.let { return "FIELDWATCH-PILOT-$it" }
        val mac = MacUtil.normalize(device.mac).replace(":", "")
        return "FIELDWATCH-PILOT-${device.kind.name}-$mac"
    }

    fun selfUid(): String = SELF_UID

    fun stableId(raw: String?): String? {
        val trimmed = raw?.trim()?.trimEnd('\u0000') ?: return null
        val cleaned = buildString(trimmed.length) {
            for (ch in trimmed) {
                when {
                    ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.' -> append(ch)
                    ch == ' ' -> append('-')
                }
            }
        }
        return cleaned.take(24).takeIf { it.length >= 4 }
    }

    fun type(device: Sighting, fleets: List<Fleet>, advertised: Boolean, pilot: Boolean = false): String {
        if (pilot) return "a-u-G"
        if (advertised && fleets.any { it.id in device.fleetIds && it.kind == SignatureClass.DRONE }) {
            return "a-u-A-M-H-Q"
        }
        return "a-u-G"
    }

    fun group(
        device: Sighting,
        fleets: List<Fleet>,
        advertised: Boolean,
        pilot: Boolean = false,
    ): String {
        if (pilot) return "Orange"
        if (advertised && fleets.any { it.id in device.fleetIds && it.kind == SignatureClass.DRONE }) {
            return "Yellow"
        }
        if (!advertised && TakPublish.hasAttention(device, fleets)) return "Maroon"
        return "Cyan"
    }

    fun xml(
        device: Sighting,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
        lat: Double,
        lon: Double,
        advertised: Boolean,
        now: Long,
        staleMs: Long = TakDefaults.STALE_MS,
        pilot: Boolean = false,
    ): String {
        val eventUid = if (pilot) pilotUid(device) else uid(device)
        val cotType = type(device, fleets, advertised, pilot)
        val callsign = xmlEscape(
            if (pilot) TakPublish.pilotCallsign(device)
            else TakPublish.callsign(device, fleets, watchlist),
        )
        val remarks = xmlEscape(remarks(device, fleets, advertised, pilot, watchlist))
        val hae = if (pilot) 9999999.0 else device.payloadAlt?.takeIf { it.isFinite() } ?: 9999999.0
        val course = if (advertised && !pilot) device.payloadHeading?.takeIf { it.isFinite() } else null
        val speed = if (advertised && !pilot) device.payloadSpeed?.takeIf { it.isFinite() && it >= 0 } else null
        val linkUid = when {
            pilot -> uid(device)
            advertised && TakPublish.pilotPin(device) != null -> pilotUid(device)
            else -> null
        }
        val linkType = when {
            linkUid == null -> null
            pilot -> type(device, fleets, advertised, pilot = false)
            else -> type(device, fleets, advertised, pilot = true)
        }
        return eventXml(
            uid = eventUid,
            cotType = cotType,
            lat = lat,
            lon = lon,
            hae = hae,
            course = course,
            speed = speed,
            callsign = callsign,
            remarks = remarks,
            group = group(device, fleets, advertised, pilot),
            now = now,
            staleMs = staleMs,
            staleNow = false,
            linkUid = linkUid,
            linkType = linkType,
        )
    }

    fun tombstoneXml(uid: String, lat: Double, lon: Double, now: Long): String = eventXml(
        uid = uid,
        cotType = "a-u-G",
        lat = lat,
        lon = lon,
        hae = 9999999.0,
        callsign = "Fieldwatch",
        remarks = "Fieldwatch · gone",
        group = "Cyan",
        now = now,
        staleMs = 0L,
        staleNow = true,
        linkUid = null,
        linkType = null,
    )

    fun selfXml(lat: Double, lon: Double, now: Long, staleMs: Long = TakDefaults.STALE_MS): String = eventXml(
        uid = SELF_UID,
        cotType = "a-f-G-U-C",
        lat = lat,
        lon = lon,
        hae = 9999999.0,
        callsign = "Fieldwatch",
        remarks = "Fieldwatch TAK heartbeat (this phone)",
        group = "Cyan",
        now = now,
        staleMs = staleMs,
        staleNow = false,
        linkUid = null,
        linkType = null,
        role = "Team Member",
    )

    fun remarks(
        device: Sighting,
        fleets: List<Fleet>,
        advertised: Boolean,
        pilot: Boolean = false,
        watchlist: List<WatchTarget> = emptyList(),
    ): String {
        val callsign = if (pilot) TakPublish.pilotCallsign(device)
            else TakPublish.callsign(device, fleets, watchlist)
        val names = fleets.filter { it.id in device.fleetIds }.map { it.name }.distinct()
        val kind = if (device.kind == RadioKind.WIFI) "Wi-Fi" else "BLE"
        val where = when {
            pilot -> "operator (pilot) position"
            advertised -> "advertised position"
            else -> "heard here (operator GPS)"
        }
        val radio = buildString {
            append(kind)
            append("  ")
            append(device.mac)
            append("  ")
            append(device.rssi)
            append(" dBm")
            if (device.kind == RadioKind.WIFI && device.channel > 0) {
                append("  ch ")
                append(device.channel)
            } else if (device.kind == RadioKind.WIFI && device.frequencyMhz > 0) {
                append("  ")
                append(device.frequencyMhz)
                append(" MHz")
            }
        }
        val advertisedName = device.name.trim()
        val showName = advertisedName.isNotEmpty() &&
            !advertisedName.equals(device.mac, ignoreCase = true) &&
            !callsign.contains(advertisedName, ignoreCase = true)
        val uas = device.payloadUasId?.trim()?.takeIf { it.isNotEmpty() }
        val sigLine = names.take(3).filter { name ->
            !callsign.contains(name, ignoreCase = true)
        }.joinToString(", ")
        val extra = device.attentionNotes(fleets).firstOrNull()
        return buildString {
            append(callsign)
            append('\n')
            append(radio)
            append('\n')
            append(where)
            if (uas != null) {
                append(" · UAS ID ")
                append(uas.take(24))
            }
            if (showName) {
                append('\n')
                append(advertisedName.take(32))
            }
            if (sigLine.isNotEmpty()) {
                append('\n')
                append(sigLine)
            }
            extra?.let { (name, note) ->
                append('\n')
                append("Extra attention")
                if (!callsign.contains(name, ignoreCase = true) &&
                    !sigLine.contains(name, ignoreCase = true)
                ) {
                    append(" (")
                    append(name)
                    append(")")
                }
                append(": ")
                append(note.take(120))
            }
        }.trimEnd().take(TakDefaults.REMARKS_MAX)
    }

    private fun eventXml(
        uid: String,
        cotType: String,
        lat: Double,
        lon: Double,
        hae: Double,
        course: Double? = null,
        speed: Double? = null,
        callsign: String,
        remarks: String,
        group: String,
        now: Long,
        staleMs: Long,
        staleNow: Boolean,
        linkUid: String?,
        linkType: String?,
        role: String = "Team Member",
    ): String {
        val t = TIME.format(Instant.ofEpochMilli(now))
        val stale = TIME.format(Instant.ofEpochMilli(if (staleNow) now else now + staleMs))
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<event version=\"2.0\" uid=\"${xmlEscape(uid)}\" type=\"$cotType\" ")
            append("time=\"$t\" start=\"$t\" stale=\"$stale\" how=\"m-g\">")
            append("<point lat=\"${fmt(lat)}\" lon=\"${fmt(lon)}\" hae=\"${fmt(hae)}\" ")
            append("ce=\"9999999\" le=\"9999999\"/>")
            append("<detail>")
            append("<contact callsign=\"$callsign\"/>")
            append("<__group name=\"$group\" role=\"$role\"/>")
            if (linkUid != null && linkType != null) {
                append("<link uid=\"${xmlEscape(linkUid)}\" type=\"$linkType\" relation=\"p-p\"/>")
            }
            if (course != null || speed != null) {
                val c = course ?: 0.0
                val s = speed ?: 0.0
                append("<track course=\"${fmt(c)}\" speed=\"${fmt(s)}\"/>")
            }
            append("<remarks>$remarks</remarks>")
            append("</detail>")
            append("</event>")
        }
    }

    private fun fmt(n: Double): String {
        if (!n.isFinite()) return "9999999"
        return "%.7f".format(Locale.US, n).trimEnd('0').trimEnd('.')
    }

    internal fun xmlEscape(raw: String): String = buildString(raw.length + 8) {
        for (ch in raw) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                '\n' -> append("&#10;")
                '\r' -> { }
                else -> if (ch.code >= 32) append(ch)
            }
        }
    }
}
