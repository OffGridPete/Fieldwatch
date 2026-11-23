package app.fieldwatch.ui.screen

import app.fieldwatch.domain.LanguageText

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
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import app.fieldwatch.ui.component.FieldwatchSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
                    LanguageText.text("ui.one.mac.each.custom"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (radios.isEmpty()) {
                item {
                    Text(
                        LanguageText.text("ui.no.named.radios.set"),
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
                        onMine = { on -> vm.setNamedRadioMine(row.id, on) },
                        onMute = { on -> vm.setNamedRadioMute(row.id, on) },
                        onQuiet = { on -> vm.setNamedRadioQuiet(row.id, on) },
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
            title = { Text(LanguageText.text("ui.clear.named.radios")) },
            text = {
                Text("Remove ${radios.size} named radios. Signature watches stay.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearRadioBookmarks()
                        clearAll = false
                    },
                ) { Text(LanguageText.text("ui.clear.label")) }
            },
            dismissButton = {
                TextButton(onClick = { clearAll = false }) { Text(LanguageText.text("language.cancel")) }
            },
        )
    }
    if (renameTarget != null) {
        var draft by remember(renameTarget.id) { mutableStateOf(renameTarget.label) }
        var notesDraft by remember(renameTarget.id) { mutableStateOf(renameTarget.observerNotes) }
        AlertDialog(
            onDismissRequest = { renameId = null },
            title = { Text(LanguageText.text("ui.named.radio")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FieldwatchOutlinedField(
                        value = draft,
                        onValueChange = { draft = it.take(RadioBookmarks.MAX_NAME) },
                        label = LanguageText.text("ui.custom.name"),
                    )
                    FieldwatchOutlinedField(
                        value = notesDraft,
                        onValueChange = { notesDraft = it.take(RadioBookmarks.MAX_NOTES) },
                        label = LanguageText.text("ui.observer.notes"),
                        singleLine = false,
                        minLines = 3,
                        supportingText = "${notesDraft.trim().length}/${RadioBookmarks.MAX_NOTES}",
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.updateNamedRadio(renameTarget.id, draft, notesDraft)
                        renameId = null
                    },
                ) { Text(LanguageText.text("ui.save.label")) }
            },
            dismissButton = {
                TextButton(onClick = { renameId = null }) { Text(LanguageText.text("language.cancel")) }
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
    onMine: (Boolean) -> Unit,
    onMute: (Boolean) -> Unit,
    onQuiet: (Boolean) -> Unit,
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
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioKindMark(kind, size = 14.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    row.label.ifBlank { mac },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.Edit, LanguageText.text("ui.edit.label"), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.Delete, LanguageText.text("language.section.remove"), modifier = Modifier.size(18.dp))
                    }
                }
            }
            val underName = Modifier.padding(start = 22.dp)
            if (row.label.isNotBlank()) {
                Text(
                    mac,
                    modifier = underName,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                if (onAir) LanguageText.text("ui.air.tap.open.detail") else LanguageText.text("ui.not.session"),
                modifier = underName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val obs = row.observerNotes.trim()
            if (obs.isNotEmpty()) {
                Text(
                    obs,
                    modifier = underName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = underName.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    LanguageText.text("ui.mine.label"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                FieldwatchSwitch(checked = row.mine, onCheckedChange = onMine)
                Spacer(Modifier.width(16.dp))
                Text(
                    LanguageText.text("ui.alert.label"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                FieldwatchSwitch(checked = row.alert, onCheckedChange = onAlert)
                Spacer(Modifier.width(16.dp))
                Text(
                    LanguageText.text("ui.mute.label"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                FieldwatchSwitch(checked = row.mute, onCheckedChange = onMute)
            }
            Row(
                modifier = underName.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    LanguageText.text("ui.quiet.gone.label"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                FieldwatchSwitch(checked = row.alertOnGone, onCheckedChange = onQuiet)
            }
        }
    }
}
