## Why

SearchMob now works, but a new user lands on a bare home screen with no guidance on the two things
that make it useful: granting the permissions the always-on service needs, and wiring SearchMob in as
their browser's search engine. This change makes setup obvious and one-tap-easy (serving the
**customizable** and **always-on** goals), and adds a home-screen widget for fast access (a common
expectation for a search app).

## What Changes

- Add a **first-run wizard**: a short, skippable, click-through flow shown once on first launch that
  walks through notifications permission, the battery-optimization exemption, and setting SearchMob as
  the browser's default search engine; persists a "completed" flag so it doesn't reappear.
- Add a **browser-setup guide**: an in-app screen (reachable from Settings and the wizard) with the
  exact localhost URLs the user needs (the page to visit and the `…/search?q=%s` template for manual
  add) each with **one-tap copy-to-clipboard**, plus concise per-browser instructions (Chrome,
  Firefox, generic/manual). URLs use the live bound port from `LocalServerState`.
- Add a **home-screen widget** (Jetpack Glance): a tappable search box/bar that deep-links into the
  app's Search screen, plus a compact indicator that the service is running.

## Capabilities

### New Capabilities
- `first-run-wizard`: a one-time, skippable multi-page onboarding covering permissions and
  default-search setup, with persisted completion state.
- `browser-setup-guide`: in-app, copy-to-clipboard instructions for adding SearchMob as a browser
  search engine, using the live loopback URL/template.
- `home-screen-widget`: an Android app widget that launches an in-app search from the home screen.

### Modified Capabilities
<!-- None: this builds on add-search-ui-and-theming via navigation; no existing requirement changes. -->

## Impact

- New code under `ui/onboarding/`, `ui/setup/` (guide), and a `widget/` package (Glance receiver +
  Compose-Glance UI + a deep-link target in the Search flow).
- New dependency: `androidx.glance:glance-appwidget`. A `<receiver>` + widget metadata in the manifest.
- Deep link: `MainActivity`/nav handles a `searchmob://search` (or intent extra) to open Search,
  optionally with a prefilled query.
- Reuses existing pieces: `LocalServerState` (port), `BatteryOptimization`/`OemGuidance`,
  `PreferencesStore` (wizard-completed flag + persisted prefs), the OpenSearch endpoint already served.
- No new outbound network, no telemetry, no new dangerous permissions.

## Non-goals

- Auto-setting the browser's default search engine programmatically (Android browsers don't allow it;
  we guide + copy instead).
- A full home-screen launcher experience or multiple widget sizes/skins (one clean widget now;
  resizing/theming can come later).
- Network/LAN exposure of the server (still loopback-only; that's `add-network-mode`).
