package com.allient.folio

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app language (Preferences → Language).
 *
 * - Android 13+: the platform per-app language ([LocaleManager]), the same value as Settings → Apps → Folio → Language.
 *   The system applies it to every activity and restarts them when it changes.
 * - Older versions: stored in `app-language` and applied by [wrap] in each activity's `attachBaseContext`.
 *
 * With no choice ("") Folio follows the system languages: the first one it is translated into, else English
 * ([AppLanguages.resolve]). Android `res/values*` resolve that way by themselves; Compose Multiplatform resources read the
 * JVM default locale instead, so [wrap] also sets [Locale.setDefault] to the resolved language, which keeps the shared
 * screens, date and number formatting in the same language as the rest of the app.
 */
internal object AppLocales {
    private const val PREFERENCES = "app-language"
    private const val KEY = "language"

    /** The chosen language tag, or "" to follow the system. */
    fun choice(context: Context): String = AppLanguages.choice(
        if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java)?.applicationLocales?.toLanguageTags().orEmpty()
        else context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
    )

    /** Saves [tag] ("" = system) and shows it right away. Call on the main thread. */
    fun choose(activity: Activity, tag: String) {
        val value = AppLanguages.choice(tag)
        if (value == choice(activity)) return
        if (Build.VERSION.SDK_INT >= 33) {
            // The platform stores it and restarts the app's activities in the new language.
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (value.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(value)
        } else {
            activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(KEY, value).commit()
            activity.recreate()
        }
    }

    /** For `attachBaseContext`: applies the chosen language before Android 13, and sets the JVM default locale. */
    fun wrap(base: Context): Context {
        val choice = choice(base)
        if (Build.VERSION.SDK_INT < 33) {
            // AppCompat activities (the reader) apply AppCompat's in-memory app locales over their base context.
            val locales = androidx.core.os.LocaleListCompat.forLanguageTags(choice)
            if (androidx.appcompat.app.AppCompatDelegate.getApplicationLocales() != locales) androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
        }
        val context = if (Build.VERSION.SDK_INT < 33 && choice.isNotEmpty()) {
            val configuration = Configuration(base.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(choice)) }
            base.createConfigurationContext(configuration)
        } else base
        Locale.setDefault(resolved(context))
        return context
    }

    /** The locale the app shows: the first of the context's locales Folio is translated into, else English. */
    fun resolved(context: Context): Locale {
        val locales = context.resources.configuration.locales
        val all = (0 until locales.size()).map { locales[it] }
        val language = AppLanguages.resolve(all.map { it.toLanguageTag() })
        // Keep the region (es-MX, pt-BR) for dates and numbers when it belongs to the chosen language.
        return all.firstOrNull { it.language == language } ?: Locale.forLanguageTag(language)
    }
}
