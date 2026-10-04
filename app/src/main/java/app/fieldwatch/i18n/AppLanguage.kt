package app.fieldwatch.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.annotation.PluralsRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** One locale source for activities, services, notifications and exports. */
object AppLanguage {
    private lateinit var application: Context
    @Volatile private var activityReady = false
    private var cachedConfiguration: Configuration? = null
    private var cachedContext: Context? = null

    fun initialize(context: Context) {
        application = context.applicationContext
        TextRuntime.localeProvider = ::locale
        TextRuntime.renderer = { key, english, args ->
            TextResources.ids[key]?.let { appText(it, *args) } ?: TextRuntime.english(english, args)
        }
    }

    /** Called after AppCompat restores its language, without retaining an Activity. */
    fun refresh() {
        activityReady = true
    }

    @Synchronized
    fun context(): Context {
        val base = if (activityReady) application else ContextCompat.getContextForLanguage(application)
        val configuration = Configuration(base.resources.configuration)
        val selected = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (selected.isNotEmpty()) configuration.setLocales(LocaleList.forLanguageTags(selected))
        // Default English resources also require English dates and speech on unsupported systems.
        val requested = configuration.locales[0]
        val simplifiedChinese = requested.language == "zh" && requested.script != "Hant" &&
            requested.country !in listOf("TW", "HK", "MO")
        if (requested.language != "en" && !simplifiedChinese) configuration.setLocales(LocaleList(Locale.US))
        if (configuration != cachedConfiguration) {
            cachedConfiguration = configuration
            cachedContext = application.createConfigurationContext(configuration)
        }
        return cachedContext ?: application
    }

    fun locale(): Locale = context().resources.configuration.locales[0]

    fun selection(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags()

    fun select(languageTag: String) {
        require(languageTag in listOf("", "en", "zh-Hans"))
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }
}

/** Also usable from callbacks and services, where Compose stringResource is unavailable. */
fun appText(@StringRes id: Int, vararg args: Any?): String =
    AppLanguage.context().resources.let { if (args.isEmpty()) it.getString(id) else it.getString(id, *args) }

fun appQuantity(@PluralsRes id: Int, count: Int): String =
    AppLanguage.context().resources.getQuantityString(id, count, count)
