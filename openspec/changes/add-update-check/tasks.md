## 1. Branch & preferences

- [x] 1.1 Create branch `feat/add-update-check` off `main`
- [x] 1.2 Add `updateCheckEnabled` boolean preference (default TRUE) and key to `UserPreferences`
- [x] 1.3 Add `lastUpdateCheckMs` storage key (stored as a string, parsed to Long, default 0)
- [x] 1.4 Expose `updateCheckEnabled` as an observable `Flow` + suspend setter + one-shot read, and
        `lastUpdateCheckMs` getter/setter on `PreferencesRepository`

## 2. Update checker

- [x] 2.1 Add `VersionTag.toVersionCode` parsing `YY.MM.VV` (optional `v`) to `YY*10000 + MM*100 + VV`
- [x] 2.2 Add `UpdateInfo` with `isNewerThan(current)` (strictly greater) comparison
- [x] 2.3 Add `UpdateChecker.fetchLatest()` GETting the GitHub Releases "latest" endpoint through the
        shared privacy-proxy client, short timeout, bounded body, fail-soft (null on any failure)
- [x] 2.4 Make the base URL injectable so tests use MockWebServer

## 3. Throttle & coordinator

- [x] 3.1 Add pure `isUpdateCheckDue(lastCheckMs, nowMs, intervalMs)` (about 24h)
- [x] 3.2 Add `UpdateCheckCoordinator.checkIfDue()` gating on the preference + throttle, stamping the
        timestamp after each attempt (success or failure), returning info only when strictly newer

## 4. Launch wiring & prompt

- [x] 4.1 Read the running build's version code via `PackageInfo.longVersionCode` (API 28+ / fallback)
- [x] 4.2 Run the check in a lifecycle-scoped, background-dispatched coroutine in `MainActivity` that
        never blocks startup or the search UI
- [x] 4.3 Show a Material3 "Update available" dialog (new version, "Open releases page", "Not now");
        open the browser only on tap, fall back to the releases page, never auto-download/install

## 5. Settings & disclosure

- [x] 5.1 Add a Settings "Updates" section with a default-on toggle and a disclosing subtitle
- [x] 5.2 Update the About/privacy strings so "only outbound traffic is the searches you run" is truthful
- [x] 5.3 Update `README.md` and `SECURITY.md` with the disclosure; keep no-analytics/no-id claims intact

## 6. Tests & verify

- [x] 6.1 Tag parsing + version-code comparison (newer/equal/older/malformed, no throw)
- [x] 6.2 Checker fail-soft on HTTP error, timeout, malformed JSON (MockWebServer)
- [x] 6.3 Throttle due vs not-due; coordinator gate (off => no network, not due => no network)
- [x] 6.4 Preference `updateCheckEnabled` default true + round-trip; `lastUpdateCheckMs` round-trip
- [x] 6.5 `./gradlew --no-daemon ktlintFormat ktlintCheck lint test assembleDebug` BUILD SUCCESSFUL
- [x] 6.6 `openspec validate add-update-check --strict`
