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
    val interworking: Interworking? = null,
    /** From the packet just heard. A later successful decode replaces this whole object. */
    val homekit: HomekitFacts? = null,
    /** From the packet just heard. A later parsed FFF6 payload replaces this whole object. */
    val matter: MatterFacts? = null,
    /** From the packet just heard. A later two-byte Sidewalk beacon replaces this whole object. */
    val sidewalk: SidewalkFacts? = null,
    /**
     * Newest FCD2 header. An object ID stays until a later packet sends that ID.
     * An encrypted or non-v2 packet keeps the objects and hides them.
     */
    val bthome: BthomeFacts? = null,
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
        interworking = Interworking.merge(interworking, newer.interworking),
        homekit = newer.homekit ?: homekit,
        matter = newer.matter ?: matter,
        sidewalk = newer.sidewalk ?: sidewalk,
        bthome = when {
            newer.bthome == null -> bthome
            bthome == null -> newer.bthome
            else -> bthome.merge(newer.bthome)
        },
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

/**
 * Other vendor IEs keep the first copy whose OUI, type, and first 16 hex
 * characters match. A French plate's position sits past that prefix, so the
 * newest 6A:5C:35 type 1 replaces the stored one.
 */
private fun mergeVendorIes(old: List<VendorIeRecord>, extra: List<VendorIeRecord>): List<VendorIeRecord> {
    if (extra.isEmpty()) return old
    val out = ArrayList<VendorIeRecord>(12)
    val seen = HashSet<Triple<String, Int, String>>(12)
    var frenchAt = -1
    for (ie in old + extra) {
        if (FrenchRid.isPlate(ie)) {
            if (frenchAt >= 0) {
                out[frenchAt] = ie
            } else if (out.size < 12) {
                frenchAt = out.size
                out += ie
            }
            continue
        }
        if (!seen.add(Triple(ie.oui, ie.type, ie.dataHex.take(16)))) continue
        if (out.size < 12) out += ie
    }
    return out
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

/**
 * Privacy mode shows this instead of raw advertisement hex.
 * A coordinate uses the same word. The byte count stays when the row label does not already have it.
 */
fun maskedRawBytes(hex: String, withByteCount: Boolean): String {
    if (!withByteCount) return "masked"
    return "${hex.length / 2} bytes, masked"
}
