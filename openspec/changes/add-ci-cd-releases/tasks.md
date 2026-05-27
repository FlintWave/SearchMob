## 1. Branch setup

- [ ] 1.1 Create branch `feat/add-ci-cd-releases` off `main`.
- [ ] 1.2 Confirm the scaffold CI (`./gradlew ktlintCheck lint test assembleDebug`) is green before adding the release pipeline.

## 2. Release signing build config

- [ ] 2.1 Add a release `signingConfig` to `app/build.gradle.kts` that reads keystore material from environment variables fed by GitHub Secrets (`SIGNING_KEY_BASE64`, `KEY_ALIAS`, `KEY_STORE_PASSWORD`, `KEY_PASSWORD`); apply it to the `release` build type. Fall back gracefully (unsigned/debug) for local debug builds when env vars are absent.
- [ ] 2.2 Ensure no keystore file is committed: add `*.jks`/`*.keystore` and any decoded-keystore path to `.gitignore`; verify nothing keystore-related is tracked or in history.
- [ ] 2.3 Add `versionName`/`versionCode` annotation markers in `app/build.gradle.kts` so release-please can bump both fields.

## 3. Automated versioning + changelog (release-please)

- [ ] 3.1 Add `release-please-config.json` and `.release-please-manifest.json` configured for the Android/Gradle target, with `extra-files` patterns that bump `versionName` and `versionCode` in `app/build.gradle.kts`.
- [ ] 3.2 Add a release-please workflow (`.github/workflows/release-please.yml`) on push to `main`, SHA-pinned `googleapis/release-please-action`, with least-privilege `permissions` (`contents: write`, `pull-requests: write`).

## 4. Build + sign + publish workflow

- [ ] 4.1 Add `.github/workflows/release.yml` triggered on `release: types: [published]` (and/or `v*` tag), default `permissions: contents: read`.
- [ ] 4.2 In the build job, set up Temurin JDK 17 + Gradle and run `assembleRelease` (optionally `bundleRelease`); sign via Gradle `signingConfigs` or SHA-pinned `ilharp/sign-android-release`, reading the four signing secrets.
- [ ] 4.3 Generate `SHA256SUMS` over the signed artifact(s) with `sha256sum`.
- [ ] 4.4 Publish with SHA-pinned `softprops/action-gh-release`, attaching the signed APK + `SHA256SUMS` (and optional AAB); grant `contents: write` only on this publish job.
- [ ] 4.5 Ensure signing/release steps cannot run on PRs or non-release pushes (secrets only exposed on release/tag triggers).

## 5. F-Droid distribution path

- [ ] 5.1 Add `metadata/org.searchmob.yml` build recipe: Gradle `assembleRelease`, build entry mapped to release tags with matching `versionName`/`versionCode`.
- [ ] 5.2 Set `AllowedAPKSigningKeys` to the maintainer release key's SHA-256 fingerprint; document the reproducible-builds goal and (where reproducible) `Binaries` verification against the GitHub Release APK, with source-built fallback.
- [ ] 5.3 Document FOSS-eligibility: provide a way to enumerate release dependencies and confirm no proprietary Google libraries (Play Services/Firebase/etc.) are present.

## 6. Supply-chain hardening + hygiene

- [ ] 6.1 Pin all third-party actions in every workflow by full commit SHA (with trailing version comment).
- [ ] 6.2 Add `.github/dependabot.yml` with update entries for both `github-actions` and `gradle` ecosystems.
- [ ] 6.3 Add actionlint + shellcheck to CI (extend `ci.yml` or add a lint workflow) covering all workflow YAML and all shell (inline `run:` and any helper scripts).
- [ ] 6.4 Set explicit least-privilege `permissions` on every workflow (default `contents: read`; elevate only the publish and release-please jobs).

## 7. Documentation

- [ ] 7.1 Document maintainer secret setup (generating the keystore, base64-encoding it, configuring `SIGNING_KEY_BASE64`/`KEY_ALIAS`/`KEY_STORE_PASSWORD`/`KEY_PASSWORD` as repo secrets) — note the maintainer MUST configure these before a real release can sign; keystore is never committed.
- [ ] 7.2 Document the F-Droid submission steps (manual fdroiddata MR) and state AGPL corresponding-source is satisfied via the public tagged GitHub source.

## 8. Verification

- [ ] 8.1 Run `actionlint` and `shellcheck` locally/in CI and confirm they pass on all workflows and shell.
- [ ] 8.2 Validate `.github/dependabot.yml` (valid YAML; both ecosystems present and accepted by Dependabot).
- [ ] 8.3 Dry-run a release: push a prerelease/tag (or trigger the release workflow on a prerelease) and confirm the signed APK + `SHA256SUMS` are attached to the GitHub Release.
- [ ] 8.4 Confirm the published APK signature verifies (`apksigner verify`), the `SHA256SUMS` matches the recomputed checksum, and the APK installs on a device/emulator.
- [ ] 8.5 Confirm release-please opens a release PR that bumps both `versionName` and `versionCode`.
- [ ] 8.6 Confirm the scaffold debug CI is still green and signing steps did not run on the PR.

## 9. Merge + finalize

- [ ] 9.1 Run `openspec validate add-ci-cd-releases --strict` and fix until valid.
- [ ] 9.2 Open a PR for `feat/add-ci-cd-releases`; address review.
- [ ] 9.3 Note that signing secrets must be configured in the GitHub repo by the maintainer (out-of-repo) before the first signed release.
- [ ] 9.4 Merge to `main`, then archive the change with `openspec archive add-ci-cd-releases`.
