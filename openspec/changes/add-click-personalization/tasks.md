## 1. Core model (A1)

- [x] 1.1 Add `engine/rank/Personalization.kt`: the `PersonalizationModel` + `Personalizer` (key construction, `updateFromClick`, `boost`, `reorder`, decay, caps/eviction, `toJson`/`fromJson` as `beta_bernoulli_v1`), a one-to-one port of the desktop `personalize.py`.
- [x] 1.2 Add `data/prefs/PersonalizationPreferences.kt`: load/save/reset/export/import over the encrypted store, key `ranking.personalization`, fail-soft to an empty model.
- [x] 1.3 Add the `personalization_enabled` flag + `onboarding_version` to `ui/prefs/**`.

## 2. Apply pass and native click training (A1)

- [x] 2.1 Insert `Personalizer.reorder` in `MetaSearchResultProvider.aggregateRanked` (between sort and `DomainRanker`), gated by a `personalize` flag and a `personalization` provider.
- [x] 2.2 Apply only for the owner: in-app always; `SearchServer` passes `isOwnerRequest(call)`.
- [x] 2.3 Train from native clicks via `SearchViewModel.onResultOpened` using the displayed order.

## 3. Opt-in UI (A1)

- [x] 3.1 Add a recommended first-run wizard step with the honest blurb + the onboarding-version re-show gate.
- [x] 3.2 Add the Settings toggle plus Export / Import / Reset, sharing the portable JSON.

## 4. Served-page learning (A2)

- [x] 4.1 Add an owner-only `GET /click` route in `SearchServer.kt` with a bounded in-memory `rid -> displayed (url, host)` map; record the skip-above update and 302 to the recorded destination for `rid+pos`; 404 non-owner callers.
- [x] 4.2 Render result links through `/click` only for the owner with personalization on; everyone else gets plain links. Wire `personalizationPreferences` + the enabled flag through `SearchServer` + `SearchMobService`.

## 5. Tests and gate

- [x] 5.1 Pure-helper tests for the model (skip-above, decay, caps, clamp, cold-start, epsilon, JSON round-trip) and a cross-platform desktop-fixture test.
- [x] 5.2 `SearchViewModel` native-training tests and a `ClickRouteTest` (owner records + redirects, disabled renders plain links, forged/stale rid + bad pos fail safe).
- [x] 5.3 `ktlintCheck` + `lintDebug` + `testDebugUnitTest` + `assembleDebug` green; open the PRs.
