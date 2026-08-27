package app.readbound.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleController {
    fun apply(context: Context, language: String, recreate: Boolean = false) {
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(
                context.resources.configuration.apply { setLocale(locale) },
                context.resources.displayMetrics,
            )
            if (recreate) (context as? Activity)?.recreate()
        }
    }
}
