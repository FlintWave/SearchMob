package org.searchmob.i18n

import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Wraps [content] so the whole Compose tree renders in the chosen UI [languageTag] and lays out in
 * that language's direction. The saved language preference is the single source of truth: a non-empty
 * supported tag wins; an empty value ("follow the OS") falls back to the host context's own locale, so
 * a system per-app language still applies.
 *
 * This overrides [LocalConfiguration], [LocalContext], and [LocalLayoutDirection] with a
 * locale-adjusted configuration and context. Because the preference is collected reactively upstream,
 * switching the language re-runs this and the entire UI re-translates and (for Arabic/Urdu) flips to
 * right-to-left live, with no Activity restart, mirroring how the theme switch already works. No
 * dependency on AppCompat or the system per-app-locale machinery.
 */
@Composable
fun LocalizedApp(
    languageTag: String,
    content: @Composable () -> Unit,
) {
    val base = LocalContext.current
    val baseConfig = LocalConfiguration.current

    // Resolve the effective tag: the pinned language, else the host's current locale (device or any
    // system per-app override), normalized to a shipped locale (else English).
    val effectiveTag =
        if (languageTag.isNotBlank() && SupportedLocales.isSupported(languageTag)) {
            SupportedLocales.normalizeTag(languageTag)
        } else {
            SupportedLocales.normalizeTag(baseConfig.locales.get(0).toLanguageTag())
        }

    val localizedConfig =
        remember(effectiveTag, baseConfig) {
            Configuration(baseConfig).apply { setLocale(SupportedLocales.javaLocaleFor(effectiveTag)) }
        }
    // Wrap the host context (the Activity) rather than handing back the detached context from
    // createConfigurationContext: a ContextWrapper keeps the baseContext chain intact, so things that
    // walk it to find the Activity (e.g. rememberLauncherForActivityResult) still work, while
    // getResources() returns the locale-adjusted resources Compose reads strings from.
    val localizedContext =
        remember(base, localizedConfig) {
            val configContext = base.createConfigurationContext(localizedConfig)
            object : ContextWrapper(base) {
                override fun getResources(): Resources = configContext.resources

                override fun getAssets() = configContext.assets
            }
        }
    val layoutDirection =
        if (SupportedLocales.isRtl(effectiveTag)) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalConfiguration provides localizedConfig,
        LocalContext provides localizedContext,
        LocalLayoutDirection provides layoutDirection,
        content = content,
    )
}
