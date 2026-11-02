package app.fieldwatch.domain

/**
 * Time left in a learning window. Trusted networks and cell places share the day length.
 * Trusted 0 is 1 hour. Cell 0 is None, which is not a clock.
 */
object LearnLeft {
    const val DAY_MS = 86_400_000L
    const val HOUR_MS = 3_600_000L
    private const val MIN_MS = 60_000L

    fun trustedWindowMs(learnDays: Int): Long =
        if (learnDays == 0) HOUR_MS else learnDays.coerceAtLeast(1).toLong() * DAY_MS

    fun cellWindowMs(learnDays: Int): Long? =
        if (learnDays <= 0) null else learnDays.toLong() * DAY_MS

    /** Milliseconds still inside the window. 0 means it has ended. */
    fun remainingMs(startedAt: Long, windowMs: Long, now: Long): Long {
        if (startedAt <= 0L || windowMs <= 0L) return 0L
        val left = windowMs - (now - startedAt)
        return if (left > 0L) left else 0L
    }

    fun label(remainingMs: Long): String {
        if (remainingMs <= 0L) return LanguageText.text("learn.left.ended")
        if (remainingMs >= 2 * DAY_MS) {
            val days = (remainingMs / DAY_MS).toInt()
            return LanguageText.text("learn.left.days", mapOf("n" to days.toString()))
        }
        if (remainingMs >= HOUR_MS) {
            val hours = (remainingMs / HOUR_MS).toInt()
            return if (hours == 1) {
                LanguageText.text("learn.left.hour.1")
            } else {
                LanguageText.text("learn.left.hours", mapOf("n" to hours.toString()))
            }
        }
        if (remainingMs >= MIN_MS) {
            val mins = (remainingMs / MIN_MS).toInt()
            return if (mins == 1) {
                LanguageText.text("learn.left.min.1")
            } else {
                LanguageText.text("learn.left.mins", mapOf("n" to mins.toString()))
            }
        }
        return LanguageText.text("learn.left.soon")
    }
}
