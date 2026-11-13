package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareFailureTest {
    @Test
    fun outOfMemory() {
        val error = RuntimeException("Failure from system", OutOfMemoryError())
        assertEquals(
            "The phone ran out of memory building this share.",
            ShareFailure.explain(error),
        )
    }

    @Test
    fun outOfStorage() {
        val error = java.io.IOException("write failed: ENOSPC (No space left on device)")
        assertEquals(
            "The phone is out of storage. Free some space and try again.",
            ShareFailure.explain(error),
        )
    }

    @Test
    fun shareTooLarge() {
        val error = RuntimeException(
            "Failure from system",
            TransactionTooLargeException("data parcel size 1400000 bytes"),
        )
        assertEquals(
            "This share is too large for the phone to open.",
            ShareFailure.explain(error),
        )
    }

    @Test
    fun noApp() {
        val error = ActivityNotFoundException("no activity")
        assertEquals(
            "No app on this phone can open this share.",
            ShareFailure.explain(error),
        )
    }

    @Test
    fun unknownSheetFailureStaysUnexplained() {
        assertNull(ShareFailure.explain(RuntimeException("Failure from system")))
    }
}

private class TransactionTooLargeException(message: String) : RuntimeException(message)

private class ActivityNotFoundException(message: String) : RuntimeException(message)
