# Tasks: ten-language UI, RTL, and result tailoring (Android)

## Locale core

- [x] `i18n/SupportedLocales.kt`: the ten-language `AppLocale` registry (tag, English name, endonym,
      RTL flag), `normalizeTag` (incl. the Indonesian `in`->`id` legacy code), `isSupported`, `isRtl`,
      `javaLocaleFor` (Simplified-Chinese region qualification), `resolveSystemTag` (fail-soft),
      `effectiveTag`.

## Authoring + translations

- [x] `tools/i18n_author.py`: offline ollama (`translategemma`) authoring of the nine target locales
      from `values/strings.xml`; incremental/resumable; placeholder masking (`%s`/`%d`/`%1$s`/`%1$d`
      and `{token}`); echo detection; Android XML escaping; `values-<q>` qualifier mapping
      (`values-b+id`, `values-zh-rCN`).
- [x] `res/values-<q>/strings.xml` for all nine targets, with English fallback for any missing key.
- [x] `res/values/strings.xml`: new label keys (`settings_language`, `settings_language_system`,
      `search_results_for`, `search_sort_label`, `search_apply`); `app_name` `translatable="false"`;
      `MissingTranslation` suppressed (English fallback is intentional).

## App locale application

- [x] `i18n/LocalizedApp.kt`: Compose override of `LocalConfiguration`/`LocalContext`/
      `LocalLayoutDirection` from the `language` pref (single source of truth; live, no restart, RTL).
- [x] `MainActivity.kt`: wrap `SearchMobTheme` in `LocalizedApp(prefs.language)`.
- [x] `ui/prefs/UserPreferences.kt` + `PreferencesRepository.kt`: the `language` pref (DataStore),
      folded into the presentation `combine`, with `setLanguage`/one-shot read.
- [x] `ui/settings/SettingsScreen.kt` + `SettingsViewModel.kt`: the language picker (endonyms +
      "Follow system") and its setter.

## Served page

- [x] `server/SearchServer.kt`: a `ServedText` bundle (locale-adjusted `Resources`, reusing the
      in-app `R.string`); per-request locale resolution (pinned pref -> `Accept-Language` -> OS ->
      English); localized home + results render + `lang`/`dir` (RTL) on every page; optional app
      context (English fallback in tests).
- [x] `service/SearchMobService.kt`: pass the application context to `SearchServer`.

## Result tailoring

- [x] `engine/Region.kt`: `LanguageRegion` + `languageRegionFor` (DuckDuckGo `kl`; Brave
      `search_lang`/`country`/`ui_lang`); `null` for English/unmapped (ported from desktop).
- [x] `engine/EngineAdapter.kt` (+ `EngineRegistry`, `DuckDuckGoAdapter`, `BraveApiAdapter`): carry
      and read the language/region params; other engines unchanged.
- [x] Thread the active locale to tailoring: `MetaSearchResultProvider` `languageProvider` lambda
      wired from the in-app repo (`AppDependencies`) and the served path (`SearchMobService`).

## Tests

- [x] `i18n/SupportedLocalesTest.kt`: normalization/fallback, RTL, `javaLocaleFor`, the shipped set.
- [x] `engine/RegionTest.kt`: `languageRegionFor` params + `null`; DuckDuckGo/Brave requests gain the
      params for a non-English locale and none for English (via `MockWebServer`).
- [x] `i18n/StringCatalogIntegrityTest.kt`: no stray keys, placeholder integrity per locale.

## Verify

- [x] `ktlintCheck`, `:app:lintDebug`, `:app:testDebugUnitTest`, `:app:assembleDebug` green.
- [x] `openspec validate add-language-i18n --strict` passes.
- [ ] Emulator spot-check: live switch en/es/ar (incl. Arabic RTL), served page localized + `dir=rtl`,
      results tailored. Screenshots per the release-verification procedure.
- [ ] Own PR to `main` behind green CI.
