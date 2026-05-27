# SearchMob Roadmap

A private, battery-friendly, always-on metasearch service for Android — the experience of a
local SearXNG instance, rebuilt as a native app that is gentle on battery and keeps user data
encrypted at rest (zero-knowledge optional).

This roadmap is the high-level plan. Each phase is tracked in detail as an OpenSpec change under
`openspec/changes/<name>/` (proposal, design, tasks, spec deltas). Phases are built in order; each
feature lands on its own branch, is tested, fixed, and re-tested until green before merging `main`.

## Locked decisions

| Decision | Choice | Why |
|---|---|---|
| Form factor | **Native Kotlin/Compose app** (not Termux/SearXNG) | `specialUse` foreground service needs no idle wake-lock → near-zero idle battery; SearXNG is heavy and fragile on ARM64 |
| Search sources | **Free engines by default + optional BYO API keys** | Free like SearXNG (scrape/free endpoints); optional Brave/Mojeek keys buy reliability. **Never scrape Google** (JS wall, litigation, risk to user's own IP) |
| Access scope | **On-device first**, network mode later | Simplest + most private; nothing listens on the network until opt-in |
| Storage | **Store nothing by default**; opt-in encrypted history; optional zero-knowledge | Data you never collect can't leak; Keystore-wrapped DEK + SQLCipher; Argon2id passphrase for zero-knowledge |
| Distribution | GitHub Releases + F-Droid (not Google Play) | Off-Play allows `specialUse` + battery-exemption without store rejection |
| License | **AGPL-3.0-or-later** | Privacy-aligned strong copyleft |

## Phases

All seven phase proposals are written and pass `openspec validate --strict`. Status legend:
**Built** = implemented + locally verified; **Planned** = OpenSpec proposal complete, ready to implement; **Deferred** = later.

| # | Change | Goal | Status |
|---|---|---|---|
| 1 | `bootstrap-project-scaffold` | Gradle/Kotlin/Compose project (minSdk 26, targetSdk 35), repo hygiene, CI build skeleton, empty app shell that builds + launches | **Built (local)** — green via `ktlintCheck lint test assembleDebug`; pending PR/merge (repo) + on-device VM check |
| 2 | `add-foreground-service` | `specialUse` FGS, boot persistence, battery-opt exemption flow, OEM autostart guidance, event-driven (no idle wake-lock) | Planned |
| 3 | `add-local-search-server` | Embedded Ktor server on `127.0.0.1`, request pipeline, OpenSearch descriptor for browsers | Planned |
| 4 | `add-metasearch-engine-core` | Engine adapter SPI, parallel OkHttp fan-out, dedup/merge/ranking, privacy proxying; free engines + optional BYO keys | Planned |
| 5 | `add-encrypted-storage` | Store-nothing default, encrypted prefs (DataStore+Keystore), opt-in encrypted history (SQLCipher), zero-knowledge passphrase mode | Planned |
| 6 | `add-search-ui-and-theming` | Compose search/results UI, settings, light/dark/Material You, engine toggles, API-key entry, persistent prefs | Planned |
| 7 | `add-ci-cd-releases` | release-please, build+sign APK, GitHub Releases, F-Droid metadata, conventional commits, shellcheck/actionlint, Dependabot | Planned |
| 8 | `add-network-mode` *(later)* | Opt-in LAN/Tailscale server with TLS + access control | Deferred |

## Key constraints carried through every phase

- **Battery:** event-driven; never hold a wake-lock while idle; loopback is not Doze-gated.
- **Privacy:** no telemetry, no analytics, no device identifiers; upstream engines see no cookies,
  no referrer, a rotated User-Agent, and no user identity.
- **Android restrictions:** target API 35; FGS must declare `specialUse` type + matching
  permission; OEM battery killers (Samsung/Xiaomi/OnePlus/Huawei) require user setup steps that
  can reset on firmware updates — ship in-app guidance.

## Testing

A local Android VM/emulator will be set up for on-device verification once the scaffold and
foreground service are in place (phases 1–2). Until then, phases include unit tests and build
verification in CI.
