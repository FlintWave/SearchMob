## Context

The scaffold (`bootstrap-project-scaffold`) shipped `.github/workflows/ci.yml`, which builds debug
artifacts and runs lint/tests on push/PR. SearchMob is distributed **off-Play** via GitHub Releases
and F-Droid, so a release pipeline is the next foundational piece: it must turn a tagged commit into
a versioned, signed, checksummed, verifiable artifact users can sideload, and feed F-Droid a build
recipe. This is pure CI/release infrastructure plus build configuration; it does not touch the app's
runtime, permissions, network, or storage behavior, and therefore has **no battery or on-device
privacy impact**. The relevant privacy/trust concern here is *distribution* integrity (signing,
checksums, reproducibility, FOSS-only release dependencies) and *supply-chain* integrity of CI.

## Goals / Non-Goals

**Goals:**
- Conventional-Commits-driven versioning and changelog with release-please managing `versionName` and
  `versionCode`, opening a release PR, and tagging + creating a GitHub Release on merge.
- A tag-triggered workflow that builds `assembleRelease`, signs it from secrets, generates
  `SHA256SUMS`, and attaches the signed APK + checksums to the GitHub Release.
- An F-Droid build-recipe metadata template plus documented reproducibility and signing-key
  verification (`AllowedAPKSigningKeys`/`Binaries`) and FOSS-eligibility constraints.
- Supply-chain hardening across all workflows: SHA-pinned actions, Dependabot, actionlint + shellcheck,
  least-privilege `permissions`.

**Non-Goals:**
- Any app feature/runtime behavior change (engines, permissions, UI, service, storage).
- Google Play / Play App Signing / Play Console delivery (intentionally off-Play).
- Achieving bit-for-bit reproducibility within this change (goal + verification documented; remaining
  nondeterminism is follow-up).
- Auto-submitting the fdroiddata merge request (manual maintainer step).

## Decisions

**Decision: release-please for versioning/changelog over manual bumps or semantic-release.**
`googleapis/release-please-action` consumes Conventional Commits (already mandated by project context
and CONTRIBUTING.md), opens a maintained release PR, and on merge creates the tag and GitHub Release.
Configured for an Android/Gradle target via `release-please-config.json` +
`.release-please-manifest.json`, with an `extra-files` entry so the PR also bumps `versionName` (and
a generic strategy for `versionCode`) in `app/build.gradle.kts`. *Alternatives:* semantic-release
(heavier Node toolchain, less GitHub-native release-PR flow); manual version bumps (error-prone, not
auditable). release-please keeps the human-in-the-loop merge gate the project's "test before merge"
workflow wants.

**Decision: trigger the release build on the release-please-created Release/tag, not on every push.**
The build/sign/publish workflow runs `on: release: types: [published]` (and/or push of a `v*` tag),
so signing secrets are only exposed for genuine releases, and a prerelease tag can be used for
dry-runs. *Alternative:* building on every merge to main wastes CI and broadens secret exposure.

**Decision: sign from a base64 keystore in GitHub Secrets, decoded at build time; never commit a keystore.**
Secrets `SIGNING_KEY_BASE64`, `KEY_ALIAS`, `KEY_STORE_PASSWORD`, `KEY_PASSWORD` are consumed either by
a maintained signing action (`ilharp/sign-android-release`, SHA-pinned) or by a Gradle release
`signingConfig` that reads them from environment variables. The decoded keystore lives only in the
runner's ephemeral workspace and is `.gitignore`d. *Alternative:* Play App Signing (off-Play, N/A);
checking in an encrypted keystore (still leaks the ciphertext + adds a passphrase secret anyway).
Gradle `signingConfigs` is preferred when reproducibility matters because the same Gradle invocation
F-Droid runs then produces an identically-signed-able artifact; the action is acceptable as a simpler
alternative.

**Decision: publish with `softprops/action-gh-release`, attaching the APK and a `SHA256SUMS` file.**
Checksums are generated with `sha256sum` over the signed artifact(s) into `SHA256SUMS` so users (and
F-Droid verification) can confirm integrity independent of GitHub. *Alternative:* relying solely on
GitHub's asset digests (not portable, not user-verifiable offline).

**Decision: F-Droid via a `Build`-recipe metadata template targeting reproducible verification.**
Provide `metadata/<applicationId>.yml` describing `gradle: yes`/`assembleRelease`, with
`AllowedAPKSigningKeys` pinned to the maintainer key fingerprint and (where reproducible)
`Binaries` pointing at the GitHub Release APK so F-Droid can verify the published binary matches a
from-source rebuild. FOSS-eligibility is enforced by keeping the **release** dependency graph free of
proprietary Google libraries (no Play Services / Firebase / proprietary maps), which aligns with the
"no telemetry, no Google libs" project stance. *Alternative:* F-Droid building unverified (no
`Binaries`); acceptable fallback but weaker trust than reproducible verification.

**Decision: supply-chain hardening as a cross-cutting requirement on every workflow.**
All third-party actions pinned by full commit SHA (matching the existing `ci.yml` style); Dependabot
configured for `github-actions` (keeps SHAs current with PRs) and `gradle` (app/build deps);
`actionlint` lints workflow YAML and `shellcheck` lints any shell (inline `run:` blocks / helper
scripts); each workflow declares minimal `permissions` (default `contents: read`, elevating to
`contents: write` only on the publish job, and `pull-requests: write` + `contents: write` for the
release-please job). *Alternative:* tag-pinned actions (mutable, supply-chain risk), rejected.

## Risks / Trade-offs

- **Leaked signing secrets** → restrict secret-using jobs to release/tag triggers, never echo secrets,
  decode keystore only into the ephemeral runner workspace, set least-privilege `permissions`, and
  keep keystores `.gitignore`d and out of history.
- **release-please fails to bump `versionCode`/`versionName` in Gradle Kotlin DSL** → use explicit
  `extra-files` patterns with annotation comments in `app/build.gradle.kts`; verify on a dry-run that
  the release PR edits both fields before relying on it.
- **Non-reproducible build blocks F-Droid binary verification** → start with `AllowedAPKSigningKeys`
  pinned and document the reproducibility goal; if rebuild diverges, fall back to F-Droid building
  from source (drop `Binaries`) rather than blocking distribution.
- **A transitive proprietary dependency sneaks into the release graph** → add a dependency-report check
  / manual review of the release configuration's dependencies so F-Droid FOSS-eligibility is not
  silently broken.
- **SHA-pinned actions go stale (miss security fixes)** → Dependabot `github-actions` ecosystem opens
  update PRs; actionlint guards malformed workflows after updates.
- **Signing-key loss** (maintainer-held) → out of CI scope, but documented: losing the key breaks
  update continuity for sideloaded installs; the maintainer must back it up securely.

## Migration Plan

1. Land the build config + workflows + Dependabot + lint on branch `feat/add-ci-cd-releases`; CI
   (debug) stays green; release workflow is present but only fires on release/tag.
2. Maintainer generates the keystore locally and configures repo secrets (`SIGNING_KEY_BASE64`,
   `KEY_ALIAS`, `KEY_STORE_PASSWORD`, `KEY_PASSWORD`), outside the repo, key never committed.
3. Dry-run on a prerelease tag: confirm the signed APK + `SHA256SUMS` attach to the Release and the
   APK installs on a device; confirm actionlint/shellcheck pass and Dependabot config validates.
4. Merge; let release-please open its first real release PR; merging it tags + publishes.
5. Submit the fdroiddata MR using the metadata template (manual, out-of-repo).
- **Rollback:** the release workflow only triggers on release/tag, so reverting the workflow files
  removes the pipeline with no effect on app code or prior releases; delete a bad prerelease/tag to
  undo a dry-run.

## Open Questions

- Ship an AAB (`bundleRelease`) as an extra Release asset, or APK-only? (APK is canonical; AAB optional.)
- Pin F-Droid `Binaries` for reproducible verification immediately, or start source-built and add
  `Binaries` once a rebuild is confirmed reproducible?
