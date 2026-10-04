package app.fieldwatch.domain

import app.fieldwatch.i18n.localized

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class SignaturePack(
    val format: String = FORMAT,
    val formatVersion: Int = FORMAT_VERSION,
    val exportedAt: String = "",
    val appVersion: String = "",
    val catalogVersion: Int = 0,
    val fleets: List<Fleet> = emptyList(),
) {
    companion object {
        const val FORMAT = "fieldwatch-signatures"
        const val LEGACY_FORMAT = "spectre-signatures"
        const val FORMAT_VERSION = 1
    }
}

data class StockCatalogUpdateResult(
    val alreadyLatest: Boolean = false,
    val added: Int = 0,
    val updated: Int = 0,
    val catalogVersion: Int = 0,
    /** Decode maps dropped because this APK does not know their source. Signatures still match. */
    val skippedDecode: Int = 0,
    val error: String? = null,
)

data class ParsedSignaturePack(
    val pack: SignaturePack,
    val skippedDecode: Int = 0,
)

data class SignatureImportResult(
    val added: Int = 0,
    val merged: Int = 0,
    val skipped: Int = 0,
    val renamed: Int = 0,
    val error: String? = null,
) {
    fun summary(): String {
        error?.let { return it }
        if (added == 0 && merged == 0) {
            return if (skipped == 0) localized("signature_exchange_nothing_to_import", "Nothing to import.")
            else localized("signature_exchange_nothing_new_already_on_this_phone", "Nothing new. %1\$s already on this phone.", skipped)
        }
        val parts = mutableListOf<String>()
        if (added > 0) {
            parts += if (renamed > 0) localized("signature_exchange_added_renamed", "Added %1\$s (%2\$s renamed)", added, renamed) else localized("signature_exchange_added", "Added %1\$s", added)
        }
        if (merged > 0) parts += localized("signature_exchange_merged_extra_rules_on", "merged extra rules on %1\$s", merged)
        if (skipped > 0) parts += localized("signature_exchange_skipped_already_present", "skipped %1\$s already present", skipped)
        return parts.joinToString(" · ").replaceFirstChar { it.uppercase() } + "."
    }
}

object SignatureExchange {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun pack(
        fleets: List<Fleet>,
        catalogVersion: Int,
        appVersion: String,
        exportedAt: String,
    ): SignaturePack = SignaturePack(
        format = SignaturePack.FORMAT,
        formatVersion = SignaturePack.FORMAT_VERSION,
        exportedAt = exportedAt,
        appVersion = appVersion,
        catalogVersion = catalogVersion,
        fleets = fleets,
    )

    fun encode(pack: SignaturePack): String = json.encodeToString(SignaturePack.serializer(), pack)

    fun parse(text: String): SignaturePack = parsePack(text).pack

    /**
     * Same as [parse], plus how many decode maps were dropped because [DecodeSource]
     * was unknown. Signatures still import; those rows just have no field map.
     */
    fun parsePack(text: String): ParsedSignaturePack {
        val trimmed = text.trim().trimStart('\uFEFF')
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException(localized("signature_exchange_this_file_is_empty", "This file is empty."))
        }
        val pack = try {
            json.decodeFromString(SignaturePack.serializer(), trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException(
                localized("signature_exchange_not_a_fieldwatch_signature_pack_export_from", "Not a Fieldwatch signature pack. Export from Settings → Export signatures."),
                e,
            )
        }
        if (pack.format != SignaturePack.FORMAT && pack.format != SignaturePack.LEGACY_FORMAT) {
            throw IllegalArgumentException(
                localized("signature_exchange_not_a_fieldwatch_signature_pack_open_a", "Not a Fieldwatch signature pack (open a fieldwatch-signatures JSON file; spectre-signatures still imports)."),
            )
        }
        if (pack.fleets.isEmpty()) {
            throw IllegalArgumentException(localized("signature_exchange_this_pack_has_no_signatures", "This pack has no signatures."))
        }
        var skipped = 0
        val fleets = pack.fleets.map { fleet ->
            if (fleet.decode?.source == DecodeSource.UNSUPPORTED) {
                skipped++
                fleet.copy(decode = null)
            } else {
                fleet
            }
        }
        return ParsedSignaturePack(pack.copy(fleets = fleets), skipped)
    }

    fun merge(existing: List<Fleet>, incoming: List<Fleet>): Pair<List<Fleet>, SignatureImportResult> {
        if (incoming.isEmpty()) {
            return existing to SignatureImportResult(error = localized("signature_exchange_this_pack_has_no_signatures", "This pack has no signatures."))
        }
        val stockIds = DefaultCatalog.fleets().map { it.id }.toSet()
        val next = existing.toMutableList()
        val byId = next.mapIndexed { index, fleet -> fleet.id to index }.toMap().toMutableMap()
        val fingerprints = next.map { fingerprint(it) }.toMutableSet()
        val names = next.map { it.name.trim().lowercase() }.toMutableSet()
        var added = 0
        var merged = 0
        var skipped = 0
        var renamed = 0

        for (raw in incoming) {
            val fleet = raw.copy(
                id = raw.id.ifBlank { UUID.randomUUID().toString() },
                kind = raw.kind.folded(),
            )
            if (fleet.rules.isEmpty()) {
                skipped++
                continue
            }
            val index = byId[fleet.id]
            if (index != null) {
                val local = next[index]
                val have = local.rules.map { ruleKey(it) }.toSet()
                val missing = fleet.rules.filter { ruleKey(it) !in have }
                val decode = fleet.decode ?: local.decode
                val decodeChanged = decode != local.decode
                if (missing.isEmpty() && !decodeChanged) {
                    skipped++
                } else {
                    val updated = local.copy(rules = local.rules + missing, decode = decode)
                    next[index] = updated
                    fingerprints.remove(fingerprint(local))
                    fingerprints.add(fingerprint(updated))
                    merged++
                }
                continue
            }
            val fp = fingerprint(fleet)
            if (fp in fingerprints) {
                skipped++
                continue
            }
            val (name, didRename) = uniqueName(fleet.name, names)
            val stock = fleet.id in stockIds
            val imported = fleet.copy(
                name = name,
                builtIn = stock,
            )
            next += imported
            byId[imported.id] = next.lastIndex
            fingerprints.add(fingerprint(imported))
            names.add(imported.name.trim().lowercase())
            added++
            if (didRename) renamed++
        }
        val sorted = next.sortedBy { it.name.lowercase() }
        return sorted to SignatureImportResult(
            added = added,
            merged = merged,
            skipped = skipped,
            renamed = renamed,
        )
    }

    fun fingerprint(fleet: Fleet): String {
        val rules = fleet.rules.map { ruleKey(it) }.sorted()
        return listOf(
            if (fleet.matchAny) "ANY" else "ALL",
            fleet.minPeers.toString(),
            fleet.peerWindowSec.toString(),
            fleet.clusterByOui.toString(),
            fleet.sequentialMac.toString(),
            rules.joinToString(";"),
        ).joinToString("|")
    }

    fun ruleKey(rule: MatchRule): String =
        listOf(
            rule.kind.name,
            rule.text.uppercase(),
            rule.companyId.toString(),
            rule.dataPrefixHex.uppercase(),
            rule.radio?.name.orEmpty(),
            rule.enabled.toString(),
        ).joinToString("|")

    private fun uniqueName(desired: String, taken: Set<String>): Pair<String, Boolean> {
        val base = desired.trim().ifBlank { "Imported signature" }
        if (base.lowercase() !in taken) return base to false
        val imported = "$base (imported)"
        if (imported.lowercase() !in taken) return imported to true
        var i = 2
        while ("$base (imported $i)".lowercase() in taken) i++
        return "$base (imported $i)" to true
    }

    /**
     * Replace stock rows from a GitHub / file pack. Keeps user mute ([Fleet.enabled]),
     * extra rules they added on a stock id, and every custom row. Does not touch
     * watchlist or Settings.
     */
    fun overlayStock(existing: List<Fleet>, incoming: List<Fleet>): Pair<List<Fleet>, StockCatalogUpdateResult> {
        val stockIn = incoming.filter { it.builtIn || it.id.startsWith("fleet-") }
            .filter { it.rules.isNotEmpty() }
            .map { it.copy(kind = it.kind.folded(), builtIn = true) }
        if (stockIn.isEmpty()) {
            return existing to StockCatalogUpdateResult(error = localized("signature_exchange_this_pack_has_no_stock_signatures", "This pack has no stock signatures."))
        }
        val byId = existing.mapIndexed { index, fleet -> fleet.id to index }.toMap().toMutableMap()
        val next = existing.toMutableList()
        var added = 0
        var updated = 0
        for (stock in stockIn) {
            val index = byId[stock.id]
            if (index == null) {
                next += stock
                byId[stock.id] = next.lastIndex
                added++
                continue
            }
            val local = next[index]
            if (!local.builtIn) continue
            val stockKeys = stock.rules.map { ruleKey(it) }.toSet()
            val extras = local.rules.filter { ruleKey(it) !in stockKeys }
            val overlaid = stock.copy(
                enabled = local.enabled,
                rules = stock.rules + extras,
                builtIn = true,
                decode = stock.decode ?: local.decode,
            )
            if (stockFieldsDiffer(local, overlaid)) {
                next[index] = overlaid
                updated++
            }
        }
        val incomingIds = stockIn.map { it.id }.toSet()
        if ("fleet-unknown" !in incomingIds) {
            next.removeAll { it.builtIn && it.id == "fleet-unknown" }
        }
        val sorted = next.sortedBy { it.name.lowercase() }
        return sorted to StockCatalogUpdateResult(added = added, updated = updated)
    }

    private fun stockFieldsDiffer(local: Fleet, overlaid: Fleet): Boolean {
        if (local.name != overlaid.name) return true
        if (local.kind != overlaid.kind) return true
        if (local.colorIndex != overlaid.colorIndex) return true
        if (local.notes != overlaid.notes) return true
        if (local.attentionNote != overlaid.attentionNote) return true
        if (local.matchAny != overlaid.matchAny) return true
        if (local.minPeers != overlaid.minPeers) return true
        if (local.peerWindowSec != overlaid.peerWindowSec) return true
        if (local.clusterByOui != overlaid.clusterByOui) return true
        if (local.sequentialMac != overlaid.sequentialMac) return true
        if (local.decode != overlaid.decode) return true
        if (local.rules.map { ruleKey(it) } != overlaid.rules.map { ruleKey(it) }) return true
        return false
    }
}
