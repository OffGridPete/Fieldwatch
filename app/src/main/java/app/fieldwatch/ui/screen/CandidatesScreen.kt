package app.fieldwatch.ui.screen

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
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import app.fieldwatch.ui.component.FieldwatchActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.Palette
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.nightIf
import app.fieldwatch.domain.SignatureCandidate
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.RadioClassBadge
import app.fieldwatch.ui.RadioKindMark
import app.fieldwatch.ui.FieldwatchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CandidatesScreen(
    vm: FieldwatchViewModel,
    demoMode: Boolean,
    onBack: () -> Unit,
    onCreate: (SignatureCandidate) -> Unit,
) {
    val ui by vm.candidates.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = {
            NestedTopBar(
                title = "Signature candidates",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { pad ->
        when {
            ui.loading -> {
                Column(
                    Modifier.padding(pad).fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Reading the log and re-matching the catalog…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ui.error != null -> {
                Column(Modifier.padding(pad).padding(16.dp)) {
                    Text(ui.error ?: "", color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                val report = ui.report
                LazyColumn(
                    Modifier.padding(pad).fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            (report?.sourceLabel ?: "Rotating log") +
                                " · re-matched now" +
                                (report?.let { " · ${it.families.size} ${if (it.families.size == 1) "family" else "families"}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (report != null && (report.skippedRandomized > 0 || report.skippedHouseLike > 0)) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                            ) {
                                Text(
                                    skipLine(report),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                    if (report == null || report.families.isEmpty()) {
                        item {
                            Text(
                                "No signature families in this log. Randomized addresses and house-like names are skipped. A candidate needs a unique on-air ID on two or more radios.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    } else {
                        items(report.families, key = { it.id }) { cand ->
                            CandidateCard(cand, demoMode, onCreate)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(
    cand: SignatureCandidate,
    demoMode: Boolean,
    onCreate: (SignatureCandidate) -> Unit,
) {
    val accent = Color(Palette.color(cand.colorIndex)).nightIf(LocalNightMode.current)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                RadioClassBadge(classKind = cand.kind, accent = accent)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        cand.proposedName,
                        style = compact(16.sp, 18.sp, FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accent.copy(alpha = 0.16f),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(
                            cand.kind.label(),
                            style = compact(11.sp, 13.sp, FontWeight.SemiBold),
                            color = accent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        cand.distinctRadios.toString(),
                        style = compact(18.sp, 20.sp, FontWeight.Bold).copy(fontFamily = FontFamily.Monospace),
                    )
                    RadioKindMark(cand.radioKind, size = 13.dp)
                }
            }
            Text(
                cand.ruleLabel,
                style = compact(13.sp, 16.sp, FontWeight.Medium).copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 38.dp, top = 8.dp),
            )
            Text(
                cand.why,
                style = compact(13.sp, 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 38.dp, top = 4.dp),
            )
            val examples = cand.examples.map { MacUtil.redactMacIn(it, it, demoMode && looksLikeMac(it)) }
            if (examples.isNotEmpty()) {
                val extra = if (cand.extraCount > 0) "\nand ${cand.extraCount} more" else ""
                Text(
                    examples.joinToString(" · ") + extra,
                    style = compact(12.sp, 15.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 38.dp, top = 6.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                FieldwatchActionButton(onClick = { onCreate(cand) }) {
                    Icon(Icons.Outlined.GroupAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Create signature")
                }
            }
        }
    }
}

private fun skipLine(report: app.fieldwatch.domain.CandidateReport): String = buildString {
    append("Skipped ")
    val bits = ArrayList<String>(2)
    if (report.skippedRandomized > 0) bits += "${report.skippedRandomized} randomized addresses"
    if (report.skippedHouseLike > 0) bits += "${report.skippedHouseLike} house-like names"
    append(bits.joinToString(" and "))
    append(". Those are not catalog families.")
}

private fun looksLikeMac(text: String): Boolean =
    text.count { it == ':' } >= 4

private fun compact(
    size: androidx.compose.ui.unit.TextUnit,
    line: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(
    fontSize = size,
    lineHeight = line,
    fontWeight = weight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)
