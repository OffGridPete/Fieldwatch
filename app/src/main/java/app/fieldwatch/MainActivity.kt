package app.fieldwatch

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import app.fieldwatch.alert.Alerter
import app.fieldwatch.i18n.AppLanguage
import app.fieldwatch.radio.RadioPermissions
import app.fieldwatch.ui.FieldwatchRoot
import app.fieldwatch.ui.FieldwatchViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val vm: FieldwatchViewModel by viewModels()
    private var hadPermissions = false

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
        AppLanguage.refresh()
        (application as FieldwatchApp).alerter.refreshLanguage()
        enableEdgeToEdge()
        setContent {
            FieldwatchRoot(vm) {
                permissionLauncher.launch(RadioPermissions.required())
            }
        }
        hadPermissions = RadioPermissions.granted(this)
        if (savedInstanceState == null && hadPermissions) {
            lifecycleScope.launch { vm.startScan() }
        }
        intent?.getStringExtra(Alerter.EXTRA_DEVICE_KEY)?.let { vm.select(it) }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermissions()
        val granted = RadioPermissions.granted(this)
        if (granted && !hadPermissions) {
            (application as FieldwatchApp).refreshFix()
            vm.startScan()
        }
        hadPermissions = granted
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Alerter.EXTRA_DEVICE_KEY)?.let { vm.select(it) }
    }
}
