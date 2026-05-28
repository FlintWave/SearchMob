# automated-releases Specification

## Purpose
TBD - created by archiving change add-ci-cd-releases. Update Purpose after archive.
## Requirements
### Requirement: Conventional-Commits-driven version and changelog automation
The project SHALL use release-please (`googleapis/release-please-action`) to automate versioning and
changelog generation from Conventional Commits. release-please SHALL open and maintain a release pull
request that updates the changelog and bumps both `versionName` and `versionCode` in
`app/build.gradle.kts`. The release-please configuration (`release-please-config.json` and
`.release-please-manifest.json`) SHALL be committed to the repository.

#### Scenario: Conventional commits accumulate into a release PR
- **WHEN** one or more Conventional Commits (e.g. `feat:`, `fix:`) land on the default branch
- **THEN** release-please opens or updates a release pull request
- **AND** the PR's diff bumps `versionName` and `versionCode` in `app/build.gradle.kts`
- **AND** the PR updates the changelog with the new entries

#### Scenario: Version bump follows commit type
- **WHEN** the unreleased commits include a `feat:` commit
- **THEN** the proposed `versionName` reflects at least a minor version increment per SemVer
- **AND** `versionCode` is incremented monotonically

### Requirement: Merging the release PR tags and creates a GitHub Release
When the release-please release pull request is merged, the project SHALL create a git tag for the new
version and a corresponding GitHub Release. The release-please job SHALL run with least-privilege
permissions sufficient only to open PRs and create releases (`contents: write`, `pull-requests:
write`).

#### Scenario: Merge produces a tag and Release
- **WHEN** the maintainer merges the release-please release pull request
- **THEN** a git tag matching the released version is created (e.g. `v0.2.0`)
- **AND** a GitHub Release for that tag is created with the generated changelog as its notes

### Requirement: Release build is signed from secrets and never embeds keystores in source
On a published GitHub Release (or a pushed version tag), the project SHALL build `assembleRelease`
and produce a release-signed APK. Signing material SHALL be supplied exclusively via the GitHub
Secrets `SIGNING_KEY_BASE64`, `KEY_ALIAS`, `KEY_STORE_PASSWORD`, and `KEY_PASSWORD`, decoded only into
the runner's ephemeral workspace. No keystore file and no signing password SHALL be committed to the
repository or appear in its git history. The release workflow MUST NOT run its signing step on
ordinary pushes or pull requests.

#### Scenario: Tagged release produces a signed APK
- **WHEN** the build/sign workflow runs for a published release or version tag with the signing
  secrets configured
- **THEN** it builds `assembleRelease` and produces an APK signed with the release key
- **AND** verifying the APK signature (e.g. `apksigner verify`) succeeds

#### Scenario: No keystore material is committed
- **WHEN** the repository working tree and history are inspected
- **THEN** no keystore (`.jks`/`.keystore`) file is present or tracked
- **AND** signing passwords appear only as references to GitHub Secrets, never as literals
- **AND** any decoded keystore path is covered by `.gitignore`

#### Scenario: Signing does not run on untrusted triggers
- **WHEN** a pull request or a non-release push triggers CI
- **THEN** the signing/release step does not execute and the signing secrets are not consumed

### Requirement: Signed APK and SHA256SUMS are published to the GitHub Release
The release workflow SHALL publish the signed APK as an asset on the GitHub Release using
`softprops/action-gh-release`, and SHALL generate and attach a `SHA256SUMS` file containing the
SHA-256 checksum of each published artifact. The publishing job SHALL request only `contents: write`
permission. Optionally, an AAB (`bundleRelease`) MAY be attached as an additional asset.

#### Scenario: Release assets include the APK and checksums
- **WHEN** the publish job completes for a release
- **THEN** the signed APK is attached as a Release asset
- **AND** a `SHA256SUMS` file is attached as a Release asset
- **AND** the SHA-256 in `SHA256SUMS` matches the published APK when recomputed locally

### Requirement: Releases provide corresponding source for AGPL compliance
Because every release is built from the public GitHub repository at a tagged commit, the project SHALL
satisfy AGPL-3.0 corresponding-source availability through that public source. Each GitHub Release
SHALL be associated with the exact tag/commit it was built from.

#### Scenario: Release is traceable to public source
- **WHEN** a user views a published GitHub Release
- **THEN** the Release references the git tag and commit the artifacts were built from
- **AND** that tagged source is publicly available in the repository

