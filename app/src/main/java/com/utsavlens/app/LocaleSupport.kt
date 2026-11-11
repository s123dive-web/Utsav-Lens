package com.utsavlens.app

import android.content.Context
import android.content.res.Configuration
import com.utsavlens.app.di.AppModule
import com.utsavlens.core.AppLanguage
import com.utsavlens.core.settings.SettingsCodec
import java.util.Locale

/**
 * In-app language (English / Hindi / Marathi) without AppCompat: every Activity and Service wraps
 * its base context with the locale stored in settings. The settings file is tiny, so it is read
 * synchronously here; the Activity is recreated when the user changes the language.
 */
object LocaleSupport {
    @Volatile private var cachedTag: String? = null

    fun currentTag(context: Context): String {
        cachedTag?.let { return it }
        val tag = try {
            val file = AppModule.settingsFile(context)
            SettingsCodec().decode(if (file.isFile) file.readText() else null).language
        } catch (_: Exception) {
            AppLanguage.SYSTEM.tag
        }
        cachedTag = tag
        return tag
    }

    fun onLanguageChanged(tag: String) {
        cachedTag = tag
    }

    fun wrap(base: Context): Context {
        val language = AppLanguage.fromTag(currentTag(base))
        if (language == AppLanguage.SYSTEM) return base
        val locale = Locale.forLanguageTag(language.tag)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /** The language theme names and built-in greetings should be shown in. */
    fun contentLanguage(context: Context): AppLanguage {
        val setting = AppLanguage.fromTag(currentTag(context))
        if (setting != AppLanguage.SYSTEM) return setting
        return AppLanguage.contentLanguage(context.resources.configuration.locales[0].toLanguageTag())
    }
}
