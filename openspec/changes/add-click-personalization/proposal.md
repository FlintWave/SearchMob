## Why

SearchMob's ranking is static: results come back as engine-consensus order (RRF) plus whatever
raise/lower/pin/block rules the user authored by hand. Nothing improves on its own. We want ranking
that gets better the more the user searches, by quietly learning from the results they actually
click, without breaking the store-nothing, owner-safe privacy posture. This brings Android to parity
with the desktop app, which already shipped the feature, using the same model and a JSON format that
moves between devices.

## What Changes

- Add an on-device learning layer that adjusts ranking from implicit click feedback: a per-domain
  and per-(query-term x domain) Beta-Bernoulli click model, applied as a bounded boost between the
  relevance sort and the existing domain-rule pass.
- Use the position-bias-resistant "click greater-than skip-above" signal.
- Train from two owner-only sources: native in-app clicks, and clicks on the served browser page
  through a new owner-only `/click` redirect. Network/LAN clients never train it and are never
  personalized.
- Persist the model encrypted in the same store as the ranking rules
  (`ranking.personalization`); it survives restarts and is absent (graceful) when the vault is locked.
- Make it opt-in: a recommended step in the first-run wizard (re-shown once after a feature update)
  and a Settings toggle, with Export / Import / Reset sharing a portable `beta_bernoulli_v1` JSON
  with the desktop app.
- Bound the effect for safety: boost clamped to [0.5, 2.0], epsilon-greedy exploration, cold-start
  gates, time decay, and size caps with least-observed eviction.

## Capabilities

### New Capabilities
- `click-personalization`: learn a bounded ranking adjustment from the owner's implicit click
  feedback, stored encrypted, owner-only, portable across devices, opt-in and resettable.

### Modified Capabilities
<!-- None. The existing result-personalization capability (manual rules/lenses/goggles) is unchanged;
     this is a sibling capability that applies before it. -->

## Impact

- New code: `engine/rank/Personalization.kt` (model + apply), `data/prefs/PersonalizationPreferences.kt`
  (encrypted persistence).
- Modified: `engine/MetaSearchResultProvider.kt` (apply pass + owner `personalize` flag),
  `server/SearchServer.kt` (owner-only `/click` route + click-tracking links), `service/SearchMobService.kt`,
  `ui/search/SearchViewModel.kt` + `SearchScreen.kt` (native click training), `ui/onboarding/**`
  (wizard step + onboarding-version gate), `ui/settings/**` (toggle + export/import/reset), and
  `ui/prefs/**` (the opt-in flag).
- No new third-party dependencies, no new outbound network calls, no new LAN-facing surface beyond
  the loopback-gated `/click` route.
