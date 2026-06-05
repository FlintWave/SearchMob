# Design: ten-language UI, RTL, and result tailoring (Android)

## Locale core (`i18n/SupportedLocales.kt`)

A single source of truth shared by the picker, the Compose override, the served page, and result
tailoring. It holds the ten `AppLocale`s (tag, English name, endonym, RTL flag), `normalizeTag`
(reduce any BCP-47-ish tag to a shipped primary subtag, else English), `isSupported`, `isRtl`,
`javaLocaleFor` (the JVM `Locale` to put on a `Configuration`), `resolveSystemTag` (the device
language reduced to a shipped tag), and `effectiveTag` (pinned tag, else system). Mirrors the desktop
`i18n/locales.py`.

Two JVM traps are handled here: Indonesian (a `Locale` built for `id` reports its language as the
legacy `in`, so `normalizeTag` maps `in` back to `id` and the resources live in `values-b+id`), and
Simplified Chinese (`javaLocaleFor("zh")` returns `zh-CN` so the `values-zh-rCN` folder matches).
`resolveSystemTag` is fail-soft (English when the framework locale cannot be read) so callers never
have to guard it.

## Translating the UI: per-locale `values-<q>/strings.xml`, authored offline

The English `values/strings.xml` is the source of truth. Android resolves the right translation at
runtime from a `values-<qualifier>/strings.xml` sibling, falling back to the base string for any
missing key — so the catalog degrades to readable English rather than a placeholder, and a few
intentionally-untranslated strings (loanwords, the brand name) are simply absent.

`tools/i18n_author.py` is an offline developer tool (not shipped) that translates the base file into
the nine targets with a local ollama model (`translategemma`), so strings are real translations, not
fabrications, and the catalog is regenerable. It is incremental and resumable (only fills keys a
locale is missing). Two correctness measures, ported from the desktop authoring tool, matter because
the model is imperfect:
- **Placeholder masking.** Before translating, every format placeholder — `%s`, `%d`, Android's
  positional `%1$s`/`%1$d`, and any `{token}` — is replaced with an opaque `{pN}` token and restored
  afterward. Without this the model echoes sentences containing a bare `%s` and translates a
  placeholder's name, breaking the format string.
- **Echo detection.** A model that returns the input unchanged has failed; the tool retries once with
  an insistent prompt, then keeps the English source (which Android falls back to). It also preserves
  Android escaping (`\'`, `&amp;`, the positional specs) on the way out.

`MissingTranslation` lint is suppressed on the base `<resources>` because English fallback for an
un-authored key is intentional, and `app_name` is `translatable="false"`. A test
(`StringCatalogIntegrityTest`) guards the safety-critical invariants: no locale carries a key absent
from the English source, and every translated value keeps the exact format placeholders of its
source.

## Applying the locale in the app: a self-managed Compose override

The app is a deliberately AppCompat-free `ComponentActivity` + framework-Material Compose stack with
`minSdk 26`, and it already switches theme reactively (no restart) by collecting a
`Flow<UserPreferences>` and recomposing. Language switching reuses that exact pattern instead of the
AppCompat per-app-language API (which would force a dependency, a theme reparent, an `AppCompatActivity`
switch, and an Activity recreate that defeats live switching).

`LocalizedApp(languageTag) { ... }` (in `i18n/LocalizedApp.kt`) wraps the app content and overrides
`LocalConfiguration`, `LocalContext` (a `createConfigurationContext` with the chosen locale), and
`LocalLayoutDirection`. `stringResource` then resolves in the chosen language and the tree flips to
right-to-left for Arabic/Urdu, live and reactively, with no `recreate()`. The saved `language`
preference (empty = follow the OS) is the single source of truth; "follow the OS" resolves from the
host context's own configuration, so a system per-app locale is still honored. `MainActivity` wraps
`SearchMobTheme` in `LocalizedApp(prefs.language)`. `android:supportsRtl="true"` is already set.

The `language` preference is stored in DataStore exactly like the theme prefs (folded into the
existing presentation sub-`combine` in `PreferencesRepository`, with a `setLanguage` writer and a
one-shot read). The Settings screen adds a single-select language dropdown modeled on the theme
selector, listing the locales by endonym plus a "Follow system" entry that maps to the empty value.

## Served page (`server/SearchServer.kt`)

The served pages are built with the kotlinx.html DSL. A `ServedText` bundle holds a per-request,
locale-adjusted `Resources` (built from the application context via `createConfigurationContext`) plus
the resolved `tag` and `rtl` flag; it exposes the chrome strings, reusing the same `R.string`
resources the in-app UI uses (so a string is authored once for both surfaces). The home and results
render functions and their helpers (verticals, sort bar, summary, did-you-mean, settings link, update
banner) take this bundle and set the document `lang`/`dir`. The owner-only served settings page sets
`lang`/`dir` and its localized title; its deeper admin sub-cards stay English, since the fully
localized native Settings screen is the primary settings surface on Android.

The per-request locale resolution mirrors the desktop precedence: the owner's saved `language`, then
the visitor's `Accept-Language` (first supported entry), then the OS, then English. The application
context is optional (null in tests / when not wired), in which case every served string falls back to
its English default, so existing server tests are unaffected.

## Result tailoring (`engine/Region.kt`)

A non-English locale biases result requests toward that language without changing how results are
ranked. `languageRegionFor(tag)` returns a `LanguageRegion` (or `null` for English / unmapped),
ported verbatim from the desktop `engines/region.py`. It rides on the `EngineContext`:
- DuckDuckGo reads `ddgKl` (its region-language code; empty where DDG has no region for the locale).
- Brave reads `braveSearchLang` + `braveCountry` + `braveUiLang`.
- Mojeek, Marginalia, Mwmbl, and Kagi have no documented parameter and are untouched.

The active locale is threaded from the UI path (the `language` preference, via a `languageProvider`
lambda on the in-process repository) and the served path (the owner's language) into
`EngineRegistry.activeEngines`, which sets the `LanguageRegion` on each engine's context. English and
unmapped locales yield `null`, so the request is byte-for-byte the current region-neutral request and
English-locale search cannot regress.

## Testing

- `SupportedLocalesTest`: tag normalization/reduction (including the Indonesian legacy code and
  Simplified-Chinese region qualification), `isRtl`, `isSupported`, `effectiveTag`, the shipped set.
- `RegionTest`: `languageRegionFor` per-locale params and `null` for English/unmapped; the DuckDuckGo
  request gains `kl` for a non-English locale and none for English; the Brave request gains
  country/search_lang/ui_lang for a non-English locale and none for English (driven through the
  adapters against a `MockWebServer`).
- `StringCatalogIntegrityTest`: no locale carries a stray key, and every translated value keeps its
  source's format placeholders.
- The existing server and Compose tests stay green (the served context is optional → English; the
  Compose override is additive).
