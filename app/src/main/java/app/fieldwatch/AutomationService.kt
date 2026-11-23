package app.fieldwatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.fieldwatch.domain.Automation
import app.fieldwatch.domain.AutomationExport
import app.fieldwatch.domain.GeoExport
import app.fieldwatch.domain.LogExportKind
import app.fieldwatch.domain.LogExportRadios
import app.fieldwatch.domain.SitExport
import app.fieldwatch.radio.ForegroundChoice
import app.fieldwatch.radio.ScanService
import app.fieldwatch.radio.chooseForeground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tasker and other apps send an explicit service intent. A broadcast with the
 * same action is forwarded here. The result is automation/status.txt.
 */
class AutomationService : LifecycleService() {
    private val gate = Mutex()
    private var busy = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (busy == 0) showForeground()
        busy += 1
        val action = intent?.action
        val formatRaw = intent?.getStringExtra(Automation.EXTRA_FORMAT)
        val nameRaw = intent?.getStringExtra(Automation.EXTRA_NAME)
        val radiosRaw = intent?.getStringExtra(Automation.EXTRA_RADIOS)
        lifecycleScope.launch {
            try {
                val status = gate.withLock {
                    withContext(Dispatchers.IO) {
                        runAction(action, formatRaw, nameRaw, radiosRaw)
                    }
                }
                withContext(Dispatchers.IO) { writeStatus(status) }
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    busy -= 1
                    if (busy == 0) {
                        if (Build.VERSION.SDK_INT >= 24) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                        } else {
                            @Suppress("DEPRECATION")
                            stopForeground(true)
                        }
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runAction(
        action: String?,
        formatRaw: String?,
        nameRaw: String?,
        radiosRaw: String?,
    ): Automation.Status {
        val app = application as FieldwatchApp
        val parsed = Automation.action(action)
        val base = Automation.Status(
            ok = false,
            action = action.orEmpty(),
            scanning = app.devices.stats.value.scanning,
            sit = app.sits.ui.value.open?.name.orEmpty(),
            saved = app.sits.newestSavedFile()?.summary?.name.orEmpty(),
        )
        if (parsed == null) return base.copy(error = Automation.UNKNOWN_ACTION)
        return when (parsed) {
            Automation.Action.CLOSE -> {
                val closed = withContext(Dispatchers.Main.immediate) { MainActivity.closeScreen() }
                snapshot(
                    app,
                    action.orEmpty(),
                    ok = closed,
                    error = if (closed) "" else Automation.SCREEN_STAYED,
                )
            }
            Automation.Action.START_SCAN -> {
                withContext(Dispatchers.Main.immediate) {
                    ContextCompat.startForegroundService(
                        this@AutomationService,
                        Intent(this@AutomationService, ScanService::class.java),
                    )
                }
                snapshot(app, action.orEmpty(), ok = true)
            }
            Automation.Action.STOP_SCAN -> {
                withContext(Dispatchers.Main.immediate) {
                    stopService(Intent(this@AutomationService, ScanService::class.java))
                }
                snapshot(app, action.orEmpty(), ok = true)
            }
            Automation.Action.SCAN_STATUS -> snapshot(app, action.orEmpty(), ok = true)
            Automation.Action.START_SIT -> {
                val started = app.startNamedSit(nameRaw.orEmpty())
                snapshot(
                    app,
                    action.orEmpty(),
                    ok = started,
                    error = if (started) "" else Automation.ALREADY_OPEN,
                )
            }
            Automation.Action.STOP_SIT -> {
                val ended = app.endOpenSit()
                snapshot(
                    app,
                    action.orEmpty(),
                    ok = ended,
                    error = if (ended) "" else Automation.NONE_OPEN,
                )
            }
            Automation.Action.EXPORT_SIT -> exportSit(app, action.orEmpty(), formatRaw, radiosRaw)
            Automation.Action.EXPORT_LOG -> exportLog(app, action.orEmpty(), formatRaw, radiosRaw)
        }
    }

    private fun snapshot(
        app: FieldwatchApp,
        action: String,
        ok: Boolean,
        file: String = "",
        format: String = "",
        error: String = "",
    ): Automation.Status = Automation.Status(
        ok = ok,
        action = action,
        scanning = app.devices.stats.value.scanning,
        sit = app.sits.ui.value.open?.name.orEmpty(),
        saved = app.sits.newestSavedFile()?.summary?.name.orEmpty(),
        file = file,
        format = format,
        error = error,
    )

    private suspend fun exportSit(
        app: FieldwatchApp,
        action: String,
        formatRaw: String?,
        radiosRaw: String?,
    ): Automation.Status {
        val kind = Automation.format(formatRaw)
            ?: return snapshot(app, action, ok = false, error = Automation.UNKNOWN_FORMAT)
        val file = app.sits.newestSavedFile()
            ?: return snapshot(app, action, ok = false, format = formatName(kind), error = Automation.NONE_SAVED)
        val radios = Automation.radios(radiosRaw)
        val now = System.currentTimeMillis()
        val bytes = runCatching {
            AutomationExport.sitBytes(
                kind = kind,
                devices = file.radios.map { it.toSighting() },
                path = file.operatorPath,
                gnss = file.gnss,
                fleets = app.config.fleets,
                watchlist = app.config.watchlist,
                radios = radios,
                versionName = BuildConfig.VERSION_NAME,
                deviceInfo = deviceInfo(),
                now = now,
                tag = app.config.takInstallTag,
                callsign = app.config.settings.takCallsign,
                sitName = file.summary.name,
            )
        }
        val body = bytes.getOrElse { err ->
            return snapshot(app, action, ok = false, format = formatName(kind), error = err.message.orEmpty())
        }
        val name = SitExport.suggestedName(kind, file.summary.name, stamp())
        val out = writeBytes(name, body)
        return snapshot(app, action, ok = true, file = out.absolutePath, format = formatName(kind))
    }

    private suspend fun exportLog(
        app: FieldwatchApp,
        action: String,
        formatRaw: String?,
        radiosRaw: String?,
    ): Automation.Status {
        val kind = Automation.format(formatRaw)
            ?: return snapshot(app, action, ok = false, error = Automation.UNKNOWN_FORMAT)
        if (!Automation.allowsLog(kind)) {
            return snapshot(app, action, ok = false, format = formatName(kind), error = Automation.SIT_FORMAT)
        }
        val radios = Automation.radios(radiosRaw)
        val out = runCatching { writeLog(app, kind, radios) }.getOrElse { err ->
            return snapshot(app, action, ok = false, format = formatName(kind), error = err.message.orEmpty())
        }
        return snapshot(app, action, ok = true, file = out.absolutePath, format = formatName(kind))
    }

    private suspend fun writeLog(app: FieldwatchApp, kind: LogExportKind, radios: LogExportRadios): File {
        if (kind == LogExportKind.LOG_CSV || kind == LogExportKind.LOG_JSONL) {
            val made = app.logs.exportBundle(asJsonl = kind == LogExportKind.LOG_JSONL, radios = radios)
            try {
                val name = app.logs.suggestedExportName(asJsonl = kind == LogExportKind.LOG_JSONL)
                return writeBytes(name, made.readBytes())
            } finally {
                made.delete()
            }
        }
        val pins = app.logs.readRadios().filter { radios.matches(it.kind) }
        val text = AutomationExport.logMapText(kind, pins, BuildConfig.VERSION_NAME, deviceInfo())
        val fmt = GeoExport.formatOf(kind) ?: error(Automation.SIT_FORMAT)
        return writeBytes(GeoExport.suggestedName(fmt, stamp()), text.toByteArray(Charsets.UTF_8))
    }

    private fun writeBytes(name: String, bytes: ByteArray): File {
        val folder = folder()
        val out = File(folder, name)
        val tmp = File(folder, "$name.tmp")
        tmp.writeBytes(bytes)
        if (out.exists() && !out.delete()) tmp.copyTo(out, overwrite = true)
        if (!tmp.renameTo(out)) tmp.copyTo(out, overwrite = true)
        tmp.delete()
        return out
    }

    private fun writeStatus(status: Automation.Status) {
        val out = File(folder(), "status.txt")
        val tmp = File(folder(), "status.txt.tmp")
        tmp.writeText(Automation.status(status))
        if (!tmp.renameTo(out)) tmp.copyTo(out, overwrite = true)
        tmp.delete()
    }

    private fun folder(): File {
        val base = getExternalFilesDir(null) ?: filesDir
        return File(base, "automation").apply { mkdirs() }
    }

    private fun showForeground() {
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_fieldwatch)
            .setContentTitle(getString(R.string.app_name))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        when (chooseForeground(Build.VERSION.SDK_INT) { types -> postForeground(notification, types) }) {
            ForegroundChoice.UNTYPED -> startForeground(NOTIF_ID, notification)
            ForegroundChoice.REFUSED -> Unit
            else -> Unit
        }
    }

    private fun postForeground(notification: Notification, types: Int): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, types)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        true
    }.getOrDefault(false)

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Automation", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
                setSound(null, null)
            },
        )
    }

    private fun deviceInfo(): String = listOf(
        "model=${Build.MODEL}",
        "release=${Build.VERSION.RELEASE}",
        "device=${Build.DEVICE}",
    ).joinToString(",")

    private fun formatName(kind: LogExportKind): String = when (kind) {
        LogExportKind.LOG_CSV -> "csv"
        LogExportKind.LOG_JSONL -> "jsonl"
        LogExportKind.GPX -> "gpx"
        LogExportKind.KML -> "kml"
        LogExportKind.GEOJSON -> "geojson"
        LogExportKind.WIGLE -> "wigle"
        LogExportKind.ATAK -> "atak"
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    companion object {
        private const val CHANNEL = "fieldwatch_automation"
        private const val NOTIF_ID = 44
    }
}
