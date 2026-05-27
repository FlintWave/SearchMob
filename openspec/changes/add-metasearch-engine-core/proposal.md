## Why

SearchMob is a metasearch service, but after phase 3 the localhost endpoint only serves a stub
result provider — it does not actually search anything. This change is the search brain: it fans
out a query to multiple free engines, merges and ranks the results, and returns them through the
existing HTTP contract. It directly serves the **private** goal — every upstream request is
proxied so engines see no cookies, no referrer, no user identity, and a rotated User-Agent — and
the **customizable** goal — users can enable/disable engines and supply their own Brave/Mojeek API
keys for higher reliability, exactly like a self-hosted SearXNG.

## What Changes

- Add an **Engine Adapter SPI**: a Kotlin interface each engine implements (stable `id`, display
  name, supported categories/capabilities, and a `suspend` function taking a query + params and
  returning parsed, normalized results) with a **per-engine timeout** and **fail-soft** error
  handling (an adapter never throws into the aggregator; it returns an empty/error result).
- Implement the **default FREE engine adapters** (no key required): DuckDuckGo (html/lite
  endpoint), Brave (html), Mojeek (html), Marginalia (free API), Mwmbl, and Wikipedia. HTML
  engines parse via **Jsoup**; API/JSON engines parse JSON. All HTTP goes through **OkHttp**.
- Implement **optional bring-your-own-API-key adapters**: Brave Search API and Mojeek API, active
  only when the user has supplied a key. This change only *consumes* an injected key; key storage
  is the later `add-encrypted-storage` change.
- Add **parallel fan-out** in an aggregator: query all enabled engines concurrently with
  coroutines under **bounded concurrency**, enforce the per-engine timeout, and return **partial
  results** — one slow or broken engine never blocks or fails the overall query.
- Add a normalized **result model** (title, url, snippet, source engine, position) plus
  **aggregation**: deduplicate by normalized URL and merge/rank across engines with a
  **deterministic** algorithm (reciprocal rank fusion, boosting results returned by multiple
  engines), so ranking is unit-testable.
- Add the **privacy proxy** behavior on every outbound request: send **no cookies**, **no
  referrer**, **no user identifier**, and a **rotated/randomized User-Agent** per request, plus
  per-engine politeness/rate-limit awareness so a single mobile IP does not trip bot-detection.
- Expose a **per-engine enable/disable** configuration surface (the toggle UI is the later
  `add-search-ui-and-theming` change; here we only define and consume the config).
- **Replace the stub `SearchResultProvider`** from `add-local-search-server` with the real
  aggregator behind the same HTTP contract — no route changes.

## Non-goals

- **Never scrape Google.** Google is explicitly excluded as an engine: its JS wall, litigation
  history, and the risk of CAPTCHA-walling the user's own residential mobile IP make it a
  hard constraint, not a future engine. This is a permanent non-goal of the engine set.
- **No key storage / encrypted preferences** — adapters consume an injected key only; encrypted
  storage of keys and engine config is `add-encrypted-storage`.
- **No engine-toggle or API-key entry UI** — this change exposes the config surface; the Compose
  settings UI is `add-search-ui-and-theming`.
- **No new HTTP routes or server lifecycle changes** — the aggregator slots in behind the existing
  `SearchResultProvider` contract from `add-local-search-server`.
- **No network/LAN exposure** — all engine traffic is outbound from the device only; inbound
  listening stays loopback-only (deferred `add-network-mode`).
- **No persisted query history, no telemetry, no caching to disk** — results are in-memory per
  request.

## Capabilities

### New Capabilities
- `engine-adapters`: the Engine Adapter SPI, the default free engine set (DuckDuckGo, Brave,
  Mojeek, Marginalia, Mwmbl, Wikipedia), the optional BYO-key adapters (Brave API, Mojeek API),
  the never-Google constraint, per-engine timeout, fail-soft error handling, and the per-engine
  enable/disable config surface.
- `result-aggregation`: parallel coroutine fan-out with bounded concurrency, the normalized result
  model, deduplication by normalized URL, deterministic merge/rank across engines, and partial
  (fail-soft) result return when engines error or time out.
- `search-privacy-proxy`: outbound requests carry no cookies, no referrer, no user identifier, and
  a rotated User-Agent per request, with per-engine politeness/rate-limit awareness to avoid
  bot-detection from a single mobile IP.

### Modified Capabilities
<!-- None at the spec level. The aggregator implements the existing `SearchResultProvider`
     contract defined by `add-local-search-server`'s `local-http-server` capability; the HTTP
     route behavior is unchanged, only the provider implementation changes. -->

## Impact

- New code (in the `engine/` package established in the scaffold): the `EngineAdapter` SPI and a
  shared base, six default free engine adapters, two optional BYO-key adapters, an OkHttp client
  factory with the privacy-proxy interceptor (header stripping + UA rotation + politeness), the
  normalized `SearchResult` model, the fan-out + dedup + RRF ranking aggregator, and an engine
  registry/config holding per-engine enabled state and injected keys.
- Modified code: `add-local-search-server`'s stub `SearchResultProvider` is wired to the new
  aggregator (implementation swap behind the unchanged interface).
- Dependencies introduced: **OkHttp** (`com.squareup.okhttp3:okhttp`) for engine HTTP and **Jsoup**
  (`org.jsoup:jsoup`) for HTML parsing; `kotlinx.coroutines` for fan-out (already present via
  Ktor); a JSON parser for API engines (reuse `kotlinx.serialization` from phase 3). Test-only:
  OkHttp `mockwebserver` and saved HTML/JSON fixtures per engine.
- Engines introduced: DuckDuckGo, Brave, Mojeek, Marginalia, Mwmbl, Wikipedia (free); Brave API
  and Mojeek API (optional, key-gated). Google is excluded by design.
- Permissions: requires `INTERNET` (first network-using phase) for outbound engine requests; no
  dangerous permissions, no persisted data, no telemetry, no device identifiers.
