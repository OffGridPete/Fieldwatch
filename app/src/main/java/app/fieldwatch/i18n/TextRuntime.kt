package app.fieldwatch.i18n

import java.util.Locale

/** Android installs a resource renderer; JVM/domain callers retain the English contract. */
object TextRuntime {
    @Volatile
    var renderer: ((String, String, Array<out Any?>) -> String)? = null
    @Volatile
    var localeProvider: () -> Locale = { Locale.US }

    fun english(pattern: String, args: Array<out Any?>): String =
        if (args.isEmpty()) pattern else String.format(Locale.US, pattern, *args)
}

fun localized(key: String, english: String, vararg args: Any?): String =
    TextRuntime.renderer?.invoke(key, english, args) ?: TextRuntime.english(english, args)
