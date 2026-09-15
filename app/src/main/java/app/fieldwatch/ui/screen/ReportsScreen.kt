package app.fieldwatch.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import app.fieldwatch.ui.component.FieldwatchActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    state: FieldwatchUi,
    vm: FieldwatchViewModel,
    exporting: Boolean,
    onSaveToStorage: () -> Unit,
    onSignatureCandidates: () -> Unit,
) {
    val settings = state.settings
    var confirmClear by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = { NestedTopBar("Reports") },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (settings.demoMode) {
                Text(
                    "Privacy mode is on. MAC tails in Debrief, AI Export, and detail Share are **:**:**. GPS coordinates are masked. The log file still has full addresses and lat/lon.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            SectionCard("Sit report") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FieldwatchActionButton(
                    onClick = vm::startFieldDebrief,
                    enabled = !exporting,
                    modifier = Modifier.weight(1f),
                ) { Text("Debrief (text)") }
                FieldwatchActionButton(
                    onClick = vm::startFieldDebriefPdf,
                    enabled = !exporting,
                    modifier = Modifier.weight(1f),
                ) { Text("Debrief (PDF)") }
            }
            Text(
                "Last 15 minutes in memory. Same report, two formats. GPS following test when tagging is on and you have moved. Not a legal finding.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::startAiExport,
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("AI Export") }
            Text(
                "Paste-ready prompt: the onboard Debrief plus working data, asking a chat for statistical analysis and depth the phone report cannot do.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard("Catalog") {
            FieldwatchActionButton(
                onClick = onSignatureCandidates,
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Signature candidates") }
            Text(
                "Unmatched radios in the log that share a unique ID — not every unknown. You review; nothing is added until you Save.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard("Log") {
            Text(
                "${state.logLines} lines this session  ·  ${vm.logBytes() / 1024} KB on disk" +
                    if (settings.loggingEnabled) "" else "  ·  logging off",
                style = MaterialTheme.typography.bodySmall,
            )
            FieldwatchActionButton(
                onClick = vm::startExport,
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Share log") }
            FieldwatchActionButton(
                onClick = onSaveToStorage,
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save log to SD card / storage…") }
            Text(
                "Share uses the Android share sheet. Save opens the system picker (SD, Downloads, USB, Drive). Turn logging on in Settings if you need new rows.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = { confirmClear = true },
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reset / clear log")
            }
            if (confirmClear) {
                AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    title = { Text("Clear the log?") },
                    text = {
                        Text("This deletes all rotated CSV/JSON files on the phone. It cannot be undone. Live scanning will start a new empty log.")
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmClear = false
                            vm.clearLogs()
                        }) { Text("Clear log") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
                    },
                )
            }
            }
        }
    }
}
