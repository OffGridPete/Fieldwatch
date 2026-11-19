package app.fieldwatch.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.fieldwatch.domain.LanguageText

/**
 * Beep, voice, and system notification. Same order and the same notes on every
 * warning page. The voice line is the only one that names that warning.
 */
@Composable
fun WarningReachCard(
    intro: String,
    beep: Boolean,
    onBeep: (Boolean) -> Unit,
    voice: Boolean,
    onVoice: (Boolean) -> Unit,
    voiceNote: String,
    shade: Boolean,
    onShade: (Boolean) -> Unit,
    beepNote: String = LanguageText.text("ui.same.pip.watchlist.hit"),
    shadeNote: String = LanguageText.text("ui.silent.notification.warning.popup"),
) {
    SectionCard(LanguageText.text("trusted.reach")) {
        Note(intro)
        SwitchRow(LanguageText.text("ui.beep.label"), beep, onBeep)
        Note(beepNote)
        SwitchRow(LanguageText.text("ui.voice.label"), voice, onVoice)
        Note(voiceNote)
        SwitchRow(LanguageText.text("ui.system.notification"), shade, onShade)
        Note(shadeNote)
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SwitchRow(label: String, on: Boolean, set: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        FieldwatchSwitch(on, set)
    }
}
