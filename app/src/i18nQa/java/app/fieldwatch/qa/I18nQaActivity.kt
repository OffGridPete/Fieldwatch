package app.fieldwatch.qa

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import app.fieldwatch.FieldwatchApp
import app.fieldwatch.domain.DISCLAIMER_REV
import app.fieldwatch.i18n.AppLanguage
import app.fieldwatch.ui.FieldwatchRoot
import app.fieldwatch.ui.FieldwatchViewModel
import kotlinx.coroutines.launch

/** Isolated test application only. Never reads the ordinary app's data or starts radios. */
class I18nQaActivity : AppCompatActivity() {
    val vm: FieldwatchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLanguage.refresh()
        val app = application as FieldwatchApp
        app.stopScanning()
        // Do not block the main thread while another UI coroutine holds ConfigStore's mutex.
        lifecycleScope.launch {
            app.config.update { cfg ->
                cfg.copy(settings = cfg.settings.copy(
                    disclaimerAccepted = true, disclaimerRev = DISCLAIMER_REV,
                    liveTourDone = true, onlineLookup = false, tagLocation = false,
                    loggingEnabled = false, alertsEnabled = false, alertVoice = false,
                    alertBeep = false, keepScreenOn = false,
                ))
            }
            vm.refreshPermissions()
            setContent { FieldwatchRoot(vm) {} }
        }
    }
}
