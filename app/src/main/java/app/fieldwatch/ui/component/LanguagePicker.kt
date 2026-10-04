package app.fieldwatch.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.fieldwatch.R
import app.fieldwatch.i18n.AppLanguage

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguagePicker() {
    val selected = AppLanguage.selection()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("" to stringResource(R.string.settings_language_system), "zh-Hans" to "简体中文", "en" to "English")
                .forEach { (tag, label) ->
                    FieldwatchFilterChip(
                        selected = if (tag.isEmpty()) selected.isEmpty() else selected.startsWith(tag),
                        onClick = { AppLanguage.select(tag) },
                        label = { Text(label) },
                    )
                }
        }
    }
}
