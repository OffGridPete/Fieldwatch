package app.fieldwatch.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.fieldwatch.domain.LanguageText
import app.fieldwatch.domain.NoticeText

/**
 * Warning text. The explanation comes first, then Details, then the readings.
 * The column scrolls when it is long, so the buttons stay on screen.
 */
@Composable
fun NoticeBody(prose: String, details: String = "") {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (prose.isNotBlank()) Prose(prose)
        if (details.isNotBlank()) {
            Text(
                LanguageText.text("ui.details.label"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = if (prose.isNotBlank()) 14.dp else 0.dp),
            )
            DataBlock(details)
        }
    }
}

/** Heading stays put. Only the notes scroll, and only when they are long. */
@Composable
fun ReleaseNotes(notes: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            LanguageText.text("update.notes"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Prose(notes)
        }
    }
}

@Composable
private fun Prose(text: String) {
    val parts = NoticeText.paragraphs(text)
    parts.forEachIndexed { index, paragraph ->
        Text(
            paragraph,
            style = MaterialTheme.typography.bodyMedium,
            modifier = if (index == 0) Modifier else Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun DataBlock(text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        NoticeText.rows(text).forEach { row ->
            val label = row.label
            if (label == null) {
                Text(
                    row.value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        label,
                        modifier = Modifier.width(96.dp).padding(end = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        row.value,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
