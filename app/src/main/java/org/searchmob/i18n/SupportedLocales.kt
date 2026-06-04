package org.searchmob.i18n

import android.content.res.Resources
import java.util.Locale

/**
 * The set of UI languages SearchMob ships, plus tag normalization and OS-locale resolution.
 *
 * The app translates its whole interface into the ten most-spoken world languages (English plus nine
 * authored locales). Each [AppLocale] carries its BCP-47 tag, an English name, the language's own
 * endonym (shown in the picker so a speaker recognizes it), and whether it is written right-to-left.
 * Mirrors the desktop app's `i18n/locales.py` so the two apps share one notion of what is supported.
 *
 * This object is the single source of truth for the picker, the in-app locale override, the served
 * page, and the per-engine result tailoring.
 */
object SupportedLocales {
    const val DEFAULT_TAG: String = "en"

    /** One shippable UI language. */
    data class AppLocale(
        val tag: String,
        val englishName: String,
        val nativeName: String,
        val rtl: Boolean = false,
    )

    // The ten languages, ordered by global speaker count (English is the source-of-truth locale).
    // `zh` is Simplified Chinese; Arabic and Urdu are right-to-left. Native names are the endonyms.
    val SUPPORTED: List<AppLocale> =
        listOf(
            AppLocale("en", "English", "English"),
            AppLocale("zh", "Chinese (Simplified)", "简体中文"),
            AppLocale("hi", "Hindi", "हिन्दी"),
            AppLocale("es", "Spanish", "Español"),
            AppLocale("ar", "Arabic", "العربية", rtl = true),
            AppLocale("fr", "French", "Français"),
            AppLocale("bn", "Bengali", "বাংলা"),
            AppLocale("pt", "Portuguese", "Português"),
            AppLocale("id", "Indonesian", "Indonesia"),
            AppLocale("ur", "Urdu", "اردو", rtl = true),
        )

    private val byTag: Map<String, AppLocale> = SUPPORTED.associateBy { it.tag }

    // Legacy ISO-639 codes the JVM still reports for some languages (Indonesian especially: a Locale
    // built for "id" reports its language as "in"). Map them back to the modern shipped tag so OS
    // detection and any stored value resolve correctly.
    private val legacyAliases: Map<String, String> = mapOf("in" to "id", "iw" to "he", "ji" to "yi")

    /** Reduce a BCP-47-ish tag to a supported primary subtag, or [DEFAULT_TAG]. */
    fun normalizeTag(tag: String?): String {
        if (tag.isNullOrBlank()) return DEFAULT_TAG
        val primary = tag.trim().lowercase().replace('_', '-').substringBefore('-')
        val canonical = legacyAliases[primary] ?: primary
        return if (canonical in byTag) canonical else DEFAULT_TAG
    }

    /** True when [tag]'s primary subtag is one of the shipped locales. */
    fun isSupported(tag: String?): Boolean {
        if (tag.isNullOrBlank()) return false
        val primary = tag.trim().lowercase().replace('_', '-').substringBefore('-')
        return (legacyAliases[primary] ?: primary) in byTag
    }

    /** The [AppLocale] for [tag] (normalized), defaulting to English. */
    fun localeFor(tag: String?): AppLocale = byTag.getValue(normalizeTag(tag))

    /** True when the (normalized) locale is written right-to-left (Arabic, Urdu). */
    fun isRtl(tag: String?): Boolean = localeFor(tag).rtl

    /**
     * The JVM [Locale] to put on a [android.content.res.Configuration] so the matching
     * `values-<qualifier>/strings.xml` resolves. Simplified Chinese needs the region (`zh-CN`) to
     * match the `values-zh-rCN` folder; the rest match on language alone.
     */
    fun javaLocaleFor(tag: String): Locale =
        when (normalizeTag(tag)) {
            "zh" -> Locale.forLanguageTag("zh-CN")
            else -> Locale.forLanguageTag(normalizeTag(tag))
        }

    /**
     * The device's UI language reduced to a shipped tag, or [DEFAULT_TAG] when unsupported. Fail-soft:
     * returns English if the framework locale cannot be read (e.g. in a plain JVM unit test with no
     * Android runtime), so callers never have to guard the call.
     */
    fun resolveSystemTag(): String =
        runCatching {
            val system = Resources.getSystem().configuration.locales
            if (system.isEmpty) DEFAULT_TAG else normalizeTag(system.get(0).toLanguageTag())
        }.getOrDefault(DEFAULT_TAG)

    /**
     * The effective language tag to use: the saved [prefTag] when it names a shipped locale, else the
     * OS language (else English). The pref stores "" to mean "follow the OS".
     */
    fun effectiveTag(prefTag: String?): String =
        if (!prefTag.isNullOrBlank() && isSupported(prefTag)) normalizeTag(prefTag) else resolveSystemTag()
}
