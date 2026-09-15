package app.fieldwatch.radio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object RadioPermissions {
    fun required(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= 31) {
            list += Manifest.permission.BLUETOOTH_SCAN
            list += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            list += Manifest.permission.BLUETOOTH
            list += Manifest.permission.BLUETOOTH_ADMIN
        }
        if (Build.VERSION.SDK_INT >= 33) {
            list += Manifest.permission.NEARBY_WIFI_DEVICES
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        return list.toTypedArray()
    }

    fun missing(context: Context): List<String> =
        required().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

    fun granted(context: Context): Boolean = missing(context).isEmpty()
}
