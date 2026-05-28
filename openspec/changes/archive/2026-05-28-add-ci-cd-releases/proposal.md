## Why

SearchMob is distributed off-Play, via **GitHub Releases and F-Droid**, so its trustworthiness as a
**private** app depends on releases that are reproducible, verifiable, and built from the public AGPL
source rather than hand-built on a maintainer's laptop. The scaffold's CI skeleton only builds debug
artifacts; there is no path to produce a versioned, **signed**, checksummed release or to publish it
where users can install it. This change adds that release pipeline so every feature merged after the
scaffold can ship to users with an auditable supply chain.

## What Changes

- Add **automated versioning + changelog** via release-please (`googleapis/release-please-action`)
  driven by Conventional Commits: it opens/maintains a release PR, bumps `versionName` and
  `versionCode`, and on merge creates a git tag and a GitHub Release.
- Add a **release build + signing** workflow: on a release/tag, build `assembleRelease` (APK, and
  optionally `bundleRelease`/AAB) and sign it from a base64 keystore + alias/passwords supplied via
  GitHub Secrets (`SIGNING_KEY_BASE64`, `KEY_ALIAS`, `KEY_STORE_PASSWORD`, `KEY_PASSWORD`), using a
  maintained signing action or Gradle `signingConfigs`. Keystores are **never** committed.
- Add **publishing**: `softprops/action-gh-release` uploads the signed APK and a generated
  `SHA256SUMS` file as assets on the GitHub Release.
- Add an **F-Droid distribution path**: an `fdroiddata` build-recipe metadata template plus
  documentation of the reproducible-builds goal, `AllowedAPKSigningKeys`/`Binaries` verification, and
  FOSS-eligibility (no proprietary Google libraries pulled into the release).
- Add **supply-chain hardening**: pin all third-party actions by commit SHA, add Dependabot for the
  `gradle` and `github-actions` ecosystems, add `actionlint` (workflow lint) and `shellcheck` (any
  shell / helper scripts), and set least-privilege `permissions` per workflow/job.
- Document that **AGPL corresponding-source availability** is satisfied because every release is built
  from the public GitHub repository at a tagged commit.

This change introduces **no app runtime permissions, no engines, and no network behavior**; it is
release/CI infrastructure plus build configuration (Gradle `signingConfigs`) only.

## Capabilities

### New Capabilities
- `automated-releases`: release-please-driven versioning/changelog, and a tag-triggered pipeline that
  builds, signs, checksums, and publishes the APK + `SHA256SUMS` to a GitHub Release.
- `fdroid-distribution`: fdroiddata build-recipe metadata, reproducibility/signing-key verification,
  and FOSS-eligibility constraints for the F-Droid channel.
- `supply-chain-hardening`: SHA-pinned actions, Dependabot (gradle + github-actions), actionlint +
  shellcheck linting, and least-privilege workflow permissions across all CI/release workflows.

### Modified Capabilities
<!-- None. The scaffold's CI build skeleton is a workflow file, not an OpenSpec capability/spec;
     this change adds new release capabilities on top of it without altering existing requirements. -->

## Impact

- New infra: `.github/workflows/release.yml` (build/sign/publish), `release-please` config
  (`release-please-config.json` + `.release-please-manifest.json`), `.github/dependabot.yml`,
  `actionlint`/`shellcheck` steps (a new lint workflow or additions to `ci.yml`).
- Modified infra: `.github/workflows/ci.yml` gains workflow/shell linting; all workflows get explicit
  least-privilege `permissions`.
- Modified build config: `app/build.gradle.kts` gains a release `signingConfig` that reads keystore
  material from environment/secrets (no secrets in source); `versionName`/`versionCode` become
  release-please-managed.
- New metadata: F-Droid `metadata/<applicationId>.yml` build recipe (template for the fdroiddata MR).
- Secrets/setup: maintainer must configure repo secrets `SIGNING_KEY_BASE64`, `KEY_ALIAS`,
  `KEY_STORE_PASSWORD`, `KEY_PASSWORD`; `GITHUB_TOKEN` is used for releases.
- Dependencies introduced: none at the app/runtime level; CI uses pinned third-party actions.
- No app permissions, no telemetry, no persisted user data.

## Non-goals

- **No app/runtime feature work**: no engines, permissions, UI, service, or storage behavior changes.
- **No Google Play / Play Console publishing.** SearchMob is intentionally off-Play; this pipeline
  targets GitHub Releases and F-Droid only.
- **No AAB-based store delivery or Play App Signing**; AAB output is optional/best-effort, with the
  signed **APK** as the canonical, sideloadable release artifact.
- **No automatic submission of the fdroiddata merge request.** This change provides the metadata
  recipe and verification guidance; submitting/maintaining the MR to fdroiddata is a manual,
  out-of-repo maintainer step.
- **No achievement of bit-for-bit reproducible builds in this change.** Reproducibility is stated as
  a goal with the verification mechanism (`AllowedAPKSigningKeys`/`Binaries`) documented; closing any
  remaining nondeterminism is follow-up work.
- **No release of the maintainer signing key.** Key generation and secret configuration are done by
  the maintainer outside the repo; the keystore is never committed.
