package app.fieldwatch.domain

/**
 * French Direct Remote ID (arrêté du 27 décembre 2019).
 * Wi-Fi vendor IE OUI 6A:5C:35, VS type 0x01. TLV, big-endian.
 * Coordinates are degrees times 1e5. [VendorIeRecord.dataHex] starts after the OUI and type.
 * Height above takeoff stays on this plate. It is not the aircraft altitude.
 */
internal object FrenchRid {
    const val OUI = "6A:5C:35"
    const val VS_TYPE = 1

    private const val T_VERSION = 0x01
    private const val T_ID = 0x02
    private const val T_CTA = 0x03
    private const val T_LAT = 0x04
    private const val T_LON = 0x05
    private const val T_ALT = 0x06
    private const val T_HEIGHT = 0x07
    private const val T_OP_LAT = 0x08
    private const val T_OP_LON = 0x09
    private const val T_SPEED = 0x0A
    private const val T_COURSE = 0x0B

    data class Plate(
        val version: Int? = null,
        val trigram: String = "",
        val model: String = "",
        val serial: String = "",
        val frenchId: String = "",
        val splitId: Boolean = false,
        val cta: String = "",
        val heightM: Int? = null,
        val location: PayloadLocation = PayloadLocation(),
    ) {
        val present: Boolean
            get() = version != null ||
                frenchId.isNotBlank() ||
                cta.isNotBlank() ||
                heightM != null ||
                location.lat != null ||
                location.lon != null ||
                location.alt != null ||
                location.opLat != null ||
                location.headingDeg != null ||
                location.speedMps != null

        /** This beacon carried a takeoff fix. The operator row keeps one pin. */
        fun takeoffHere(): Boolean = PayloadLocation.validCoord(location.opLat, location.opLon)

        fun operatorLabel(stickyKind: Int?): String =
            if (takeoffHere()) LanguageText.text("rid.french.takeoff") else OperatorLoc.label(stickyKind)

        fun identifier(demo: Boolean): String? {
            if (frenchId.isBlank()) return null
            if (!splitId) return frenchId
            val shown = if (demo && serial.isNotEmpty()) "masked" else serial
            return LanguageText.text(
                "rid.french.id.value",
                mapOf("trigram" to trigram, "model" to model, "serial" to shown),
            ).trim()
        }

        fun lines(demo: Boolean): List<Pair<String, String>> {
            if (!present) return emptyList()
            val out = ArrayList<Pair<String, String>>(3)
            version?.let { out += LanguageText.text("rid.french.version") to it.toString() }
            identifier(demo)?.let { out += LanguageText.text("rid.french.id") to it }
            heightM?.let { out += LanguageText.text("rid.french.height") to "$it m" }
            return out
        }

        fun note(): String? = if (present) LanguageText.text("rid.french.note") else null
    }

    fun isPlate(ie: VendorIeRecord): Boolean =
        ie.oui.equals(OUI, ignoreCase = true) && ie.type == VS_TYPE

    fun fromFacts(facts: RadioFacts): Plate {
        var acc: Plate? = null
        for (ie in facts.vendorIes) {
            if (!isPlate(ie)) continue
            val parsed = parse(ie.dataHex)
            acc = if (acc == null) parsed else merge(parsed, acc)
        }
        return acc ?: Plate()
    }

    private fun merge(newer: Plate, older: Plate): Plate {
        val frenchId = newer.frenchId.ifBlank { older.frenchId }
        val fromNewer = newer.frenchId.isNotBlank()
        val cta = newer.cta.ifBlank { older.cta }
        val uas = frenchId.ifBlank { cta }.takeIf { it.isNotBlank() }
        val maker = cta.takeIf { it.isNotBlank() }?.let { Cta2063.label(it) }?.takeIf { it.isNotBlank() }
        val loc = newer.location.mergeSticky(older.location).copy(
            uasId = uas,
            aircraft = maker,
            basicId = false,
        )
        return Plate(
            version = newer.version ?: older.version,
            trigram = if (fromNewer) newer.trigram else older.trigram,
            model = if (fromNewer) newer.model else older.model,
            serial = if (fromNewer) newer.serial else older.serial,
            frenchId = frenchId,
            splitId = if (fromNewer) newer.splitId else older.splitId,
            cta = cta,
            heightM = newer.heightM ?: older.heightM,
            location = loc,
        )
    }

    private fun parse(dataHex: String): Plate {
        val b = hex(dataHex) ?: return Plate()
        var i = 0
        var version: Int? = null
        var id: FrenchId? = null
        var cta: String? = null
        var height: Int? = null
        var lat: Double? = null
        var lon: Double? = null
        var alt: Double? = null
        var opLat: Double? = null
        var opLon: Double? = null
        var speed: Double? = null
        var heading: Double? = null
        while (i < b.size) {
            if (i + 2 > b.size) break
            val type = b[i].toInt() and 0xFF
            val len = b[i + 1].toInt() and 0xFF
            if (i + 2 + len > b.size) break
            val value = b.copyOfRange(i + 2, i + 2 + len)
            i += 2 + len
            when (type) {
                T_VERSION -> if (len == 1) version = value[0].toInt() and 0xFF
                T_ID -> id = readId(value)
                T_CTA -> {
                    val text = text(value)
                    if (text.isNotEmpty()) cta = text
                }
                T_LAT -> if (len == 4) lat = i32be(value) / 1e5
                T_LON -> if (len == 4) lon = i32be(value) / 1e5
                T_ALT -> if (len == 2) alt = i16be(value).toDouble()
                T_HEIGHT -> if (len == 2) height = i16be(value)
                T_OP_LAT -> if (len == 4) opLat = i32be(value) / 1e5
                T_OP_LON -> if (len == 4) opLon = i32be(value) / 1e5
                T_SPEED -> if (len == 1) speed = (value[0].toInt() and 0xFF).toDouble()
                T_COURSE -> if (len == 2) {
                    val deg = u16be(value)
                    if (deg in 0..359) heading = deg.toDouble()
                }
            }
        }
        val takeoff = PayloadLocation.validCoord(opLat, opLon)
        val french = id
        val ctaText = cta.orEmpty()
        val uas = french?.raw?.takeIf { it.isNotBlank() } ?: ctaText.takeIf { it.isNotBlank() }
        val maker = ctaText.takeIf { it.isNotBlank() }?.let { Cta2063.label(it) }?.takeIf { it.isNotBlank() }
        return Plate(
            version = version,
            trigram = french?.trigram.orEmpty(),
            model = french?.model.orEmpty(),
            serial = french?.serial.orEmpty(),
            frenchId = french?.raw.orEmpty(),
            splitId = french?.split == true,
            cta = ctaText,
            heightM = height,
            location = PayloadLocation(
                lat = lat,
                lon = lon,
                alt = alt,
                opLat = if (takeoff) opLat else null,
                opLon = if (takeoff) opLon else null,
                opKind = if (takeoff) OperatorLoc.TAKEOFF else null,
                uasId = uas,
                headingDeg = heading,
                speedMps = speed,
                aircraft = maker,
                basicId = false,
            ),
        )
    }

    private data class FrenchId(
        val raw: String,
        val trigram: String,
        val model: String,
        val serial: String,
        val split: Boolean,
    )

    /** Legal length is 30. A shorter value that fits is still shown. Split at 6 bytes. */
    private fun readId(value: ByteArray): FrenchId {
        val raw = text(value)
        if (value.size < 6) return FrenchId(raw, "", "", "", false)
        return FrenchId(
            raw = raw,
            trigram = text(value.copyOfRange(0, 3)),
            model = text(value.copyOfRange(3, 6)),
            serial = text(value.copyOfRange(6, value.size)),
            split = true,
        )
    }

    private fun text(value: ByteArray): String {
        var end = value.size
        while (end > 0 && value[end - 1] == 0.toByte()) end--
        if (end == 0) return ""
        return String(value, 0, end, Charsets.UTF_8).replace("\u0000", "")
    }

    private fun i32be(v: ByteArray): Int =
        ((v[0].toInt() and 0xFF) shl 24) or
            ((v[1].toInt() and 0xFF) shl 16) or
            ((v[2].toInt() and 0xFF) shl 8) or
            (v[3].toInt() and 0xFF)

    private fun i16be(v: ByteArray): Int {
        val u = u16be(v)
        return if (u >= 0x8000) u - 0x10000 else u
    }

    private fun u16be(v: ByteArray): Int =
        ((v[0].toInt() and 0xFF) shl 8) or (v[1].toInt() and 0xFF)

    private fun hex(s: String): ByteArray? {
        val h = s.filter { it.isLetterOrDigit() }
        if (h.isEmpty() || h.length % 2 != 0) return null
        return try {
            ByteArray(h.length / 2) { i -> h.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
