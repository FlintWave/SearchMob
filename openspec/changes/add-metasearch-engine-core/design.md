## Context

After `add-local-search-server` (phase 3), SearchMob has a loopback Ktor server exposing
`/search`, `/api/search`, `/healthz`, and `/opensearch.xml`, all backed by a stub
`SearchResultProvider`. This change supplies the real provider: a metasearch core that fans out a
query to several free upstream engines, normalizes and merges their results, and proxies every
outbound request so engines learn nothing about the user. It is the first phase that touches the
network, so it is also where the privacy-proxy and battery disciplines stated in the project
context first become executable code.

The engine set, the never-Google constraint, the free-by-default + optional-BYO-key model, and the
privacy-proxy header policy are **locked decisions** from the project context — this design follows
them and does not relitigate them. What this design decides is the internal architecture: the
adapter SPI shape, the fan-out/aggregation algorithm, and how the proxy and politeness are enforced.

## Goals / Non-Goals

**Goals:**
- A small, stable `EngineAdapter` SPI that each engine implements, so new engines are additive and
  individually testable against saved fixtures.
- Concurrent, bounded fan-out that returns partial results and is robust to one engine being slow,
  broken, rate-limited, or returning malformed HTML — fail-soft, never fail-whole.
- A deterministic, unit-testable dedup + merge + rank pipeline (reciprocal rank fusion).
- A single enforced privacy-proxy chokepoint: no cookies, no referrer, no identifier, rotated UA,
  per-engine politeness — applied to every outbound request with no per-adapter opt-out.
- A config surface for per-engine enable/disable and injected BYO keys that the later UI and
  storage phases plug into.

**Non-Goals:**
- Google scraping (permanent constraint, not a deferral).
- Key persistence / encrypted prefs (`add-encrypted-storage`) and engine-toggle UI
  (`add-search-ui-and-theming`) — only the consuming config surface is in scope here.
- New HTTP routes or server-lifecycle changes — the aggregator implements the existing provider.
- On-disk result caching or query persistence — results are in-memory per request.
- LAN/network exposure — only outbound engine requests; inbound stays loopback.

## Decisions

- **`EngineAdapter` SPI shape.** Each adapter exposes `id: String` (stable, used in config and
  result attribution), `displayName: String`, `categories: Set<SearchCategory>` (capabilities), and
  `suspend fun search(query: SearchQuery, ctx: EngineContext): EngineResult`. `EngineResult` is a
  sealed type — `Success(results)` or `Failure(reason)` — so the adapter contract is **fail-soft by
  construction**: an adapter returns `Failure` rather than throwing, and the aggregator treats both
  uncaught exceptions and `Failure` as "this engine contributed nothing." `EngineContext` carries
  the shared OkHttp client (already wrapped with the privacy interceptor), an optional injected API
  key, and the per-engine timeout. *Alternative (adapters own their own OkHttp clients) rejected:*
  it would let an adapter bypass the privacy proxy; centralizing the client makes the proxy
  non-optional.
- **HTML vs API engines share the SPI.** DuckDuckGo/Brave/Mojeek parse HTML with Jsoup against the
  html/lite endpoints; Marginalia/Mwmbl/Wikipedia and the BYO Brave/Mojeek API adapters parse JSON.
  A small `HtmlEngineAdapter` / `JsonEngineAdapter` base reduces boilerplate but the aggregator only
  knows the SPI. *Alternative (separate registries per type) rejected* as needless.
- **BYO-key adapters are key-gated and override the free counterpart.** The Brave/Mojeek API
  adapters register only when a key is injected; when present, the corresponding free HTML adapter
  for that engine is skipped to avoid double-counting and extra upstream load. The injected key is
  passed via `EngineContext` (storage is phase 5). *Alternative (run both free + API for the same
  engine) rejected:* duplicates and wasted requests.
- **Bounded parallel fan-out via coroutines + a semaphore.** The aggregator launches one coroutine
  per enabled engine inside a `supervisorScope` (a child failure does not cancel siblings), guarded
  by a `Semaphore(maxConcurrent)` so a long engine list does not open an unbounded socket burst from
  one mobile IP. Each adapter call is wrapped in `withTimeout(perEngineTimeout)`; a timeout is
  caught and recorded as a non-fatal engine failure. The overall call returns once all engines
  finish or are timed out — partial results are returned. *Alternative (`async`/`awaitAll`) rejected:*
  one failure cancels the whole scope unless every call is individually guarded; `supervisorScope`
  plus per-call try/catch is the explicit fail-soft pattern.
- **Deterministic ranking via Reciprocal Rank Fusion (RRF).** Each result gets a score
  `sum over engines of 1 / (k + rank_in_that_engine)` with a fixed constant `k` (e.g. 60). Results
  endorsed by multiple engines naturally rise; ties broken deterministically (by score, then
  normalized URL, then engine id order) so output is stable and unit-testable. *Alternative (simple
  interleave / first-seen) rejected:* it does not reward cross-engine agreement and is order-
  sensitive.
- **Dedup by normalized URL before ranking.** Normalization lowercases the host, strips the
  scheme's default-port and trailing slash, drops a leading `www.`, and removes known tracking query
  params (e.g. `utm_*`, `fbclid`). Duplicates merge into one result that accumulates contributing
  engines and per-engine ranks (feeding RRF). The kept title/snippet is from the highest-ranked
  contributor. *Alternative (dedup by title) rejected* as too lossy/false-positive-prone.
- **Privacy proxy as a single OkHttp `Interceptor`.** One application interceptor on the shared
  client strips/omits `Cookie` and `Referer`, sets `User-Agent` to a value picked per request from a
  curated pool of common desktop-browser UAs, and uses a no-op `CookieJar` so no cookies are ever
  stored or sent. Because every adapter shares this client, the policy cannot be bypassed.
  *Alternative (each adapter sets headers) rejected:* easy to forget, impossible to assert centrally.
- **Politeness / rate-limit awareness.** A per-engine-host limiter enforces a minimum spacing
  between requests and applies short backoff when an engine returns 429/503; the bounded semaphore
  already caps simultaneous outbound connections. This reduces the chance a single residential mobile
  IP trips upstream bot-detection. *Alternative (no throttling) rejected:* risks CAPTCHA-walling the
  user's own IP.
- **Config surface.** An `EngineRegistry` holds the set of adapters and an `EngineConfig`
  (per-engine `enabled` flag + optional injected key). This change ships sane defaults (all free
  engines enabled, BYO adapters off until keyed) read from injected config; the UI/storage phases
  later supply persisted values. The aggregator queries only enabled engines.

## Risks / Trade-offs

- [Upstream HTML changes break a scraper] → adapters are fail-soft, so a broken parser degrades to
  "engine contributed nothing" rather than failing the query; per-engine parse tests against saved
  fixtures catch regressions, and engines are independently fixable.
- [A single mobile IP gets CAPTCHA-walled / rate-limited by an engine] → bounded concurrency +
  per-host politeness spacing + UA rotation + backoff on 429/503; if an engine still blocks, it
  fails soft and the others still return. Google is excluded entirely for exactly this reason.
- [Privacy regression — an adapter leaks cookies/referrer/identity] → the proxy is a single shared
  interceptor with a no-op cookie jar that adapters cannot bypass; privacy-proxy header assertions
  (via OkHttp MockWebServer) are part of the test suite and gate merge.
- [Battery: network requests on a battery-sensitive always-on app] → requests happen only in
  response to an actual query; the server already brokers a short timed wake-lock per request
  (phase 3) and releases it in `finally`. The core adds no idle work, no background polling, and no
  wake-lock of its own — it runs entirely within the request's existing wake-lock window. Bounded
  concurrency also caps the radio-on burst.
- [Android-version restrictions] → this phase first declares the `INTERNET` permission (normal, not
  dangerous; no runtime prompt). minSdk 26 / targetSdk 35 impose no cleartext concerns since all
  engine endpoints are HTTPS; the manifest keeps `usesCleartextTraffic=false`. No `specialUse`/FGS
  contract change — the core is invoked by the existing service-owned server.
- [Non-determinism in ranking makes tests flaky] → RRF with a fixed `k` and fully specified
  tie-breakers yields byte-stable ordering for a fixed set of engine inputs; ranking is tested with
  static inputs independent of the network.

## Migration Plan

- Implement the SPI, adapters, proxy, and aggregator behind the existing `SearchResultProvider`
  interface, then swap the phase-3 stub for the real aggregator via dependency injection — a
  one-line wiring change, no route or contract change.
- Rollback is trivial: re-point the provider binding back to the stub. Because nothing is persisted
  and no schema exists, there is no data migration and no rollback data risk.
- Ship behind the existing config surface with all free engines enabled by default; BYO-key adapters
  stay inert until phase 5 supplies keys.

## Open Questions

- Final RRF constant `k` and the exact tracking-param strip list — pick sensible defaults now
  (`k=60`, `utm_*`/`fbclid`/`gclid`), revisit if real queries show poor merging during VM
  verification.
- Exact per-engine politeness spacing values — tune against real engine behavior during on-device
  verification; defaults err on the conservative (slower) side.
- The size/composition of the rotated User-Agent pool — start with a small curated set of current
  desktop browser UAs; not blocking.
