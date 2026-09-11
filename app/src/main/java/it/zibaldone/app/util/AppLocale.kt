package it.zibaldone.app.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The languages the UI can be shown in.
 *
 * [SYSTEM] is not a language: it means "whatever the device asks for",
 * so an Italian phone gets `values-it` and everything else falls back to
 * the default (English) resources. The two explicit entries exist because
 * the app is used on tablets whose system language does not always match
 * the language the user wants to work in.
 *
 * [tag] is a BCP-47 tag, empty for [SYSTEM]; it is what gets persisted,
 * so new entries can be added without touching the stored value format.
 */
enum class AppLocale(val tag: String) {
    SYSTEM(""),
    ITALIAN("it"),
    ENGLISH("en");

    companion object {
        /** Reverse lookup for the persisted [tag]; unknown tags fall back to [SYSTEM]. */
        fun fromTag(tag: String?): AppLocale = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

/**
 * Reads and applies the in-app language choice.
 *
 * Why not `AppCompatDelegate.setApplicationLocales`: the app has a single
 * [androidx.activity.ComponentActivity] and no AppCompat theme or activity,
 * and the per-app locale API is only native from API 33 (this app ships
 * with `minSdk = 26`). Wrapping the base context in `attachBaseContext` is
 * the mechanism that behaves identically on every supported release, adds
 * no dependency, and keeps a single source of truth for the choice — the
 * preference below.
 *
 * The wrapped context is what Compose hands to `stringResource`, so the
 * whole UI (and the Toasts raised from the ViewModel with the same
 * context) follows the choice after an `Activity.recreate()`.
 */
object LocalePreference {
    private const val PREFS_NAME = "zibaldone_settings"
    private const val KEY_LANGUAGE = "ui_language"

    /** The language currently chosen by the user, [AppLocale.SYSTEM] until they pick one. */
    fun current(context: Context): AppLocale =
        AppLocale.fromTag(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, null)
        )

    /** Stores the choice; the caller is expected to recreate the activity afterwards. */
    fun store(
        context: Context,
        locale: AppLocale
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, locale.tag)
            .apply()
    }

    /**
     * Returns [base] re-configured for the stored language, or [base] itself
     * when the user follows the system. Call it from `attachBaseContext`:
     * resources resolved later (including the ones Compose reads) then come
     * from the overridden configuration.
     */
    fun wrap(base: Context): Context {
        val choice = current(base)
        if (choice == AppLocale.SYSTEM) return base

        val locale = Locale.forLanguageTag(choice.tag)
        // Locale.setDefault so that anything formatting outside the resource
        // system (dates, numbers) agrees with the UI language.
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }
}
