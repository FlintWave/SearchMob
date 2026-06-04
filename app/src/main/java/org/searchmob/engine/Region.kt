package org.searchmob.engine

/**
 * Maps a UI locale to the per-engine language/region parameters that tailor results to it.
 *
 * When the interface is switched to a language, results should follow: DuckDuckGo takes a `kl`
 * region-language code, and the Brave API takes `country` + `search_lang` + `ui_lang`. This holds
 * the small, sourced lookup from a shipped locale tag to those values; [languageRegionFor] returns
 * `null` for English (and any unmapped tag), so the engines fall back to their default,
 * region-neutral behaviour exactly as before.
 *
 * Only engines that document a language/region parameter use this (DuckDuckGo, Brave). Mojeek,
 * Marginalia, Mwmbl, and Kagi have no such parameter and are left unchanged. Ported 1:1 from the
 * desktop app's `engines/region.py` so the two apps tailor results identically.
 */
data class LanguageRegion(
    /** DuckDuckGo's region-language code (e.g. `es-es`); empty when DDG has no region for the locale. */
    val ddgKl: String = "",
    /** Brave API `search_lang` (ISO-639-1). */
    val braveSearchLang: String = "",
    /** Brave API `country` (ISO-3166 alpha-2). */
    val braveCountry: String = "",
    /** Brave API `ui_lang` (BCP-47). */
    val braveUiLang: String = "",
)

// Locale tag -> engine parameters. English is intentionally absent (region-neutral default). DDG
// `kl` uses its region-language form; some locales have no DDG region (left empty). Brave
// search_lang/country/ui_lang are filled per the Brave API's documented codes.
private val REGIONS: Map<String, LanguageRegion> =
    mapOf(
        "zh" to LanguageRegion("cn-zh", "zh-hans", "CN", "zh-CN"),
        "hi" to LanguageRegion("in-en", "hi", "IN", "hi-IN"),
        "es" to LanguageRegion("es-es", "es", "ES", "es-ES"),
        "ar" to LanguageRegion("xa-ar", "ar", "SA", "ar-SA"),
        "fr" to LanguageRegion("fr-fr", "fr", "FR", "fr-FR"),
        "bn" to LanguageRegion("", "bn", "IN", "bn-IN"),
        "pt" to LanguageRegion("br-pt", "pt", "BR", "pt-BR"),
        "id" to LanguageRegion("id-id", "id", "ID", "id-ID"),
        "ur" to LanguageRegion("", "ur", "PK", "ur-PK"),
    )

/** The engine language/region params for a locale [tag], or `null` (English / unmapped). */
fun languageRegionFor(tag: String?): LanguageRegion? {
    if (tag.isNullOrBlank()) return null
    val primary = tag.trim().lowercase().replace('_', '-').substringBefore('-')
    return REGIONS[primary]
}
