# SearchMob

[![CI](https://github.com/FlintWave/SearchMob/actions/workflows/ci.yml/badge.svg)](https://github.com/FlintWave/SearchMob/actions/workflows/ci.yml)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](LICENSE)

**A private, battery-friendly, always-on metasearch app for Android.**

SearchMob gives you the experience of running your own [SearXNG](https://searxng.org) instance, but
as a native Android app that runs entirely on your phone, with **no SearchMob servers and no
telemetry of any kind**. It aggregates results from several search engines, acts as a privacy proxy
so those engines see no cookies/identifier from you, and keeps anything it stores encrypted at rest
(with an optional zero-knowledge mode).

> **Status:** latest release **`26.05.02`**. Functional and verified on Android 15. Ongoing work and
> design live in [`ROADMAP.md`](ROADMAP.md) and [`openspec/`](openspec/).

## What it does

- **Private metasearch on your device.** Queries are fanned out in parallel to **DuckDuckGo, Mojeek,
  Marginalia, Mwmbl, and Wikipedia** (plus optional bring-your-own **Brave** / **Mojeek API** keys),
  then de-duplicated and re-ranked. The app proxies the requests: upstream engines see no cookies, no
  referrer, no user/device identifier, and a rotated User-Agent. It **never scrapes Google**.
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
  it's local-only, encrypted, user-purgeable, with an optional zero-knowledge passphrase. (See the
  in-app **About & privacy** screen for the full methodology and caveats.)

## Install

SearchMob is distributed via **GitHub Releases** (and F-Droid is planned), not Google Play.

1. Download the APK from the [Releases](https://github.com/FlintWave/SearchMob/releases) page.
2. Install it (you may need to allow installing from your browser/files app).
3. Follow the first-run wizard to grant the notification + battery-optimization permissions and to set
   SearchMob as your browser's search engine.

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

## License

[AGPL-3.0-or-later](LICENSE). If you run a modified version that users interact with over a network,
you must offer them the corresponding source.

Copyright © 2026 FlintWave. Contact: flintwave@tuta.com
