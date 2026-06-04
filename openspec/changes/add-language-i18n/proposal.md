## Why

The Android app is English-only: every screen, the served HTTP page, and the notifications are
hardcoded English, and searches are region-neutral regardless of UI language. For most of the world
that does not read English comfortably the app is unusable as a daily search tool even though the
results it aggregates are language-neutral. The desktop sibling shipped a ten-language UI; this change
brings Android to parity.

This makes the whole interface speak ten languages: English plus the nine most-spoken world languages
(Chinese, Hindi, Spanish, Arabic, French, Bengali, Portuguese, Indonesian, Urdu). The user picks a
language once (it is remembered, and on first launch follows the OS); Arabic and Urdu lay out
right-to-left; the served page mirrors the choice; and result requests are tailored to the chosen
language. Translating result pages themselves is out of scope — SearchMob translates its own
interface and asks the engines for language-appropriate results.

## What Changes

- Add a locale registry (the ten shipped languages, each with its English name, endonym, and text
  direction) plus tag normalization and OS-locale resolution, shared by every surface.
- Translate the whole interface via per-locale `values-<qualifier>/strings.xml`, authored offline
  from the English `values/strings.xml` by a local model (not by hand), with English as the
  source-of-truth fallback for any missing key.
- Apply the chosen language in the app with a self-managed Compose locale override: the saved
  `language` preference (empty = follow the OS) is the single source of truth, and the whole UI
  re-translates and (for Arabic/Urdu) flips to right-to-left live, with no Activity restart, exactly
  as the existing theme switch already works. No AppCompat dependency, no theme/Activity churn.
- Add a language picker to Settings; persist the choice via DataStore; default to the OS language.
- Localize the served home and results pages and set the document `lang`/`dir` (RTL) on every served
  page, resolving the locale per request (owner's saved language, then `Accept-Language`, then the
  OS, then English).
- Tailor results to the chosen language: a non-English locale carries per-engine language/region
  parameters (DuckDuckGo `kl`; Brave `country` + `search_lang` + `ui_lang`). English and any unmapped
  locale stay region-neutral, exactly as before. Engines without such a parameter are untouched.

## Capabilities

### New Capabilities
- `language-i18n`: a ten-language UI translated through per-locale resources with English fallback,
  right-to-left layout for Arabic and Urdu, a persisted per-app language choice that follows the OS
  by default and switches live, a localized served page with per-request locale resolution, and
  result tailoring through per-engine language/region parameters.

### Modified Capabilities
<!-- None in contract. Result ranking, scopes, theming, and the engine set keep their meanings; the
language/region parameters tailor result requests without changing how results are ranked or filtered. -->

## Non-goals

- Translating result content. SearchMob translates its own interface and asks engines for
  language-appropriate results; it does not machine-translate the pages other engines return.
- Per-string human translation review this change. Translations are model-authored from the English
  source and regenerable; a human polish pass can follow without reopening this contract.
- A separate region/country control. The language choice drives the result region; there is no
  independent country selector in this change.
- Adding the AppCompat per-app-language machinery. The app stays a pure ComponentActivity + Compose
  stack; the language is applied via a Compose locale override (the user-confirmed approach). The
  in-app picker is the single control (no `localeConfig`).
- New runtime dependencies or any network/telemetry. The authoring tool is an offline developer
  script; the shipped app reads static resources and makes no new outbound calls (the region
  parameters ride existing engine requests only).

## Impact

- New: `i18n/SupportedLocales.kt` (the ten-language registry + normalization + OS resolution);
  `i18n/LocalizedApp.kt` (the Compose locale/direction override); `engine/Region.kt` (`LanguageRegion`
  + `languageRegionFor`, ported from desktop); `res/values-<q>/strings.xml` for the nine targets;
  `tools/i18n_author.py` (offline authoring); tests.
- Modified: `MainActivity.kt` (wrap the app in the locale override); `ui/prefs/UserPreferences.kt`,
  `PreferencesRepository.kt`, `ui/settings/SettingsViewModel.kt` (the `language` pref);
  `ui/settings/SettingsScreen.kt` (the picker); `res/values/strings.xml` (new label keys);
  `server/SearchServer.kt` (localized render + `lang`/`dir` + per-request locale resolution);
  `engine/EngineAdapter.kt`, `EngineRegistry.kt`, `MetaSearchResultProvider.kt`,
  `engine/adapters/DuckDuckGoAdapter.kt`, `BraveApiAdapter.kt`, `ui/search/SearchResultsRepository.kt`,
  `service/SearchMobService.kt`, `ui/AppDependencies.kt` (thread the locale to result tailoring).
- No new dependencies, no new outbound calls, no telemetry. English-locale behaviour is unchanged.
