package app.fieldwatch.radio

import android.Manifest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioPermissionsTest {
    @Test
    fun notificationsAreAskedAndDoNotBlockTheScan() {
        val required = RadioPermissions.required(33).toSet()
        val asked = RadioPermissions.request(33).toSet()
        assertFalse(required.contains(Manifest.permission.POST_NOTIFICATIONS))
        assertTrue(required.contains(Manifest.permission.NEARBY_WIFI_DEVICES))
        assertTrue(required.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertEqualsAsked(required, asked)
    }

    @Test
    fun olderThan13DoesNotAskForNotifications() {
        val required = RadioPermissions.required(32).toSet()
        val asked = RadioPermissions.request(32).toSet()
        assertFalse(asked.contains(Manifest.permission.POST_NOTIFICATIONS))
        assertFalse(required.contains(Manifest.permission.NEARBY_WIFI_DEVICES))
        assertTrue(required.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(required == asked)
    }

    @Test
    fun android10AsksForTheOlderBluetoothPermissions() {
        val required = RadioPermissions.required(29).toSet()
        assertTrue(required.contains(Manifest.permission.BLUETOOTH))
        assertTrue(required.contains(Manifest.permission.BLUETOOTH_ADMIN))
        assertFalse(required.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertFalse(RadioPermissions.request(29).contains(Manifest.permission.POST_NOTIFICATIONS))
    }

    private fun assertEqualsAsked(required: Set<String>, asked: Set<String>) {
        assertTrue(asked.containsAll(required))
        assertTrue(asked.contains(Manifest.permission.POST_NOTIFICATIONS))
        assertTrue(asked.size == required.size + 1)
    }
}
