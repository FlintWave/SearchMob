## Why

The results list felt truncated: a relevant result just past the first screenful had no way to be
reached. The Android aggregator already merges and ranks the full pool (it never capped at 10), but
the Compose list and the served page rendered everything at once with no incremental reveal. This
adds infinite scroll so the pool is revealed a window at a time, matching the desktop app and keeping
the store-nothing, on-device posture.

## What Changes

- Reveal results incrementally as the user scrolls toward the end, in the Compose results list and on
  the served page, without issuing a new search while pooled results remain.
- The Compose `LazyColumn` composes only a window of the already-ranked pool and grows it as the user
  nears the end.
- The served page renders the whole pool but collapses results past the first window, revealing them
  in batches via a bottom sentinel and a small inline `IntersectionObserver` script (the same pattern
  as the existing theme script). No new request, nothing stored, and with JS off every result shows.

## Capabilities

### New Capabilities
- `result-paging`: a merged result pool revealed incrementally via infinite scroll, ranked by the
  normal pipeline, with on-demand deeper fetches where engines support pagination (deferred; see
  tasks).

### Modified Capabilities
<!-- None in contract; the aggregator already returns the full ranked pool. -->

## Non-goals

- Deep cross-engine pagination beyond what each engine natively supports.
- Persisting or caching result pages across searches (store-nothing).
- Server-side cursor state for LAN clients: the reveal is client-side; no per-client session.

## Impact

- Modified: `ui/search/SearchScreen.kt` (Compose `LazyColumn` infinite-scroll reveal);
  `server/SearchServer.kt` (served-page collapse + reveal script). No aggregator change (it already
  returns the full pool). No new dependencies, no new outbound calls, no stored data. Owner/LAN
  gating and the `/click` render cache are unchanged (positions map to the full rendered order).
