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
