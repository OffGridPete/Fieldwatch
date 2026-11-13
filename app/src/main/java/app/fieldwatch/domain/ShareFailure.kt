package app.fieldwatch.domain

/**
 * Plain reason for a share that did not open. Null when the failure is not one
 * of the cases the phone can explain.
 */
object ShareFailure {
    fun explain(error: Throwable): String? {
        val chain = ArrayList<Throwable>(4)
        var current: Throwable? = error
        while (current != null && chain.size < 8 && current !in chain) {
            chain += current
            current = current.cause
        }
        fun hit(part: String): Boolean = chain.any { thrown ->
            thrown.javaClass.name.contains(part, ignoreCase = true) ||
                thrown.message?.contains(part, ignoreCase = true) == true
        }
        val key = when {
            chain.any { it is OutOfMemoryError } || hit("OutOfMemory") || hit("Failed to allocate") ->
                "ui.share.out.of.memory"
            hit("ENOSPC") || hit("No space left") || hit("Not enough space") ->
                "ui.share.out.of.storage"
            hit("TransactionTooLarge") || hit("parcel size") ->
                "ui.share.too.large"
            hit("ActivityNotFoundException") ->
                "ui.share.no.app"
            else -> return null
        }
        return LanguageText.text(key)
    }
}
