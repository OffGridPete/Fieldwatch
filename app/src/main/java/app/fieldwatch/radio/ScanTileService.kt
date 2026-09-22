package app.fieldwatch.radio

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.fieldwatch.FieldwatchApp
import app.fieldwatch.MainActivity
import app.fieldwatch.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile: tap toggles the scan without opening the app. If
 * permissions are missing or the system refuses the background start
 * (Android 12+ FGS limits), falls back to opening MainActivity which runs
 * the normal permission flow.
 */
class ScanTileService : TileService() {
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        val app = application as FieldwatchApp
        refresh(app.devices.stats.value.scanning)
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        s.launch {
            app.devices.stats.collect { refresh(it.scanning) }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val app = application as FieldwatchApp
        when {
            app.devices.stats.value.scanning -> app.stopScanning()
            !RadioPermissions.granted(this) -> openApp()
            else -> runCatching { app.startScanning() }
                .onFailure { openApp() }
        }
        refresh(app.devices.stats.value.scanning)
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh(scanning: Boolean) {
        qsTile?.apply {
            state = if (scanning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            icon = android.graphics.drawable.Icon.createWithResource(
                this@ScanTileService, R.drawable.ic_stat_fieldwatch,
            )
            label = getString(R.string.app_name)
            if (Build.VERSION.SDK_INT >= 30) {
                stateDescription = if (scanning) "Scanning" else "Scan off"
            }
            updateTile()
        }
    }
}
