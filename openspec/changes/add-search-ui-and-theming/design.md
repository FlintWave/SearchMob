## Context

The always-on service, localhost HTTP server, metasearch engine core, and encrypted storage already
exist from earlier phases. This phase adds the native user-facing surface — a Compose search
experience and a settings screen — that lets the user run searches and customize the app on the
device. It is a cross-cutting change: it introduces new UI architecture (navigation, ViewModels, a
theming layer), a new preference data model over DataStore, and it consumes four existing
capabilities (`add-metasearch-engine-core`, `add-local-search-server`, `add-encrypted-storage`,
`add-foreground-service`). A design doc is warranted to lock the consumption boundaries and the
override/precedence rules before coding.

## Goals / Non-Goals

**Goals:**
- A Compose (Material 3) search UI with explicit loading / empty / error / results states and
  retry / pull-to-refresh.
- A settings screen whose preferences persist across restarts and reboots (DataStore) and apply
  immediately.
- Theming with light/dark/follow-system, a user override that beats the system setting, and
  Material You dynamic color on API 31+ with graceful fallback below.
- Preserve the privacy posture: no analytics, no query leakage beyond the engine fan-out, and the
  store-nothing default honored.

**Non-Goals:**
- No changes to engine adapters, the aggregation algorithm, the privacy proxy, or the localhost
  server contract — this phase only consumes them.
- No changes to the encrypted-storage crypto, the Argon2id key derivation, or the history schema —
  this phase only invokes the existing setup/record/clear entry points.
- No network/LAN mode UI (deferred to `add-network-mode`).
- No new outbound network paths from the UI itself.

## Decisions

- **Consume the core in-process by default; localhost endpoint optional.** The UI talks to the
  in-process aggregator from `add-metasearch-engine-core` for the on-device experience, which avoids
  a redundant localhost round-trip. The localhost endpoint from `add-local-search-server` remains the
  integration point for *other* on-phone apps/browsers; the search-UI abstraction is written against
  a repository interface so either source can back it without UI changes. *Alternative considered:*
  always going through 127.0.0.1 — rejected as unnecessary overhead for the in-app path.
- **MVVM with `StateFlow`-backed `SearchUiState`.** A sealed `SearchUiState`
  (`Idle`/`Loading`/`Empty`/`Error`/`Results`) makes the four required states explicit and directly
  testable, and reuses the `StateFlow` pattern already established by `add-foreground-service`.
- **Theme precedence resolved in one place.** A single `resolveColorScheme()` function takes the
  persisted theme mode + dynamic-color flag + `Build.VERSION.SDK_INT` + the system dark flag and
  returns the scheme. Precedence is: explicit Light/Dark wins over the system setting; Follow-system
  defers to the system; dynamic color, when enabled on API 31+, supplies the palette in the resolved
  light/dark variant; below API 31 it falls back to the built-in scheme. Centralizing this avoids
  scattered, untestable conditionals and makes the "override beats system" requirement unit-testable.
- **Preferences via DataStore (Preferences).** Per the locked storage decision, ordinary preferences
  use Jetpack DataStore; API keys are written through the encrypted-preferences mechanism from
  `add-encrypted-storage` rather than DataStore plaintext. DataStore's Flow API gives
  apply-immediately for free (the theme/engine state recomposes on emission) and reboot persistence
  by virtue of being on-disk. *Alternative considered:* SharedPreferences — rejected
  (`EncryptedSharedPreferences`/Jetpack Security is deprecated per context, and DataStore is the
  project's chosen mechanism).
- **Navigation via `navigation-compose`** between the search and settings destinations; the
  device-setup guidance and zero-knowledge passphrase setup are reached as hand-offs into surfaces
  owned by `add-foreground-service` and `add-encrypted-storage` respectively, not reimplemented here.

## Risks / Trade-offs

- **[Dynamic color absent or unusual below API 31 / on AOSP]** → Resolution funnels through the single
  `resolveColorScheme()` with an explicit `SDK_INT >= 31` guard and a built-in fallback scheme; a unit
  test asserts no crash and correct fallback at API 26.
- **[Privacy regression — an accidental analytics/identifier dependency creeping into the UI module]**
  → No analytics dependency is added; a verification step confirms no telemetry SDK is present and a
  network capture during a real query shows traffic only to the engine fan-out, nothing else.
- **[Store-nothing violated by caching query/results to disk]** → The results repository keeps session
  state in memory only; persistence is gated on the history toggle and routed through
  `add-encrypted-storage`. A test asserts nothing is written to disk when history is off.
- **[Preference change not applying until relaunch]** → State is observed from DataStore `Flow`s so
  recomposition is automatic; a Compose UI test flips a toggle and asserts immediate re-theming /
  re-fan-out without restart.
- **[Battery]** → This phase adds no background work, no wake-locks, and no new network paths; idle
  battery cost is unchanged from the always-on service baseline. Searches reuse the existing
  event-driven, timed-wake-lock fan-out.
- **[Accessibility/contrast regressions across four scheme variants]** → Contrast is verified against
  WCAG AA for body text in both built-in schemes, and the UI respects the system font scale.

## Migration Plan

Additive feature on its own branch `feat/add-search-ui-and-theming`; no data migration. First-run
defaults: Follow-system theme, dynamic color on where supported, all default engines enabled,
search history off. Rollback is reverting the merge — no persisted schema changes to unwind beyond
the new DataStore keys, which are ignored if unused.

## Open Questions

- None blocking. (The in-process-vs-localhost source is settled behind a repository interface; either
  can be selected without spec or UI changes.)
