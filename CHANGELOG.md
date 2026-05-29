# Changelog

All notable changes to SearchMob are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses Ubuntu-style date
versioning (`YY.MM.VV`).

## [26.05.03] - 2026-05-28

### Added
- **Typo and "similar sounding" correction.** A misspelled query now surfaces a "Did you mean"
  suggestion, taken from the upstream engines' own correction when they offer one, otherwise from a
  fully on-device corrector (Double Metaphone phonetics + Jaro-Winkler/Damerau edit distance over a
  bundled dictionary, enriched by your own search history). No new outbound calls.
- **Result personalization ("filter bubbles").** Raise, lower, pin, or block any site from an inline
  menu on each result; scope searches with named lenses (include/exclude domains and keywords); and
  import a subset of Brave Goggles rule files. All rules are stored encrypted on-device, applied
  locally to results, and can be exported/imported as JSON.
- **Kagi Search API engine** as a bring-your-own-key option, alongside the existing Brave and Mojeek
  API engines. The key is stored encrypted at rest.
- **In-app search history.** View saved queries with their time, delete individual entries or clear
  all, and export/import history as JSON for moving to a new device (Storage Access Framework; no
  storage permission required).

### Changed
- **Encrypted storage is now wired into the live app.** Search history (SQLCipher) and BYO API keys
  (AES-256-GCM) are persisted encrypted at rest via a process-wide store shared by the UI and the
  foreground service. Bring-your-own keys now also take effect on the browser-facing `/search`, not
  only the in-app search.

### Fixed
- The OpenSpec specs for the implemented work were archived and the encrypted-storage wiring landed
  with a fix for a main-thread database access and a now-shared history store made thread-safe.

## [26.05.02] - 2026-05-28

### Added
- Browser-consumable OpenSearch **suggestions** endpoint (`/suggest`), sourced from local encrypted
  history with an explicit, default-off opt-in to upstream (DuckDuckGo) autocomplete via the privacy
  proxy.
- **Opt-out launch-time update check** against GitHub Releases (on by default, routed through the
  privacy proxy, throttled to about once a day), with a Settings toggle.

### Changed
- Toolchain bump (Kotlin/AGP/Gradle, `compileSdk 36`). New launcher icon and onboarding/setup polish.

## [26.05.01] - 2026-05-28

### Added
- First published release: the private, always-on metasearch app (foreground service + loopback HTTP
  server + OpenSearch descriptor), metasearch over free engines with the privacy proxy, opt-in network
  mode (LAN/Tailscale) behind a warning, and security-audit hardening.

[26.05.03]: https://github.com/FlintWave/SearchMob/releases/tag/v26.05.03
[26.05.02]: https://github.com/FlintWave/SearchMob/releases/tag/v26.05.02
[26.05.01]: https://github.com/FlintWave/SearchMob/releases/tag/v26.05.01
