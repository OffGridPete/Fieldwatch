package app.fieldwatch.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import app.fieldwatch.domain.AppUpdate
import java.io.File

/** Confirms the downloaded APK is this app, signed like the copy already installed, then opens it. */
object AppUpdateInstall {
    fun allowed(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun settingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    /**
     * False when the file is not a newer build of this app, or the certificate differs.
     * Android would refuse that install. This stops first, and the copy on the phone stays.
     */
    fun trusted(context: Context, apk: File, versionCode: Int, versionName: String): Boolean {
        if (!apk.isFile || apk.length() < 4L) return false
        val header = ByteArray(4)
        val got = apk.inputStream().use { it.read(header) }
        if (got < 4 || !AppUpdate.apkLooksLikeZip(header)) return false
        val pm = context.packageManager
        val archive = archiveInfo(pm, apk.absolutePath) ?: return false
        if (archive.packageName != context.packageName) return false
        if (archive.versionName != versionName) return false
        if (archive.longVersionCode != versionCode.toLong()) return false
        val installed = installedInfo(pm, context.packageName) ?: return false
        if (archive.longVersionCode <= installed.longVersionCode) return false
        val archiveCerts = currentCerts(archive) ?: return false
        val installedCerts = currentCerts(installed) ?: return false
        return archiveCerts == installedCerts
    }

    fun viewIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            clipData = ClipData.newRawUri("Fieldwatch", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val targets = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        for (target in targets) {
            context.grantUriPermission(
                target.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        return intent
    }

    private fun archiveInfo(pm: PackageManager, path: String): PackageInfo? = info(pm, path, archive = true)

    private fun installedInfo(pm: PackageManager, packageName: String): PackageInfo? =
        info(pm, packageName, archive = false)

    private fun info(pm: PackageManager, name: String, archive: Boolean): PackageInfo? {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES.toLong()
        return if (Build.VERSION.SDK_INT >= 33) {
            val wrapped = PackageManager.PackageInfoFlags.of(flags)
            if (archive) pm.getPackageArchiveInfo(name, wrapped) else pm.getPackageInfo(name, wrapped)
        } else {
            @Suppress("DEPRECATION")
            if (archive) {
                pm.getPackageArchiveInfo(name, flags.toInt())
            } else {
                pm.getPackageInfo(name, flags.toInt())
            }
        }
    }

    private fun currentCerts(info: PackageInfo): Set<List<Byte>>? {
        val signing = info.signingInfo ?: return null
        val raw = if (signing.hasMultipleSigners()) {
            signing.apkContentsSigners
        } else {
            val history = signing.signingCertificateHistory ?: return null
            if (history.isEmpty()) return null
            arrayOf(history.last())
        }
        if (raw.isEmpty()) return null
        return raw.map { it.toByteArray().asList() }.toSet()
    }
}
