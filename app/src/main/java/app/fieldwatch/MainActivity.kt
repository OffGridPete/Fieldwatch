package app.fieldwatch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import app.fieldwatch.alert.Alerter
import app.fieldwatch.radio.RadioPermissions
import app.fieldwatch.radio.ScanService
import app.fieldwatch.ui.FieldwatchRoot
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.screen.CrashNoteScreen
import app.fieldwatch.ui.theme.FieldwatchTheme
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class MainActivity : ComponentActivity() {
    private val vm: FieldwatchViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        vm.refreshPermissions()
        if (RadioPermissions.granted(this)) {
            (application as FieldwatchApp).refreshFix()
            vm.startScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FieldwatchApp
        if (app.pendingCrash != null) {
            showCrashNote(app)
            return
        }
        showApp()
    }

    override fun onStart() {
        super.onStart()
        screen = WeakReference(this)
        val app = application as FieldwatchApp
        app.uiStarted()
        if (app.pendingCrash == null) {
            app.syncTrackerShade(app.tracker.notice.value)
            if (RadioPermissions.granted(this)) ScanService.includeLocation(this)
        }
    }

    override fun onStop() {
        val app = application as FieldwatchApp
        val changing = isChangingConfigurations
        app.uiStopped()
        if (!changing && app.pendingCrash == null) {
            app.syncTrackerShade(app.tracker.notice.value)
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        val app = application as FieldwatchApp
        if (app.pendingCrash != null) return
        vm.refreshPermissions()
        vm.onAppUpdateResume()
        if (RadioPermissions.granted(this)) {
            ScanService.includeLocation(this)
            if (!app.devices.stats.value.scanning) vm.startScan()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if ((application as FieldwatchApp).pendingCrash != null) return
        intent.getStringExtra(Alerter.EXTRA_DEVICE_KEY)?.let {
            vm.select(it)
            vm.seenTracker(it)
        }
    }

    private fun showCrashNote(app: FieldwatchApp) {
        val pending = app.pendingCrash
        if (pending == null) {
            showApp()
            return
        }
        setContent {
            FieldwatchTheme {
                CrashNoteScreen(
                    note = pending.text,
                    onCopy = { copyNote(pending.text) },
                    onContinue = {
                        app.dismissCrashNote()
                        showApp()
                    },
                )
            }
        }
    }

    private fun showApp() {
        setContent {
            FieldwatchRoot(vm) {
                permissionLauncher.launch(RadioPermissions.request())
            }
        }
        if (RadioPermissions.granted(this)) {
            lifecycleScope.launch { vm.startScan() }
        }
        intent?.getStringExtra(Alerter.EXTRA_DEVICE_KEY)?.let {
            vm.select(it)
            vm.seenTracker(it)
        }
    }

    override fun onDestroy() {
        if (screen?.get() === this) screen = null
        super.onDestroy()
    }

    private fun copyNote(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Fieldwatch crash", text))
    }

    companion object {
        private var screen: WeakReference<MainActivity>? = null

        /**
         * Sends the screen behind the other apps. The task stays, so swiping
         * Fieldwatch out of Recents is still what stops the scan.
         * True when the screen is already gone.
         */
        fun closeScreen(): Boolean {
            val activity = screen?.get() ?: return true
            return activity.moveTaskToBack(true)
        }
    }
}
