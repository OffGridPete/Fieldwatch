package app.fieldwatch.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * BTHome v2 service data on UUID 0xFCD2. The stored bytes start at the device-info byte.
 * An unknown object ID stops the walk. Repeated IDs stay as a sequence.
 * Encryption is shown and not opened. Command bytes are skipped and not acted on.
 */
data class BthomeObject(
    val id: Int,
    val ordinal: Int,
    val name: String,
    val kind: BthomeKind,
    val numberText: String = "",
    val unit: String = "",
    /** Raw binary byte. The live chip uses 1. Other objects leave this null. */
    val state: Int? = null,
    val text: String = "",
    val rawHex: String = "",
    val byteCount: Int = 0,
    val epochSeconds: Long? = null,
)

enum class BthomeKind { MEASUREMENT, BINARY, EVENT, TEXT, RAW, TIME, DEVICE, COMMAND }

data class BthomeFacts(
    val version: Int,
    val encrypted: Boolean,
    val triggerBased: Boolean,
    val objects: List<BthomeObject> = emptyList(),
    /** Newest packet is encrypted or not version 2. Stored objects stay, and the page hides them. */
    val hideObjects: Boolean = false,
) {
    fun merge(newer: BthomeFacts): BthomeFacts {
        if (newer.encrypted || newer.version != 2) {
            return copy(
                version = newer.version,
                encrypted = newer.encrypted,
                triggerBased = newer.triggerBased,
                hideObjects = true,
            )
        }
        val replaced = newer.objects.map { it.id }.toSet()
        val kept = objects.filter { it.id !in replaced }
        return copy(
            version = newer.version,
            encrypted = false,
            triggerBased = newer.triggerBased,
            objects = (kept + newer.objects).sortedWith(compareBy({ it.id }, { it.ordinal })),
            hideObjects = false,
        )
    }

    fun versionRow(): Pair<String, String> =
        LanguageText.text("bth.version") to version.toString()

    fun triggerLine(): String? =
        if (triggerBased) LanguageText.text("bth.trigger") else null

    fun encryptedLine(): String? =
        if (encrypted) LanguageText.text("bth.encrypted") else null

    fun objectRows(demo: Boolean, zone: ZoneId = ZoneId.systemDefault()): List<Pair<String, String>> {
        if (hideObjects) return emptyList()
        return objects.mapNotNull { obj ->
            val value = obj.display(demo, zone) ?: return@mapNotNull null
            obj.label() to value
        }
    }

    fun commandLines(): List<String> {
        if (hideObjects) return emptyList()
        return objects.filter { it.kind == BthomeKind.COMMAND }.map { "Command object present" }
    }

    fun note(): String = LanguageText.text("bth.note")

    /** One chip. Open wins. Motion does not include occupancy. */
    fun chip(): LiveDecodeChip? {
        if (hideObjects) return null
        if (objects.any { it.id in OPEN_IDS && it.state == 1 }) {
            return LiveDecodeChip("Open", emphasis = true)
        }
        if (objects.any { it.id in MOTION_IDS && it.state == 1 }) {
            return LiveDecodeChip("Motion", emphasis = false)
        }
        return null
    }

    private companion object {
        val OPEN_IDS = setOf(0x11, 0x1A, 0x1B, 0x2D)
        val MOTION_IDS = setOf(0x21, 0x22)
    }
}

object Bthome {
    fun read(records: List<ServiceDataRecord>): BthomeFacts? {
        var found: BthomeFacts? = null
        for (rec in records) {
            if (!isBthomeUuid(rec.uuid)) continue
            val bytes = hexToBytes(rec.dataHex) ?: continue
            readValue(bytes)?.let { found = it }
        }
        return found
    }

    fun readValue(data: ByteArray): BthomeFacts? {
        if (data.isEmpty()) return null
        val info = data[0].toInt() and 0xFF
        val encrypted = info and 0x01 != 0
        val trigger = info and 0x04 != 0
        val version = (info shr 5) and 0x07
        if (encrypted || version != 2) {
            return BthomeFacts(
                version = version,
                encrypted = encrypted,
                triggerBased = trigger,
                hideObjects = true,
            )
        }
        val objects = ArrayList<BthomeObject>()
        val counts = IntArray(256)
        var i = 1
        while (i < data.size) {
            val id = data[i].toInt() and 0xFF
            val spec = specs[id] ?: break
            val start = i + 1
            val size = spec.bytes(data, start) ?: break
            val ordinal = counts[id]
            counts[id] = ordinal + 1
            spec.decode(data, start, id, ordinal)?.let { objects += it }
            i = start + size
        }
        objects.sortWith(compareBy({ it.id }, { it.ordinal }))
        return BthomeFacts(
            version = version,
            encrypted = false,
            triggerBased = trigger,
            objects = objects,
        )
    }

    fun isBthomeUuid(uuid: String): Boolean {
        val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
        return when {
            hex == "FCD2" || hex == "0000FCD2" -> true
            hex.length == 32 && hex.startsWith("0000FCD2") && hex.endsWith("00001000800000805F9B34FB") -> true
            else -> false
        }
    }

    private val specs = arrayOfNulls<Spec>(256).also { table ->
        fun measure(
            id: Int,
            name: String,
            width: Int,
            signed: Boolean,
            num: Int,
            den: Int,
            decimals: Int,
            unit: String,
        ) {
            table[id] = Measure(name, width, signed, num, den, decimals, unit)
        }
        measure(0x51, "Acceleration", 2, false, 1, 1000, 3, "m/s²")
        measure(0x63, "Acceleration", 4, true, 1, 1_000_000, 6, "m/s²")
        measure(0x01, "Battery", 1, false, 1, 1, 0, "%")
        measure(0x60, "Channel", 1, false, 1, 1, 0, "")
        measure(0x12, "CO2", 2, false, 1, 1, 0, "ppm")
        measure(0x56, "Conductivity", 2, false, 1, 1, 0, "µS/cm")
        measure(0x09, "Count", 1, false, 1, 1, 0, "")
        measure(0x3D, "Count", 2, false, 1, 1, 0, "")
        measure(0x3E, "Count", 4, false, 1, 1, 0, "")
        measure(0x59, "Count", 1, true, 1, 1, 0, "")
        measure(0x5A, "Count", 2, true, 1, 1, 0, "")
        measure(0x5B, "Count", 4, true, 1, 1, 0, "")
        measure(0x43, "Current", 2, false, 1, 1000, 3, "A")
        measure(0x5D, "Current", 2, true, 1, 1000, 3, "A")
        measure(0x08, "Dew point", 2, true, 1, 100, 2, "°C")
        measure(0x5E, "Direction", 2, false, 1, 100, 2, "°")
        measure(0x40, "Distance", 2, false, 1, 1, 0, "mm")
        measure(0x41, "Distance", 2, false, 1, 10, 1, "m")
        measure(0x42, "Duration", 3, false, 1, 1000, 3, "s")
        measure(0x4D, "Energy", 4, false, 1, 1000, 3, "kWh")
        measure(0x0A, "Energy", 3, false, 1, 1000, 3, "kWh")
        measure(0x4B, "Gas", 3, false, 1, 1000, 3, "m3")
        measure(0x4C, "Gas", 4, false, 1, 1000, 3, "m3")
        measure(0x52, "Gyroscope", 2, false, 1, 1000, 3, "°/s")
        measure(0x03, "Humidity", 2, false, 1, 100, 2, "%")
        measure(0x2E, "Humidity", 1, false, 1, 1, 0, "%")
        measure(0x05, "Illuminance", 3, false, 1, 100, 2, "lx")
        measure(0x06, "Mass", 2, false, 1, 100, 2, "kg")
        measure(0x07, "Mass", 2, false, 1, 100, 2, "lb")
        measure(0x14, "Moisture", 2, false, 1, 100, 2, "%")
        measure(0x2F, "Moisture", 1, false, 1, 1, 0, "%")
        measure(0x0D, "PM2.5", 2, false, 1, 1, 0, "ug/m3")
        measure(0x0E, "PM10", 2, false, 1, 1, 0, "ug/m3")
        measure(0x0B, "Power", 3, false, 1, 100, 2, "W")
        measure(0x5C, "Power", 4, true, 1, 100, 2, "W")
        measure(0x5F, "Precipitation", 2, false, 1, 10, 1, "mm")
        measure(0x04, "Pressure", 3, false, 1, 100, 2, "hPa")
        measure(0x3F, "Rotation", 2, true, 1, 10, 1, "°")
        measure(0x61, "Rotational speed", 2, false, 1, 1, 0, "rpm")
        measure(0x65, "Settings revision", 1, false, 1, 1, 0, "")
        measure(0x44, "Speed", 2, false, 1, 100, 2, "m/s")
        measure(0x62, "Speed", 4, true, 1, 1_000_000, 6, "m/s")
        measure(0x57, "Temperature", 1, true, 1, 1, 0, "°C")
        measure(0x58, "Temperature", 1, true, 35, 100, 1, "°C")
        measure(0x45, "Temperature", 2, true, 1, 10, 1, "°C")
        measure(0x02, "Temperature", 2, true, 1, 100, 2, "°C")
        measure(0x13, "TVOC", 2, false, 1, 1, 0, "ug/m3")
        measure(0x0C, "Voltage", 2, false, 1, 1000, 3, "V")
        measure(0x4A, "Voltage", 2, false, 1, 10, 1, "V")
        measure(0x4E, "Volume", 4, false, 1, 1000, 3, "L")
        measure(0x47, "Volume", 2, false, 1, 10, 1, "L")
        measure(0x48, "Volume", 2, false, 1, 1, 0, "mL")
        measure(0x55, "Volume storage", 4, false, 1, 1000, 3, "L")
        measure(0x49, "Volume flow rate", 2, false, 1, 1000, 3, "m3/hr")
        measure(0x46, "UV index", 1, false, 1, 10, 1, "")
        measure(0x4F, "Water", 4, false, 1, 1000, 3, "L")
        table[0x00] = PacketId
        table[0x64] = LightLevel
        table[0x53] = TextObject
        table[0x54] = RawObject
        table[0x50] = Timestamp
        table[0xF0] = DeviceType
        table[0xF1] = Firmware(4)
        table[0xF2] = Firmware(3)
        table[0x3A] = Button
        table[0x3C] = Dimmer
        table[0x3B] = Command
        binary(table, 0x15, "Battery", "Normal", "Low")
        binary(table, 0x16, "Battery charging", "Not charging", "Charging")
        binary(table, 0x17, "Carbon monoxide", "Not detected", "Detected")
        binary(table, 0x18, "Cold", "Normal", "Cold")
        binary(table, 0x19, "Connectivity", "Disconnected", "Connected")
        binary(table, 0x1A, "Door", "Closed", "Open")
        binary(table, 0x1B, "Garage door", "Closed", "Open")
        binary(table, 0x1C, "Gas", "Clear", "Detected")
        binary(table, 0x0F, "Boolean", "Off", "On")
        binary(table, 0x1D, "Heat", "Normal", "Hot")
        binary(table, 0x1E, "Light", "No light", "Light detected")
        binary(table, 0x1F, "Lock", "Locked", "Unlocked")
        binary(table, 0x20, "Moisture", "Dry", "Wet")
        binary(table, 0x21, "Motion", "Clear", "Detected")
        binary(table, 0x22, "Moving", "Not moving", "Moving")
        binary(table, 0x23, "Occupancy", "Clear", "Detected")
        binary(table, 0x11, "Opening", "Closed", "Open")
        binary(table, 0x24, "Plug", "Unplugged", "Plugged in")
        binary(table, 0x10, "Power", "Off", "On")
        binary(table, 0x25, "Presence", "Away", "Home")
        binary(table, 0x26, "Problem", "OK", "Problem")
        binary(table, 0x27, "Running", "Not running", "Running")
        binary(table, 0x28, "Safety", "Unsafe", "Safe")
        binary(table, 0x29, "Smoke", "Clear", "Detected")
        binary(table, 0x2A, "Sound", "Clear", "Detected")
        binary(table, 0x2B, "Tamper", "Off", "On")
        binary(table, 0x2C, "Vibration", "Clear", "Detected")
        binary(table, 0x2D, "Window", "Closed", "Open")
    }

    private fun binary(table: Array<Spec?>, id: Int, name: String, off: String, on: String) {
        table[id] = Binary(name, off, on)
    }

    private fun hexToBytes(hex: String): ByteArray? {
        val h = hex.filter { it.isLetterOrDigit() }
        if (h.isEmpty() || h.length % 2 != 0) return null
        return ByteArray(h.length / 2) { i ->
            h.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}

private fun BthomeObject.label(): String =
    if (id == 0x01) LanguageText.text("device.battery") else name

private fun BthomeObject.display(demo: Boolean, zone: ZoneId): String? = when (kind) {
    BthomeKind.COMMAND -> null
    BthomeKind.TEXT -> if (demo) "text, $byteCount bytes" else text
    BthomeKind.RAW -> if (demo) "raw, $byteCount bytes" else rawHex.hexSpaced()
    BthomeKind.TIME -> formatBthomeTime(epochSeconds ?: 0L, zone)
    else -> if (unit.isEmpty()) numberText else "$numberText $unit"
}

private fun formatBthomeTime(epochSeconds: Long, zone: ZoneId): String =
    Instant.ofEpochSecond(epochSeconds).atZone(zone).format(BTHOME_TIME)

private val BTHOME_TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.US)

private interface Spec {
    fun bytes(data: ByteArray, at: Int): Int?
    fun decode(data: ByteArray, at: Int, id: Int, ordinal: Int): BthomeObject?
}

private class Measure(
    val name: String,
    val width: Int,
    val signed: Boolean,
    val num: Int,
    val den: Int,
    val decimals: Int,
    val unit: String,
) : Spec {
    override fun bytes(data: ByteArray, at: Int): Int? =
        if (at + width <= data.size) width else null

    override fun decode(data: ByteArray, at: Int, id: Int, ordinal: Int): BthomeObject {
        val raw = readRaw(data, at, width, signed)
        return BthomeObject(
            id = id,
            ordinal = ordinal,
            name = name,
            kind = BthomeKind.MEASUREMENT,
            numberText = formatScaled(raw, num, den, decimals),
            unit = unit,
        )
    }
}

private class Binary(val name: String, val off: String, val on: String) : Spec {
    override fun bytes(data: ByteArray, at: Int): Int? = if (at < data.size) 1 else null

    override fun decode(data: ByteArray, at: Int, id: Int, ordinal: Int): BthomeObject {
        val raw = data[at].toInt() and 0xFF
        val word = when (raw) {
            0 -> off
            1 -> on
            else -> raw.toString()
        }
        return BthomeObject(
            id = id,
            ordinal = ordinal,
            name = name,
            kind = BthomeKind.BINARY,
            numberText = word,
            state = raw,
        )
    }
}

private object PacketId : Spec {
    override fun bytes(data: ByteArray, at: Int): Int? = if (at < data.size) 1 else null
    override fun decode(data: ByteArray, at: Int, id: Int, ordinal: Int): BthomeObject? = null
}

private object LightLevel : Spec {
    override fun bytes(data: ByteArray, at: Int): Int? = if (at < data.size) 1 else null

    override fun decode(data: ByteArray, at: Int, id: Int, ordinal: Int): BthomeObject {
        val raw = data[at].toInt() and 0xFF
        val word = when (raw) {
            0 -> "Dark"
            1 -> "Twilight"
            2 -> "Bright"
            else -> raw.toString()
        }
        return BthomeObject(id, ordinal, "Light level", BthomeKind.MEASUREMENT, numberText = word)
    }
}

private object TextObject : Spec {
    override fun bytes(data: ByteArray, at: Int): Int? = variableBytes(data, at)

    override fun decode(data: ByteArray, at: Int, id: Int, ordinal: Int): BthomeObject {
        val n = data[at].toInt() and 0xFF
        val body = data.copyOfRange(at + 1, at + 1 + n)
        val text = body.toString(Charsets.UTF_8).replace('\n', ' ').replace('\r', ' ')
        return BthomeObject(
            id = id,
            ordinal = ordinal,
            name = "Text",
            kind = BthomeKind.TEXT,
            text = text,
            byteCount = n,
        )
    }
}

private object RawObject : Spec {
    override fun bytes(data: ByteArray, at: Int): Int? = variableBytes(data, at)

    override fun decode(data: ByteArray, at: Int, id: Int, ordinal: Int): BthomeObject {
        val n = data[at].toInt() and 0xFF
        val body = data.copyOfRange(at + 1, at + 1 + n)
        return BthomeObject(
            id = id,
            ordinal = ordinal,
            name = "Raw",
            kind = BthomeKind.RAW,
            rawHex = body.toHexUpper(),
            byteCount = n,
        )
    }
}

private object Timestamp : Spec {
    override fun bytes(data: ByteArray, at: Int): Int? = if (at + 4 <= data.size) 4 else null

    override fun decode(data: ByteArray, at: Int, id: Int, ordinal: Int): BthomeObject =
        BthomeObject(
            id = id,
            ordinal = ordinal,
            name = "Timestamp",
            kind = BthomeKind.TIME,
            epochSeconds = readRaw(data, at, 4, signed = false),
        )
}

private object DeviceType : Spec {
    override fun bytes(data: ByteArray, at: Int): Int? = if (at + 2 <= data.size) 2 else null

    override fun decode(data: ByteArray, at: Int, id: Int, ordinal: Int): BthomeObject =
        BthomeObject(
            id = id,
            ordinal = ordinal,
            name = "Device type",
            kind = BthomeKind.DEVICE,
            numberText = readRaw(data, at, 2, signed = false).toString(),
        )
}

private class Firmware(val width: Int) : Spec {
    override fun bytes(data: ByteArray, at: Int): Int? = if (at + width <= data.size) width else null

    override fun decode(data: ByteArray, at: Int, id: Int, ordinal: Int): BthomeObject {
        val parts = (width - 1 downTo 0).joinToString(".") { (data[at + it].toInt() and 0xFF).toString() }
        return BthomeObject(
            id = id,
            ordinal = ordinal,
            name = "Firmware",
            kind = BthomeKind.DEVICE,
            numberText = parts,
        )
    }
}

private object Button : Spec {
    override fun bytes(data: ByteArray, at: Int): Int? = if (at < data.size) 1 else null

    override fun decode(data: ByteArray, at: Int, id: Int, ordinal: Int): BthomeObject {
        val event = data[at].toInt() and 0xFF
        val word = BUTTON_EVENTS[event] ?: event.toString()
        return BthomeObject(id, ordinal, "Button", BthomeKind.EVENT, numberText = word)
    }
}

private object Dimmer : Spec {
    override fun bytes(data: ByteArray, at: Int): Int? = if (at + 2 <= data.size) 2 else null

    override fun decode(data: ByteArray, at: Int, id: Int, ordinal: Int): BthomeObject {
        val event = data[at].toInt() and 0xFF
        val steps = data[at + 1].toInt() and 0xFF
        val word = when (event) {
            0x00 -> "None"
            0x01 -> "rotate left $steps steps"
            0x02 -> "rotate right $steps steps"
            else -> event.toString()
        }
        return BthomeObject(id, ordinal, "Dimmer", BthomeKind.EVENT, numberText = word)
    }
}

private object Command : Spec {
    override fun bytes(data: ByteArray, at: Int): Int? {
        if (at >= data.size) return null
        val argLen = data[at].toInt() and 0x1F
        val total = 2 + argLen
        return if (at + total <= data.size) total else null
    }

    override fun decode(data: ByteArray, at: Int, id: Int, ordinal: Int): BthomeObject =
        BthomeObject(id, ordinal, "Command", BthomeKind.COMMAND)
}

private fun variableBytes(data: ByteArray, at: Int): Int? {
    if (at >= data.size) return null
    val n = data[at].toInt() and 0xFF
    val total = 1 + n
    return if (at + total <= data.size) total else null
}

private fun readRaw(data: ByteArray, at: Int, width: Int, signed: Boolean): Long {
    var v = 0L
    for (i in 0 until width) {
        v = v or ((data[at + i].toLong() and 0xFF) shl (8 * i))
    }
    if (!signed) return v
    // Kotlin masks a Long shift to 6 bits, so `1L shl 32` is 1. Shift the sign bit once instead.
    val sign = 1L shl (width * 8 - 1)
    return if (v and sign != 0L) v - (sign shl 1) else v
}

private fun formatScaled(raw: Long, num: Int, den: Int, decimals: Int): String {
    val neg = raw < 0
    val mag = if (neg) -raw else raw
    if (decimals == 0) {
        val whole = mag * num / den
        return signed(neg && whole != 0L, whole.toString())
    }
    var scale = 1L
    repeat(decimals) { scale *= 10 }
    val scaled = mag * num * scale / den
    val whole = scaled / scale
    val frac = (scaled % scale).toString().padStart(decimals, '0')
    val body = "$whole.$frac"
    return signed(neg && body.any { it.isDigit() && it != '0' }, body)
}

private fun signed(neg: Boolean, body: String): String = if (neg) "-$body" else body

private val BUTTON_EVENTS = mapOf(
    0x00 to "None",
    0x01 to "press",
    0x02 to "double press",
    0x03 to "triple press",
    0x04 to "long press",
    0x05 to "long double press",
    0x06 to "long triple press",
    0x80 to "hold press",
    0x81 to "hold release",
)
