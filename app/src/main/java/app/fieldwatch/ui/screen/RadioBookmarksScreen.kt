package app.fieldwatch.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import app.fieldwatch.ui.component.FieldwatchActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import app.fieldwatch.ui.component.FieldwatchSwitch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.RadioBookmarks
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.WatchTarget
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.RadioKindMark
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioBookmarksScreen(
    state: FieldwatchUi,
    vm: FieldwatchViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val radios = RadioBookmarks.radios(state.watchlist)
    val liveKeys = state.devices.filter { !it.gone }.map { it.key }.toSet()
    val demoMode = state.settings.demoMode
    var clearAll by remember { mutableStateOf(false) }
    var renameId by remember { mutableStateOf<String?>(null) }
    val renameTarget = radios.firstOrNull { it.id == renameId }

    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = {
            NestedTopBar(
                title = "Named radios (${radios.size})",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                    "One MAC each. Custom name shows on Live. Alert is optional (pip / voice / flash). Filters → Named radios only hides everything else. Signature watches stay on Signatures.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (radios.isEmpty()) {
                item {
                    Text(
                        "No named radios. Set a custom name on detail, or bookmark a radio (top-right) to watch it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            } else {
                items(radios, key = { it.id }) { row ->
                    val parsed = row.deviceKey?.let { RadioBookmarks.parseKey(it) }
                    val kind = parsed?.first ?: RadioKind.BLE
                    val mac = parsed?.second ?: row.deviceKey.orEmpty()
                    val onAir = row.deviceKey in liveKeys
                    BookmarkCard(
                        row = row,
                        kind = kind,
                        mac = MacUtil.screenMac(mac, demoMode),
                        onAir = onAir,
                        onOpen = {
                            val key = row.deviceKey ?: return@BookmarkCard
                            if (onAir) onOpen(key)
                        },
                        onAlert = { on -> vm.setRadioAlert(row.id, on) },
                        onRename = { renameId = row.id },
                        onRemove = { vm.removeRadioBookmark(row.id) },
                    )
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    FieldwatchActionButton(
                        onClick = { clearAll = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Clear all ${radios.size} named radios")
                    }
                }
            }
        }
    }

    if (clearAll) {
        AlertDialog(
            onDismissRequest = { clearAll = false },
            title = { Text("Clear named radios?") },
            text = {
                Text("Remove ${radios.size} named radios. Signature watches stay.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearRadioBookmarks()
                        clearAll = false
                    },
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { clearAll = false }) { Text("Cancel") }
            },
        )
    }
    if (renameTarget != null) {
        var draft by remember(renameTarget.id) { mutableStateOf(renameTarget.label) }
        AlertDialog(
            onDismissRequest = { renameId = null },
            title = { Text("Custom name") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(RadioBookmarks.MAX_NAME) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.renameRadioBookmark(renameTarget.id, draft)
                        renameId = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameId = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BookmarkCard(
    row: WatchTarget,
    kind: RadioKind,
    mac: String,
    onAir: Boolean,
    onOpen: () -> Unit,
    onAlert: (Boolean) -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onAir) Modifier.clickable(onClick = onOpen) else Modifier),
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioKindMark(kind, size = 18.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    row.label.ifBlank { mac },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    mac,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (onAir) "On the air — tap to open detail" else "Not this session",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(
                    "Alert",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FieldwatchSwitch(checked = row.alert, onCheckedChange = onAlert)
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Outlined.Edit, "Rename", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, "Remove", modifier = Modifier.size(20.dp))
            }
        }
    }
}
