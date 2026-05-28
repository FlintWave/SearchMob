## Why

SearchMob is sideloaded from GitHub Releases and is not in any app store, so there is no store update
mechanism to tell a user that a newer, more secure or more correct build exists. Users can run an old
build indefinitely without knowing. This change adds a small, opt-out, once-a-day launch-time check
against the GitHub Releases API so the app can tell the user (and only tell them, never auto-install)
when a newer release is available.

Because the app's privacy claim is that "the only outbound traffic is the searches you run", adding any
new outbound call is privacy-sensitive. So the check is conservative and fail-soft, routed through the
existing privacy proxy, throttled to about once a day, toggleable in Settings, and explicitly disclosed
in the in-app privacy copy, the README, and SECURITY.md so the claim stays truthful.

## What Changes

- Add an `UpdateChecker` (package `org.searchmob.update`) that GETs the GitHub Releases "latest"
  endpoint through the shared privacy-proxy OkHttp client (no cookies, stripped headers, rotated
  User-Agent which also satisfies GitHub's required User-Agent header), with a short (about 4s) timeout
  and a bounded body read. It parses `tag_name` and `html_url` and is strictly fail-soft: any HTTP
  error, timeout, malformed JSON, or malformed tag returns null instead of throwing.
- Parse the `YY.MM.VV` tag (optionally `v`-prefixed) into the same version code the build derives
  (`YY*10000 + MM*100 + VV`) and compare it against the running build's version code (read from
  `PackageInfo.longVersionCode`). An update is available only when the latest code is strictly greater.
- Add a boolean preference `updateCheckEnabled`, default TRUE (opt-out), and a `lastUpdateCheckMs`
  timestamp, both via the existing `PreferencesRepository` pattern. Throttle: only perform a network
  check when at least about 24h have elapsed since the last attempt, and stamp the timestamp after each
  attempt (success or failure) so a failing check does not hammer GitHub on every launch.
- Wire a lifecycle-scoped, background-dispatched check into `MainActivity` on launch. It does nothing
  when the preference is off or the throttle is not due, and never blocks startup or the search UI. A
  found update shows a Material3 "Update available" dialog with the new version, an "Open releases page"
  button (`ACTION_VIEW` to the release `html_url`, falling back to the releases page), and a "Not now"
  dismiss. The browser is never opened without an explicit tap, and the app never auto-downloads or
  auto-installs.
- Add a Settings "Updates" section with a "Check for updates on launch" toggle (default on) whose
  subtitle discloses the GitHub call and that it routes through the privacy proxy.
- Update the in-app About/privacy copy, `README.md`, and `SECURITY.md` so the "only outbound traffic is
  the searches you run" claim becomes truthful by also mentioning the optional, once-a-day, opt-out
  update check. The no-analytics / no-telemetry / no-identifiers claims are kept intact.

## Capabilities

### New Capabilities
- `update-check`: an opt-out, default-on, once-a-day launch-time check against the GitHub Releases API,
  routed through the privacy proxy, that prompts (never auto-installs) when a newer release exists, with
  a Settings toggle and a truthful privacy disclosure.

### Modified Capabilities
<!-- None: this adds a new outbound check; it does not change existing search or server behavior. -->

## Impact

- New code: `UpdateChecker` + `VersionTag` + `UpdateInfo`, `UpdateCheckCoordinator` + `isUpdateCheckDue`
  throttle, the `updateCheckEnabled` / `lastUpdateCheckMs` preferences, the `MainActivity` launch wiring
  and update dialog, and the Settings "Updates" toggle.
- New outbound network: one GitHub Releases API call about once a day when enabled. Disclosed in the
  About screen, README, and SECURITY.md. No telemetry, no query, and no identifier is sent.
- No new permission: INTERNET already present for search fan-out.

## Non-goals

- Auto-downloading or auto-installing the APK (the prompt only links to the releases page).
- In-app changelog rendering or release-notes display beyond the version name.
- F-Droid or Play update integration (those channels manage their own updates).
- Checking more often than once a day or on a background schedule (it is launch-time only).
