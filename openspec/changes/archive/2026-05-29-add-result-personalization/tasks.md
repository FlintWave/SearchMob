## 1. Branch & model

- [ ] 1.1 Create branch `feat/add-result-personalization` off `main`
- [ ] 1.2 Add the rules model: `RankRule` (BLOCK/LOWER/RAISE/PIN/NORMAL), `Lens`, Goggle rule, `RankingRules` (serializable)
- [ ] 1.3 Confirm no new Android permission is required

## 2. Persistence

- [ ] 2.1 Add `RankingPreferences` over the encrypted `PreferencesStore` (JSON-blob keys for domains, lenses, active lens, goggles)
- [ ] 2.2 Export/import all rules as JSON
- [ ] 2.3 Goggles subset parser (`site=` with simple wildcards, `$boost`, `$downrank`, `$discard`)

## 3. Ranking

- [ ] 3.1 Add generic `DomainRanker.apply<T>(items, rules, hostOf, textOf)`: drop block/discard/lens-excluded, pin to top, raise/lower buckets, lens include filter; deterministic, order-preserving
- [ ] 3.2 Apply it in `MetaSearchResultProvider` after `rank()`, reading rules via an injected suspend provider; shared process-wide so in-app and `/search` agree
- [ ] 3.3 Unit tests: block/lower/raise/pin, lens include/exclude + keywords, goggle boost/downrank/discard

## 4. UI

- [ ] 4.1 Inline per-result menu (Block/Lower/Raise/Pin/Normal) on the in-app result card; persists the rule and re-ranks the visible results immediately
- [ ] 4.2 Settings "Result ranking" section: manage domain rules, create/select Lenses, import Goggles (file/paste), export/import JSON

## 5. Verify & ship

- [ ] 5.1 Run `./gradlew ktlintCheck lint test assembleDebug`; confirm green
- [ ] 5.2 On the `searchmob` emulator: block/lower/raise/pin a domain and confirm reordering in both the in-app and browser (`/search`) paths; create + select a lens and confirm scoping; import a small `.goggle` and confirm effect
- [ ] 5.3 Capture network traffic to confirm no new outbound call (ranking is local; rules never leave the device)
- [ ] 5.4 Open PR against `main`, confirm CI green, merge, then `openspec archive add-result-personalization`
