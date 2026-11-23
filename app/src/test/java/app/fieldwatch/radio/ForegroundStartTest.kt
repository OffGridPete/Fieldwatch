package app.fieldwatch.radio

import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundStartTest {
    private val location = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
    private val connected = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE

    @Test
    fun beforeApi29ThereIsNoTypeMask() {
        var called = false
        val choice = chooseForeground(28) {
            called = true
            true
        }
        assertEquals(ForegroundChoice.UNTYPED, choice)
        assertFalse(called)
    }

    @Test
    fun api34UsesLocationAndRadiosWhenThatIsAllowed() {
        val seen = ArrayList<Int>()
        val choice = chooseForeground(36) { types ->
            seen += types
            true
        }
        assertEquals(ForegroundChoice.LOCATION, choice)
        assertEquals(listOf(location or connected), seen)
    }

    @Test
    fun aRefusedLocationTypeKeepsTheRadioType() {
        val seen = ArrayList<Int>()
        val choice = chooseForeground(36) { types ->
            seen += types
            types and location == 0
        }
        assertEquals(ForegroundChoice.RADIOS, choice)
        assertEquals(listOf(location or connected, connected), seen)
        assertTrue(seen.last() and location == 0)
    }

    @Test
    fun olderApisFallBackToTheRadioTypeToo() {
        val choice = chooseForeground(33) { types -> types and location == 0 }
        assertEquals(ForegroundChoice.RADIOS, choice)
    }

    @Test
    fun everyRefusalStaysRefused() {
        assertEquals(ForegroundChoice.REFUSED, chooseForeground(36) { false })
        assertEquals(ForegroundChoice.REFUSED, chooseForeground(33) { false })
    }
}