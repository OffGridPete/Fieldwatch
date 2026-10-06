package app.fieldwatch.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import app.fieldwatch.ui.AppText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.fieldwatch.BuildConfig
import app.fieldwatch.FieldwatchApp
import app.fieldwatch.radio.WifiRadio
import app.fieldwatch.domain.ScanPhoneFacts
import app.fieldwatch.domain.ScanStatus
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.component.FieldwatchActionButton
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanStatusScreen(
    state: FieldwatchUi,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val report = ScanStatus.report(
        radio = state.scanRadio,
        phone = scanPhoneFacts(context, state, now),
        now = now,
        radiosOnAir = state.wifiNow + state.bleNow,
    )
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = {
            NestedTopBar(
                title = "Diagnostics",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, app.fieldwatch.ui.localizeAppText("Back"))
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(report.verdict, style = MaterialTheme.typography.bodyLarge)
            report.rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(0.42f),
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(0.58f),
                    )
                }
            }
            FieldwatchActionButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Fieldwatch diagnostics", report.text))
                    copied = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Copy") }
            if (copied) {
                Text(
                    "Copied.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Paste this into an issue. It has no network names, no addresses, and no GPS coordinates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun scanPhoneFacts(context: Context, state: FieldwatchUi, now: Long): ScanPhoneFacts {
    val app = context.applicationContext as FieldwatchApp
    val filter = state.filter
    return ScanPhoneFacts(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        catalogVersion = state.catalogVersion,
        manufacturer = Build.MANUFACTURER.orEmpty(),
        model = Build.MODEL.orEmpty(),
        androidRelease = Build.VERSION.RELEASE.orEmpty(),
        sdkInt = Build.VERSION.SDK_INT,
        fingerprint = Build.FINGERPRINT.orEmpty(),
        locationOn = app.systemLocationOn(),
        locationPermission = app.hasFineLocation(),
        gpsFixAgeMs = app.gpsFixAgeMs(now),
        arrivalsOnly = filter.arrivalsOnly,
        watchedOnly = filter.watchedOnly,
        customNamesOnly = filter.customNamesOnly,
        showOnly = filter.namedOnly || filter.namedOnlyImplied(),
        movingWithYou = filter.movingWithYou,
        showWifi = filter.showWifi,
        showBle = filter.showBle,
        nameFilter = filter.nameQuery.isNotBlank(),
        ouiFilter = filter.ouiQuery.isNotBlank(),
        wifiFastScan = state.settings.wifiFastScan,
        wifiOsThrottled = WifiRadio.osScanThrottled(context),
        backgroundUsage = isBackgroundUsageAllowed(context),
        unrestrictedBattery = isIgnoringBatteryOptimizations(context),
    )
}
