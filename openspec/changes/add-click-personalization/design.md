## Context

The ranking pipeline is `Aggregator.aggregate (RRF + dedup)` then `ResultSorter.sort` then
`DomainRanker.apply` (user domain rules / lenses / goggles / slop blocklist), all inside
`MetaSearchResultProvider.aggregateRanked`. It is stateless. We add a learning layer that improves
ranking from the owner's own clicks while honoring the locked decisions: store-nothing-by-default,
encrypted personal state, owner-vs-network isolation, and JSON wire-format parity with the desktop
app (which shipped this feature first). The model and JSON are a direct port of the desktop
`engines/rank/personalize.py`.

## Goals / Non-Goals

**Goals:**
- Ranking that improves the more the owner searches, from implicit click feedback, fully on-device.
- Encrypted, persistent, exportable, resettable; identical math and JSON as the desktop app.
- Bounded and safe: cannot collapse diversity, cannot be poisoned by LAN clients, opt-in.

**Non-Goals:**
- Dwell-time / satisfied-click modeling (deferred).
- A learned multi-feature model (FTRL) in v1; the closed-form counting model is the right
  effort/payoff and is trivially identical across the two clients.
- Any server-side state for LAN clients, any new outbound calls, any new dependency.

## Decisions

**Beta-Bernoulli per-key counting model.** Each key (`dom:<host>` and `qt:<term>:<host>`) holds
`{alpha, beta, lastSeenEpochDays}` with prior `Beta(2, 18)` (mean 0.10). Update is one addition. The
Kotlin port (`Personalizer`) mirrors the desktop functions one-to-one; a cross-platform fixture test
loads a desktop-produced model and reproduces its boosts.

**Click greater-than skip-above signal.** On a click at displayed position p, the clicked host gets
`alpha += 1` and each distinct host above p gets `beta += 1`; hosts below p are ignored. Needs only
the in-memory displayed list plus the click, so no raw click log is stored.

**Rank-based re-sort.** `Personalizer.reorder` computes `weight = 1/(rank+1) * boost(host)` and
stable-sorts; `boost = clip(mu/global_mu, 0.5, 2.0)` with `global_mu` = prior mean (so an unseen key
is neutral). Runs between `ResultSorter.sort` and `DomainRanker.apply`, so explicit rules win.

**Owner gating.** In-app results are always the owner's. `MetaSearchResultProvider.searchWithCorrection`
gains a `personalize` flag; `SearchServer` passes `isOwnerRequest(call)`, so a network visitor gets
engine order. Served-page learning uses a new loopback-only `GET /click` route with a bounded
in-memory `rid -> displayed (url, host)` map; it redirects only to the recorded destination for
`rid+pos` (no open redirect, no forgeable training) and 404s non-owner callers.

**Persistence.** `PersonalizationPreferences` stores the model JSON under key `ranking.personalization`
in the same DEK-encrypted store as the ranking rules; fail-soft to an empty model when locked.

**Opt-in UI.** A first-run wizard step (off by default, recommended, honest copy) and a Settings
toggle, plus Export / Import / Reset. The wizard re-appears once after a feature update via an
`onboarding_version` gate so existing users discover the new option.

## Risks / Trade-offs

- [Filter-bubble collapse] -> bounded boost, epsilon exploration, and time decay keep engine
  consensus primary and let unseen domains keep surfacing.
- [LAN client poisons or reads the model] -> training and personalization are loopback-only; `/click`
  redirects only to server-recorded destinations and 404s non-owner callers.
- [Locked vault] -> personalization is absent without error, matching ranking-rule behavior.
- [Cross-platform drift] -> fixed key construction, 6-dp float rounding, integer epoch-days, and a
  cross-platform fixture test pin parity.
- [Privacy] -> the model encodes interests; stored encrypted, never transmitted, opt-in, resettable;
  the wizard copy states the residual local-device risk honestly.

## Migration Plan

Additive and off by default; no data migration. Existing profiles are unaffected until the owner
opts in. Shipped as two PRs: A1 (model + native learning + opt-in UI + owner-gated apply) then A2
(served-page `/click` learning).

## Open Questions

- None blocking. Dwell-time weighting and an FTRL tier are deferred follow-ups.
