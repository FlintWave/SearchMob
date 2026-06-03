# Design: result-paging (Android)

## Pool vs reveal

The Android aggregator already merges and ranks the full pool (it never sliced to 10), so the pool
half of "pool vs reveal" is a no-op here. This change adds the REVEAL half: show a window first and
grow it as the user scrolls. No new request until the pool is exhausted; nothing stored.

## Surfaces

- Compose list (`ui/search/SearchScreen.kt`): a `rememberLazyListState()` plus a `revealed` count
  (`mutableIntStateOf`, reset when the results change). The list composes `results.take(revealed)`; a
  `snapshotFlow` over the layout info grows `revealed` by a step when the last visible item nears the
  end of the composed window. Pure UI state, no ViewModel or network change.
- Served page (`server/SearchServer.kt`): renders the whole pool but tags results past the first
  window with `is-collapsed` (CSS `display:none`), emits a bottom `reveal-sentinel`, and an inline
  `IntersectionObserver` script that unhides the next batch as the sentinel scrolls into view. Mirrors
  the desktop served page and reuses the existing `script { unsafe { } }` inline-script pattern (no
  CSP on the server; only `X-Content-Type-Options: nosniff`). Degrades to all-visible without JS.

## Deeper fetch (deferred)

On-demand deeper fetch past the pool is deferred (see tasks): most engines return a single page and
the full pool already removes the truncation. The capability spec keeps the requirement as the target;
implementation is a documented follow-up, not silently dropped.

## Privacy / owner / parity

- Nothing is cached or persisted across searches; the reveal is client-side only.
- LAN clients get the same incremental reveal but no stored session; owner-only controls unchanged.
- The `/click` render cache still maps positions over the full rendered order (we render the whole
  pool and only visually hide the tail), so click training is unaffected.
- Parity: reveal window sizes and the served-page reveal mechanism match the desktop app.
