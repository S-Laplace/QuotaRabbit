package com.alice.quotarabbit.core.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import com.alice.quotarabbit.domain.settings.LanguagePreference
import java.util.Locale

object AppLocaleController {
    /** Update process formatting without requesting an Activity recreation. */
    fun applyProcessDefaultLocale(preference: LanguagePreference) {
        val locale = (localeListFor(preference) ?: supportedSystemLocaleList())[0] ?: return
        if (Locale.getDefault() != locale) Locale.setDefault(locale)
    }

    fun apply(context: Context, preference: LanguagePreference) {
        val chosenLocales = localeListFor(preference)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java)
            val expected = chosenLocales ?: LocaleList.getEmptyLocaleList()
            if (manager.applicationLocales != expected) manager.applicationLocales = expected
        }
        updateProcessResources(context, chosenLocales ?: supportedSystemLocaleList())
    }

    fun localizedContext(context: Context, preference: LanguagePreference): Context {
        val localeList = localeListFor(preference) ?: supportedSystemLocaleList()
        val localizedResources = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                setLocales(localeList)
            },
        ).resources
        // Keep the original Activity in the ContextWrapper chain so Compose's
        // ActivityResultRegistryOwner and LifecycleOwner are still discoverable.
        return object : ContextWrapper(context) {
            override fun getResources(): Resources = localizedResources
        }
    }

    internal fun supportedSystemLocaleList(
        systemLocales: LocaleList = Resources.getSystem().configuration.locales,
    ): LocaleList {
        for (index in 0 until systemLocales.size()) {
            val supportedLocale = supportedLocaleFor(systemLocales[index])
            if (supportedLocale != null) {
                return LocaleList(supportedLocale)
            }
        }
        return LocaleList(Locale.ENGLISH)
    }

    @Suppress("DEPRECATION")
    private fun updateProcessResources(context: Context, localeList: LocaleList) {
        val primaryLocale = localeList[0] ?: return
        Locale.setDefault(primaryLocale)
        context.resources.updateConfiguration(
            Configuration(context.resources.configuration).apply {
                setLocales(localeList)
            },
            context.resources.displayMetrics,
        )
    }

    private fun localeListFor(preference: LanguagePreference): LocaleList? =
        preference.localeTag?.let(LocaleList::forLanguageTags)

    private fun supportedLocaleFor(locale: Locale): Locale? =
        when (locale.language.lowercase(Locale.ROOT)) {
            "en" -> Locale.ENGLISH
            "ko" -> Locale.KOREAN
            "zh" -> {
                if (
                    locale.country.equals("CN", ignoreCase = true) ||
                    locale.script.equals("Hans", ignoreCase = true)
                ) {
                    Locale.SIMPLIFIED_CHINESE
                } else {
                    null
                }
            }
            else -> null
        }
}
