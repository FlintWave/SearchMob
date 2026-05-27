## 1. Branch & dependencies

- [x] 1.1 Create branch `feat/add-metasearch-engine-core` off `main`
- [x] 1.2 Add OkHttp (`com.squareup.okhttp3:okhttp`) and Jsoup (`org.jsoup:jsoup`) to `gradle/libs.versions.toml` and the `app` build script; reuse existing `kotlinx.coroutines` and `kotlinx.serialization`
- [x] 1.3 Add test-only OkHttp `mockwebserver` dependency
- [x] 1.4 Add the `INTERNET` permission to `AndroidManifest.xml` (normal permission, no runtime prompt) and keep `usesCleartextTraffic=false`

## 2. Engine Adapter SPI & result model

- [x] 2.1 Define the normalized `SearchResult` model (title, url, snippet, source engine id, position) and the `SearchQuery` / `SearchCategory` types in the `engine/` package
- [x] 2.2 Define the `EngineAdapter` SPI (`id`, `displayName`, `categories`, `suspend fun search(query, ctx)`) and a sealed `EngineResult` (`Success`/`Failure`) so adapters are fail-soft by construction
- [x] 2.3 Define `EngineContext` carrying the shared OkHttp client, optional injected API key, and per-engine timeout
- [x] 2.4 Add `HtmlEngineAdapter` / `JsonEngineAdapter` base helpers (Jsoup + JSON parsing) to reduce per-adapter boilerplate

## 3. Privacy proxy & HTTP client

- [x] 3.1 Implement a single shared OkHttp client factory with a no-op `CookieJar` (no cookies stored or sent)
- [x] 3.2 Implement the privacy `Interceptor`: strip/omit `Cookie` and `Referer`, set no user/device/install identifier, and set a `User-Agent` chosen per request from a curated rotation pool
- [ ] 3.3 Implement per-engine politeness: minimum request spacing per engine host and short backoff on HTTP 429/503; ensure adapters cannot bypass the shared client

## 4. Default free engine adapters

- [x] 4.1 Implement the DuckDuckGo adapter (html/lite endpoint, Jsoup parse)
- [ ] 4.2 Implement the Brave (html) adapter (Jsoup parse)
- [ ] 4.3 Implement the Mojeek (html) adapter (Jsoup parse)
- [ ] 4.4 Implement the Marginalia adapter (free API, JSON parse)
- [x] 4.5 Implement the Mwmbl adapter (JSON parse)
- [x] 4.6 Implement the Wikipedia adapter (JSON parse)
- [x] 4.7 Confirm NO Google adapter exists or is registered (enforced by test in 7.6)

## 5. Optional BYO-key adapters & config surface

- [ ] 5.1 Implement the Brave Search API adapter (key-gated, JSON parse), consuming an injected key only
- [ ] 5.2 Implement the Mojeek API adapter (key-gated, JSON parse), consuming an injected key only
- [x] 5.3 Implement `EngineConfig` (per-engine enabled flag + optional injected key) and `EngineRegistry` with defaults: all free engines enabled, BYO adapters inactive until keyed
- [ ] 5.4 Register a keyed API adapter in place of its free counterpart to avoid duplicate upstream load when a key is present

## 6. Aggregation: fan-out, dedup, ranking

- [x] 6.1 Implement bounded parallel fan-out (`supervisorScope` + `Semaphore(maxConcurrent)`), per-engine `withTimeout`, querying only enabled engines, returning partial results
- [x] 6.2 Implement URL normalization (lowercase host, drop leading `www.`, strip trailing slash, remove tracking params like `utm_*`/`fbclid`/`gclid`) and dedup that retains contributing engines + per-engine ranks
- [x] 6.3 Implement deterministic Reciprocal Rank Fusion ranking (fixed `k`, fully specified tie-breakers: score, then normalized URL, then engine id order)
- [x] 6.4 Wire the aggregator to replace the phase-3 stub `SearchResultProvider` behind the unchanged HTTP contract (DI swap only)

## 7. Unit tests

- [x] 7.1 Save per-engine HTML/JSON response fixtures and add a parse test per engine asserting extracted title/url/snippet against the fixture
- [x] 7.2 Test dedup determinism: same URL across engines collapses to one with both engines recorded; distinct URLs preserved
- [x] 7.3 Test ranking determinism: identical fixed inputs ranked twice yield byte-identical ordering; multi-engine agreement ranks above single-engine
- [x] 7.4 Test fail-soft: one engine that throws and one that times out (via MockWebServer delay) still yield merged results from the rest; all-fail yields empty (no error)
- [x] 7.5 Test bounded concurrency: with more engines than the limit, in-flight requests never exceed the cap
- [x] 7.6 Privacy-proxy header assertions (via MockWebServer): no `Cookie`, no `Referer`, no identifier; `User-Agent` is from the pool and rotates across requests; `Set-Cookie` is not replayed; assert no Google adapter is registered
- [x] 7.7 Test politeness: requests to one host are spaced by the minimum interval and back off on 429/503

## 8. Verify, PR & archive

- [x] 8.1 Run `./gradlew lint test` locally (or in CI) and confirm green
- [ ] 8.2 On-device/VM verification: run real queries through the localhost endpoint and confirm merged results return from multiple engines, that one disabled/blocked engine does not fail the query, and that captured upstream requests carry no cookies/referrer/identifier and a rotated UA
- [x] 8.3 Run `openspec validate add-metasearch-engine-core --strict` and fix any issues
- [ ] 8.4 Open PR `feat/add-metasearch-engine-core`, confirm CI green, merge to `main`
- [ ] 8.5 Run `openspec archive add-metasearch-engine-core`
