# Contributing to SearchMob

Thanks for your interest! SearchMob is a privacy-first, open-source Android project. By contributing
you agree your contributions are licensed under the project's [AGPL-3.0-or-later](LICENSE).

## Ground rules

- **No telemetry, no trackers, no device identifiers.** Privacy is the product. Any feature that
  phones home or fingerprints the user/device will be rejected.
- **Never add Google scraping.** It is a permanent non-goal (JS wall, litigation, and the risk of
  getting a user's own IP blocked). Use the supported free engines or optional BYO-key APIs.
- **Battery discipline.** Never hold a wake-lock while idle. Acquire only a short, timed wake-lock
  for the duration of real work and release it in a `finally` block.

## Workflow

1. **Plan via OpenSpec.** Substantial features start as an OpenSpec change under `openspec/changes/`
   (`openspec new change "<name>"`, then fill proposal → design → specs → tasks). Run
   `openspec validate <name> --strict` before implementing.
2. **One feature per branch.** Branch off `main` as `feat/<change-name>` (or `fix/`, `docs/`, `chore/`).
3. **Test, fix, re-test before merging.** Add unit tests (and instrumentation tests where it makes
   sense). `./gradlew lint test assembleDebug` must be green locally and in CI.
4. **Open a PR.** CI must pass. After merge, archive the change with `openspec archive <name>`.

## Commit messages — Conventional Commits

Format: `type(scope): summary`. Types: `feat`, `fix`, `docs`, `chore`, `refactor`, `test`, `build`,
`ci`, `perf`. Breaking changes use `!` or a `BREAKING CHANGE:` footer. These drive automated
versioning and changelogs (release-please) once the release pipeline lands.

Examples:
```
feat(engine): add Mojeek HTML adapter
fix(service): release wake-lock in finally on request error
docs(readme): clarify free-vs-BYO-key search sourcing
```

## Code style

- Kotlin official style (`kotlin.code.style=official`). Run `./gradlew ktlintCheck` once ktlint is wired.
- Keep modules/packages aligned with the layout: `ui/`, `service/`, `server/`, `engine/`, `data/`.

## Releases (maintainers)

Releases are automated and distributed via **GitHub Releases + F-Droid** (never Google Play).

**How a release happens.** Conventional Commits on `main` drive
[release-please](.github/workflows/release-please.yml), which opens/maintains a release PR that bumps
`versionName` in `app/build.gradle.kts` (`versionCode` is derived from it, so it always increases
monotonically) and updates `CHANGELOG.md`. Merging that PR tags the commit (e.g. `v0.2.0`) and
creates a GitHub Release; that publish event triggers
[`release.yml`](.github/workflows/release.yml), which runs `assembleRelease`, signs the APK, verifies
the signature, generates `SHA256SUMS`, and attaches the signed APK + `SHA256SUMS` to the Release.

**Required repository Secrets** (Settings → Secrets and variables → Actions). The keystore is
**never** committed — `*.jks`/`*.keystore`/`keystore.properties` are gitignored. Generate the key
once and keep it backed up securely (losing it breaks update continuity for sideloaded installs):

```bash
# 1. Generate a release keystore (do NOT commit it).
keytool -genkeypair -v -keystore release.keystore -alias searchmob \
  -keyalg RSA -keysize 4096 -validity 10000

# 2. Base64-encode it for the SIGNING_KEY_BASE64 secret.
base64 -w0 release.keystore   # macOS: base64 -i release.keystore
```

| Secret | Value |
|---|---|
| `SIGNING_KEY_BASE64` | base64 of `release.keystore` (decoded into the runner at build time) |
| `KEY_ALIAS` | the key alias (e.g. `searchmob`) |
| `KEY_STORE_PASSWORD` | the keystore password |
| `KEY_PASSWORD` | the key password |

`GITHUB_TOKEN` is provided automatically. The maintainer **must** configure the four signing secrets
before the first signed release; the build no-ops to an *unsigned* release APK if they are absent, so
local `./gradlew assembleDebug` / `assembleRelease` work with no keystore. For a local signed build,
create a gitignored `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`).

**F-Droid.** The build recipe lives at [`metadata/org.searchmob.yml`](metadata/org.searchmob.yml).
Submitting/maintaining the merge request to
[fdroiddata](https://gitlab.com/fdroid/fdroiddata) is a manual, out-of-repo step: copy that recipe
in, set `AllowedAPKSigningKeys` to the SHA-256 fingerprint of the release key
(`keytool -list -v -keystore release.keystore -alias searchmob`, lowercased, colons stripped), and —
once a from-source rebuild is confirmed reproducible — uncomment `Binaries:` so F-Droid verifies the
published APK byte-for-byte (falling back to source-built if a rebuild diverges). The release
dependency graph must stay FOSS (no Play Services/Firebase/proprietary SDKs); enumerate it with
`./gradlew :app:dependencies --configuration releaseRuntimeClasspath` before release.

**AGPL corresponding source** is satisfied automatically: every release is built from the public
GitHub repository at its tagged commit, which is the corresponding source for that binary.
