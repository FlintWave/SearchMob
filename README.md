# SearchMob

[![CI](https://github.com/FlintWave/SearchMob/actions/workflows/ci.yml/badge.svg)](https://github.com/FlintWave/SearchMob/actions/workflows/ci.yml)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](LICENSE)

**A private, battery-friendly, always-on metasearch app for Android.**

SearchMob gives you the experience of running your own [SearXNG](https://searxng.org) instance, but
as a native Android app that runs entirely on your phone, with **no SearchMob servers and no
telemetry of any kind**. It aggregates results from several search engines, acts as a privacy proxy
so those engines see no cookies/identifier from you, and keeps anything it stores encrypted at rest
(with an optional zero-knowledge mode).

> **Status:** released and verified on Android 15. Grab the current version from the
> [Releases](https://github.com/FlintWave/SearchMob/releases/latest) page. Ongoing work and design
> live in [`ROADMAP.md`](ROADMAP.md) and [`openspec/`](openspec/).

> **On a computer too?** There is a companion desktop app,
> [**SearchMob Desktop**](https://github.com/FlintWave/SearchMob-Desktop), for Windows, macOS, and
> Linux: the same engines, the same privacy proxy, and the same store-nothing defaults, plus
> desktop-only extras (a local-AI answer box and an MCP server for AI agents). Run **both** to keep
> every search on every device private, with no third-party search account anywhere.

## What it does

- **Private metasearch on your device.** Queries are fanned out in parallel to **DuckDuckGo, Mojeek,
  Marginalia, Mwmbl, and Wikipedia** (plus optional bring-your-own **Brave**, **Mojeek**, and **Kagi**
  API keys), then de-duplicated and re-ranked. The app proxies the requests: upstream engines see no
  cookies, no referrer, no user/device identifier, and a rotated User-Agent. It **never scrapes Google**.
- **Search operators.** The query syntax you already know: `"exact phrase"`, `-term`, `site:` /
  `-site:`, `intitle:`, `inurl:`, `filetype:` (or `ext:`), `before:` / `after:` dates, and `OR`.
  Operators the upstream engines understand are forwarded to them; all structural filters are also
  enforced on-device over the merged results, so they work consistently across every engine. The
  served home page has a collapsible cheat sheet.
- **Typo and "similar sounding" tolerance.** A misspelled query surfaces a "Did you mean" suggestion,
  from the engines' own correction when offered, otherwise from a fully on-device corrector (phonetic +
  edit-distance over a bundled dictionary, enriched by your own history). No new outbound calls.
- **Your ranking, your bubble.** Raise, lower, pin, or block any site; scope searches with named
  lenses (the ready-to-use **sample scopes are installed by default** and selectable before you even
  search); or import a subset of [Brave Goggles](https://search.brave.com/help/goggles). All rules
  stay on-device (encrypted), are applied locally to the results, and are exportable as JSON.
- **Search verticals.** Category tabs for **Web**, **News**, **Forums**, and **Academic**: each is a
  scoped search over the same engines (a `site:` filter, no new third-party API) with a sensible
  default sort. In the app and on the served page (`?vertical=`).
- **Settings in your browser.** The served results page has its own owner-only (loopback) Settings
  page mirroring the app: default sort, the AI-slop filter, the Wikipedia summary, suggestions, full
  domain-rule and scope management, Goggles import, and search-history view/clear.
- **No data ever leaves for us.** No analytics, no crash/diagnostic reporting, no accounts, no ad IDs,
  no device identifiers. The only outbound traffic is the searches you run, plus an optional once-a-day
  update check to GitHub (on by default, routed through the privacy proxy) that you can turn off in
  Settings.
- **Always-on, battery-friendly.** A native `specialUse` foreground service runs a loopback-only HTTP
  search server, restarts on boot, and is event-driven. It holds **no wake-lock while idle**, so the
  CPU sleeps and idle drain is near-zero.
- **Use it from your browser.** The service exposes an OpenSearch descriptor, so you can set SearchMob
  as your browser's default search engine (Firefox/Fennec, Chromium browsers, or any browser via a
  manual template). The in-app guide gives copy-paste URLs and per-browser steps.
- **Customizable.** In-app search UI plus light/dark/Material-You theming, per-engine toggles, and a
  home-screen search widget. Preferences persist across reboots.
- **Encrypted at rest, store-nothing by default.** Search history is **off** by default; when enabled
  it's local-only, encrypted (SQLCipher + Android Keystore), user-purgeable, with an optional
  zero-knowledge passphrase. You can **view your history in the app, delete individual entries, and
  export/import it as JSON** to move it to a new device. BYO API keys are likewise encrypted at rest.
  (See the in-app **About & privacy** screen for the full methodology and caveats.)

## Install

SearchMob is distributed via **GitHub Releases** (and F-Droid is planned), not Google Play.

1. Download the APK from the [Releases](https://github.com/FlintWave/SearchMob/releases) page.
2. Install it (you may need to allow installing from your browser/files app).
3. Follow the first-run wizard to grant the notification + battery-optimization permissions and to set
   SearchMob as your browser's search engine.

On your computer, install [SearchMob Desktop](https://github.com/FlintWave/SearchMob-Desktop) too, so
your phone and desktop both search privately.

## Architecture (summary)

| Aspect | Choice |
|---|---|
| Form factor | Native Android app, Kotlin + Jetpack Compose (Material 3) |
| Always-on | `specialUse` foreground service, started on boot, no idle wake-lock |
| Search | Metasearch over free engines + optional bring-your-own API keys |
| Local interface | Loopback-only HTTP endpoint (Ktor) + OpenSearch descriptor for browsers |
| Storage | Store-nothing by default; opt-in encrypted history; optional zero-knowledge |
| Distribution | GitHub Releases + F-Droid (not Google Play) |

Full rationale and the locked decisions live in [`openspec/config.yaml`](openspec/config.yaml) and
[`ROADMAP.md`](ROADMAP.md).

## Building

Requires **JDK 17** and the **Android SDK** (API 35).

```bash
./gradlew assembleDebug                 # build a debug APK
./gradlew ktlintCheck lint test         # static analysis + unit tests
./gradlew connectedDebugAndroidTest     # instrumentation tests (needs a device/emulator)
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## Versioning

SearchMob uses **Ubuntu-style date versioning**: `YY.MM.VV` (two-digit year, month, and per-month
build), e.g. `26.05.00`. `versionCode` is derived as `YY*10000 + MM*100 + VV`.

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md). We use [Conventional Commits](https://www.conventionalcommits.org)
and build each feature on its own branch, tested before merge. Please also read
[`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) and, for security reports, [`SECURITY.md`](SECURITY.md).

## Credits

App icon: <a href="https://www.flaticon.com/free-icons/search" title="search icons">Search icons created by Freepik - Flaticon</a>.

## License

[AGPL-3.0-or-later](LICENSE). If you run a modified version that users interact with over a network,
you must offer them the corresponding source.

Copyright © 2026 FlintWave. Contact: flintwave@tuta.com
