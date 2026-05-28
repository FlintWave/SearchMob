## Context

SearchMob is greenfield: an empty repository with OpenSpec planning only. Before any feature can be
built "on its own branch, tested, and merged," there must be a project that compiles, launches, and
runs tests both locally and in CI. This change defines that foundation. It is deliberately minimal,
with no permissions, no network, and no storage, so that risk and review surface stay tiny and every later
phase starts from a known-green baseline.

## Goals / Non-Goals

**Goals:**
- A single-module Android app (`app`) that builds a debug APK and launches to a Compose home screen.
- A Material 3 theming base honoring system light/dark and dynamic color, ready for phase 6 to extend.
- A reproducible build: Gradle wrapper checked in, version catalog for all dependency versions, JDK 17.
- Working unit + instrumentation test harnesses, each with a passing sample test.
- A CI workflow that runs `lint test assembleDebug` on every push/PR.
- Repo hygiene that matches the AGPL-3.0 + Conventional Commits conventions.

**Non-Goals:**
- The foreground service, boot persistence, or any battery/permission work (phase 2).
- The embedded HTTP server (phase 3) and any search engine code (phase 4).
- Encrypted storage / DataStore / SQLCipher (phase 5).
- Release signing, F-Droid metadata, release-please (phase 7).
- Multi-module splitting. Start single-module; split later only if build times demand it.

## Decisions

- **Single `app` module to start, not multi-module.** Rationale: fastest path to green; premature
  module boundaries slow iteration. The package layout (`service/`, `server/`, `engine/`, `data/`,
  `ui/`) is created now as packages so later phases have a home without a refactor. Alternative
  (multi-module from day one) rejected: more Gradle overhead before we know the boundaries.
- **Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`).** Rationale: single source of
  truth for versions, Dependabot/renovate-friendly, the modern AGP default. Alternative (Groovy DSL)
  rejected as legacy.
- **Compose + Material 3 with `dynamicColorScheme` on API 31+, static fallback below.** Rationale:
  Material You is a stated customization goal; dynamic color is unavailable < API 31 so a curated
  light/dark scheme is the floor. minSdk 26 chosen to cover ~95%+ of devices while allowing modern APIs.
- **Theme follows system setting now; explicit light/dark/auto override is deferred to phase 6.**
  Keeps the scaffold's behavior testable (system dark → dark UI) without building a settings store yet.
- **CI uses `actions/setup-java@v4` (Temurin 17) + `gradle/actions/setup-gradle`, pinned by SHA.**
  Matches the project CI standard; the heavier signing/release jobs come in phase 7 so this stays fast.
- **ktlint + Android Lint as the static-analysis floor.** Cheap, fast, enforces consistency before
  the codebase grows.

## Risks / Trade-offs

- [Android SDK / emulator may be unavailable on the planning machine] → CI is the source of truth for
  build/test green; on-device verification happens on the emulator/VM set up at the end of phase 1-2.
  Instrumentation tests can be marked to run on CI emulator or deferred to the VM step.
- [`targetSdk 35` pulls in Android 14/15 behavior changes early] → acceptable and intended; we want to
  surface FGS/permission constraints from the start rather than discover them late. The scaffold itself
  declares no FGS, so no behavior change bites yet.
- [Compose BOM version drift] → pin via the version catalog and let Dependabot (phase 7) propose bumps.
- [Single-module may need splitting later] → mitigated by establishing package boundaries now; a later
  split is mechanical.

## Open Questions

- None blocking. Emulator/VM choice (Android Studio AVD vs. cloud) is resolved when on-device testing
  begins after phase 2; the user has offered to set up a local Android VM at that point.
