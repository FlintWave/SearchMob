## Why

Users want control over their own results, the way Kagi lets you raise, lower, pin, or block sites and
scope searches with lenses, so they can escape a one-size-fits-all ranking. Kagi stores those
preferences server-side against an account. SearchMob can do the same thing more privately: keep the
rules in the existing encrypted on-device store and apply them locally, after results come back, so
nothing about a user's preferences ever leaves the device. This serves the locked goals of being
private and customizable.

## What Changes

- Add user-controlled, on-device result ranking applied after aggregation, in
  `MetaSearchResultProvider`, so it affects both the in-app search and the browser-facing `/search`.
  Ranking is a deterministic, score-free reordering: blocked sites are dropped, pinned sites go to the
  top, raised sites sort above normal, lowered sites sort to the bottom, each bucket preserving the
  prior order.
- **Per-domain rules**: Block, Lower, Raise, Pin, or Normal for a domain. An inline menu on each result
  sets the rule for that result's domain with immediate effect; a Settings list manages all rules.
- **Lenses**: named include/exclude domain sets (plus optional include/exclude keywords matched against
  title and snippet) that scope a search. The active lens is selectable and sticky.
- **Goggles import**: import a subset of Brave Goggles rule files (`site=` patterns with simple
  wildcards, `$boost`, `$downrank`, `$discard`) from a local file or pasted text, so community re-ranking
  rule sets can be reused. URL import, if offered, is a one-time user-initiated fetch and is disclosed.
- All rules persist in the encrypted preferences store (the same DEK-encrypted store as BYO API keys),
  are editable in Settings, and can be exported/imported as JSON for portability and sharing.

## Impact

- Affected specs: new capability `result-personalization`.
- Affected code: new `engine/rank/**` (rules model, `DomainRanker`, Goggles parser), new
  `data/prefs/RankingPreferences.kt` over the encrypted `PreferencesStore`,
  `engine/MetaSearchResultProvider.kt` (apply the ranker, read rules), `engine/aggregate/UrlNormalizer.kt`
  (reuse host extraction), `SearchMobApplication`/`AppDependencies`/`SearchMobService` (provide the rules
  to both graphs), `ui/search/SearchScreen.kt` + `SearchViewModel.kt` (inline menu + instant re-rank),
  `ui/settings/SettingsScreen.kt` + `SettingsViewModel.kt` (manager).
- No new Android permission; no new outbound network call (ranking is applied locally to results
  already fetched; rules never leave the device). Goggles URL import, if added, is the only optional,
  user-initiated fetch.
