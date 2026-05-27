# SearchMob

[![CI](https://github.com/ErikChevalier/SearchMob/actions/workflows/ci.yml/badge.svg)](https://github.com/ErikChevalier/SearchMob/actions/workflows/ci.yml)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](LICENSE)

**A private, battery-friendly, always-on metasearch service for Android.**

SearchMob recreates the experience of running your own local [SearXNG](https://searxng.org)
instance — but as a native Android app designed to be gentle on battery, run with no servers of
its own, and keep any data it stores encrypted at rest (zero-knowledge optional).

> **Status:** early scaffolding. See [`ROADMAP.md`](ROADMAP.md) for the plan and
> [`openspec/`](openspec/) for detailed, spec-driven feature proposals.

## Why

- **Private** — your queries never touch a SearchMob server (there are none). The app acts as a
  privacy proxy to upstream engines: no cookies, no referrer, no user identifier, rotated
  User-Agent. No telemetry, no analytics, no device identifiers. It never scrapes Google (which
  would risk getting your own IP blocked).
- **Battery-friendly** — a native foreground service that is event-driven and holds **no wake-lock
  while idle**, so the CPU sleeps and idle drain is near-zero.
- **Always available** — runs as an always-on local service, started on boot, reachable on-device.
- **Customizable** — light/dark/Material You theming and easily changed, reboot-persistent preferences.
- **Free** — works out of the box with free search engines; optionally add your own Brave/Mojeek API
  key for higher reliability.

## Architecture (summary)

| Aspect | Choice |
|---|---|
| Form factor | Native Android app — Kotlin + Jetpack Compose (Material 3) |
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
./gradlew assembleDebug    # build a debug APK
./gradlew test             # run JVM unit tests
./gradlew lint             # run Android Lint
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md). We use [Conventional Commits](https://www.conventionalcommits.org)
and build each feature on its own branch, tested before merge. Please also read
[`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) and, for security reports, [`SECURITY.md`](SECURITY.md).

## License

[AGPL-3.0-or-later](LICENSE). If you run a modified version that users interact with over a network,
you must offer them the corresponding source.
