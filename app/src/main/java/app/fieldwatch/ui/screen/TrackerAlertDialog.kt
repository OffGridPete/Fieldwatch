package app.fieldwatch.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fieldwatch.domain.CellWatch
import app.fieldwatch.domain.LanguageText
import app.fieldwatch.domain.TrackerCopy
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.NoticeBody

/**
 * One tracker warning. GNSS, trusted networks, and a cell warning come first.
 * Open radio shows that radio and counts as seen. Dismiss only counts as seen.
 * Tap outside dismisses.
 */
@Composable
fun TrackerAlertDialog(vm: FieldwatchViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val gnss by vm.gnssNotice.collectAsStateWithLifecycle()
    val trusted by vm.trustedNotice.collectAsStateWithLifecycle()
    val book by vm.cellBook.collectAsStateWithLifecycle()
    val notice by vm.trackerNotice.collectAsStateWithLifecycle()
    val streets by vm.placeStreetNames.collectAsStateWithLifecycle()
    val shown = notice ?: return
    if (gnss?.showDialog == true) return
    if (trusted != null) return
    if (ui.settings.cellWatch && CellWatch.showing(book) != null) return
    val demo = ui.settings.demoMode
    AlertDialog(
        onDismissRequest = { vm.dismissTracker() },
        title = { Text(TrackerCopy.title(shown.kind)) },
        text = {
            NoticeBody(
                TrackerCopy.prose(shown, TrackerCopy.streets(shown, streets, demo)),
                TrackerCopy.details(shown, demo),
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { vm.openTracker(shown) }) {
                    Text(LanguageText.text("tracker.open"))
                }
                TextButton(onClick = { vm.dismissTracker() }) {
                    Text(LanguageText.text("trusted.dismiss"))
                }
            }
        },
    )
}
