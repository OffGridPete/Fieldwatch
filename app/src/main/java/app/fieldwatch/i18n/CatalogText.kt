package app.fieldwatch.i18n

import app.fieldwatch.domain.*

/** An overlay on untouched stock fields; no translated text is persisted as a rule. */
object CatalogText {
    private val stock by lazy { DefaultCatalog.fleets().associateBy { it.id } }
    private val presets by lazy { FilterEngine().defaultPresets().associateBy { it.id } }

    fun field(fleet: Fleet, field: String, value: String): String {
        if (!fleet.builtIn) return value
        val original = stock[fleet.id] ?: return value
        val expected = when (field) {
            "name" -> original.name
            "notes" -> original.notes
            "attention" -> original.attentionNote
            else -> return value
        }
        if (value != expected) return value
        return localized("catalog_${fleet.id.replace('-', '_')}_$field", value)
    }

    fun preset(preset: FilterPreset): String {
        val original = presets[preset.id] ?: return preset.name
        if (preset.name != original.name) return preset.name
        return localized("preset_${preset.id.replace('-', '_')}", preset.name)
    }

    /** Editors render stock text without replacing the underlying editable map. */
    fun decodeField(fleet: Fleet, field: DecodeField, part: String, value: String, enumKey: String = ""): String {
        if (!fleet.builtIn) return value
        val source = stock[fleet.id]?.decode?.fields?.firstOrNull { it.id == field.id } ?: return value
        val expected = when (part) {
            "label" -> source.label
            "enum" -> source.enumLabels?.get(enumKey)
            "note" -> source.enumNotes?.get(enumKey)
            else -> return value
        }
        if (value != expected) return value
        val base = "catalog_${fleet.id.replace('-', '_')}_decode_${field.id.replace('-', '_')}_$part"
        return localized(if (part == "label") base else "${base}_${resourceKey(enumKey)}", value)
    }

    fun decoded(row: DecodedFieldValue, fleets: List<Fleet>): DecodedFieldValue {
        val fleet = fleets.firstOrNull { it.id == row.fleetId && it.builtIn } ?: return row
        val original = stock[fleet.id] ?: return row
        val field = fleet.decode?.fields?.firstOrNull { it.id == row.id } ?: return row
        val source = original.decode?.fields?.firstOrNull { it.id == row.id } ?: return row
        val key = "catalog_${fleet.id.replace('-', '_')}_decode_${row.id.replace('-', '_')}"
        val label = if (row.label == source.label) localized("${key}_label", row.label) else row.label
        val unit = field.unit?.trim().orEmpty()
        val unitSuffix = if (unit.isEmpty()) "" else " $unit"
        val value = row.display.removeSuffix(unitSuffix)
        val named = source.enumLabels?.entries?.firstOrNull { (id, text) ->
            text == value && field.enumLabels?.get(id) == text
        }
        val display = if (named != null) localized("${key}_enum_${resourceKey(named.key)}", value) + unitSuffix
            else if (field.type == DecodeType.BOOL && field.enumLabels.isNullOrEmpty() && value in listOf("yes", "no"))
                localized("decode_boolean_$value", value) + unitSuffix
            else row.display
        val note = source.enumNotes?.entries?.firstOrNull { (id, text) ->
            text.trim() == row.note && field.enumNotes?.get(id)?.trim() == row.note
        }?.let { localized("${key}_note_${resourceKey(it.key)}", row.note) } ?: row.note
        return row.copy(fleetName = fleet.displayName(), label = label, display = display, note = note)
    }

    private fun resourceKey(value: String): String = value.replace('-', '_').replace(Regex("[^a-zA-Z0-9_]"), "_")
}

fun List<DecodedFieldValue>.forDisplay(fleets: List<Fleet>): List<DecodedFieldValue> = map { CatalogText.decoded(it, fleets) }

/** Re-render cached parse results, keeping recorded payloads and user decode maps intact. */
fun Sighting.displayLiveDecode(fleets: List<Fleet>): List<LiveDecodeChip> {
    val parsed = SignatureFieldDecoder.decodeSighting(this, fleets).forDisplay(fleets)
    if (parsed.isEmpty()) return liveDecode
    return parsed.filter { it.live && it.display.isNotBlank() }.distinctBy { it.display.lowercase() }
        .map { LiveDecodeChip(it.display.trim(), it.emphasis, it.note.trim()) }
}

fun Fleet.displayName(): String = CatalogText.field(this, "name", name)
fun Fleet.displayNotes(): String = CatalogText.field(this, "notes", notes)
fun Fleet.displayAttention(): String = CatalogText.field(this, "attention", attentionNote)

fun Sighting.displayAttentionNotes(fleets: List<Fleet>): List<Pair<String, String>> =
    fleetIds.mapNotNull { id -> fleets.firstOrNull { it.id == id && it.attentionNote.isNotBlank() }?.let { it.displayName() to it.displayAttention() } }

fun Sighting.displaySignatureNotes(fleets: List<Fleet>): List<Pair<String, String>> =
    fleetIds.mapNotNull { id -> fleets.firstOrNull { it.id == id && it.notes.isNotBlank() }?.let { it.displayName() to it.displayNotes() } }
