## Why

Nothing can be built, tested, or released until SearchMob has a working build system and a
runnable app shell. This phase establishes the foundation every later feature (always-on service,
search core, encrypted storage, UI) branches from, and proves the toolchain end to end (it
launches on a device and builds in CI) so that "test before merge" is possible from phase 2 on.
It directly serves the **customizable** and **always-on** goals by standing up the Compose/Material 3
theming base and the app process that the foreground service will later attach to.

## What Changes

- Create a Gradle (Kotlin DSL) Android project: `app` module, Kotlin, Jetpack Compose + Material 3,
  Gradle version catalog (`libs.versions.toml`), `minSdk 26 / targetSdk 35 / compileSdk 35`.
- Add a runnable **app shell**: a single `MainActivity` hosting a Compose home screen placeholder,
  with a base Material 3 theme that follows the system light/dark setting (dynamic color on
  Android 12+, static fallback below).
- Add repo hygiene: `LICENSE` (AGPL-3.0-or-later), `README.md`, `CONTRIBUTING.md` (Conventional
  Commits), `SECURITY.md`, `CODE_OF_CONDUCT.md`, `.editorconfig`, issue/PR templates.
- Add a minimal **CI build skeleton** (GitHub Actions): JDK 17 + Gradle, run `./gradlew lint test
  assembleDebug` on push/PR. (Signing/release pipeline is deferred to `add-ci-cd-releases`.)
- Add unit-test (JUnit) and instrumentation-test (`androidx.test`/Compose UI test) scaffolding with
  one trivial passing test each, to prove the test harness works.
- Wire ktlint/Android Lint for static checks.

This change introduces **no runtime permissions**, **no network calls**, and **no search engines**;
it is pure scaffolding.

## Capabilities

### New Capabilities
- `app-shell`: the application installs, launches to a home screen, and renders a Material 3 theme
  that honors the system light/dark setting: the baseline UI surface and process that later
  phases extend.
- `project-build`: the project builds debug artifacts and runs its unit/instrumentation test suites
  reproducibly, locally and in CI, on JDK 17 + Gradle.

### Modified Capabilities
<!-- None. This is the first change; no existing specs. -->

## Impact

- New code: `settings.gradle.kts`, root + `app` `build.gradle.kts`, `gradle/libs.versions.toml`,
  `app/src/main/AndroidManifest.xml`, `MainActivity.kt`, base theme (`Theme.kt`, `Color.kt`,
  `Type.kt`), `app/src/test` + `app/src/androidTest` sample tests.
- New infra: `.github/workflows/ci.yml`, `.github/ISSUE_TEMPLATE/`, `.github/PULL_REQUEST_TEMPLATE.md`.
- New docs: `LICENSE`, `README.md`, `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`.
- Dependencies introduced: Android Gradle Plugin, Kotlin, Compose BOM + Material 3, AndroidX
  core/activity-compose/lifecycle, JUnit, androidx.test, Compose UI test, ktlint.
- No permissions, no network, no persisted user data.
