package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ListSortTest {
    @Test
    fun newestAlertPutsMostRecentFirstAndNeverAlertedLast() {
        val alerts = mapOf("a" to 10L, "b" to 30L, "c" to 20L)
        val keys = listOf("a", "never", "c", "b")
        val sorted = keys.sortedWith(
            compareByDescending<String> { alerts[it] ?: 0L }.thenBy { it },
        )
        assertEquals(listOf("b", "c", "a", "never"), sorted)
    }
}
