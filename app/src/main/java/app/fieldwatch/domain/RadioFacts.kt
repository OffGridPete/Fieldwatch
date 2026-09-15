package app.fieldwatch.domain

data class MfgRecord(
    val companyId: Int,
    val dataHex: String,
)

data class VendorIeRecord(
    val oui: String,
    val type: Int,
    val dataHex: String,
)

data class ServiceDataRecord(
    val uuid: String,
    val dataHex: String,
)

data class RadioFacts(
    val txPowerDbm: Int? = null,
    val advFlags: Int? = null,
    val appearance: Int? = null,
    val addressType: String? = null,
    val advertisingIntervalMs: Double? = null,
    val periodicIntervalMs: Double? = null,
    val connectable: Boolean? = null,
    val primaryPhy: String? = null,
    val secondaryPhy: String? = null,
    val deviceClass: Int? = null,
    val wifiStandard: String? = null,
    val channelWidth: String? = null,
    val centerFreq0: Int? = null,
    val centerFreq1: Int? = null,
    val capabilities: String? = null,
    val supportedRates: String? = null,
    val security: String? = null,
    val mfgRecords: List<MfgRecord> = emptyList(),
    val vendorIes: List<VendorIeRecord> = emptyList(),
    val serviceData: List<ServiceDataRecord> = emptyList(),
) {
    fun merge(newer: RadioFacts): RadioFacts = copy(
        txPowerDbm = newer.txPowerDbm ?: txPowerDbm,
        advFlags = newer.advFlags ?: advFlags,
        appearance = newer.appearance ?: appearance,
        addressType = newer.addressType ?: addressType,
        advertisingIntervalMs = newer.advertisingIntervalMs ?: advertisingIntervalMs,
        periodicIntervalMs = newer.periodicIntervalMs ?: periodicIntervalMs,
        // Scan responses report not connectable; keep Yes once any ad was.
        connectable = when {
            connectable == true || newer.connectable == true -> true
            else -> newer.connectable ?: connectable
        },
        primaryPhy = newer.primaryPhy ?: primaryPhy,
        secondaryPhy = newer.secondaryPhy ?: secondaryPhy,
        deviceClass = newer.deviceClass ?: deviceClass,
        wifiStandard = newer.wifiStandard ?: wifiStandard,
        channelWidth = newer.channelWidth ?: channelWidth,
        centerFreq0 = newer.centerFreq0 ?: centerFreq0,
        centerFreq1 = newer.centerFreq1 ?: centerFreq1,
        capabilities = newer.capabilities?.ifBlank { null } ?: capabilities,
        supportedRates = newer.supportedRates?.ifBlank { null } ?: supportedRates,
        security = newer.security?.ifBlank { null } ?: security,
        mfgRecords = mergeMfg(mfgRecords, newer.mfgRecords),
        vendorIes = mergeVendorIes(vendorIes, newer.vendorIes),
        serviceData = mergeServiceData(serviceData, newer.serviceData),
    )

    companion object {
        val Empty = RadioFacts()
    }
}

private fun mergeMfg(old: List<MfgRecord>, extra: List<MfgRecord>): List<MfgRecord> {
    if (extra.isEmpty()) return old
    if (old.isEmpty()) return extra
    val out = ArrayList<MfgRecord>(old.size + extra.size)
    out += old
    for (next in extra) {
        val prefix = next.dataHex.take(2).uppercase()
        val idx = out.indexOfFirst {
            it.companyId == next.companyId && it.dataHex.take(2).uppercase() == prefix
        }
        if (idx < 0) {
            out += next
        } else if (next.dataHex.length >= out[idx].dataHex.length) {
            out[idx] = next
        }
    }
    return if (out.size <= 8) out else out.take(8)
}

private fun mergeVendorIes(old: List<VendorIeRecord>, extra: List<VendorIeRecord>): List<VendorIeRecord> {
    if (extra.isEmpty()) return old
    return (old + extra).distinctBy { it.oui to it.type to it.dataHex.take(16) }.take(12)
}

private fun mergeServiceData(old: List<ServiceDataRecord>, extra: List<ServiceDataRecord>): List<ServiceDataRecord> {
    if (extra.isEmpty()) return old
    val by = LinkedHashMap<String, ServiceDataRecord>()
    old.forEach { by[serviceDataMergeKey(it)] = it }
    extra.forEach { rec ->
        val key = serviceDataMergeKey(rec)
        val prev = by[key]
        if (prev == null || rec.dataHex.length >= prev.dataHex.length) by[key] = rec
    }
    return by.values.toList()
}

/** Eddystone FEAA rotates UID / URL / TLM; keep one slot per frame type. */
private fun serviceDataMergeKey(rec: ServiceDataRecord): String {
    val uuidHex = rec.uuid.filter { it.isLetterOrDigit() }.uppercase()
    val short = when {
        uuidHex.length == 4 -> uuidHex
        uuidHex.length == 32 && uuidHex.startsWith("0000") -> uuidHex.substring(4, 8)
        else -> uuidHex
    }
    if (short == "FEAA") {
        val frame = rec.dataHex.filter { it.isLetterOrDigit() }.uppercase().take(2)
        if (frame.length == 2) return "FEAA:$frame"
    }
    return uuidHex.ifBlank { rec.uuid }
}

fun ByteArray.toHexUpper(): String = joinToString("") { "%02X".format(it) }

fun String.hexSpaced(): String = chunked(2).joinToString(" ")
