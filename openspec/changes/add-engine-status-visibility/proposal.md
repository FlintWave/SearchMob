## Why

When an engine raises, times out, or returns nothing, the aggregator silently treats it as no
contribution (the fail-soft path). That is correct for resilience but invisible: if a strong engine
(e.g. DuckDuckGo) is rate-limiting or blocking one user's network, that user's results quietly
degrade with no signal, which reads as "the engine is bad" rather than "an engine is down for me".
This makes the per-engine outcome visible to the owner so a degraded search is diagnosable, without
adding any telemetry or weakening the store-nothing posture. Brings Android to parity with the
desktop app.

## What Changes

- Have the aggregator report a per-engine outcome for each search: contributed (with a result count),
  returned empty, or failed (error/timeout). Computed locally and never sent off the device.
- Surface it to the owner: an unobtrusive "N of M engines responded" line in the app results
  (tap to expand per-engine detail) and the same on the served page for the loopback owner only.
- Keep it owner-only and store-nothing: network/LAN clients do not see engine status; nothing is
  persisted or transmitted.

## Capabilities

### New Capabilities
- `engine-status-visibility`: report and display, to the owner only, which engines contributed,
  returned nothing, or failed for a given search, computed locally with no telemetry.

### Modified Capabilities
<!-- None. The aggregator keeps its fail-soft behaviour; this only exposes the per-engine outcome it
already determines while fanning out. -->

## Non-goals

- Retrying, reordering, or disabling engines automatically based on failures.
- Any remote reporting, analytics, or error upload: explicitly never.
- Showing engine status to LAN clients: owner-only.

## Impact

- Modified: `engine/aggregate/Aggregator.kt` (capture the per-engine outcome alongside the merged
  results); `engine/MetaSearchResultProvider.kt` + `server/SearchOutcome` (thread it through);
  `ui/search/SearchScreen.kt` (the in-app line); `server/SearchServer.kt` (owner-only line, gated on
  `editable`); `res/values/strings.xml` (+ authored locales). No new dependencies, no new outbound
  calls, no telemetry.
