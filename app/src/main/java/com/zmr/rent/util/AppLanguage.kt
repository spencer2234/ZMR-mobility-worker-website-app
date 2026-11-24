package com.zmr.rent.util

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import java.util.Locale

/** The app's own language setting, which works on every Android version the app supports. */
object AppLanguage {
    /** Language code to its name, written in that language. */
    val OPTIONS = listOf("en" to "English", "hi" to "हिन्दी", "ta" to "தமிழ்", "kn" to "ಕನ್ನಡ")

    private const val PREFS = "app_language"
    private const val KEY = "code"

    /** The chosen code, or the phone's language when it is one the app supports. */
    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: Locale.getDefault().language.takeIf { code -> OPTIONS.any { it.first == code } }
            ?: "en"

    fun save(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, code) }
    }

    /** A context using the saved language. Applied when the activity starts. */
    fun wrap(base: Context): Context {
        val code = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return base
        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(locale) })
    }
}
