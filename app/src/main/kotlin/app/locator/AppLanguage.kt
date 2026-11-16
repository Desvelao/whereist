package app.locator

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app's language. null means "follow the system": Android then picks the system language if the app has it,
 * and English (the default resources) otherwise. Android 13+ stores the choice with the system (so it also shows in
 * the system's per-app language settings); older versions keep it in preferences and apply it in [wrap].
 */
object AppLanguage {
    /** Languages the app is translated to; keep in sync with `res/xml/locales_config.xml`. */
    val supported = listOf("en", "es", "de", "fr", "it")

    private const val PREFS = "language"
    private const val KEY = "tag"

    /** The chosen language tag, or null for the system default. */
    fun current(context: Context): String? {
        val tag = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.takeUnless { it.isEmpty }?.get(0)?.language
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        }
        return tag?.takeIf { it in supported }
    }

    /** Saves the choice. On Android 13+ the system recreates the activity; older versions need the caller to. */
    fun set(context: Context, tag: String?) {
        require(tag == null || tag in supported) { "Unsupported language" }
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply { if (tag == null) remove(KEY) else putString(KEY, tag) }.apply()
        }
    }

    /** For `attachBaseContext` of activities: applies the saved language on Android 12 and older. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.takeIf { it in supported } ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    /** The language's own name, such as "Deutsch". */
    fun displayName(tag: String): String = Locale.forLanguageTag(tag).let { it.getDisplayLanguage(it).replaceFirstChar { c -> c.titlecase(it) } }
}
