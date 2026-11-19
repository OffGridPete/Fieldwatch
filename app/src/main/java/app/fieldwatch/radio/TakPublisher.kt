package app.fieldwatch.radio

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.CotEvent
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.GnssLive
import app.fieldwatch.domain.GnssNotice
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.TakBirth
import app.fieldwatch.domain.TakDefaults
import app.fieldwatch.domain.TakFeedStatus
import app.fieldwatch.domain.TakLoopbackFacts
import app.fieldwatch.domain.TakGnss
import app.fieldwatch.domain.TakGnssTrack
import app.fieldwatch.domain.TakHeardHere
import app.fieldwatch.domain.TakPublish
import app.fieldwatch.domain.TakSent
import app.fieldwatch.domain.WatchTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import app.fieldwatch.domain.TakInstall
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sends Cursor-on-Target UDP to ATAK SA multicast or a unicast host:port.
 * Same-phone ATAK often ignores multicast loopback, so unicast also hits
 * 127.0.0.1 on the same port. A loopback socket opened with no Wi-Fi or
 * Ethernet is replaced once either is connected, including when cellular
 * was already up. One opened on that LAN stays after the LAN drops. A unicast or multicast
 * socket opened with no network is replaced once a network exists. A socket
 * opened on a network is kept after that network drops. Privacy mode skips the feed.
 */
class TakPublisher(context: Context? = null) {
    private val appContext = context?.applicationContext
    private val last = ConcurrentHashMap<String, TakSent>()
    /** Radios that left the feed and were resent once with the spot hold. */
    private val parked = ConcurrentHashMap<String, TakSent>()
    private val gnssMarks = LinkedHashMap<String, TakGnssTrack>()
    /** Tags replaced this session. A late tick must not put those spots back. */
    private val retiredTags = ConcurrentHashMap.newKeySet<String>()
    private val legacySelfSent = AtomicBoolean(false)
    private val gate = Any()
    @Volatile private var multicast: MulticastSocket? = null
    @Volatile private var unicast: DatagramSocket? = null
    @Volatile private var loopback: DatagramSocket? = null
    /** True when that socket was opened while the phone had no network. */
    private var multicastOffline = false
    private var unicastOffline = false
    /** True when the loopback socket was opened while Wi-Fi or Ethernet was connected. */
    private var loopbackOnLan = false
    private var loopbackBirth = TakBirth.NONE
    /** Null until a datagram send is attempted. */
    private var lastSendOk: Boolean? = null
    private val _status = MutableStateFlow(TakFeedStatus())
    val status: StateFlow<TakFeedStatus> = _status.asStateFlow()

    fun publish(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        settings: AppSettings,
        watchlist: List<WatchTarget>,
        installTag: String,
        legacySelfRetired: Boolean,
        now: Long = System.currentTimeMillis(),
        selfFix: Pair<Double, Double>? = null,
        gnssRemark: String? = null,
        gnssNotice: GnssNotice? = null,
        gnssLives: List<GnssLive> = emptyList(),
        swarmNote: (Sighting) -> String? = { null },
    ): Boolean {
        if (!settings.takEnabled || settings.demoMode) {
            retractRadios(settings, now)
            if (last.isEmpty() && parked.isEmpty()) gnssMarks.clear()
            _status.value = if (!settings.takEnabled) {
                TakFeedStatus(detail = "Off")
            } else {
                TakFeedStatus(paused = true, detail = "Privacy mode — feed paused")
            }
            return false
        }
        val tag = TakInstall.clean(installTag)
        if (tag.isEmpty()) {
            Log.w(TAG, "TAK install tag is missing")
            _status.value = TakFeedStatus(at = now, error = "Install tag is missing")
            return false
        }
        val phoneCallsign = TakInstall.callsign(settings.takCallsign, tag)
        val selfUid = CotEvent.selfUid(tag)
        val host = settings.takHost.trim().ifBlank { TakDefaults.HOST }
        val port = settings.takPort.coerceIn(1, 65_535)
        val dests = destinations(host, port)
        if (dests.isEmpty()) {
            Log.w(TAG, "TAK host $host did not resolve")
            _status.value = TakFeedStatus(
                at = now,
                dest = "$host:$port",
                error = "Host $host did not resolve",
            )
            return false
        }
        val destLabel = dests.joinToString { "${it.first.hostAddress}:${it.second}" }
        fun current(): Boolean = tag !in retiredTags
        val selfOk = selfFix != null && PayloadOk(selfFix)
        var sent = 0
        var gone = 0
        var lastErr: String? = null
        var legacyJustSent = false

        if (selfOk && current()) {
            val legacyUid = TakInstall.legacySelfDelete(
                retired = legacySelfRetired || legacySelfSent.get(),
                takEnabled = true,
                privacy = false,
            )
            if (legacyUid != null) {
                val xml = CotEvent.tombstoneXml(
                    uid = legacyUid,
                    lat = selfFix!!.first,
                    lon = selfFix.second,
                    now = now,
                    callsign = "Fieldwatch",
                    remarks = "Fieldwatch · gone",
                )
                if (sendAll(dests, xml.toByteArray(Charsets.UTF_8))) {
                    if (legacySelfSent.compareAndSet(false, true)) legacyJustSent = true
                    gone++
                } else {
                    lastErr = "send failed"
                }
            }
        }

        if (selfOk && current()) {
            val prev = last[selfUid]
            val gnssLine = gnssRemark?.trim().orEmpty()
            val noteChanged = (prev?.note ?: "") != gnssLine
            if (noteChanged || TakPublish.shouldEmit(prev?.at, prev?.lat, prev?.lon, now, selfFix!!.first, selfFix.second)) {
                val xml = CotEvent.selfXml(
                    lat = selfFix.first,
                    lon = selfFix.second,
                    now = now,
                    tag = tag,
                    callsign = phoneCallsign,
                    remarksExtra = gnssLine.ifBlank { null },
                )
                if (sendAll(dests, xml.toByteArray(Charsets.UTF_8)) && current()) {
                    last[selfUid] = TakSent(
                        selfUid,
                        selfUid,
                        now,
                        selfFix.first,
                        selfFix.second,
                        note = gnssLine,
                    )
                    sent++
                } else if (!current()) {
                    last.remove(selfUid)
                } else {
                    lastErr = "send failed"
                }
            }
        }

        val gnssFix = if (selfOk) selfFix else null
        if (current()) {
        val gnssPlan = TakGnss.plan(
            open = gnssMarks.values.firstOrNull { it.open },
            mode = settings.takGnss,
            notice = gnssNotice,
            lives = gnssLives,
            lat = gnssFix?.first,
            lon = gnssFix?.second,
            now = now,
            hold = settings.takGnssHold,
            tag = tag,
            hasMarks = gnssMarks.isNotEmpty(),
        )
        if (gnssPlan.clearAll) {
            for (mark in gnssMarks.values) {
                val xml = CotEvent.tombstoneXml(
                    uid = mark.uid,
                    lat = mark.startLat,
                    lon = mark.startLon,
                    now = now,
                    callsign = TakGnss.CALLOUT,
                    remarks = "Where this phone was. Not the source.",
                )
                if (sendAll(dests, xml.toByteArray(Charsets.UTF_8))) {
                    gone++
                } else {
                    lastErr = "send failed"
                }
            }
            if (lastErr == null) gnssMarks.clear()
        } else {
            var gnssOk = true
            val drawn = gnssPlan.plot
            val gnssUid = gnssPlan.track?.uid
            if (gnssOk && current() && gnssPlan.send && drawn != null && gnssUid != null) {
                val xml = if (drawn.pin) {
                    CotEvent.gnssPinXml(
                        uid = gnssUid,
                        lat = drawn.lat,
                        lon = drawn.lon,
                        now = now,
                        staleMs = gnssPlan.staleMs,
                        remarks = gnssPlan.remarks,
                    )
                } else {
                    CotEvent.gnssOvalXml(
                        uid = gnssUid,
                        lat = drawn.lat,
                        lon = drawn.lon,
                        semiMajorM = drawn.semiMajorM,
                        semiMinorM = drawn.semiMinorM,
                        angleDeg = drawn.angleDeg,
                        now = now,
                        staleMs = gnssPlan.staleMs,
                        remarks = gnssPlan.remarks,
                    )
                }
                if (sendAll(dests, xml.toByteArray(Charsets.UTF_8))) {
                    sent++
                } else {
                    gnssOk = false
                    lastErr = "send failed"
                }
            }
            if (gnssOk && current()) {
                val track = gnssPlan.track
                if (track != null) gnssMarks[track.uid] = track
            }
        }
        }

        val chosen = devices.asSequence()
            .filter { TakPublish.eligible(it, settings, fleets, watchlist) }
            .sortedWith(
                compareByDescending<Sighting> { TakPublish.rank(it, fleets, watchlist) }
                    .thenByDescending { it.rssi },
            )
            .toList()
        var emitted = sent
        for (device in chosen) {
            if (emitted >= TakDefaults.MAX_PER_TICK) break
            val marks = TakPublish.markers(device, settings, fleets, watchlist, tag)
            for (mark in marks) {
                if (emitted >= TakDefaults.MAX_PER_TICK) break
                val prev = last[mark.uid]
                val lat: Double
                val lon: Double
                val peakRssi: Int
                if (mark.advertised || mark.pilot) {
                    if (!TakPublish.shouldEmit(prev?.at, prev?.lat, prev?.lon, now, mark.lat, mark.lon)) {
                        continue
                    }
                    lat = mark.lat
                    lon = mark.lon
                    peakRssi = mark.device.rssi
                } else {
                    when (TakPublish.heardHereAction(prev?.at, prev?.rssi, now, mark.device.rssi)) {
                        TakHeardHere.SKIP -> continue
                        TakHeardHere.MOVE -> {
                            lat = mark.lat
                            lon = mark.lon
                            peakRssi = mark.device.rssi
                        }
                        TakHeardHere.REFRESH -> {
                            val held = prev ?: continue
                            lat = held.lat
                            lon = held.lon
                            peakRssi = held.rssi
                        }
                    }
                }
                if (!current() && TakInstall.phoneScoped(mark.uid, tag)) continue
                val face = CotEvent.face(
                    device = mark.device,
                    fleets = fleets,
                    watchlist = watchlist,
                    advertised = mark.advertised,
                    tag = tag,
                    pilot = mark.pilot,
                    viaCallsign = TakInstall.viaLine(mark.uid, tag, phoneCallsign),
                    swarmNote = swarmNote(mark.device),
                )
                val xml = CotEvent.render(
                    uid = mark.uid,
                    face = face,
                    lat = lat,
                    lon = lon,
                    now = now,
                    staleMs = TakDefaults.STALE_MS,
                )
                if (!sendAll(dests, xml.toByteArray(Charsets.UTF_8))) {
                    lastErr = "send failed"
                    continue
                }
                if (!current() && TakInstall.phoneScoped(mark.uid, tag)) continue
                last[mark.uid] = TakSent(
                    mark.uid,
                    mark.deviceKey,
                    now,
                    lat,
                    lon,
                    peakRssi,
                    face = face,
                    staleMs = TakDefaults.STALE_MS,
                )
                parked.remove(mark.uid)
                sent++
                emitted++
            }
        }

        val keep = TakPublish.keepUids(
            devices = devices,
            settings = settings,
            fleets = fleets,
            watchlist = watchlist,
            previous = last.values,
            tag = tag,
            selfUid = selfUid,
            selfOk = selfOk && current(),
        )
        val holdMs = settings.takRadioHold.staleMs()
        var holdBudget = TakDefaults.MAX_PER_TICK
        for (uid in last.keys.filter { it !in keep }) {
            val prev = last[uid] ?: continue
            val face = prev.face
            if (uid == selfUid || face == null || (!current() && TakInstall.phoneScoped(uid, tag))) {
                last.remove(uid)
                continue
            }
            if (holdBudget <= 0) break
            val xml = CotEvent.render(uid, face, prev.lat, prev.lon, now, holdMs)
            if (sendAll(dests, xml.toByteArray(Charsets.UTF_8))) {
                last.remove(uid)
                parked[uid] = prev.copy(at = now, staleMs = holdMs)
                sent++
                holdBudget--
            } else {
                lastErr = "send failed"
                break
            }
        }
        for (spot in parked.values.toList()) {
            if (!current() && TakInstall.phoneScoped(spot.uid, tag)) {
                parked.remove(spot.uid)
                continue
            }
            if (spot.uid in keep || spot.staleMs == holdMs) continue
            val face = spot.face
            if (face == null) {
                parked.remove(spot.uid)
                continue
            }
            if (holdBudget <= 0) break
            val xml = CotEvent.render(spot.uid, face, spot.lat, spot.lon, now, holdMs)
            if (sendAll(dests, xml.toByteArray(Charsets.UTF_8))) {
                parked[spot.uid] = spot.copy(at = now, staleMs = holdMs)
                sent++
                holdBudget--
            } else {
                lastErr = "send failed"
                break
            }
        }

        if (sent == 0 && gone == 0 && lastErr == null && chosen.isEmpty() && !selfOk) {
            Log.i(TAG, "TAK on, 0 eligible radios (need Extra attention / payload latlon / GPS stamp)")
        }
        if (sent > 0 || gone > 0) {
            Log.i(TAG, "TAK sent $sent marker(s), $gone gone to $destLabel")
        }
        val detail = when {
            lastErr != null -> lastErr
            sent == 0 && gone == 0 && chosen.isEmpty() && !selfOk ->
                "0 eligible radios (need Extra attention / payload latlon / GPS stamp)"
            else -> ""
        }
        if (!current()) {
            last.keys.filter { TakInstall.phoneScoped(it, tag) }.forEach { last.remove(it) }
            parked.keys.filter { TakInstall.phoneScoped(it, tag) }.forEach { parked.remove(it) }
        }
        _status.value = TakFeedStatus(
            at = now,
            sent = sent,
            gone = gone,
            onFeed = last.size,
            dest = destLabel,
            error = lastErr,
            detail = detail,
        )
        return legacyJustSent
    }

    /**
     * Drop spots this session still has for [tag], then the caller saves a new tag.
     * Shared Remote ID markers are left alone. Privacy and a feed that is off do not call this.
     */
    fun retireTag(tag: String, callsign: String, settings: AppSettings, now: Long = System.currentTimeMillis()) {
        val mark = TakInstall.clean(tag)
        if (mark.isEmpty()) return
        retiredTags.add(mark)
        if (!settings.takEnabled || settings.demoMode) return
        val host = settings.takHost.trim().ifBlank { TakDefaults.HOST }
        val port = settings.takPort.coerceIn(1, 65_535)
        val dests = destinations(host, port)
        if (dests.isEmpty()) return
        val spots = LinkedHashMap<String, TakSent>()
        last.forEach { (uid, spot) -> if (TakInstall.phoneScoped(uid, mark)) spots[uid] = spot }
        parked.forEach { (uid, spot) -> if (TakInstall.phoneScoped(uid, mark)) spots.putIfAbsent(uid, spot) }
        for ((uid, spot) in spots) {
            val face = spot.face
            val xml = if (face != null) {
                CotEvent.tombstoneXml(uid, spot.lat, spot.lon, now, face.callsign, face.remarks)
            } else {
                CotEvent.tombstoneXml(uid, spot.lat, spot.lon, now, callsign, "Fieldwatch · gone")
            }
            if (!sendAll(dests, xml.toByteArray(Charsets.UTF_8))) return
            last.remove(uid)
            parked.remove(uid)
        }
        val gnss = synchronized(gate) {
            gnssMarks.values.filter { TakInstall.phoneScoped(it.uid, mark) }.toList()
        }
        for (held in gnss) {
            val xml = CotEvent.tombstoneXml(
                uid = held.uid,
                lat = held.startLat,
                lon = held.startLon,
                now = now,
                callsign = TakGnss.CALLOUT,
                remarks = "Where this phone was. Not the source.",
            )
            if (!sendAll(dests, xml.toByteArray(Charsets.UTF_8))) return
            synchronized(gate) { gnssMarks.remove(held.uid) }
        }
    }

    /** No-op while TAK is off or Privacy mode has paused the feed. */
    fun sendNow(settings: AppSettings, packets: List<String>): Boolean {
        if (!settings.takEnabled || settings.demoMode || packets.isEmpty()) return false
        val host = settings.takHost.trim().ifBlank { TakDefaults.HOST }
        val port = settings.takPort.coerceIn(1, 65_535)
        val dests = destinations(host, port)
        if (dests.isEmpty()) return false
        for (xml in packets) {
            if (!sendAll(dests, xml.toByteArray(Charsets.UTF_8))) return false
        }
        return true
    }

    fun close() {
        synchronized(gate) {
            runCatching { multicast?.close() }
            runCatching { unicast?.close() }
            runCatching { loopback?.close() }
            multicast = null
            unicast = null
            loopback = null
            multicastOffline = false
            unicastOffline = false
            loopbackOnLan = false
            loopbackBirth = TakBirth.NONE
        }
        last.clear()
        parked.clear()
        gnssMarks.clear()
        _status.value = TakFeedStatus(detail = "Off")
    }

    /** Feed off or Privacy. Drop radio spots under their own names. GNSS marks are left for ATAK. */
    private fun retractRadios(settings: AppSettings, now: Long) {
        if (last.isEmpty() && parked.isEmpty()) return
        val host = settings.takHost.trim().ifBlank { TakDefaults.HOST }
        val port = settings.takPort.coerceIn(1, 65_535)
        val dests = destinations(host, port)
        if (dests.isEmpty()) return
        val spots = LinkedHashMap<String, TakSent>()
        last.forEach { (uid, spot) -> spots[uid] = spot }
        parked.forEach { (uid, spot) -> spots.putIfAbsent(uid, spot) }
        for ((uid, spot) in spots) {
            val face = spot.face
            val xml = if (face != null) {
                CotEvent.tombstoneXml(uid, spot.lat, spot.lon, now, face.callsign, face.remarks)
            } else {
                CotEvent.tombstoneXml(uid, spot.lat, spot.lon, now)
            }
            if (!sendAll(dests, xml.toByteArray(Charsets.UTF_8))) return
            last.remove(uid)
            parked.remove(uid)
        }
    }

    private fun destinations(host: String, port: Int): List<Pair<InetAddress, Int>> {
        val primary = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return emptyList()
        val out = LinkedHashMap<String, Pair<InetAddress, Int>>()
        fun add(addr: InetAddress, p: Int) {
            val key = "${addr.hostAddress}:$p"
            out.putIfAbsent(key, addr to p)
        }
        add(primary, port)
        if (!primary.isLoopbackAddress) {
            runCatching { InetAddress.getByName("127.0.0.1") }.getOrNull()?.let { add(it, port) }
        }
        return out.values.toList()
    }

    private fun sendAll(dests: List<Pair<InetAddress, Int>>, bytes: ByteArray): Boolean {
        var ok = false
        for ((addr, port) in dests) {
            if (send(addr, port, bytes)) ok = true
        }
        return ok
    }

    private fun send(addr: InetAddress, port: Int, bytes: ByteArray): Boolean {
        return synchronized(gate) {
            runCatching {
                val packet = DatagramPacket(bytes, bytes.size, addr, port)
                when {
                    addr.isMulticastAddress -> {
                        val sock = multicastSock()
                        runCatching { sock.timeToLive = 1 }
                        sock.send(packet)
                    }
                    addr.isLoopbackAddress -> loopbackSock().send(packet)
                    else -> unicastSock().send(packet)
                }
                true
            }.onFailure { err ->
                Log.w(TAG, "TAK send ${addr.hostAddress}:$port failed: $err")
            }.also { result ->
                lastSendOk = result.getOrDefault(false)
            }.getOrDefault(false)
        }
    }

    /** Same-phone socket facts for Diagnostics. No host address. */
    fun loopbackFacts(): TakLoopbackFacts = synchronized(gate) {
        val sock = loopback
        val open = sock != null && !sock.isClosed
        TakLoopbackFacts(
            open = open,
            birth = if (open) loopbackBirth else null,
            lastSendOk = lastSendOk,
        )
    }

    private fun multicastSock(): MulticastSocket = synchronized(gate) {
        val online = networkUp()
        val open = multicast
        if (open != null && !open.isClosed && !replaceTakSocket(multicastOffline, online)) {
            return open
        }
        if (open != null) runCatching { open.close() }
        MulticastSocket().also {
            it.reuseAddress = true
            runCatching { it.timeToLive = 1 }
            multicast = it
            multicastOffline = !online
        }
    }

    private fun unicastSock(): DatagramSocket = synchronized(gate) {
        val online = networkUp()
        val open = unicast
        if (open != null && !open.isClosed && !replaceTakSocket(unicastOffline, online)) {
            return open
        }
        if (open != null) runCatching { open.close() }
        DatagramSocket().also {
            it.reuseAddress = true
            unicast = it
            unicastOffline = !online
        }
    }

    /**
     * 127.0.0.1. A socket opened without Wi-Fi or Ethernet is replaced once
     * either connects, including when cellular was already up. Android does
     * not let an app pin this socket to lo, so cellular alone does not deliver.
     */
    private fun loopbackSock(): DatagramSocket = synchronized(gate) {
        val nets = networks()
        val open = loopback
        val replace = replaceLoopbackSocket(loopbackOnLan, nets.lan)
        if (open != null && !open.isClosed && !replace) return open
        if (open != null) runCatching { open.close() }
        val sock = DatagramSocket(null)
        sock.reuseAddress = true
        try {
            sock.bind(InetSocketAddress(InetAddress.getByAddress(LOOPBACK_V4), 0))
        } catch (err: Exception) {
            runCatching { sock.close() }
            throw err
        }
        loopback = sock
        loopbackOnLan = nets.lan
        loopbackBirth = nets.birth
        sock
    }

    private data class NetFacts(val lan: Boolean, val birth: TakBirth)

    /** Every connected network. A Wi-Fi link with no internet still counts. */
    private fun networks(): NetFacts {
        val context = appContext ?: return NetFacts(lan = false, birth = TakBirth.NONE)
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetFacts(lan = false, birth = TakBirth.NONE)
        var wifi = false
        var ethernet = false
        var cellular = false
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) cellular = true
        }
        return NetFacts(
            lan = takLanUp(wifi, ethernet),
            birth = takBirth(wifi, ethernet, cellular),
        )
    }

    private fun networkUp(): Boolean {
        val context = appContext ?: return false
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    companion object {
        private const val TAG = "FieldwatchTak"
        private val LOOPBACK_V4 = byteArrayOf(127, 0, 0, 1)
        private fun PayloadOk(fix: Pair<Double, Double>): Boolean =
            app.fieldwatch.domain.PayloadLocation.validCoord(fix.first, fix.second)
    }
}
