package app.fieldwatch.radio

import android.util.Log
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.CotEvent
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.TakDefaults
import app.fieldwatch.domain.TakFeedStatus
import app.fieldwatch.domain.TakHeardHere
import app.fieldwatch.domain.TakPublish
import app.fieldwatch.domain.TakSent
import app.fieldwatch.domain.WatchTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket
import java.util.concurrent.ConcurrentHashMap

/**
 * Sends Cursor-on-Target UDP to ATAK SA multicast or a unicast host:port.
 * Same-phone ATAK often ignores multicast loopback, so unicast also hits
 * 127.0.0.1 on the same port. Privacy mode skips the feed.
 */
class TakPublisher {
    private val last = ConcurrentHashMap<String, TakSent>()
    private val gate = Any()
    @Volatile private var multicast: MulticastSocket? = null
    @Volatile private var unicast: DatagramSocket? = null
    private val _status = MutableStateFlow(TakFeedStatus())
    val status: StateFlow<TakFeedStatus> = _status.asStateFlow()

    fun publish(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        settings: AppSettings,
        watchlist: List<WatchTarget>,
        now: Long = System.currentTimeMillis(),
        selfFix: Pair<Double, Double>? = null,
    ) {
        if (!settings.takEnabled) {
            last.clear()
            _status.value = TakFeedStatus(detail = "Off")
            return
        }
        if (settings.demoMode) {
            last.clear()
            _status.value = TakFeedStatus(paused = true, detail = "Privacy mode — feed paused")
            return
        }
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
            return
        }
        val destLabel = dests.joinToString { "${it.first.hostAddress}:${it.second}" }
        val selfOk = selfFix != null && PayloadOk(selfFix)
        var sent = 0
        var gone = 0
        var lastErr: String? = null

        if (selfOk) {
            val prev = last[SELF_UID]
            if (TakPublish.shouldEmit(prev?.at, prev?.lat, prev?.lon, now, selfFix!!.first, selfFix.second)) {
                val xml = CotEvent.selfXml(selfFix.first, selfFix.second, now)
                if (sendAll(dests, xml.toByteArray(Charsets.UTF_8))) {
                    last[SELF_UID] = TakSent(SELF_UID, SELF_UID, now, selfFix.first, selfFix.second)
                    sent++
                } else {
                    lastErr = "send failed"
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
            val marks = TakPublish.markers(device, settings, fleets, watchlist)
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
                val xml = CotEvent.xml(
                    device = mark.device,
                    fleets = fleets,
                    watchlist = watchlist,
                    lat = lat,
                    lon = lon,
                    advertised = mark.advertised,
                    now = now,
                    pilot = mark.pilot,
                )
                if (!sendAll(dests, xml.toByteArray(Charsets.UTF_8))) {
                    lastErr = "send failed"
                    continue
                }
                last[mark.uid] = TakSent(mark.uid, mark.deviceKey, now, lat, lon, peakRssi)
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
            selfUid = SELF_UID,
            selfOk = selfOk,
        )
        val dead = last.keys.filter { it !in keep }.take(TakDefaults.MAX_PER_TICK)
        for (uid in dead) {
            val prev = last[uid] ?: continue
            val xml = CotEvent.tombstoneXml(uid, prev.lat, prev.lon, now)
            if (sendAll(dests, xml.toByteArray(Charsets.UTF_8))) {
                last.remove(uid)
                gone++
            } else {
                lastErr = "send failed"
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
        _status.value = TakFeedStatus(
            at = now,
            sent = sent,
            gone = gone,
            onFeed = last.size,
            dest = destLabel,
            error = lastErr,
            detail = detail,
        )
    }

    fun close() {
        synchronized(gate) {
            runCatching { multicast?.close() }
            runCatching { unicast?.close() }
            multicast = null
            unicast = null
        }
        last.clear()
        _status.value = TakFeedStatus(detail = "Off")
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
                if (addr.isMulticastAddress) {
                    val sock = multicastSock()
                    runCatching { sock.timeToLive = 1 }
                    sock.send(packet)
                } else {
                    unicastSock().send(packet)
                }
                true
            }.onFailure { err ->
                Log.w(TAG, "TAK send ${addr.hostAddress}:$port failed: $err")
            }.getOrDefault(false)
        }
    }

    private fun multicastSock(): MulticastSocket = synchronized(gate) {
        val open = multicast
        if (open != null && !open.isClosed) return open
        MulticastSocket().also {
            it.reuseAddress = true
            runCatching { it.timeToLive = 1 }
            multicast = it
        }
    }

    private fun unicastSock(): DatagramSocket = synchronized(gate) {
        val open = unicast
        if (open != null && !open.isClosed) return open
        DatagramSocket().also {
            it.reuseAddress = true
            unicast = it
        }
    }

    companion object {
        private const val TAG = "FieldwatchTak"
        private const val SELF_UID = "FIELDWATCH-SELF"
        private fun PayloadOk(fix: Pair<Double, Double>): Boolean =
            app.fieldwatch.domain.PayloadLocation.validCoord(fix.first, fix.second)
    }
}
