package app.fieldwatch.domain

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable

data class DecodedFieldValue(
    val fleetId: String,
    val fleetName: String,
    val id: String,
    val label: String,
    val display: String,
    val offset: Int,
    val length: Int,
    /** Scaled numeric value when the field is an integer, float, bits, or bool. */
    val number: Double? = null,
    /** Catalog sentence for this named value. Empty when the signature did not write one. */
    val note: String = "",
    /** Signature asked for this field on the live row. */
    val live: Boolean = false,
    /** This value uses the stronger live chip. */
    val emphasis: Boolean = false,
)

/** One decoded label drawn next to the signature name. Text is the catalog's own wording. */
@Serializable
data class LiveDecodeChip(
    val text: String,
    val emphasis: Boolean,
    val note: String = "",
) {
    /** Title-case for reports and the live chip. Catalog text is stored lowercase. */
    fun reportLabel(): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed
        return trimmed.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
        }
    }
}

fun List<LiveDecodeChip>.reportLabels(): List<String> =
    map { it.reportLabel() }.filter { it.isNotEmpty() }

/**
 * Parses [Fleet.decode] maps from a BLE advertisement, or from Wi-Fi Remote ID
 * framed as BLE FFFA ([OpenDroneId.wifiFffaPayloads]).
 * Used by device detail, reports, and sticky payload coordinates for TAK/CoT.
 * Not a matcher — [SignatureEngine.match] still decides the label.
 */
object SignatureFieldDecoder {
    private val cache = ConcurrentHashMap<String, List<DecodedFieldValue>>()

    fun decodeSighting(device: Sighting, fleets: List<Fleet>): List<DecodedFieldValue> {
        if (device.fleetIds.isEmpty()) return emptyList()
        if (device.kind != RadioKind.BLE && device.kind != RadioKind.WIFI) return emptyList()
        val byId = fleets.associateBy { it.id }
        val out = ArrayList<DecodedFieldValue>()
        for (id in device.fleetIds) {
            val fleet = byId[id] ?: continue
            val decode = fleet.decode ?: continue
            if (decode.fields.isEmpty()) continue
            out += decodeFleet(fleet, decode, device)
        }
        return out
    }

    /** Labels for decode fields marked live. Cached with the field parse. No fleet id is special. */
    fun liveChips(device: Sighting, fleets: List<Fleet>): List<LiveDecodeChip> {
        if (device.fleetIds.isEmpty()) return emptyList()
        val out = ArrayList<LiveDecodeChip>()
        val seen = HashSet<String>()
        for (row in decodeSighting(device, fleets)) {
            if (!row.live) continue
            val text = row.display.trim()
            if (text.isEmpty() || !seen.add(text.lowercase())) continue
            out += LiveDecodeChip(text, row.emphasis, row.note.trim())
        }
        return out
    }

    fun payloadHex(decode: FleetDecode, device: Sighting): String? = payloads(decode, device).firstOrNull()?.second

    fun decodeFleet(fleet: Fleet, decode: FleetDecode, device: Sighting): List<DecodedFieldValue> {
        val candidates = payloads(decode, device)
        if (candidates.isEmpty()) return emptyList()
        val out = ArrayList<DecodedFieldValue>()
        val seen = HashSet<String>()
        for ((bytes, hex) in candidates) {
            val key = cacheKey(fleet.id, decode, hex)
            val parsed = cache[key] ?: parse(fleet, decode, bytes).also { cache[key] = it }
            for (row in parsed) {
                if (seen.add(row.id)) out += row
            }
        }
        return out
    }

    private fun cacheKey(fleetId: String, decode: FleetDecode, hex: String): String {
        val fp = decode.fields.joinToString(",") {
            "${it.id}:${it.offset}:${it.type}:${it.resolvedLength()}:${it.live}:${it.liveEmphasis}:${it.enumNotes}"
        }
        return "$fleetId|${decode.source}|$hex|$fp"
    }

    private fun payloads(decode: FleetDecode, device: Sighting): List<Pair<ByteArray, String>> {
        val hexes: List<String> = when (decode.source) {
            DecodeSource.MANUFACTURER_DATA -> {
                if (device.kind != RadioKind.BLE) emptyList()
                else {
                    val records = device.facts.mfgRecords.ifEmpty {
                        device.manufacturerId?.let { listOf(MfgRecord(it, device.manufacturerDataHex)) }
                            ?: emptyList()
                    }
                    val want = decode.companyId?.takeIf { it != 0 }
                    val chosen = if (want != null) records.filter { it.companyId == want } else records
                    chosen.map { rec ->
                        if (decode.includeCompanyId) companyIdPrefix(rec.companyId) + rec.dataHex
                        else rec.dataHex
                    }
                }
            }
            DecodeSource.SERVICE_DATA -> {
                val want = decode.serviceUuid?.let { uuidKey(it) } ?: return emptyList()
                val ads = device.facts.serviceData.filter { uuidKey(it.uuid) == want }.map { it.dataHex }
                if (want == "FFFA") ads + OpenDroneId.wifiFffaPayloads(device.facts) else ads
            }
            DecodeSource.UNSUPPORTED -> emptyList()
        }
        val out = ArrayList<Pair<ByteArray, String>>(hexes.size)
        for (hex in hexes) {
            val raw = hexToBytes(hex) ?: continue
            val bytes = stripIntelliRocks(raw)
            if (bytes.isEmpty()) continue
            val key = bytes.joinToString("") { "%02X".format(it) }
            out += bytes to key
        }
        return out
    }

    private fun parse(fleet: Fleet, decode: FleetDecode, payload: ByteArray): List<DecodedFieldValue> {
        val out = ArrayList<DecodedFieldValue>(decode.fields.size)
        for (field in decode.fields) {
            if (!gateOk(field.gate, payload)) continue
            val len = field.resolvedLength()
            if (field.offset < 0 || len < 1 || field.offset + len > payload.size) continue
            val parsed = runCatching { formatField(field, payload) }.getOrNull() ?: continue
            if (parsed.display.isEmpty()) continue
            out += DecodedFieldValue(
                fleetId = fleet.id,
                fleetName = fleet.name,
                id = field.id,
                label = field.label,
                display = parsed.display,
                offset = field.offset,
                length = len,
                number = parsed.number,
                note = enumNote(field, parsed.rawKey),
                live = field.live,
                emphasis = field.live && emphasized(field, parsed.rawKey),
            )
        }
        return out
    }

    private fun gateOk(gate: DecodeWhen?, payload: ByteArray): Boolean {
        if (gate == null) return true
        if (!gateOkOnce(gate, payload)) return false
        return gateOk(gate.and, payload)
    }

    private fun gateOkOnce(gate: DecodeWhen, payload: ByteArray): Boolean {
        if (gate.op == DecodeWhenOp.LEN) return payload.size == gate.length.coerceAtLeast(1)
        val want = hexToBytes(gate.valueHex) ?: return false
        val len = want.size.coerceAtLeast(1)
        if (gate.offset < 0 || gate.offset + len > payload.size) return false
        val got = payload.copyOfRange(gate.offset, gate.offset + len)
        if (want.size != got.size) return false
        return when (gate.op) {
            DecodeWhenOp.LEN -> payload.size == gate.length
            DecodeWhenOp.EQ -> got.contentEquals(want)
            DecodeWhenOp.NEQ -> !got.contentEquals(want)
            DecodeWhenOp.MASK -> {
                var ok = true
                for (i in got.indices) {
                    val g = got[i].toInt() and 0xff
                    val w = want[i].toInt() and 0xff
                    if ((g and w) != w) ok = false
                }
                ok
            }
            DecodeWhenOp.NMASK -> {
                var ok = true
                for (i in got.indices) {
                    val g = got[i].toInt() and 0xff
                    val w = want[i].toInt() and 0xff
                    if ((g and w) != 0) ok = false
                }
                ok
            }
        }
    }

    private data class ParsedField(val display: String, val number: Double?, val rawKey: String)

    private fun emphasized(field: DecodeField, rawKey: String): Boolean {
        if (field.liveEmphasis.isEmpty() || rawKey.isEmpty()) return false
        val want = field.liveEmphasis.map { normalizeEnumKey(it) }.toSet()
        return normalizeEnumKey(rawKey) in want || rawKey in field.liveEmphasis
    }

    private fun enumNote(field: DecodeField, rawKey: String): String {
        val notes = field.enumNotes ?: return ""
        if (rawKey.isEmpty()) return ""
        notes[rawKey]?.let { return it.trim() }
        val key = normalizeEnumKey(rawKey)
        notes[key]?.let { return it.trim() }
        return notes.entries.firstOrNull { normalizeEnumKey(it.key) == key }?.value?.trim().orEmpty()
    }

    private fun formatField(field: DecodeField, payload: ByteArray): ParsedField? {
        val len = field.resolvedLength()
        val slice = payload.copyOfRange(field.offset, field.offset + len)
        val le = field.endian != DecodeEndian.BE
        val unit = field.unit?.trim().orEmpty()
        var rawNum: Double? = null
        var rawKey = ""
        val text: String = when (field.type) {
            DecodeType.UTF8 -> {
                val s = slice.toString(Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
                rawKey = s
                s.ifEmpty { null }
            }
            DecodeType.HEX -> {
                val h = slice.joinToString("") { "%02X".format(it) }
                rawKey = h
                h.chunked(2).joinToString(" ")
            }
            DecodeType.MAC -> {
                if (slice.size < 6) return null
                val mac = slice.take(6).joinToString(":") { "%02X".format(it) }
                rawKey = mac
                mac
            }
            DecodeType.BOOL -> {
                val on = slice.any { it.toInt() != 0 }
                rawNum = if (on) 1.0 else 0.0
                rawKey = if (on) "1" else "0"
                if (on) "yes" else "no"
            }
            DecodeType.BITS -> {
                val width = (field.bitWidth ?: 1).coerceIn(1, 32)
                val bitStart = field.bitOffset ?: 0
                val word = readU(slice, 0, slice.size, le)
                val value = (word shr bitStart) and ((1L shl width) - 1)
                rawNum = value.toDouble()
                rawKey = value.toString()
                value.toString()
            }
            DecodeType.F32 -> {
                if (slice.size < 4) return null
                val bits = readU(slice, 0, 4, le).toInt()
                val f = Float.fromBits(bits).toDouble()
                rawNum = f
                rawKey = f.toString()
                formatNumber(f)
            }
            DecodeType.U8, DecodeType.U16, DecodeType.U24, DecodeType.U32 -> {
                val v = readU(slice, 0, slice.size, le)
                rawNum = v.toDouble()
                rawKey = v.toString()
                v.toString()
            }
            DecodeType.I8, DecodeType.I16, DecodeType.I32 -> {
                val bits = slice.size * 8
                val v = signExtend(readU(slice, 0, slice.size, le), bits)
                rawNum = v.toDouble()
                rawKey = v.toString()
                v.toString()
            }
        } ?: return null
        val scaledNum = rawNum?.let { raw ->
            var n = raw
            if (field.scale != null || field.offsetAdd != null || field.modulo != null) {
                field.modulo?.let { m -> if (m != 0.0) n %= m }
                field.scale?.let { n *= it }
                field.offsetAdd?.let { n += it }
            }
            n
        }
        val scaled = if (rawNum != null &&
            (field.scale != null || field.offsetAdd != null || field.modulo != null)
        ) {
            formatNumber(scaledNum!!)
        } else {
            text
        }
        val n = rawNum
        val mapped = field.enumLabels?.let { labels ->
            labels[rawKey]
                ?: labels["0x${rawKey.uppercase()}"]
                ?: n?.toLong()?.let { labels[it.toString()] }
                ?: n?.toLong()?.let { labels["0x%X".format(it)] }
        }
        val shown = mapped ?: scaled
        val display = if (unit.isEmpty()) shown else "$shown $unit"
        return ParsedField(display, scaledNum?.takeIf { it.isFinite() }, rawKey)
    }

    private fun readU(bytes: ByteArray, offset: Int, len: Int, le: Boolean): Long {
        var v = 0L
        if (le) {
            for (i in 0 until len) {
                v = v or ((bytes[offset + i].toLong() and 0xff) shl (8 * i))
            }
        } else {
            for (i in 0 until len) {
                v = (v shl 8) or (bytes[offset + i].toLong() and 0xff)
            }
        }
        return v
    }

    private fun signExtend(v: Long, bits: Int): Long {
        val shift = 64 - bits
        return (v shl shift) shr shift
    }

    private fun formatNumber(n: Double): String {
        if (!n.isFinite()) return n.toString()
        if (n == n.toLong().toDouble()) return n.toLong().toString()
        var s = "%.6f".format(Locale.US, n)
        s = s.trimEnd('0').trimEnd('.')
        return s
    }

    private fun uuidKey(uuid: String): String {
        val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
        return when {
            hex.length == 4 -> hex
            hex.length == 32 && hex.startsWith("0000") && hex.endsWith("00001000800000805F9B34FB") ->
                hex.substring(4, 8)
            else -> hex
        }
    }

    private fun companyIdPrefix(companyId: Int): String =
        "%02X%02X".format(companyId and 0xff, (companyId shr 8) and 0xff)

    private fun hexToBytes(hex: String): ByteArray? {
        val h = hex.filter { it.isLetterOrDigit() }
        if (h.isEmpty() || h.length % 2 != 0) return null
        return ByteArray(h.length / 2) { i ->
            h.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    /**
     * Govee sometimes glues ASCII "INTELLI_ROCKS" onto the sensor payload.
     * Strip it so length-gated H5074/H5075/H510x maps still fit.
     */
    private fun stripIntelliRocks(bytes: ByteArray): ByteArray {
        if (bytes.size <= INTELLI_ROCKS.size) return bytes
        val idx = indexOfSlice(bytes, INTELLI_ROCKS)
        if (idx < 0) return bytes
        return if (idx == 0) ByteArray(0) else bytes.copyOf(idx)
    }

    private fun indexOfSlice(hay: ByteArray, needle: ByteArray): Int {
        val last = hay.size - needle.size
        if (last < 0) return -1
        outer@ for (i in 0..last) {
            for (j in needle.indices) {
                if (hay[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private val INTELLI_ROCKS = "INTELLI_ROCKS".toByteArray(Charsets.US_ASCII)
}

/** Store enum keys as decimal so 0x05, 05, and 5 all match a u8 of 5. */
fun normalizeEnumKey(raw: String): String {
    val t = raw.trim()
    if (t.isEmpty()) return t
    if (t.startsWith("0x", ignoreCase = true)) {
        return t.substring(2).toLongOrNull(16)?.toString() ?: t
    }
    t.toLongOrNull()?.let { return it.toString() }
    val hex = t.filter { it.isLetterOrDigit() }
    if (hex.any { it in 'A'..'F' || it in 'a'..'f' }) {
        hex.toLongOrNull(16)?.let { return it.toString() }
    }
    return t
}

/** Keep [Sighting.liveDecode] in step with the signature's live flags. Same instance when nothing changed. */
fun Sighting.withLiveDecode(fleets: List<Fleet>): Sighting {
    val tag = SmartTag.read(this)
    val chips = ArrayList<LiveDecodeChip>(4)
    chips += SignatureFieldDecoder.liveChips(this, fleets)
    val state = tag?.state
    if (state != null) chips += LiveDecodeChip(state, emphasis = tag.separated)
    val hk = facts.homekit
    if (hk != null && !hk.paired) {
        chips += LiveDecodeChip(
            LanguageText.text("hk.chip", mapOf("category" to hk.categoryName)),
            emphasis = true,
        )
    }
    if (facts.matter?.commissionable == true) {
        chips += LiveDecodeChip(LanguageText.text("matter.chip"), emphasis = true)
    }
    if (facts.sidewalk?.mode == SidewalkMode.REGISTRATION) {
        chips += LiveDecodeChip(LanguageText.text("sw.chip"), emphasis = true)
    }
    facts.bthome?.chip()?.let { chips += it }
    payloadAuth?.chipLabel()?.let { chips += LiveDecodeChip(it, emphasis = false) }
    val id = SmartTag.separatedId(this)
    return if (chips == liveDecode && id == smartTagId) this else copy(liveDecode = chips, smartTagId = id)
}

fun normalizeEnumLabels(map: Map<String, String>?): Map<String, String>? {
    if (map.isNullOrEmpty()) return null
    val out = linkedMapOf<String, String>()
    for ((k, v) in map) {
        val key = normalizeEnumKey(k)
        val label = v.trim()
        if (key.isEmpty() || label.isEmpty()) continue
        out[key] = label
    }
    return out.ifEmpty { null }
}

fun normalizeValueHex(raw: String): String {
    var h = raw.trim().removePrefix("0x").removePrefix("0X").filter { it.isLetterOrDigit() }.uppercase()
    if (h.length % 2 == 1) h = "0$h"
    return h
}

fun normalizeGate(gate: DecodeWhen?): DecodeWhen? {
    if (gate == null) return null
    val rest = normalizeGate(gate.and)
    if (gate.op == DecodeWhenOp.LEN) {
        return DecodeWhen(
            offset = 0,
            length = gate.length.coerceAtLeast(1),
            op = DecodeWhenOp.LEN,
            valueHex = "",
            and = rest,
        )
    }
    val hex = normalizeValueHex(gate.valueHex)
    if (hex.isEmpty()) return rest
    val len = (hex.length / 2).coerceAtLeast(1)
    return DecodeWhen(
        offset = gate.offset.coerceAtLeast(0),
        length = len,
        op = gate.op,
        valueHex = hex,
        and = rest,
    )
}

fun DecodeField.normalized(): DecodeField = copy(
    enumLabels = normalizeEnumLabels(enumLabels),
    gate = normalizeGate(gate),
)
