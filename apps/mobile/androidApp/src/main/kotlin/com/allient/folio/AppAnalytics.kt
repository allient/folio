package com.allient.folio

import android.content.Context
import android.os.Bundle
import android.telephony.TelephonyManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.Locale
import java.util.TimeZone

/**
 * Google Analytics for Firebase (anonymous usage statistics: screens and feature usage) and
 * Firebase Crashlytics (crash reports). One user choice controls both.
 *
 * - Never logs note or book content, titles, file names or search text. Parameters are short
 *   enums (for example `kind=pdf`), never user text.
 * - No advertising ID, no ad storage or personalization (manifest meta-data and consent mode).
 * - On by default, except in the EU/EEA, UK and Switzerland, where nothing is sent until the user
 *   answers the one-time prompt ([needsChoice]). Preferences › Privacy turns it off at any time.
 * - Without `google-services.json` Firebase isn't initialised and every call is a no-op.
 */
object AppAnalytics {
    private const val PREFS = "analytics"
    private const val ENABLED = "share_usage_stats"

    private var analytics: FirebaseAnalytics? = null
    private var crashlytics: FirebaseCrashlytics? = null
    private lateinit var prefs: android.content.SharedPreferences
    private var consentRegion = false

    /** Observable for Compose: the current choice, and whether the one-time prompt is still due. */
    var collecting by mutableStateOf(false)
        private set
    var choicePending by mutableStateOf(false)
        private set

    fun init(context: Context) {
        if (analytics != null) return
        val app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        consentRegion = isConsentRegion(app)
        analytics = runCatching { FirebaseAnalytics.getInstance(app) }.getOrNull()
        crashlytics = analytics?.let { runCatching { FirebaseCrashlytics.getInstance() }.getOrNull() }
        apply(isEnabled())
    }

    /** True until a user in a consent region has answered the prompt. */
    fun needsChoice(): Boolean = analytics != null && consentRegion && !prefs.contains(ENABLED)

    fun isEnabled(): Boolean = if (!::prefs.isInitialized) false else if (prefs.contains(ENABLED)) prefs.getBoolean(ENABLED, false) else !consentRegion

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(ENABLED, enabled).apply()
        apply(enabled)
    }

    /** Records a handled error in Crashlytics (no user text in [where]). */
    fun recordError(error: Throwable, where: String) {
        if (!isEnabled()) return
        crashlytics?.run { setCustomKey("where", where); recordException(error) }
    }

    fun screen(name: String) = event(FirebaseAnalytics.Event.SCREEN_VIEW, FirebaseAnalytics.Param.SCREEN_NAME to name)

    /** [params] values must be short identifiers, never user text. */
    fun event(name: String, vararg params: Pair<String, Any>) {
        val target = analytics ?: return
        if (!isEnabled()) return
        val bundle = Bundle()
        params.forEach { (key, value) ->
            when (value) {
                is Long -> bundle.putLong(key, value)
                is Int -> bundle.putLong(key, value.toLong())
                is Boolean -> bundle.putString(key, value.toString())
                else -> bundle.putString(key, value.toString().take(40))
            }
        }
        target.logEvent(name, bundle)
    }

    private fun apply(enabled: Boolean) {
        collecting = enabled
        choicePending = needsChoice()
        val target = analytics ?: return
        val granted = if (enabled) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED
        target.setConsent(mapOf(
            FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to granted,
            FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
            FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
            FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED,
        ))
        target.setAnalyticsCollectionEnabled(enabled)
        crashlytics?.isCrashlyticsCollectionEnabled = enabled
    }

    /** EU/EEA, UK and Switzerland, from the SIM or network country, then the locale and time zone. */
    private fun isConsentRegion(context: Context): Boolean {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val country = listOfNotNull(telephony?.simCountryIso, telephony?.networkCountryIso, Locale.getDefault().country)
            .firstOrNull { it.isNotBlank() }?.uppercase(Locale.ROOT)
        return country in ConsentCountries || TimeZone.getDefault().id.startsWith("Europe/")
    }

    private val ConsentCountries = setOf(
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU",
        "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE", "IS", "LI", "NO", "GB", "CH",
    )
}
