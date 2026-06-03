# Tasks: result-paging (Android)

## Implementation

- [x] Compose: `ui/search/SearchScreen.kt` `ResultsList` holds the full ranked pool but composes a
      window (`results.take(revealed)`), growing `revealed` via a `LazyListState` + `snapshotFlow`
      when the last visible item nears the end. Resets when the results change.
- [x] Served page: `server/SearchServer.kt` renders the whole pool, collapses results past the first
      window, and reveals them in batches via a sentinel + inline `IntersectionObserver` script. No
      new request, store-nothing, JS-off shows all. `/click` cache and owner/LAN gating unchanged.
- [x] No aggregator change needed: it already returns the full ranked pool (unlike desktop, which
      had to raise its cap).

## Deferred deeper-fetch (boundary, not dropped)

- [ ] On-demand deeper fetch past the pool when an engine supports a page/offset param. Deferred: most
      engines return a single page and the full pool already removes truncation. The spec keeps the
      requirement as the target; this is a logged follow-up.

## Verify + ship

- [x] ktlint + lintDebug + testDebugUnitTest + assembleDebug green; `PagingRevealTest` covers the
      served collapse + reveal markup and the small-pool no-machinery case.
- [x] Emulator: served page returned 46 results (10 visible + 36 collapsed) with the sentinel +
      reveal script on a real query.
- [ ] Ship as part of the RC feature pile (paging + engine-status + media-intent + the two new tasks);
      one `-rc` tag for the pile, not a per-feature GA.
