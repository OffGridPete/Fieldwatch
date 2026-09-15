package app.fieldwatch.domain

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** ATAK CIV CoT input is the default port. SA multicast is 239.2.3.1:6969. */
object TakDefaults {
    const val HOST = "239.2.3.1"
    const val PORT = 10011
    const val MIN_INTERVAL_MS = 10_000L
    const val MOVE_M = 30.0
    const val STALE_MS = 120_000L
    const val MAX_PER_TICK = 24
}

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
     * Advertised decode lat/lon win. Otherwise the operator GPS at last hear,
     * and only while GPS tagging is on. GPS off + no payload = no pin.
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
        watchlist.firstOrNull { it.deviceKey == device.key }
            ?.label?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { return it.take(32) }
        val byId = fleets.associateBy { it.id }
        device.fleetIds.firstOrNull { id -> byId[id]?.attentionNote?.isNotBlank() == true }
            ?.let { id -> byId[id]?.name?.trim()?.takeIf { it.isNotEmpty() }?.let { return it.take(32) } }
        device.fleetIds.firstOrNull()
            ?.let { id -> byId[id]?.name?.trim()?.takeIf { it.isNotEmpty() }?.let { return it.take(32) } }
        val advertised = device.name.trim()
        if (advertised.isNotEmpty() && !advertised.equals(device.mac, ignoreCase = true)) {
            return advertised.take(32)
        }
        return device.mac.takeLast(8)
    }
}

object CotEvent {
    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC)

    fun uid(device: Sighting): String {
        val mac = MacUtil.normalize(device.mac).replace(":", "")
        return "FIELDWATCH-${device.kind.name}-$mac"
    }

    fun type(device: Sighting, fleets: List<Fleet>, advertised: Boolean): String {
        if (advertised && fleets.any { it.id in device.fleetIds && it.kind == SignatureClass.DRONE }) {
            return "a-u-A-M-H-Q"
        }
        return "a-u-G"
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
    ): String {
        val t = TIME.format(Instant.ofEpochMilli(now))
        val stale = TIME.format(Instant.ofEpochMilli(now + staleMs))
        val hae = device.payloadAlt?.takeIf { it.isFinite() } ?: 9999999.0
        val callsign = xmlEscape(TakPublish.callsign(device, fleets, watchlist))
        val remarks = xmlEscape(remarks(device, fleets, advertised))
        val cotType = type(device, fleets, advertised)
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<event version=\"2.0\" uid=\"${uid(device)}\" type=\"$cotType\" ")
            append("time=\"$t\" start=\"$t\" stale=\"$stale\" how=\"m-g\">")
            append("<point lat=\"${fmt(lat)}\" lon=\"${fmt(lon)}\" hae=\"${fmt(hae)}\" ")
            append("ce=\"9999999\" le=\"9999999\"/>")
            append("<detail>")
            append("<contact callsign=\"$callsign\"/>")
            append("<__group name=\"Cyan\" role=\"Team Member\"/>")
            append("<remarks>$remarks</remarks>")
            append("</detail>")
            append("</event>")
        }
    }

    fun remarks(device: Sighting, fleets: List<Fleet>, advertised: Boolean): String {
        val names = fleets.filter { it.id in device.fleetIds }.map { it.name }.distinct()
        val kind = if (device.kind == RadioKind.WIFI) "Wi-Fi" else "BLE"
        val where = if (advertised) "advertised position" else "heard here (operator GPS)"
        return buildString {
            append("Fieldwatch · $kind · ${device.mac} · ${device.rssi} dBm · $where")
            if (names.isNotEmpty()) {
                append(" · ")
                append(names.take(3).joinToString(", "))
            }
            device.attentionNotes(fleets).firstOrNull()?.let { (name, note) ->
                append(" · Extra attention ($name): ")
                append(note.take(120))
            }
        }.take(400)
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
                else -> if (ch.code >= 32) append(ch)
            }
        }
    }
}
