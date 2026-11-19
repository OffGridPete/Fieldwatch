package app.fieldwatch.domain

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.util.UUID

@Serializable
data class SignaturePack(
    val format: String = FORMAT,
    val formatVersion: Int = FORMAT_VERSION,
    val exportedAt: String = "",
    val appVersion: String = "",
    val catalogVersion: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val publisher: String = "",
    val fleets: List<Fleet> = emptyList(),
) {
    companion object {
        const val FORMAT = "fieldwatch-signatures"
        const val LEGACY_FORMAT = "spectre-signatures"
        const val RSI_FORMAT = "radio-signatures"
        const val PUBLISHER = "fieldwatch"
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
    /** formatVersion is newer than this app. Understood signatures were still read. */
    val newer: Boolean = false,
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
            return if (skipped == 0) "Nothing to import."
            else "Nothing new. $skipped already on this phone."
        }
        val parts = mutableListOf<String>()
        if (added > 0) {
            parts += if (renamed > 0) "Added $added ($renamed renamed)" else "Added $added"
        }
        if (merged > 0) parts += "merged extra rules on $merged"
        if (skipped > 0) parts += "skipped $skipped already present"
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

    /** Phone export. Baseline [pack] stays fieldwatch-signatures with no publisher. */
    fun exportPack(
        fleets: List<Fleet>,
        catalogVersion: Int,
        appVersion: String,
        exportedAt: String,
    ): SignaturePack = SignaturePack(
        format = SignaturePack.RSI_FORMAT,
        formatVersion = SignaturePack.FORMAT_VERSION,
        exportedAt = exportedAt,
        appVersion = appVersion,
        catalogVersion = catalogVersion,
        publisher = SignaturePack.PUBLISHER,
        fleets = fleets.map { it.forExport() },
    )

    fun encode(pack: SignaturePack): String {
        val text = json.encodeToString(SignaturePack.serializer(), pack)
        if (pack.fleets.none { it.kindRaw.isNotEmpty() }) return text
        val root = json.parseToJsonElement(text).jsonObject.toMutableMap()
        val encoded = root.getValue("fleets") as JsonArray
        root["fleets"] = JsonArray(
            encoded.mapIndexed { index, element ->
                val raw = pack.fleets[index].kindRaw
                if (raw.isEmpty()) {
                    element
                } else {
                    val obj = element.jsonObject.toMutableMap()
                    obj["kind"] = JsonPrimitive(raw)
                    obj.remove("kindRaw")
                    JsonObject(obj)
                }
            },
        )
        val element: JsonElement = JsonObject(root)
        return json.encodeToString(JsonElement.serializer(), element)
    }

    fun parse(text: String): SignaturePack = parsePack(text).pack

    /**
     * Same as [parse], plus how many decode maps were dropped because [DecodeSource]
     * was unknown. Signatures still import; those rows just have no field map.
     */
    fun parsePack(text: String): ParsedSignaturePack {
        val trimmed = text.trim().trimStart('\uFEFF')
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("This file is empty.")
        }
        val root = try {
            json.parseToJsonElement(trimmed)
        } catch (e: Exception) {
            e.needsNewerApp()?.let { throw it }
            throw IllegalArgumentException(
                "Not a Fieldwatch signature pack. Export from Settings → Export signatures.",
                e,
            )
        }
        val pack = try {
            json.decodeFromJsonElement(SignaturePack.serializer(), root)
        } catch (e: Exception) {
            e.needsNewerApp()?.let { throw it }
            throw IllegalArgumentException(
                "Not a Fieldwatch signature pack. Export from Settings → Export signatures.",
                e,
            )
        }
        if (
            pack.format != SignaturePack.FORMAT &&
            pack.format != SignaturePack.LEGACY_FORMAT &&
            pack.format != SignaturePack.RSI_FORMAT
        ) {
            throw IllegalArgumentException(
                "Not a Fieldwatch signature pack (open a fieldwatch-signatures file; .rsi and older JSON both import).",
            )
        }
        if (pack.fleets.isEmpty()) {
            throw IllegalArgumentException("This pack has no signatures.")
        }
        val rawFleets = (root as? JsonObject)?.get("fleets") as? JsonArray
        var skipped = 0
        val fleets = pack.fleets.mapIndexed { index, fleet ->
            var next = fleet
            val rawKind = rawKindToken(rawFleets, index)
            if (rawKind != null && SignatureClass.entries.none { it.name == rawKind }) {
                next = next.copy(kind = SignatureClass.OTHER, kindRaw = rawKind)
            }
            if (next.decode?.source == DecodeSource.UNSUPPORTED) {
                skipped++
                next = next.copy(decode = null)
            }
            next
        }
        return ParsedSignaturePack(
            pack = pack.copy(fleets = fleets),
            skippedDecode = skipped,
            newer = pack.formatVersion > SignaturePack.FORMAT_VERSION,
        )
    }

    private fun rawKindToken(fleets: JsonArray?, index: Int): String? {
        val obj = fleets?.getOrNull(index) as? JsonObject ?: return null
        val kind = obj["kind"] as? JsonPrimitive ?: return null
        return kind.content.takeIf { kind.isString }
    }

    fun merge(existing: List<Fleet>, incoming: List<Fleet>): Pair<List<Fleet>, SignatureImportResult> {
        if (incoming.isEmpty()) {
            return existing to SignatureImportResult(error = "This pack has no signatures.")
        }
        val stockIds = DefaultCatalog.fleets().map { it.id }.toSet()
        val next = existing.toMutableList()
        val fingerprints = next.map { fingerprint(it) }.toMutableSet()
        val names = next.map { it.name.trim().lowercase() }.toMutableSet()
        var added = 0
        var merged = 0
        var skipped = 0
        var renamed = 0

        for (raw in incoming) {
            val fleet = raw.copy(
                id = raw.id.ifBlank { UUID.randomUUID().toString() },
            )
            if (fleet.rules.isEmpty()) {
                skipped++
                continue
            }
            val index = indexOfSame(next, fleet)
            if (index >= 0) {
                val local = next[index]
                val have = local.rules.map { ruleKey(it) }.toSet()
                val missing = fleet.rules.filter { ruleKey(it) !in have }
                val decode = fleet.decode ?: local.decode
                val decodeChanged = decode != local.decode
                val filledPublisher = filledPublisher(local.publisher, fleet.publisher)
                if (missing.isEmpty() && !decodeChanged) {
                    if (filledPublisher != local.publisher) {
                        val updated = local.copy(publisher = filledPublisher)
                        fingerprints.remove(fingerprint(local))
                        fingerprints.add(fingerprint(updated))
                        next[index] = updated
                    }
                    skipped++
                } else {
                    val updated = local.copy(
                        rules = local.rules + missing,
                        decode = decode,
                        publisher = filledPublisher,
                    )
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
            val storedId = storageId(next, fleet)
            val stock = fleet.exportId() in stockIds &&
                (fleet.publisher.isEmpty() || fleet.publisher == SignaturePack.PUBLISHER)
            val imported = fleet.copy(
                id = storedId,
                name = name,
                builtIn = stock,
            )
            next += imported
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
            fleet.publisher,
            if (fleet.matchAny) "ANY" else "ALL",
            fleet.minPeers.toString(),
            fleet.peerWindowSec.toString(),
            fleet.clusterByOui.toString(),
            fleet.sequentialMac.toString(),
            rules.joinToString(";"),
        ).joinToString("|")
    }

    fun ruleKey(rule: MatchRule): String {
        val foreign = rule.foreign
        if (foreign != null) return "FOREIGN|$foreign"
        return listOf(
            rule.kind.name,
            rule.text.uppercase(),
            rule.companyId.toString(),
            rule.dataPrefixHex.uppercase(),
            rule.radio?.name.orEmpty(),
            rule.enabled.toString(),
        ).joinToString("|")
    }

    private fun publishersCompatible(a: String, b: String): Boolean =
        a.isEmpty() || b.isEmpty() || a == b

    /** An empty publisher is the same row. Two different publishers are two rows. */
    private fun indexOfSame(list: List<Fleet>, incoming: Fleet): Int {
        val want = incoming.exportId()
        val hits = ArrayList<Int>(2)
        list.forEachIndexed { index, local ->
            if (local.exportId() != want) return@forEachIndexed
            if (!publishersCompatible(local.publisher, incoming.publisher)) return@forEachIndexed
            hits += index
        }
        if (hits.isEmpty()) return -1
        if (incoming.publisher.isNotEmpty()) {
            hits.firstOrNull { list[it].publisher == incoming.publisher }?.let { return it }
            return hits.firstOrNull { list[it].publisher.isEmpty() } ?: -1
        }
        hits.firstOrNull { list[it].publisher.isEmpty() }?.let { return it }
        hits.firstOrNull { list[it].publisher == SignaturePack.PUBLISHER }?.let { return it }
        return hits.first()
    }

    private fun filledPublisher(local: String, incoming: String): String =
        if (local.isEmpty() && incoming.isNotEmpty()) incoming else local

    private fun storageId(existing: List<Fleet>, fleet: Fleet): String {
        val raw = fleet.exportId()
        if (existing.none { it.id == raw }) return raw
        if (fleet.publisher.isEmpty()) return raw
        return "${fleet.publisher}:$raw"
    }

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
            .map { it.copy(builtIn = true) }
        if (stockIn.isEmpty()) {
            return existing to StockCatalogUpdateResult(error = "This pack has no stock signatures.")
        }
        val next = existing.toMutableList()
        var added = 0
        var updated = 0
        for (stock in stockIn) {
            val index = indexOfSame(next, stock)
            if (index < 0) {
                next += stock.copy(id = storageId(next, stock), builtIn = true)
                added++
                continue
            }
            val local = next[index]
            if (!local.builtIn) continue
            val stockKeys = stock.rules.map { ruleKey(it) }.toSet()
            val extras = local.rules.filter { ruleKey(it) !in stockKeys }
            val filled = filledPublisher(local.publisher, stock.publisher)
            val overlaid = stock.copy(
                id = local.id,
                enabled = local.enabled,
                rules = stock.rules + extras,
                builtIn = true,
                decode = stock.decode ?: local.decode,
                publisher = filled,
            )
            val changed = stockFieldsDiffer(local, overlaid)
            if (changed || filled != local.publisher) {
                next[index] = overlaid
                if (changed) updated++
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
        if (local.kindRaw != overlaid.kindRaw) return true
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
