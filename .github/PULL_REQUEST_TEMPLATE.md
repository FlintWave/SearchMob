<!-- Use a Conventional Commits title, e.g. "feat(engine): add Mojeek adapter" -->

## What & why

<!-- What does this change do, and which goal/OpenSpec change does it serve? -->

Relates to OpenSpec change: `<change-name>` <!-- e.g. add-foreground-service -->

## Checklist

- [ ] Built on its own `feat/`/`fix/` branch off `main`
- [ ] `./gradlew ktlintCheck lint test assembleDebug` is green locally
- [ ] Added/updated tests (unit and/or instrumentation)
- [ ] No telemetry, trackers, or device identifiers added
- [ ] No Google scraping introduced
- [ ] Battery: no wake-lock held while idle
- [ ] OpenSpec change validates (`openspec validate <name> --strict`) and tasks are checked off
