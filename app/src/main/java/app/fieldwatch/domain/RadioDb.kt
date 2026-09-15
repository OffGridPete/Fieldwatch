package app.fieldwatch.domain

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Offline assigned-number tables packed from official IEEE MA-L/M/S/CID
 * and Bluetooth SIG Assigned Numbers YAML. Binary-searched; names decoded
 * only on a hit.
 */
object RadioDb {
    @Volatile private var ready = false
    private val lock = Any()

    private var malKeys = IntArray(0)
    private var malIdx = ShortArray(0)
    private var cidKeys = IntArray(0)
    private var cidIdx = ShortArray(0)
    private var longKeys = LongArray(0)
    private var longIdx = ShortArray(0)
    private var btKeys = IntArray(0)
    private var btIdx = ShortArray(0)
    private var appKeys = IntArray(0)
    private var appIdx = ShortArray(0)
    private var uuidKeys = IntArray(0)
    private var uuidIdx = ShortArray(0)
    private var nameOff = IntArray(0)
    private var nameBlob = ByteArray(0)
    var builtYmd: Int = 0
        private set
    var nameCount: Int = 0
        private set

    fun init(context: Context) {
        if (ready) return
        synchronized(lock) {
            if (ready) return
            context.applicationContext.assets.open("lookups/radiodb.bin").use { input ->
                parse(input.readBytes())
            }
            ready = true
        }
    }

    fun isReady(): Boolean = ready

    fun vendorForMac(mac: String): String? {
        if (!ready) return null
        if (MacUtil.isRandomized(mac)) {
            val univ = MacUtil.wifiOui24Universal(mac) ?: return null
            val name = vendorForOui24(univ) ?: return null
            return name.takeUnless { isPhoneHouseVendor(it) }
        }
        val hex = mac.filter { it.isLetterOrDigit() }.uppercase()
        if (hex.length >= 9) {
            longName(36, hex.take(9).toLong(16))?.let { return it }
        }
        if (hex.length >= 7) {
            longName(28, hex.take(7).toLong(16))?.let { return it }
        }
        if (hex.length >= 6) {
            return u32Name(malKeys, malIdx, hex.take(6).toInt(16))
        }
        return null
    }

    fun vendorForOui24(oui: String): String? {
        if (!ready) return null
        val hex = oui.filter { it.isLetterOrDigit() }.uppercase()
        if (hex.length < 6) return null
        val key = hex.take(6).toInt(16)
        return u32Name(malKeys, malIdx, key) ?: u32Name(cidKeys, cidIdx, key)
    }

    fun company(id: Int): String? {
        if (!ready) return null
        return u16Name(btKeys, btIdx, id and 0xFFFF)
    }

    fun appearance(value: Int): String? {
        if (!ready) return null
        val v = value and 0xFFFF
        u16Name(appKeys, appIdx, v)?.let { return it }
        val category = v and 0xFFC0
        if (category != v) u16Name(appKeys, appIdx, category)?.let { return it }
        return null
    }

    fun serviceUuid(uuid: String): String? {
        if (!ready) return null
        val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
        val short = when {
            hex.length == 4 -> hex.toInt(16)
            hex.length == 8 -> hex.takeLast(4).toInt(16)
            hex.length == 32 && hex.startsWith("0000") && hex.endsWith("00001000800000805F9B34FB") ->
                hex.substring(4, 8).toInt(16)
            else -> return null
        }
        return u16Name(uuidKeys, uuidIdx, short)
    }

    private fun longName(bits: Int, prefix: Long): String? {
        val key = bits.toLong() shl 56 or prefix
        var lo = 0
        var hi = longKeys.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = longKeys[mid]
            when {
                v < key -> lo = mid + 1
                v > key -> hi = mid - 1
                else -> return nameAt(longIdx[mid].toInt() and 0xFFFF)
            }
        }
        return null
    }

    private fun u32Name(keys: IntArray, idx: ShortArray, target: Int): String? {
        var lo = 0
        var hi = keys.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = keys[mid]
            when {
                v < target -> lo = mid + 1
                v > target -> hi = mid - 1
                else -> return nameAt(idx[mid].toInt() and 0xFFFF)
            }
        }
        return null
    }

    private fun u16Name(keys: IntArray, idx: ShortArray, target: Int): String? {
        var lo = 0
        var hi = keys.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = keys[mid]
            when {
                v < target -> lo = mid + 1
                v > target -> hi = mid - 1
                else -> return nameAt(idx[mid].toInt() and 0xFFFF)
            }
        }
        return null
    }

    private fun nameAt(index: Int): String? {
        if (index < 0 || index + 1 >= nameOff.size) return null
        val start = nameOff[index]
        val end = nameOff[index + 1]
        if (start < 0 || end > nameBlob.size || end < start) return null
        return String(nameBlob, start, end - start, StandardCharsets.UTF_8)
    }

    private fun parse(bytes: ByteArray) {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4)
        buf.get(magic)
        require(magic.contentEquals("SPLK".toByteArray())) { "radiodb magic" }
        val version = buf.short.toInt() and 0xFFFF
        buf.short
        require(version == 1) { "radiodb version $version" }
        builtYmd = buf.int
        val nsec = buf.int
        val sections = LinkedHashMap<String, Int>(nsec)
        repeat(nsec) {
            val tag = ByteArray(4)
            buf.get(tag)
            val off = buf.int
            sections[String(tag, StandardCharsets.US_ASCII).trimEnd('\u0000')] = off
        }
        fun slice(tag: String): ByteBuffer {
            val off = sections[tag] ?: error("missing $tag")
            return ByteBuffer.wrap(bytes, off, bytes.size - off).order(ByteOrder.LITTLE_ENDIAN)
        }
        slice("mal").let { b ->
            val n = b.int
            malKeys = IntArray(n) { b.int }
            malIdx = ShortArray(n) { b.short }
        }
        slice("cid").let { b ->
            val n = b.int
            cidKeys = IntArray(n) { b.int }
            cidIdx = ShortArray(n) { b.short }
        }
        slice("long").let { b ->
            val n = b.int
            longKeys = LongArray(n) { b.long }
            longIdx = ShortArray(n) { b.short }
        }
        slice("btc").let { b ->
            val n = b.int
            btKeys = IntArray(n) { b.short.toInt() and 0xFFFF }
            btIdx = ShortArray(n) { b.short }
        }
        slice("app").let { b ->
            val n = b.int
            appKeys = IntArray(n) { b.short.toInt() and 0xFFFF }
            appIdx = ShortArray(n) { b.short }
        }
        slice("uuid").let { b ->
            val n = b.int
            uuidKeys = IntArray(n) { b.short.toInt() and 0xFFFF }
            uuidIdx = ShortArray(n) { b.short }
        }
        slice("noff").let { b ->
            val n = b.int
            nameCount = n
            nameOff = IntArray(n + 1) { b.int }
        }
        val nstrAt = sections["nstr"] ?: error("missing nstr")
        val last = nameOff.last()
        nameBlob = bytes.copyOfRange(nstrAt, nstrAt + last)
    }

    private fun isPhoneHouseVendor(vendor: String): Boolean {
        val v = vendor.lowercase()
        return PHONE_HOUSE_VENDOR.any { it in v }
    }

    private val PHONE_HOUSE_VENDOR = listOf(
        "apple", "google", "samsung electronics", "microsoft",
    )
}
