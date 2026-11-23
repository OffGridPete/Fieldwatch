package app.fieldwatch.radio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object RadioPermissions {
    /**
     * Radios the scan cannot start without. On Android 13 and newer,
     * [request] also asks for notifications. Declining that one does not
     * block the app. The scanning notification is a foreground-service
     * notice and still shows. Alert notifications stay off until it is allowed.
     */
    fun required(sdk: Int = Build.VERSION.SDK_INT): Array<String> = radios(sdk)

    fun request(sdk: Int = Build.VERSION.SDK_INT): Array<String> {
        val list = radios(sdk).toMutableList()
        if (sdk >= 33) list += Manifest.permission.POST_NOTIFICATIONS
        return list.toTypedArray()
    }

    private fun radios(sdk: Int): Array<String> {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (sdk >= 31) {
            list += Manifest.permission.BLUETOOTH_SCAN
            list += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            list += Manifest.permission.BLUETOOTH
            list += Manifest.permission.BLUETOOTH_ADMIN
        }
        if (sdk >= 33) list += Manifest.permission.NEARBY_WIFI_DEVICES
        return list.toTypedArray()
    }

    fun missing(context: Context): List<String> =
        required().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

    fun granted(context: Context): Boolean = missing(context).isEmpty()
}
