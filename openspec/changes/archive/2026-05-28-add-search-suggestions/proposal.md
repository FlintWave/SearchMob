## Why

Browsers that add SearchMob as a search engine (via the OpenSearch descriptor) can offer an
autocomplete dropdown in the address bar if the engine advertises an OpenSearch Suggestions URL.
SearchMob does not, so users get no suggestions while typing. This change adds a browser-consumable
suggestions endpoint that is private by default: suggestions come from the user's own opt-in,
encrypted, on-device search history (nothing leaves the device), with an explicit, default-off opt-in
to a web autocomplete source for users who want live suggestions and accept the trade-off. This serves
the locked goals of being private (store-nothing default, opt-in only) and customizable.

## What Changes

- Add a loopback (and, under existing network mode, network-bound) endpoint `GET /suggest?q=<term>`
  that returns OpenSearch Suggestions JSON: the two-element array `["<echoed query>", ["s1", "s2", ...]]`
  served as `application/x-suggestions+json`. The JSON is built with kotlinx.serialization so the
  browser-controlled query and every suggestion are correctly escaped. `q` is clamped to
  `MAX_QUERY_LENGTH`; a blank/empty `q` returns `["", []]`.
- Add a `SuggestionsProvider` abstraction with three implementations:
  - `HistorySuggestionsProvider`: distinct past queries from the encrypted history that prefix-match
    (case-insensitive) the term, most-recent first. Backed by a new history DAO query and a
    `HistoryStore.suggest(...)` method (with an in-memory reference implementation). Returns empty when
    history is disabled, locked, or empty; never throws.
  - `UpstreamSuggestionsProvider`: fetches `https://ac.duckduckgo.com/ac/?q=<term>&type=list` through
    the shared privacy-proxy OkHttp client (no cookies, stripped headers, rotated UA) with a SHORT
    timeout (about 2s) and a bounded body read, parses the `["term", ["s1", ...]]` shape, and returns
    the list. Fail-soft: any error or timeout returns empty so typing never hangs.
  - `CompositeSuggestionsProvider`: always queries history; queries upstream ONLY when the opt-in
    preference is on; merges local-first, de-dupes case-insensitively, and caps the total (8).
- Add a boolean preference `upstreamSuggestionsEnabled`, default FALSE, persisted via the existing
  DataStore-backed `PreferencesStore`, exposed as an observable `Flow` plus a suspend setter on
  `PreferencesRepository`. The foreground service observes it (and the history-enabled flag) to gate
  the composite provider and the local source.
- Add a Settings "Suggestions" section with a toggle "Live suggestions from the web" (OFF by default)
  and a subtitle explaining the trade-off. Unlike network mode there is no blocking warning dialog.
- Add a `<Url type="application/x-suggestions+json" template=".../suggest?q={searchTerms}"/>` entry to
  the OpenSearch descriptor alongside the existing html and json Url entries, using the bound port the
  descriptor already interpolates.

## Capabilities

### New Capabilities
- `search-suggestions`: a browser-consumable OpenSearch suggestions endpoint with two sources, the
  always-available local encrypted history and a default-off opt-in upstream web autocomplete, merged
  local-first with a documented privacy trade-off.

### Modified Capabilities
<!-- None: this adds a new route + descriptor entry without changing existing search/route requirements. -->

## Impact

- New code: the `/suggest` route + `suggestionsJson(...)` builder + descriptor entry on the server, the
  `SuggestionsProvider` interface and three implementations, the `suggest(...)` DAO query and
  `HistoryStore` method, the `upstreamSuggestionsEnabled` preference, the service observers, and the
  Settings toggle + strings.
- No new permissions: INTERNET already covers the upstream fetch, which reuses the existing
  privacy-proxy OkHttp client.
- New outbound network only when the user opts in (default off): the upstream suggestion fetch to
  DuckDuckGo, sent through the same privacy proxy as search fan-out. No telemetry.

## Non-goals

- Ranking/scoring suggestions beyond "local-first, most-recent-first history" and upstream order.
- Caching upstream suggestions on device (each keystroke is a fresh, fail-soft fetch when enabled).
- Suggestion sources other than local history and the single opt-in DuckDuckGo autocomplete endpoint.
- A blocking warning dialog for the opt-in (the subtitle communicates the trade-off; this is not a
  network-exposure change).
