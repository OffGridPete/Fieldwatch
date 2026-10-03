package app.fieldwatch.domain

/**
 * ASTM F3411 / OpenDroneID packed messages from BLE FFFA or Wi-Fi vendor IE
 * FA:0B:BC type 0x0D. Location, Basic ID, System, Self ID. Heading follows
 * opendroneid.c (direction byte + EW flag), not the catalog *2 scale.
 */
object OpenDroneId {
    const val BLE_UUID = "FFFA"
    const val WIFI_OUI = "FA:0B:BC"
    const val WIFI_TYPE = 0x0D
    private const val MSG = 25
    private const val INV_DIR = 255
    private const val INV_SPEED = 255
    /** ASTM Basic ID id_type. 1 is a CTA-2063 serial. Session and CAA are not. */
    private const val ID_SERIAL = 1

    fun fromFacts(facts: RadioFacts): PayloadLocation {
        var acc = PayloadLocation()
        for (sd in facts.serviceData) {
            if (uuid16(sd.uuid) != 0xFFFA) continue
            acc = parseMessages(messagesBle(sd.dataHex)).mergeSticky(acc)
        }
        for (ie in facts.vendorIes) {
            if (!ie.oui.equals(WIFI_OUI, ignoreCase = true) || ie.type != WIFI_TYPE) continue
            acc = parseMessages(messagesWifi(ie.dataHex)).mergeSticky(acc)
        }
        return acc
    }

    /**
     * Wi-Fi vendor IE payloads framed as BLE FFFA ads: `0x0D` + counter + each
     * 25-byte ASTM message. Stock Remote ID Decode fields maps then apply.
     */
    fun wifiFffaPayloads(facts: RadioFacts): List<String> {
        val out = ArrayList<String>()
        for (ie in facts.vendorIes) {
            if (!ie.oui.equals(WIFI_OUI, ignoreCase = true) || ie.type != WIFI_TYPE) continue
            out += wrapWifiAsFffa(ie.dataHex)
        }
        return out
    }

    internal fun wrapWifiAsFffa(dataHex: String): List<String> {
        val b = hex(dataHex) ?: return emptyList()
        if (b.isEmpty()) return emptyList()
        val bleShaped = b.size >= 2 + MSG && b[0] == 0x0D.toByte()
        val start = if (bleShaped) 2 else 1
        if (start > b.size) return emptyList()
        val counter = (if (bleShaped) b[1] else b[0]).toInt() and 0xFF
        return framedMessages(b, start).map { msg ->
            buildString(2 + 2 + msg.size * 2) {
                append("0D")
                append("%02X".format(counter))
                for (byte in msg) append("%02X".format(byte.toInt() and 0xFF))
            }
        }
    }

    internal fun messagesBle(dataHex: String): List<ByteArray> {
        val b = hex(dataHex) ?: return emptyList()
        if (b.isEmpty()) return emptyList()
        val start = if (b.size >= 2 + MSG && b[0] == 0x0D.toByte()) 2 else 0
        return framedMessages(b, start)
    }

    internal fun messagesWifi(dataHex: String): List<ByteArray> {
        val b = hex(dataHex) ?: return emptyList()
        if (b.isEmpty()) return emptyList()
        // BLE-shaped [0x0D][counter][msgs], else ASTM [counter][msgs].
        val start = if (b.size >= 2 + MSG && b[0] == 0x0D.toByte()) 2 else 1
        return framedMessages(b, start)
    }

    /** Single 25-byte messages, or an ASTM message pack (type nibble 0xF). */
    private fun framedMessages(b: ByteArray, start: Int): List<ByteArray> {
        if (start >= b.size) return emptyList()
        val head = b[start].toInt() and 0xFF
        if (head shr 4 == 0xF && start + 3 <= b.size) {
            val size = (b[start + 1].toInt() and 0xFF).coerceAtLeast(MSG)
            val count = b[start + 2].toInt() and 0xFF
            val out = ArrayList<ByteArray>(count)
            var i = start + 3
            repeat(count) {
                if (i + MSG > b.size) return@repeat
                out += b.copyOfRange(i, i + MSG)
                i += size
            }
            return out
        }
        return chunks(b, start)
    }

    private fun chunks(b: ByteArray, start: Int): List<ByteArray> {
        if (start + MSG > b.size) return emptyList()
        val out = ArrayList<ByteArray>((b.size - start) / MSG)
        var i = start
        while (i + MSG <= b.size) {
            out += b.copyOfRange(i, i + MSG)
            i += MSG
        }
        return out
    }

    internal fun parseMessages(msgs: List<ByteArray>): PayloadLocation {
        var acc = PayloadLocation()
        for (m in msgs) {
            if (m.size < MSG) continue
            acc = parseOne(m).mergeSticky(acc)
        }
        return acc
    }

    private fun parseOne(m: ByteArray): PayloadLocation {
        val type = (m[0].toInt() and 0xFF) shr 4
        return when (type) {
            0 -> basicId(m)
            1 -> location(m)
            3 -> selfId(m)
            4 -> systemMsg(m)
            else -> PayloadLocation()
        }
    }

    private fun location(m: ByteArray): PayloadLocation {
        val flags = m[1].toInt() and 0xFF
        val ew = (flags shr 1) and 1
        val speedMult = flags and 1
        val dirRaw = m[2].toInt() and 0xFF
        val heading = if (dirRaw == INV_DIR) {
            null
        } else {
            val deg = dirRaw + if (ew == 1) 180 else 0
            (deg % 360).toDouble()
        }
        val sh = m[3].toInt() and 0xFF
        val speed = if (sh == INV_SPEED) {
            null
        } else if (speedMult == 0) {
            sh * 0.25
        } else {
            sh * 0.75 + 255 * 0.25
        }
        val vRaw = m[4].toInt()
        val vspeed = (if (vRaw > 127) vRaw - 256 else vRaw) * 0.5
        val lat = i32le(m, 5) * 1e-7
        val lon = i32le(m, 9) * 1e-7
        val altGeo = (u16le(m, 15) * 0.5) - 1000.0
        return PayloadLocation(
            lat = lat,
            lon = lon,
            alt = altGeo,
            headingDeg = heading,
            speedMps = speed,
            vspeedMps = vspeed,
        )
    }

    private fun basicId(m: ByteArray): PayloadLocation {
        val idType = (m[1].toInt() and 0xFF) shr 4
        val id = m.copyOfRange(2, 22).toString(Charsets.US_ASCII).trim('\u0000', ' ')
        val maker = if (idType == ID_SERIAL) Cta2063.label(id).orEmpty() else ""
        return PayloadLocation(
            uasId = id.takeIf { it.isNotEmpty() },
            aircraft = maker,
            basicId = true,
        )
    }

    private fun selfId(m: ByteArray): PayloadLocation {
        val text = m.copyOfRange(2, 25).toString(Charsets.US_ASCII).trim('\u0000', ' ')
        return PayloadLocation(selfId = text.takeIf { it.isNotEmpty() })
    }

    private fun systemMsg(m: ByteArray): PayloadLocation {
        val lat = i32le(m, 2) * 1e-7
        val lon = i32le(m, 6) * 1e-7
        return PayloadLocation(opLat = lat, opLon = lon)
    }

    private fun i32le(m: ByteArray, at: Int): Int {
        val b0 = m[at].toInt() and 0xFF
        val b1 = m[at + 1].toInt() and 0xFF
        val b2 = m[at + 2].toInt() and 0xFF
        val b3 = m[at + 3].toInt() and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun u16le(m: ByteArray, at: Int): Int =
        (m[at].toInt() and 0xFF) or ((m[at + 1].toInt() and 0xFF) shl 8)

    private fun uuid16(uuid: String): Int? {
        val hex = uuid.filter { it.isLetterOrDigit() }
        return when {
            hex.length == 4 -> hex.toIntOrNull(16)
            hex.length >= 8 && hex.startsWith("0000", ignoreCase = true) ->
                hex.substring(4, 8).toIntOrNull(16)
            else -> hex.take(4).toIntOrNull(16)
        }
    }

    private fun hex(s: String): ByteArray? {
        val h = s.filter { it.isLetterOrDigit() }
        if (h.isEmpty() || h.length % 2 != 0) return null
        return ByteArray(h.length / 2) { i -> h.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
