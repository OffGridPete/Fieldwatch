package app.fieldwatch.radio

import android.util.Log
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.CotEvent
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.TakDefaults
import app.fieldwatch.domain.TakPublish
import app.fieldwatch.domain.WatchTarget
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
    private val last = ConcurrentHashMap<String, Sent>()
    private val gate = Any()
    @Volatile private var multicast: MulticastSocket? = null
    @Volatile private var unicast: DatagramSocket? = null

    fun publish(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        settings: AppSettings,
        watchlist: List<WatchTarget>,
        now: Long = System.currentTimeMillis(),
        selfFix: Pair<Double, Double>? = null,
    ) {
        if (!settings.takEnabled || settings.demoMode) {
            last.clear()
            return
        }
        val host = settings.takHost.trim().ifBlank { TakDefaults.HOST }
        val port = settings.takPort.coerceIn(1, 65_535)
        val dests = destinations(host, port)
        if (dests.isEmpty()) {
            Log.w(TAG, "TAK host $host did not resolve")
            return
        }
        var sent = 0
        if (selfFix != null && PayloadOk(selfFix)) {
            val prev = last[SELF_UID]
            if (TakPublish.shouldEmit(prev?.at, prev?.lat, prev?.lon, now, selfFix.first, selfFix.second)) {
                val xml = selfXml(selfFix.first, selfFix.second, now)
                if (sendAll(dests, xml.toByteArray(Charsets.UTF_8))) {
                    last[SELF_UID] = Sent(now, selfFix.first, selfFix.second)
                    sent++
                }
            }
        }
        val chosen = devices.asSequence()
            .filter { TakPublish.eligible(it, settings, fleets, watchlist) }
            .sortedWith(
                compareByDescending<Sighting> { TakPublish.rank(it, fleets, watchlist) }
                    .thenByDescending { it.rssi },
            )
            .take(TakDefaults.MAX_PER_TICK)
            .toList()
        if (chosen.isEmpty() && sent == 0) {
            Log.i(TAG, "TAK on, 0 eligible radios (need Extra attention / payload latlon / GPS stamp)")
        }
        for (device in chosen) {
            val pin = TakPublish.pin(device, settings) ?: continue
            val prev = last[device.key]
            if (!TakPublish.shouldEmit(prev?.at, prev?.lat, prev?.lon, now, pin.first, pin.second)) {
                continue
            }
            val advertised = TakPublish.advertisedPin(device)
            val xml = CotEvent.xml(
                device = device,
                fleets = fleets,
                watchlist = watchlist,
                lat = pin.first,
                lon = pin.second,
                advertised = advertised,
                now = now,
            )
            if (!sendAll(dests, xml.toByteArray(Charsets.UTF_8))) continue
            last[device.key] = Sent(now, pin.first, pin.second)
            sent++
        }
        if (sent > 0) Log.i(TAG, "TAK sent $sent marker(s) to ${dests.joinToString { "${it.first.hostAddress}:${it.second}" }}")
    }

    fun close() {
        synchronized(gate) {
            runCatching { multicast?.close() }
            runCatching { unicast?.close() }
            multicast = null
            unicast = null
        }
        last.clear()
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

    private fun selfXml(lat: Double, lon: Double, now: Long): String {
        val t = java.time.Instant.ofEpochMilli(now).atOffset(java.time.ZoneOffset.UTC)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))
        val stale = java.time.Instant.ofEpochMilli(now + TakDefaults.STALE_MS).atOffset(java.time.ZoneOffset.UTC)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<event version=\"2.0\" uid=\"$SELF_UID\" type=\"a-f-G-U-C\" ")
            append("time=\"$t\" start=\"$t\" stale=\"$stale\" how=\"m-g\">")
            append("<point lat=\"$lat\" lon=\"$lon\" hae=\"9999999\" ce=\"9999999\" le=\"9999999\"/>")
            append("<detail>")
            append("<contact callsign=\"Fieldwatch\"/>")
            append("<__group name=\"Cyan\" role=\"Team Member\"/>")
            append("<remarks>Fieldwatch TAK heartbeat (this phone)</remarks>")
            append("</detail>")
            append("</event>")
        }
    }

    private data class Sent(val at: Long, val lat: Double, val lon: Double)

    companion object {
        private const val TAG = "FieldwatchTak"
        private const val SELF_UID = "FIELDWATCH-SELF"
        private fun PayloadOk(fix: Pair<Double, Double>): Boolean =
            app.fieldwatch.domain.PayloadLocation.validCoord(fix.first, fix.second)
    }
}
