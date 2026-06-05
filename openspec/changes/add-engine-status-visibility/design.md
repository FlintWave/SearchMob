# Design: engine-status-visibility (Android)

## Capturing the outcome

`Aggregator.aggregate` already fans out to every engine with bounded concurrency and tolerates
per-engine failure/timeout (the result is mapped to `null` and dropped). Instead of discarding the
non-successes, pair each engine's adapter with its result and derive a per-engine outcome while
folding: `CONTRIBUTED(n)` for a `Success` with items, `EMPTY` for a `Success` with none, `FAILED` for
a `Failure`, a timeout, or a thrown exception. This is exact (a timeout is distinct from a genuine
empty) and costs nothing extra. `AggregationResult` carries `engineStatus: List<EngineOutcome>`
alongside the results and the consensus correction; the engine label is the adapter's `id`.

## Threading it through

`MetaSearchResultProvider.aggregateRanked` returns the engine status with the results and correction;
`searchWithCorrection` places it on `SearchOutcome.engineStatus`. The in-app `SearchUiState.Results`
carries it to the UI; the served route reads it off the outcome.

## Surfacing

- **App:** an unobtrusive "N of M engines responded" row above the results (`SearchScreen`), tappable
  to expand the per-engine detail. In-app results are always the owner's, so it is always shown. Not
  color-only; the count is in words.
- **Served page:** the same line as a native `<details>` disclosure (keyboard-accessible, no JS),
  rendered for the loopback owner only — gated on `editable` (a wired `RankingPreferences` plus a
  loopback request, the same gate the editing controls use). A LAN visitor never sees it.

## Privacy / owner / parity

- Computed from data already in hand; nothing is stored or transmitted — the strongest reinforcement
  of the store-nothing / no-telemetry posture (even diagnostics stay on-device).
- Owner-only on the served surface via the existing owner gate.
- Mirrors the desktop `engine-status-visibility` change so the two apps report and display the same
  outcome.

## Testing

- `AggregatorTest`: the outcome distinguishes contributed / empty / failed, and a timeout is `FAILED`.
- `EngineStatusRouteTest`: the served line shows for the loopback owner and is absent on a read-only
  (no-owner-prefs) server.

## Non-goals reminder

No auto-retry/disable, no remote error reporting. Purely informational.
