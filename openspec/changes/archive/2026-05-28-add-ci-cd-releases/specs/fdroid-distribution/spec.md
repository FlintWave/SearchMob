## ADDED Requirements

### Requirement: F-Droid build-recipe metadata is provided in-repo
The project SHALL provide an F-Droid metadata build recipe (`metadata/<applicationId>.yml`, for
application id `org.searchmob`) suitable for submission to fdroiddata. The recipe SHALL declare a
Gradle build that invokes the release assembly (`assembleRelease`) and SHALL map its build entries to
the project's release tags so each tagged release corresponds to an F-Droid build.

#### Scenario: Metadata recipe builds from a release tag with Gradle
- **WHEN** the F-Droid metadata recipe is inspected
- **THEN** it specifies a Gradle-based build that runs `assembleRelease`
- **AND** its build version entry references a release tag and the matching `versionName`/`versionCode`

### Requirement: F-Droid verifies the maintainer signing key and (where reproducible) the published binary
The F-Droid metadata SHALL pin the maintainer's signing key via `AllowedAPKSigningKeys` set to the
release key's SHA-256 certificate fingerprint. Where the build is reproducible, the metadata SHALL
declare `Binaries` pointing at the GitHub Release APK so F-Droid can verify the published binary
against a from-source rebuild; reproducible builds are an explicit goal of this distribution path.

#### Scenario: Signing key is pinned in metadata
- **WHEN** the F-Droid metadata recipe is inspected
- **THEN** `AllowedAPKSigningKeys` is set to the SHA-256 fingerprint of the maintainer's release signing key

#### Scenario: Published binary is verifiable when builds are reproducible
- **WHEN** the build is reproducible and the metadata declares `Binaries` for the GitHub Release APK
- **THEN** an F-Droid from-source rebuild produces an artifact that matches the published APK under
  F-Droid's binary verification
- **AND** if a rebuild diverges, the metadata falls back to source-built distribution (no `Binaries`)
  rather than publishing an unverified binary

### Requirement: Release artifacts are FOSS-eligible (no proprietary Google libraries)
The release build's dependency graph SHALL contain no proprietary or non-free libraries, including no
Google Play Services, Firebase, proprietary Google Maps, or any closed-source SDK, so the app remains
eligible for the main F-Droid repository. The build SHALL provide a way to enumerate release
dependencies so this constraint can be checked.

#### Scenario: Release dependencies contain no proprietary Google libraries
- **WHEN** the release configuration's dependency report is generated and reviewed
- **THEN** it lists no Google Play Services, Firebase, or other proprietary/closed-source SDK
- **AND** no `AntiFeatures` for non-free dependencies are required in the F-Droid metadata

#### Scenario: Introducing a proprietary dependency is caught
- **WHEN** a proprietary library is added to the release dependency graph
- **THEN** the dependency review/report surfaces it as a FOSS-eligibility violation before release
