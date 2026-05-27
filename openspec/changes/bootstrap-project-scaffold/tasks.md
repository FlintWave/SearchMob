## 1. Branch & repo hygiene

- [ ] 1.1 Create branch `feat/bootstrap-project-scaffold` off `main`
- [ ] 1.2 Add `LICENSE` (AGPL-3.0-or-later, full text)
- [ ] 1.3 Add `README.md` (project pitch, locked decisions table link to ROADMAP, build/run instructions, status)
- [ ] 1.4 Add `CONTRIBUTING.md` documenting Conventional Commits and the branch-per-feature/test-before-merge workflow
- [ ] 1.5 Add `SECURITY.md` (private vulnerability reporting via GitHub advisories) and `CODE_OF_CONDUCT.md`
- [ ] 1.6 Add `.editorconfig`, `.github/ISSUE_TEMPLATE/` (bug + feature), and `.github/PULL_REQUEST_TEMPLATE.md`

## 2. Gradle project skeleton

- [ ] 2.1 Add Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) pinned to a current stable Gradle
- [ ] 2.2 Add root `build.gradle.kts`, `settings.gradle.kts` (project name `SearchMob`, include `:app`)
- [ ] 2.3 Add `gradle/libs.versions.toml` with AGP, Kotlin, Compose BOM, Material 3, AndroidX core/activity-compose/lifecycle, JUnit, androidx.test, Compose UI test, ktlint
- [ ] 2.4 Add `app/build.gradle.kts`: applicationId `org.searchmob.app`, minSdk 26, targetSdk 35, compileSdk 35, Compose enabled, JDK 17 toolchain
- [ ] 2.5 Create package skeleton under `app/src/main/java/org/searchmob/`: `ui/`, `service/`, `server/`, `engine/`, `data/` (empty placeholders with package-info or `.gitkeep`)

## 3. App shell

- [ ] 3.1 Add `AndroidManifest.xml` with a single launcher `MainActivity` and NO permissions
- [ ] 3.2 Implement `MainActivity` hosting a Compose `SearchMobApp()` root composable with a placeholder home screen
- [ ] 3.3 Implement Material 3 theme (`ui/theme/Theme.kt`, `Color.kt`, `Type.kt`): system light/dark following, dynamic color on API 31+, static light/dark fallback below
- [ ] 3.4 Add app name, icon (adaptive launcher icon placeholder), and string resources

## 4. Test harness

- [ ] 4.1 Add unit test source set with one passing JUnit test (e.g. a trivial pure-Kotlin assertion)
- [ ] 4.2 Add instrumentation/Compose UI test source set with one passing test asserting the home screen renders
- [ ] 4.3 Add ktlint config and confirm `./gradlew ktlintCheck` (or equivalent) passes

## 5. CI build skeleton

- [ ] 5.1 Add `.github/workflows/ci.yml`: trigger on push + PR; `actions/checkout`, `actions/setup-java@v4` (Temurin 17), `gradle/actions/setup-gradle` — all third-party actions pinned by commit SHA
- [ ] 5.2 CI runs `./gradlew lint test assembleDebug`; cache Gradle
- [ ] 5.3 Add a build-status badge to `README.md`

## 6. Verify & merge

- [ ] 6.1 Run `./gradlew lint test assembleDebug` locally (or in CI) and confirm green
- [ ] 6.2 Verify the debug APK installs and launches to the home screen (emulator/VM or device); confirm dark/light follows system
- [ ] 6.3 Run `openspec validate bootstrap-project-scaffold --strict` and fix any issues
- [ ] 6.4 Open PR, confirm CI green, merge to `main`, then `openspec archive bootstrap-project-scaffold`
