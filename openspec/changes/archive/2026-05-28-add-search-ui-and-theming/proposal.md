## Why

The earlier phases stood up the always-on service, the localhost HTTP server, the metasearch
engine core, and encrypted storage, but the user has no native, polished way to actually *search*
or to *customize* the app on the device. This phase delivers the user-facing surface that makes
SearchMob usable: a Compose search experience and a settings screen. It directly serves the
**customizable** and **private** goals: the user picks engines, brings their own keys, controls
theme, and decides whether anything is ever stored (store-nothing by default), all without any
analytics or query leakage beyond the engine fan-out.

## What Changes

- Add a **Compose (Material 3) search UI**: a query input with submit; a results screen that renders
  aggregated results (title, snippet, source-engine attribution) with tap-to-open; explicit
  **loading / empty / error** states; and pull-to-refresh or a retry affordance on error.
- Wire the UI to the metasearch core, consumed **directly** via the in-process aggregator
  (`add-metasearch-engine-core`) or via the **localhost HTTP endpoint** (`add-local-search-server`),
  with no new outbound network paths beyond the existing engine fan-out.
- Add a **Settings/preferences screen** covering: theme mode (Light / Dark / Follow system),
  a Material You dynamic-color toggle, per-engine enable/disable toggles, bring-your-own API key
  entry for Brave and Mojeek, a search-history on/off switch with a **clear history** action and a
  zero-knowledge passphrase setup entry point (handed off to `add-encrypted-storage`), and an entry
  point to the battery / OEM device-setup guidance (owned by `add-foreground-service`).
- **Persist all preferences via Jetpack DataStore** so they survive app restarts *and* device
  reboots, and **apply them immediately** on change (no relaunch required). API keys are persisted
  through the encrypted-preferences mechanism from `add-encrypted-storage`.
- Add **theming**: full light/dark support and Material You dynamic color on API 31+, with the
  explicit user theme/dynamic-color override taking **precedence over the system setting**, and
  adequate contrast/accessibility in every variant.
- Enforce **privacy in the UI**: no analytics or telemetry, no query leakage to third parties beyond
  the engine fan-out, and honor the store-nothing default (never write history unless the user has
  enabled it).

## Capabilities

### New Capabilities
- `search-ui`: the Compose search surface, covering query input + submit, aggregated results rendering
  (title, snippet, source-engine attribution, tap-to-open), the loading / empty / error states,
  retry / pull-to-refresh, and the privacy guarantees of the results surface.
- `settings-and-preferences`: the settings screen and its reboot-persistent, apply-immediately
  preference store: theme mode, dynamic-color toggle, per-engine toggles, BYO API-key entry,
  search-history on/off + clear-history + zero-knowledge passphrase setup hand-off, and the
  device-setup-guidance entry point.
- `theming`: light / dark / follow-system theming with the user override taking precedence over the
  system setting, Material You dynamic color on API 31+ (graceful fallback below), and
  contrast/accessibility requirements.

### Modified Capabilities
<!-- None. This change only introduces new capabilities. -->

## Impact

- **New code (in the `ui/` package established by the scaffold):** Compose screens for search and
  settings, their `ViewModel`s, a `SearchUiState` model (loading/empty/error/results), a
  preferences repository over DataStore, an app `Theme`/`ColorScheme` layer wiring the override +
  dynamic color, and navigation between the search and settings destinations.
- **Consumes existing capabilities:** the metasearch aggregator (`add-metasearch-engine-core`) or
  the localhost server (`add-local-search-server`) for results; the encrypted-preferences / history
  store and zero-knowledge passphrase setup (`add-encrypted-storage`); the battery/OEM guidance
  surface (`add-foreground-service`).
- **Dependencies introduced:** Compose Material 3 (incl. `androidx.compose.material3` dynamic-color
  APIs), `androidx.lifecycle` ViewModel/Compose integration, `androidx.navigation:navigation-compose`,
  `androidx.datastore:datastore-preferences`, and Compose UI test artifacts
  (`androidx.compose.ui:ui-test-junit4`). No new runtime permissions. No analytics SDKs.
- **No new network paths:** the UI introduces no outbound calls of its own; all search traffic flows
  through the existing privacy-proxied engine fan-out.
