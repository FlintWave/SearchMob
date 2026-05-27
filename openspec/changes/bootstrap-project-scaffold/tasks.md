## 1. Branch & repo hygiene

- [x] 1.1 Create branch `feat/bootstrap-project-scaffold` off `main`
- [x] 1.2 Add `LICENSE` (AGPL-3.0-or-later, full text)
- [x] 1.3 Add `README.md` (project pitch, locked decisions table link to ROADMAP, build/run instructions, status)
- [x] 1.4 Add `CONTRIBUTING.md` documenting Conventional Commits and the branch-per-feature/test-before-merge workflow
- [x] 1.5 Add `SECURITY.md` (private vulnerability reporting via GitHub advisories) and `CODE_OF_CONDUCT.md`
- [x] 1.6 Add `.editorconfig`, `.github/ISSUE_TEMPLATE/` (bug + feature), and `.github/PULL_REQUEST_TEMPLATE.md`

## 2. Gradle project skeleton

- [x] 2.1 Add Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) pinned to a current stable Gradle (8.11.1)
- [x] 2.2 Add root `build.gradle.kts`, `settings.gradle.kts` (project name `SearchMob`, include `:app`)
- [x] 2.3 Add `gradle/libs.versions.toml` with AGP, Kotlin, Compose BOM, Material 3, AndroidX core/activity-compose/lifecycle, JUnit, androidx.test, Compose UI test, ktlint
- [x] 2.4 Add `app/build.gradle.kts`: applicationId `org.searchmob`, minSdk 26, targetSdk 35, compileSdk 35, Compose enabled, JDK 17 toolchain
- [x] 2.5 Create package skeleton under `app/src/main/java/org/searchmob/`: `ui/`, `service/`, `server/`, `engine/`, `data/`

## 3. App shell

- [x] 3.1 Add `AndroidManifest.xml` with a single launcher `MainActivity` and NO permissions
- [x] 3.2 Implement `MainActivity` hosting a Compose `SearchMobApp()` root composable with a placeholder home screen
- [x] 3.3 Implement Material 3 theme (`ui/theme/Theme.kt`, `Color.kt`, `Type.kt`): system light/dark following, dynamic color on API 31+, static light/dark fallback below
- [x] 3.4 Add app name, icon (adaptive launcher icon placeholder), and string resources

## 4. Test harness

- [x] 4.1 Add unit test source set with one passing JUnit test (`ExampleUnitTest`)
- [x] 4.2 Add instrumentation/Compose UI test source set with one test asserting the home screen renders (`HomeScreenTest`) — runs on emulator/VM (task 6.2)
- [x] 4.3 Add ktlint config (Compose-aware naming) and confirm `./gradlew ktlintCheck` passes

## 5. CI build skeleton

- [x] 5.1 Add `.github/workflows/ci.yml`: trigger on push + PR; `actions/checkout`, `actions/setup-java@v4` (Temurin 17), `gradle/actions/setup-gradle` — all third-party actions pinned by commit SHA
- [x] 5.2 CI runs `./gradlew ktlintCheck lint test assembleDebug`; Gradle cached via setup-gradle
- [x] 5.3 Add a build-status badge to `README.md`

## 6. Verify & merge

- [x] 6.1 Run `./gradlew ktlintCheck lint test assembleDebug` locally and confirm green (BUILD SUCCESSFUL; APK produced; unit test passed)
- [ ] 6.2 Verify the debug APK installs and launches to the home screen (emulator/VM or device); confirm dark/light follows system — pending Android VM
- [x] 6.3 Run `openspec validate bootstrap-project-scaffold --strict` and fix any issues
- [ ] 6.4 Open PR, confirm CI green, merge to `main`, then `openspec archive bootstrap-project-scaffold` — pending GitHub repo URL
