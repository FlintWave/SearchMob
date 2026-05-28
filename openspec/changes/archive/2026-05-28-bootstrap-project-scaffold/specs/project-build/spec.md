## ADDED Requirements

### Requirement: Project builds debug artifacts reproducibly
The project SHALL build a debug APK using the checked-in Gradle wrapper on JDK 17, with all
dependency versions declared in a Gradle version catalog. The build SHALL succeed from a clean
checkout without manually installed dependencies beyond the Android SDK and JDK 17.

#### Scenario: Clean debug build succeeds
- **WHEN** a contributor runs `./gradlew assembleDebug` on a clean checkout with JDK 17 and the
  Android SDK installed
- **THEN** the build completes successfully and produces a debug APK

#### Scenario: Dependency versions are centralized
- **WHEN** a dependency version needs to change
- **THEN** it is declared in `gradle/libs.versions.toml` and not hard-coded in module build scripts

### Requirement: Unit and instrumentation test suites run green
The project SHALL include a JUnit unit-test source set and an AndroidX instrumentation/Compose UI
test source set, each containing at least one passing test, so the test harnesses are proven to work
before features are added.

#### Scenario: Unit tests pass
- **WHEN** `./gradlew test` is run
- **THEN** the unit test suite executes and all tests pass

#### Scenario: Instrumentation tests are present and runnable
- **WHEN** the instrumentation test suite is executed on an emulator or device
- **THEN** the sample Compose UI test runs and passes

### Requirement: Continuous integration verifies every change
The repository SHALL provide a GitHub Actions workflow that, on every push and pull request, sets up
JDK 17 and Gradle and runs lint, unit tests, and a debug assembly. A failing check SHALL block the
change from being considered green.

#### Scenario: CI runs on pull requests
- **WHEN** a pull request is opened or updated
- **THEN** the CI workflow runs `lint`, `test`, and `assembleDebug` and reports pass/fail status

#### Scenario: CI fails on broken build or test
- **WHEN** a change breaks compilation or a test
- **THEN** the CI workflow reports failure for that change
