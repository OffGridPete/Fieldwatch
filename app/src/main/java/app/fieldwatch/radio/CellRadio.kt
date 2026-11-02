package app.fieldwatch.radio

import android.content.Context
import android.os.Build
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import app.fieldwatch.domain.CellField
import app.fieldwatch.domain.CellGen
import app.fieldwatch.domain.CellIdentity
import app.fieldwatch.domain.CellReading

/**
 * Cached serving cell. Location permission is enough.
 * Do not call requestCellInfoUpdate, and do not ask for READ_PHONE_STATE.
 */
object CellRadio {
    fun read(context: Context): CellReading {
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: return CellReading()
        val infos = runCatching { telephony.allCellInfo }.getOrNull().orEmpty()
        val registered = infos.firstOrNull { it.isRegistered }
        val parsed = registered?.let { parse(it) }
        val identity = parsed?.identity()
        val empty = if (registered == null || parsed == null) emptyList() else parsed.emptyFields()
        return CellReading(
            registered = registered != null,
            generation = parsed?.gen,
            identity = identity,
            pci = parsed?.pci,
            channel = parsed?.channel,
            signal = parsed?.signal,
            timingAdvance = parsed?.timing,
            emptyFields = if (identity != null) emptyList() else empty,
            neighborCount = infos.count { !it.isRegistered },
            secondSimRegistered = secondSim(context, telephony),
        )
    }

    private fun secondSim(context: Context, telephony: TelephonyManager): Boolean? = runCatching {
        val manager = context.getSystemService(SubscriptionManager::class.java) ?: return@runCatching null
        // The public list. A missing phone-state grant throws, which is "not returned".
        val infos = manager.activeSubscriptionInfoList ?: return@runCatching null
        if (infos.size < 2) return@runCatching false
        val primary = SubscriptionManager.getDefaultDataSubscriptionId()
        val other = infos.firstOrNull { info ->
            val id = info.subscriptionId
            id != primary && id != SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }?.subscriptionId ?: return@runCatching false
        val cells = telephony.createForSubscriptionId(other).allCellInfo ?: return@runCatching null
        cells.any { it.isRegistered }
    }.getOrNull()

    private data class Parsed(
        val gen: CellGen,
        val plmn: String?,
        val area: Int?,
        val cell: Long?,
        val pci: Int?,
        val channel: Int?,
        val signal: Int?,
        val timing: Int?,
    ) {
        fun identity(): CellIdentity? {
            val network = plmn ?: return null
            val areaCode = area ?: return null
            val cellId = cell ?: return null
            return CellIdentity(gen, network, areaCode, cellId)
        }

        fun emptyFields(): List<CellField> = buildList {
            if (plmn == null) add(CellField.NETWORK)
            if (area == null) add(CellField.AREA)
            if (cell == null) add(CellField.CELL)
        }
    }

    private fun parse(info: CellInfo): Parsed? = when (info) {
        is CellInfoGsm -> {
            val id = info.cellIdentity
            val signal = info.cellSignalStrength
            Parsed(
                CellGen.GSM,
                plmn(id.mccString, id.mncString),
                id.lac.filled(),
                id.cid.filled()?.toLong(),
                id.bsic.filled(),
                id.arfcn.filled(),
                signal.dbm.filled(),
                signal.timingAdvance.filled(),
            )
        }
        is CellInfoWcdma -> {
            val id = info.cellIdentity
            Parsed(
                CellGen.WCDMA,
                plmn(id.mccString, id.mncString),
                id.lac.filled(),
                id.cid.filled()?.toLong(),
                id.psc.filled(),
                id.uarfcn.filled(),
                info.cellSignalStrength.dbm.filled(),
                null,
            )
        }
        is CellInfoTdscdma -> {
            val id = info.cellIdentity
            Parsed(
                CellGen.TDSCDMA,
                plmn(id.mccString, id.mncString),
                id.lac.filled(),
                id.cid.filled()?.toLong(),
                id.cpid.filled(),
                id.uarfcn.filled(),
                info.cellSignalStrength.dbm.filled(),
                null,
            )
        }
        is CellInfoLte -> {
            val id = info.cellIdentity
            val signal = info.cellSignalStrength
            Parsed(
                CellGen.LTE,
                plmn(id.mccString, id.mncString),
                id.tac.filled(),
                id.ci.filled()?.toLong(),
                id.pci.filled(),
                id.earfcn.filled(),
                signal.rsrp.filled() ?: signal.dbm.filled(),
                signal.timingAdvance.filled(),
            )
        }
        is CellInfoNr -> {
            val id = info.cellIdentity as? CellIdentityNr ?: return null
            val signal = info.cellSignalStrength as? CellSignalStrengthNr
            Parsed(
                CellGen.NR,
                plmn(id.mccString, id.mncString),
                id.tac.filled(),
                id.nci.filledLong(),
                id.pci.filled(),
                id.nrarfcn.filled(),
                signal?.ssRsrp?.filled() ?: signal?.dbm?.filled(),
                if (Build.VERSION.SDK_INT >= 34) signal?.timingAdvanceMicros?.filled() else null,
            )
        }
        else -> null
    }

    private fun plmn(mcc: String?, mnc: String?): String? {
        val left = mcc?.trim().orEmpty()
        val right = mnc?.trim().orEmpty()
        if (left.length != 3 || right.length !in 2..3) return null
        if (!left.all { it.isDigit() } || !right.all { it.isDigit() }) return null
        return "$left-$right"
    }

    private fun Int.filled(): Int? =
        if (this == CellInfo.UNAVAILABLE || this == Int.MAX_VALUE) null else this

    private fun Long.filledLong(): Long? =
        if (this == CellInfo.UNAVAILABLE_LONG || this == Long.MAX_VALUE || this == CellInfo.UNAVAILABLE.toLong()) {
            null
        } else {
            this
        }
}
