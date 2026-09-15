package app.fieldwatch.radio

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.os.Build
import android.util.SparseArray
import app.fieldwatch.domain.MfgRecord
import app.fieldwatch.domain.RadioFacts
import app.fieldwatch.domain.ServiceDataRecord
import app.fieldwatch.domain.toHexUpper
import java.util.UUID

object BleAdParser {
    fun facts(result: ScanResult): RadioFacts {
        val record = result.scanRecord
        val parsed = parse(record?.bytes)
        val mfg = parsed.mfg.ifEmpty { manufacturerRecords(record) }
        val serviceData = parsed.serviceData.ifEmpty { serviceDataRecords(record) }
        val flags = parsed.flags ?: record?.advertiseFlags?.takeIf { it >= 0 }
        val txAdv = parsed.txPower ?: record?.txPowerLevel?.takeIf { it != Integer.MIN_VALUE }
        val txScan = if (Build.VERSION.SDK_INT >= 26) {
            result.txPower.takeIf { it != ScanResult.TX_POWER_NOT_PRESENT }
        } else null
        val interval = parsed.advertisingIntervalMs
        val periodic = if (Build.VERSION.SDK_INT >= 26) {
            val raw = result.periodicAdvertisingInterval
            if (raw > 0) raw * 1.25 else null
        } else null
        val deviceClass = parsed.deviceClass ?: result.device?.bluetoothClass?.let { btClass24(it) }
        return RadioFacts(
            txPowerDbm = txScan ?: txAdv,
            advFlags = flags,
            appearance = parsed.appearance,
            addressType = addressTypeOf(result.device),
            advertisingIntervalMs = interval,
            periodicIntervalMs = periodic,
            connectable = if (Build.VERSION.SDK_INT >= 26) result.isConnectable else null,
            primaryPhy = if (Build.VERSION.SDK_INT >= 26) phyName(result.primaryPhy) else null,
            secondaryPhy = if (Build.VERSION.SDK_INT >= 26) phyName(result.secondaryPhy) else null,
            deviceClass = deviceClass,
            mfgRecords = mfg,
            serviceData = serviceData,
        )
    }

    data class Parsed(
        val flags: Int? = null,
        val txPower: Int? = null,
        val appearance: Int? = null,
        val advertisingIntervalMs: Double? = null,
        val deviceClass: Int? = null,
        val localName: String? = null,
        val mfg: List<MfgRecord> = emptyList(),
        val serviceData: List<ServiceDataRecord> = emptyList(),
        val uuids: List<String> = emptyList(),
    )

    fun parse(bytes: ByteArray?): Parsed {
        if (bytes == null || bytes.isEmpty()) return Parsed()
        var flags: Int? = null
        var txPower: Int? = null
        var appearance: Int? = null
        var interval: Double? = null
        var deviceClass: Int? = null
        var localName: String? = null
        val mfg = ArrayList<MfgRecord>(2)
        val serviceData = ArrayList<ServiceDataRecord>(2)
        val uuids = ArrayList<String>(4)
        var i = 0
        while (i < bytes.size) {
            val len = bytes[i].toInt() and 0xFF
            if (len == 0) break
            if (i + len >= bytes.size) break
            val type = bytes[i + 1].toInt() and 0xFF
            val start = i + 2
            val end = i + 1 + len
            val data = bytes.copyOfRange(start, end)
            when (type) {
                0x01 -> if (data.isNotEmpty()) flags = data[0].toInt() and 0xFF
                0x02, 0x03, 0x14 -> uuids += uuid16List(data)
                0x04, 0x05, 0x1F -> uuids += uuid32List(data)
                0x06, 0x07, 0x15 -> uuids += uuid128List(data)
                0x08, 0x09 -> {
                    val n = utf8Name(data)
                    if (n.isNotEmpty() && (type == 0x09 || localName == null)) localName = n
                }
                0x0A -> if (data.isNotEmpty()) txPower = data[0].toByte().toInt()
                0x0D -> if (data.size >= 3) {
                    deviceClass = (data[0].toInt() and 0xFF) or
                        ((data[1].toInt() and 0xFF) shl 8) or
                        ((data[2].toInt() and 0xFF) shl 16)
                }
                0x16 -> if (data.size >= 2) {
                    val uuid = "%02X%02X".format(data[1].toInt() and 0xFF, data[0].toInt() and 0xFF)
                    serviceData += ServiceDataRecord(uuid, data.copyOfRange(2, data.size).toHexUpper())
                }
                0x19 -> if (data.size >= 2) {
                    appearance = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
                }
                0x1A -> if (data.size >= 2) {
                    val units = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
                    interval = units * 0.625
                }
                0x20 -> if (data.size >= 4) {
                    val uuid = "%02X%02X%02X%02X".format(
                        data[3].toInt() and 0xFF, data[2].toInt() and 0xFF,
                        data[1].toInt() and 0xFF, data[0].toInt() and 0xFF,
                    )
                    serviceData += ServiceDataRecord(uuid, data.copyOfRange(4, data.size).toHexUpper())
                }
                0x21 -> if (data.size >= 16) {
                    val uuid = uuidFromLe(data.copyOfRange(0, 16))
                    serviceData += ServiceDataRecord(uuid, data.copyOfRange(16, data.size).toHexUpper())
                }
                0xFF -> if (data.size >= 2) {
                    val id = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
                    mfg += MfgRecord(id, data.copyOfRange(2, data.size).toHexUpper())
                }
            }
            i += len + 1
        }
        return Parsed(flags, txPower, appearance, interval, deviceClass, localName, mfg, serviceData, uuids.distinct())
    }

    fun flagsLabel(flags: Int): String = buildList {
        if (flags and 0x01 != 0) add("LE Limited Discoverable")
        if (flags and 0x02 != 0) add("LE General Discoverable")
        if (flags and 0x04 != 0) add("BR/EDR not supported")
        if (flags and 0x08 != 0) add("Simultaneous LE + BR/EDR (Controller)")
        if (flags and 0x10 != 0) add("Simultaneous LE + BR/EDR (Host)")
        if (isEmpty()) add("0x%02X".format(flags))
    }.joinToString(", ")

    fun mfgDecodedFields(record: MfgRecord): List<Pair<String, String>> =
        app.fieldwatch.domain.AdvPayloadDecoder.decodeManufacturer(record).map { it.label to it.value }

    private fun manufacturerRecords(record: ScanRecord?): List<MfgRecord> {
        val data: SparseArray<ByteArray> = record?.manufacturerSpecificData ?: return emptyList()
        if (data.size() == 0) return emptyList()
        val out = ArrayList<MfgRecord>(data.size())
        for (i in 0 until data.size()) {
            val id = data.keyAt(i)
            val bytes = data.valueAt(i) ?: ByteArray(0)
            out += MfgRecord(id, bytes.toHexUpper())
        }
        return out
    }

    private fun serviceDataRecords(record: ScanRecord?): List<ServiceDataRecord> {
        val map = record?.serviceData ?: return emptyList()
        if (map.isEmpty()) return emptyList()
        return map.map { (uuid, bytes) ->
            ServiceDataRecord(uuid.toString().uppercase(), (bytes ?: ByteArray(0)).toHexUpper())
        }
    }

    private fun utf8Name(data: ByteArray): String {
        if (data.isEmpty()) return ""
        return data.toString(Charsets.UTF_8)
            .trim()
            .trim('\u0000')
            .map { if (it.isISOControl()) ' ' else it }
            .joinToString("")
            .trim()
            .take(48)
    }

    private fun uuid16List(data: ByteArray): List<String> =
        data.toList().chunked(2).mapNotNull { pair ->
            if (pair.size < 2) null
            else "%02X%02X".format(pair[1].toInt() and 0xFF, pair[0].toInt() and 0xFF)
        }

    private fun uuid32List(data: ByteArray): List<String> =
        data.toList().chunked(4).mapNotNull { pair ->
            if (pair.size < 4) null
            else "%02X%02X%02X%02X".format(
                pair[3].toInt() and 0xFF, pair[2].toInt() and 0xFF,
                pair[1].toInt() and 0xFF, pair[0].toInt() and 0xFF,
            )
        }

    private fun uuid128List(data: ByteArray): List<String> =
        data.toList().chunked(16).mapNotNull { pair ->
            if (pair.size < 16) null else uuidFromLe(pair.toByteArray())
        }

    private fun uuidFromLe(le: ByteArray): String {
        val be = ByteArray(16)
        for (i in 0 until 16) be[i] = le[15 - i]
        val hi = ((be[0].toLong() and 0xFF) shl 56) or ((be[1].toLong() and 0xFF) shl 48) or
            ((be[2].toLong() and 0xFF) shl 40) or ((be[3].toLong() and 0xFF) shl 32) or
            ((be[4].toLong() and 0xFF) shl 24) or ((be[5].toLong() and 0xFF) shl 16) or
            ((be[6].toLong() and 0xFF) shl 8) or (be[7].toLong() and 0xFF)
        val lo = ((be[8].toLong() and 0xFF) shl 56) or ((be[9].toLong() and 0xFF) shl 48) or
            ((be[10].toLong() and 0xFF) shl 40) or ((be[11].toLong() and 0xFF) shl 32) or
            ((be[12].toLong() and 0xFF) shl 24) or ((be[13].toLong() and 0xFF) shl 16) or
            ((be[14].toLong() and 0xFF) shl 8) or (be[15].toLong() and 0xFF)
        return UUID(hi, lo).toString().uppercase()
    }

    private fun btClass24(cls: BluetoothClass): Int? {
        val major = cls.majorDeviceClass
        val device = cls.deviceClass
        if (major == 0 && device == 0) return null
        var service = 0
        val bits = intArrayOf(
            BluetoothClass.Service.LIMITED_DISCOVERABILITY,
            BluetoothClass.Service.POSITIONING,
            BluetoothClass.Service.NETWORKING,
            BluetoothClass.Service.RENDER,
            BluetoothClass.Service.CAPTURE,
            BluetoothClass.Service.OBJECT_TRANSFER,
            BluetoothClass.Service.AUDIO,
            BluetoothClass.Service.TELEPHONY,
            BluetoothClass.Service.INFORMATION,
        )
        val shifts = intArrayOf(13, 16, 17, 18, 19, 20, 21, 22, 23)
        for (i in bits.indices) {
            if (cls.hasService(bits[i])) service = service or (1 shl shifts[i])
        }
        // Android deviceClass already includes major+minor in the low bits used by the stack.
        return (device and 0x1FFC) or service
    }

    private fun phyName(phy: Int): String? = when (phy) {
        ScanResult.PHY_UNUSED -> null
        1 -> "LE 1M"
        2 -> "LE 2M"
        3 -> "LE Coded"
        else -> "PHY $phy"
    }

    private fun addressTypeOf(device: BluetoothDevice?): String? {
        if (device == null) return null
        // BluetoothDevice.getAddressType() is API 35. The ADDRESS_TYPE_*
        // constants exist earlier; calling the getter on 12–14 is a
        // NoSuchMethodError on the scan callback and kills the process.
        if (Build.VERSION.SDK_INT < 35) return null
        return when (device.addressType) {
            BluetoothDevice.ADDRESS_TYPE_PUBLIC -> "Public"
            BluetoothDevice.ADDRESS_TYPE_RANDOM -> "Random"
            BluetoothDevice.ADDRESS_TYPE_UNKNOWN -> "Unknown"
            BluetoothDevice.ADDRESS_TYPE_ANONYMOUS -> "Anonymous"
            else -> "Type ${device.addressType}"
        }
    }
}
