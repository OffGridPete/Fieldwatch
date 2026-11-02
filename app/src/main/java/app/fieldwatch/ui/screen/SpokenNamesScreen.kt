package app.fieldwatch.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fieldwatch.domain.AlertVoiceWhat
import app.fieldwatch.domain.LanguageText
import app.fieldwatch.domain.SpokenName
import app.fieldwatch.domain.SpokenNames
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.component.FieldwatchActionButton
import app.fieldwatch.ui.component.FieldwatchFilterChip
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import app.fieldwatch.ui.component.FieldwatchSwitch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpokenNamesScreen(
    state: FieldwatchUi,
    vm: FieldwatchViewModel,
    onBack: () -> Unit,
) {
    val rows = state.settings.spokenNames.sortedWith(
        compareBy({ fleetTitle(state, it.fleetId).lowercase() }, { it.networkName.lowercase() }),
    )
    var editId by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    val editing = rows.firstOrNull { it.id == editId }
    val deleting = rows.firstOrNull { it.id == deleteId }
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = {
            NestedTopBar(
                title = LanguageText.text("settings.spoken.names"),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, LanguageText.text("chrome.back"))
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    LanguageText.text("spoken.name.note"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.settings.alertVoiceWhat == AlertVoiceWhat.CLASS && rows.isNotEmpty()) {
                item {
                    Text(
                        LanguageText.text("spoken.class.only"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (rows.isEmpty()) {
                item {
                    Text(
                        LanguageText.text("spoken.empty"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            } else {
                items(rows, key = { it.id }) { row ->
                    SpokenNameRow(
                        signature = fleetTitle(state, row.fleetId),
                        network = row.networkName,
                        label = row.label,
                        onEdit = { editId = row.id },
                        onDelete = { deleteId = row.id },
                    )
                }
            }
        }
    }
    if (editing != null) {
        SpokenNameEditDialog(
            signature = fleetTitle(state, editing.fleetId),
            network = editing.networkName,
            label = editing.label,
            onDismiss = { editId = null },
            onSave = { network, label ->
                vm.updateSpokenName(editing.id, network, label)
                editId = null
            },
        )
    }
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text(LanguageText.text("spoken.remove")) },
            text = { Text(LanguageText.text("spoken.remove.ask")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteSpokenName(deleting.id)
                        deleteId = null
                    },
                ) { Text(LanguageText.text("spoken.remove")) }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) { Text(LanguageText.text("language.cancel")) }
            },
        )
    }
}

@Composable
private fun SpokenNameRow(
    signature: String,
    network: String,
    label: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$signature · $network",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, LanguageText.text("spoken.name"))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, LanguageText.text("spoken.remove"))
            }
        }
    }
}

@Composable
private fun SpokenNameEditDialog(
    signature: String,
    network: String,
    label: String,
    onDismiss: () -> Unit,
    onSave: (network: String, label: String) -> Unit,
) {
    var net by remember(signature, network, label) { mutableStateOf(network) }
    var words by remember(signature, network, label) { mutableStateOf(label) }
    val clippedNet = net.trim().take(SpokenNames.MAX_NETWORK)
    val clippedWords = words.trim().take(SpokenNames.MAX_LABEL)
    val unchanged = clippedNet == network.trim() && clippedWords == label.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LanguageText.text("spoken.name")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    signature,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                FieldwatchOutlinedField(
                    value = net,
                    onValueChange = { net = it.take(SpokenNames.MAX_NETWORK) },
                    label = LanguageText.text("spoken.network"),
                )
                FieldwatchOutlinedField(
                    value = words,
                    onValueChange = { words = it.take(SpokenNames.MAX_LABEL) },
                    label = LanguageText.text("spoken.words"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(clippedNet, clippedWords) },
                enabled = clippedNet.isNotEmpty() && clippedWords.isNotEmpty() && !unchanged,
            ) { Text(LanguageText.text("ui.save.label")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(LanguageText.text("language.cancel")) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpokenNameCard(
    deviceKey: String,
    network: String,
    fleetIds: List<String>,
    spokenNames: List<SpokenName>,
    voiceWhat: AlertVoiceWhat,
    individualName: String?,
    fleetName: (String) -> String,
    onSave: (fleetId: String, network: String, label: String) -> Unit,
    onRemove: (fleetId: String, network: String) -> Unit,
) {
    val fleetKey = fleetIds.joinToString("\u0000")
    var picked by remember(deviceKey, network, fleetKey) {
        mutableStateOf(SpokenNames.preferredFleet(fleetIds, spokenNames, network) ?: fleetIds.first())
    }
    val pickedOk = if (picked in fleetIds) picked else fleetIds.first()
    val stored = SpokenNames.find(spokenNames, pickedOk, network)?.label.orEmpty()
    var draft by remember(deviceKey, pickedOk, network) { mutableStateOf(stored) }
    var askRemove by remember(deviceKey, pickedOk) { mutableStateOf(false) }
    val clipped = draft.trim().take(SpokenNames.MAX_LABEL)
    val saved = stored.isNotBlank() && clipped == stored.trim()
    val hasSaved = fleetIds.any { SpokenNames.find(spokenNames, it, network) != null }
    var formChoice by remember(deviceKey, network, fleetKey) { mutableStateOf<Boolean?>(null) }
    val showForm = formChoice ?: hasSaved
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    LanguageText.text("spoken.card.title"),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                FieldwatchSwitch(showForm, { formChoice = it })
            }
            Text(
                LanguageText.text("spoken.card.note"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (voiceWhat == AlertVoiceWhat.CLASS) {
                Text(
                    LanguageText.text("spoken.class.only"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showForm) {
                if (!individualName.isNullOrBlank()) {
                    Text(
                        LanguageText.text("spoken.named.instead"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    LanguageText.text("voice.signature"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (fleetIds.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fleetIds.forEach { id ->
                            FieldwatchFilterChip(
                                selected = id == pickedOk,
                                onClick = { picked = id },
                                label = { Text(fleetName(id), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                } else {
                    Text(fleetName(pickedOk), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    LanguageText.text("spoken.network"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(network, style = MaterialTheme.typography.bodyMedium)
                FieldwatchOutlinedField(
                    value = draft,
                    onValueChange = { draft = it.take(SpokenNames.MAX_LABEL) },
                    label = LanguageText.text("spoken.alert.say"),
                )
                FieldwatchActionButton(
                    onClick = { onSave(pickedOk, network, clipped) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = clipped.isNotEmpty() && !saved,
                ) {
                    if (saved) {
                        Icon(Icons.Outlined.Check, null, modifier = Modifier.size(18.dp))
                        Text(
                            LanguageText.text("ui.saved.label"),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    } else {
                        Text(LanguageText.text("spoken.save"))
                    }
                }
                if (stored.isNotBlank()) {
                    TextButton(
                        onClick = { askRemove = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(LanguageText.text("spoken.remove")) }
                }
            }
        }
    }
    if (askRemove) {
        AlertDialog(
            onDismissRequest = { askRemove = false },
            title = { Text(LanguageText.text("spoken.remove")) },
            text = { Text(LanguageText.text("spoken.remove.ask")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        draft = ""
                        askRemove = false
                        onRemove(pickedOk, network)
                    },
                ) { Text(LanguageText.text("spoken.remove")) }
            },
            dismissButton = {
                TextButton(onClick = { askRemove = false }) { Text(LanguageText.text("language.cancel")) }
            },
        )
    }
}

private fun fleetTitle(state: FieldwatchUi, id: String): String =
    state.fleets.firstOrNull { it.id == id }?.name ?: id
