package app.fieldwatch.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import app.fieldwatch.ui.component.FieldwatchFilterChip
import app.fieldwatch.ui.component.FieldwatchActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fieldwatch.domain.DecodedFieldValue
import app.fieldwatch.domain.DecodeEndian
import app.fieldwatch.domain.DecodeField
import app.fieldwatch.domain.DecodeSource
import app.fieldwatch.domain.DecodeType
import app.fieldwatch.domain.DecodeWhen
import app.fieldwatch.domain.DecodeWhenOp
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.FleetDecode
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.SignatureFieldDecoder
import app.fieldwatch.domain.defaultLength
import app.fieldwatch.domain.hexSpaced
import app.fieldwatch.domain.normalized
import app.fieldwatch.domain.resolvedLength
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.component.SectionCard
import app.fieldwatch.ui.component.spectreTileFill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecodeFieldsScreen(
    fleet: Fleet,
    previewDevice: Sighting?,
    onSave: (FleetDecode?) -> Unit,
    onBack: () -> Unit,
) {
    val initial = fleet.decode
    var source by remember { mutableStateOf(initial?.source ?: DecodeSource.MANUFACTURER_DATA) }
    var companyText by remember {
        mutableStateOf(initial?.companyId?.takeIf { it != 0 }?.let { "0x%04X".format(it) }.orEmpty())
    }
    var serviceUuid by remember { mutableStateOf(initial?.serviceUuid.orEmpty()) }
    var fields by remember { mutableStateOf(initial?.fields ?: emptyList()) }
    var confirmRemove by remember { mutableStateOf(false) }

    fun currentDecode(): FleetDecode? {
        val cleaned = fields.filter { it.label.isNotBlank() && it.id.isNotBlank() }.map { it.normalized() }
        if (cleaned.isEmpty()) return null
        return FleetDecode(
            source = source,
            serviceUuid = serviceUuid.trim().ifBlank { null },
            companyId = parseCompanyId(companyText),
            fields = cleaned,
        )
    }

    val previewDecode = currentDecode()
    val previewRows = remember(previewDecode, previewDevice) {
        if (previewDecode == null || previewDevice == null) emptyList()
        else SignatureFieldDecoder.decodeFleet(fleet.copy(decode = previewDecode), previewDecode, previewDevice)
    }
    val previewHex = remember(previewDecode, previewDevice) {
        if (previewDecode == null || previewDevice == null) null
        else SignatureFieldDecoder.payloadHex(previewDecode, previewDevice)
    }

    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = {
            NestedTopBar(
                title = "Decode fields",
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = { TextButton(onClick = { onSave(currentDecode()) }) { Text("Save") } },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard("Source") {
            Text(
                "Map cleartext BLE bytes after this signature matches. Encrypted ads stay hex.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldwatchFilterChip(
                    selected = source == DecodeSource.MANUFACTURER_DATA,
                    onClick = { source = DecodeSource.MANUFACTURER_DATA },
                    label = { Text("Manufacturer") },
                )
                FieldwatchFilterChip(
                    selected = source == DecodeSource.SERVICE_DATA,
                    onClick = { source = DecodeSource.SERVICE_DATA },
                    label = { Text("Service data") },
                )
            }
            if (source == DecodeSource.MANUFACTURER_DATA) {
                CompactField(
                    companyText,
                    { companyText = it },
                    "Company ID",
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Optional. Byte 0 is the first byte after the company ID. Empty = any record.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                CompactField(
                    serviceUuid,
                    { serviceUuid = it },
                    "Service UUID",
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Required. 16-bit (FEAA) or full UUID.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }

            SectionCard("Fields") {
            fields.forEachIndexed { index, field ->
                FieldCard(
                    field = field,
                    onChange = { next ->
                        fields = fields.toMutableList().also { it[index] = next }
                    },
                    onDelete = {
                        fields = fields.filterIndexed { i, _ -> i != index }
                    },
                )
            }
            FieldwatchActionButton(
                onClick = {
                    val nextOffset = fields.lastOrNull()?.let { it.offset + it.resolvedLength() } ?: 0
                    val n = fields.size + 1
                    fields = fields + DecodeField(
                        id = "field_$n",
                        label = "Field $n",
                        offset = nextOffset,
                        type = DecodeType.U8,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add field") }
            if (initial != null || fields.isNotEmpty()) {
                FieldwatchActionButton(
                    onClick = { confirmRemove = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Remove decode map") }
                Text(
                    "Removes every field and the Live code mark. Save after adding fields still keeps the map.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }

            SectionCard("Preview") {
            PreviewBlock(
                previewDevice = previewDevice,
                previewHex = previewHex,
                previewRows = previewRows,
            )
            }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove decode map?") },
            text = {
                Text("Clears all fields on this signature. Live no longer shows the code mark. Raw advertisements stay. This cannot be undone except by adding fields again.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        onSave(null)
                    },
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PreviewBlock(
    previewDevice: Sighting?,
    previewHex: String?,
    previewRows: List<DecodedFieldValue>,
) {
    when {
        previewDevice == null -> {
            Text(
                "No matching radio on the air. Save anyway; detail will fill in when one is heard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        previewHex.isNullOrBlank() -> {
            Text(
                "A matching radio is on the air, but this advertisement has no bytes for the source above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> {
            Text(
                previewHex.hexSpaced(),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (previewRows.isEmpty()) {
                Text(
                    "Nothing parsed. Check offset, length, and that byte 0 is after the company ID.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                previewRows.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            row.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Text(row.display, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldCard(
    field: DecodeField,
    onChange: (DecodeField) -> Unit,
    onDelete: () -> Unit,
) {
    val needsEndian = field.type !in setOf(
        DecodeType.U8, DecodeType.I8, DecodeType.UTF8, DecodeType.HEX, DecodeType.BOOL,
    )
    val numeric = field.type in setOf(
        DecodeType.U8, DecodeType.I8, DecodeType.U16, DecodeType.I16,
        DecodeType.U24, DecodeType.U32, DecodeType.I32, DecodeType.F32, DecodeType.BITS,
    )
    val idIsCustom = !looksGeneratedId(field.id, field.label)
    var more by remember(field.id) {
        mutableStateOf(idIsCustom || field.modulo != null)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = spectreTileFill(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactField(
                    field.label,
                    { next ->
                        val id = if (looksGeneratedId(field.id, field.label)) slugId(next) else field.id
                        onChange(field.copy(label = next, id = id))
                    },
                    "Label",
                    modifier = Modifier.weight(1f),
                )
                TypeMenu(field.type, Modifier.width(112.dp)) { onChange(field.copy(type = it)) }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete field") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactField(
                    field.offset.toString(),
                    { onChange(field.copy(offset = it.toIntOrNull() ?: 0)) },
                    "Offset",
                    keyboard = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                CompactField(
                    (field.length ?: field.type.defaultLength()).toString(),
                    { onChange(field.copy(length = it.toIntOrNull()?.coerceAtLeast(1))) },
                    "Length",
                    keyboard = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                CompactField(
                    field.unit.orEmpty(),
                    { onChange(field.copy(unit = it.ifBlank { null })) },
                    "Unit",
                    modifier = Modifier.width(72.dp),
                )
                if (needsEndian) {
                    EndianMenu(field.endian, Modifier.weight(1f)) { onChange(field.copy(endian = it)) }
                }
            }
            if (field.type == DecodeType.BITS) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactField(
                        (field.bitOffset ?: 0).toString(),
                        { onChange(field.copy(bitOffset = it.toIntOrNull() ?: 0)) },
                        "Bit offset",
                        keyboard = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    CompactField(
                        (field.bitWidth ?: 1).toString(),
                        { onChange(field.copy(bitWidth = it.toIntOrNull()?.coerceAtLeast(1) ?: 1)) },
                        "Bit width",
                        keyboard = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (numeric) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactField(
                        field.scale?.toString().orEmpty(),
                        { onChange(field.copy(scale = it.toDoubleOrNull())) },
                        "Scale",
                        keyboard = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    CompactField(
                        field.offsetAdd?.toString().orEmpty(),
                        { onChange(field.copy(offsetAdd = it.toDoubleOrNull())) },
                        "Add",
                        keyboard = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            OnlyIfBlock(field.gate, onChange = { onChange(field.copy(gate = it)) })
            NamedValuesBlock(field.id, field.enumLabels, onChange = { onChange(field.copy(enumLabels = it)) })
            if (!more) {
                TextButton(onClick = { more = true }) { Text("More") }
            } else {
                CompactField(
                    field.id,
                    { onChange(field.copy(id = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' })) },
                    "ID",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (numeric) {
                    CompactField(
                        field.modulo?.toString().orEmpty(),
                        { onChange(field.copy(modulo = it.toDoubleOrNull())) },
                        "Modulo",
                        keyboard = KeyboardType.Decimal,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = { more = false }) { Text("Hide extra") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlyIfBlock(gate: DecodeWhen?, onChange: (DecodeWhen?) -> Unit) {
    if (gate == null) {
        TextButton(onClick = {
            onChange(DecodeWhen(offset = 0, length = 1, op = DecodeWhenOp.EQ, valueHex = ""))
        }) { Text("Only if…") }
        return
    }
    Text("Only if", style = MaterialTheme.typography.titleSmall)
    if (gate.op == DecodeWhenOp.LEN) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WhenOpMenu(gate.op, Modifier.weight(1f)) { onChange(gate.copy(op = it)) }
            CompactField(
                gate.length.toString(),
                { onChange(gate.copy(length = it.toIntOrNull()?.coerceAtLeast(1) ?: 1)) },
                "Bytes",
                keyboard = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactField(
                gate.offset.toString(),
                { onChange(gate.copy(offset = it.toIntOrNull() ?: 0)) },
                "Offset",
                keyboard = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            CompactField(
                gate.length.toString(),
                { onChange(gate.copy(length = it.toIntOrNull()?.coerceAtLeast(1) ?: 1)) },
                "Length",
                keyboard = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WhenOpMenu(gate.op, Modifier.weight(1f)) { onChange(gate.copy(op = it)) }
            CompactField(
                gate.valueHex,
                { onChange(gate.copy(valueHex = it)) },
                "Hex",
                modifier = Modifier.width(96.dp),
            )
        }
    }
    TextButton(onClick = { onChange(null) }) { Text("Remove") }
}

@Composable
private fun NamedValuesBlock(
    fieldId: String,
    labels: Map<String, String>?,
    onChange: (Map<String, String>?) -> Unit,
) {
    var rows by remember(fieldId) {
        mutableStateOf(labels?.toList() ?: emptyList())
    }
    var open by remember(fieldId) { mutableStateOf(rows.isNotEmpty()) }
    if (!open) {
        TextButton(onClick = {
            open = true
            rows = listOf("" to "")
        }) { Text("Named values…") }
        return
    }
    Text("Named values", style = MaterialTheme.typography.titleSmall)
    rows.forEachIndexed { index, (raw, shown) ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactField(
                raw,
                { next ->
                    val nextRows = rows.toMutableList().also { it[index] = next to shown }
                    rows = nextRows
                    onChange(nextRows.toEnumMap())
                },
                "Raw",
                modifier = Modifier.width(88.dp),
            )
            CompactField(
                shown,
                { next ->
                    val nextRows = rows.toMutableList().also { it[index] = raw to next }
                    rows = nextRows
                    onChange(nextRows.toEnumMap())
                },
                "Show as",
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    val nextRows = rows.filterIndexed { i, _ -> i != index }
                    rows = nextRows
                    if (nextRows.isEmpty()) {
                        open = false
                        onChange(null)
                    } else {
                        onChange(nextRows.toEnumMap())
                    }
                },
            ) { Icon(Icons.Outlined.Delete, "Delete value") }
        }
    }
    Row {
        TextButton(onClick = { rows = rows + ("" to "") }) { Text("Add value") }
        TextButton(
            onClick = {
                open = false
                rows = emptyList()
                onChange(null)
            },
        ) { Text("Remove") }
    }
}

@Composable
private fun CompactField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value,
        onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhenOpMenu(op: DecodeWhenOp, modifier: Modifier, onChange: (DecodeWhenOp) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = when (op) {
        DecodeWhenOp.EQ -> "equals"
        DecodeWhenOp.NEQ -> "not equals"
        DecodeWhenOp.MASK -> "mask"
        DecodeWhenOp.LEN -> "length"
    }
    ExposedDropdownMenuBox(open, { open = it }, modifier) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("When") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text("equals") }, onClick = { onChange(DecodeWhenOp.EQ); open = false })
            DropdownMenuItem(text = { Text("not equals") }, onClick = { onChange(DecodeWhenOp.NEQ); open = false })
            DropdownMenuItem(text = { Text("mask") }, onClick = { onChange(DecodeWhenOp.MASK); open = false })
            DropdownMenuItem(text = { Text("payload length") }, onClick = { onChange(DecodeWhenOp.LEN); open = false })
        }
    }
}

private fun List<Pair<String, String>>.toEnumMap(): Map<String, String>? {
    val out = linkedMapOf<String, String>()
    for ((k, v) in this) {
        val key = k.trim()
        val label = v.trim()
        if (key.isEmpty() || label.isEmpty()) continue
        out[key] = label
    }
    return out.ifEmpty { null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeMenu(type: DecodeType, modifier: Modifier, onChange: (DecodeType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }, modifier) {
        OutlinedTextField(
            value = type.name.lowercase(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(open, { open = false }) {
            DecodeType.entries.forEach { t ->
                DropdownMenuItem(
                    text = { Text(t.name.lowercase()) },
                    onClick = { onChange(t); open = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndianMenu(endian: DecodeEndian, modifier: Modifier, onChange: (DecodeEndian) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }, modifier) {
        OutlinedTextField(
            value = if (endian == DecodeEndian.BE) "BE" else "LE",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Endian") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text("little") }, onClick = { onChange(DecodeEndian.LE); open = false })
            DropdownMenuItem(text = { Text("big") }, onClick = { onChange(DecodeEndian.BE); open = false })
        }
    }
}

private fun parseCompanyId(text: String): Int? {
    val t = text.trim()
    if (t.isEmpty()) return null
    val hex = t.removePrefix("0x").removePrefix("0X")
    return hex.toIntOrNull(16) ?: t.toIntOrNull()
}

private fun slugId(label: String): String {
    val slug = label.lowercase().map { ch ->
        if (ch.isLetterOrDigit()) ch else '_'
    }.joinToString("").trim('_')
    val clipped = slug.take(32).ifBlank { "field" }
    return if (clipped.first().isLetter()) clipped else "f_$clipped"
}

private fun looksGeneratedId(id: String, label: String): Boolean =
    id == slugId(label) || id.matches(Regex("field_\\d+"))
