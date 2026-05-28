## Context

The scaffold (phase 1) gives us a launchable Compose app with empty `service/`, `server/`,
`engine/`, `data/`, and `ui/` packages. This phase fills in `service/` with the always-on backbone
every later phase depends on. The defining tension is **always-on vs. battery**: the service must
never die on its own, yet must cost ~0 battery while idle. The locked project decisions already
settle the big questions (native `specialUse` FGS, event-driven, no idle wake-lock, off-Play
distribution so `specialUse` is allowed); this design records *how* the lifecycle, state, and setup
flows are wired, not whether to use them.

A real subtlety drives the implementation: a foreground service does **not** by itself keep the CPU
awake. Foreground status protects the process from being killed and grants foreground-service
privileges, but during Doze/idle the CPU can still sleep, which is exactly what we want for ~0
idle drain. A wake-lock (not foreground status) is what keeps the CPU running, so the rule is
"foreground always, wake-lock only during a unit of work." There is no real work unit in this
phase, but the helper and the discipline are established here so later phases cannot regress it.

## Goals / Non-Goals

**Goals:**
- A `specialUse` foreground `Service` that starts on app-open and on boot, runs `START_STICKY`,
  posts a persistent notification on a dedicated channel, and can be stopped by the user.
- An observable `stopped` / `starting` / `running` state model the UI and notification both read
  from a single source of truth.
- A timed `PARTIAL_WAKE_LOCK` helper that an idle service never holds, wrapping any future unit of
  work and releasing in `finally`.
- A battery-optimization exemption flow (detect + user-initiated prompt) and a per-OEM autostart
  guidance surface.

**Non-Goals:**
- Binding the Ktor server or any socket (phase 3). The service body is a placeholder that only
  manages lifecycle + state.
- Any network request, search, or engine code (phase 4); any persisted state (phase 5).
- Polished settings UI (phase 6). Only the minimal controls to drive the lifecycle and setup
  flows are built here.

## Decisions

- **`specialUse` foreground service type, not `dataSync` or `connectedDevice`.** Rationale: locked
  decision: the service's purpose (a long-lived on-device search backbone) matches no standard
  Android FGS category, and `specialUse` is the catch-all that off-Play distribution permits without
  store review. **Android-version note:** on API 35 a `dataSync` FGS may not be started from
  `BOOT_COMPLETED`, but a `specialUse` FGS may; this is a concrete reason `specialUse` is correct
  for our boot-start requirement. We declare the matching
  `FOREGROUND_SERVICE_SPECIAL_USE` permission and the
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property so the manifest is valid on API 34+. Alternative
  (`dataSync`) rejected: boot-restricted and semantically wrong.
- **`START_STICKY`, not `START_REDELIVER_INTENT` or `START_NOT_STICKY`.** Rationale: the service
  carries no per-start payload (no command to redeliver) and must come back after an OS kill;
  `START_STICKY` recreates it with a null intent, which the service handles by re-entering the
  `running` state. Alternatives rejected: `START_NOT_STICKY` defeats always-on;
  `START_REDELIVER_INTENT` implies a payload we do not have.
- **Single source of truth for state via a `StateFlow<ServiceState>`** exposed by the service (or a
  holder it owns), consumed by both the Compose UI and the notification builder. Rationale: avoids
  the notification and UI disagreeing about whether the service is running; `StateFlow` is the
  idiomatic observable for Compose (`collectAsStateWithLifecycle`) and is already on the classpath.
  Alternative (LiveData / broadcast) rejected: extra surface, less Compose-native.
- **`startForeground(...)` is called immediately in `onStartCommand` / `onCreate`.** Rationale: API
  26+ requires promotion to foreground within ~5s of `startForegroundService(...)` or the system
  throws; we transition `starting -> running` as part of that call so the window is never missed.
  On API 33+ we request `POST_NOTIFICATIONS`; if denied, the FGS still runs but its notification may
  be suppressed; we degrade gracefully and surface this in guidance.
- **Boot start via a manifest-declared `BroadcastReceiver` for `BOOT_COMPLETED`**, which calls
  `ContextCompat.startForegroundService(...)`. Rationale: locked requirement; the receiver does the
  minimal work of kicking the service and returns fast (no work on the broadcast thread).
- **Wake-lock discipline encapsulated in a single helper** (e.g. `withWorkWakeLock { ... }`) that
  acquires a `PARTIAL_WAKE_LOCK` with a timeout, runs the block, and releases in `finally`.
  Rationale: centralizing it makes "never hold while idle" enforceable and testable; the timeout is
  a backstop so a bug can never leak an indefinite lock. **Battery note:** an idle service holds no
  lock, so the CPU sleeps during Doze and idle drain is ~0; this is the whole point of choosing
  native over Termux's `termux-wake-lock`.
- **Battery-optimization exemption is detect-then-offer, never auto.** Rationale: locked
  privacy/UX stance: we read `PowerManager.isIgnoringBatteryOptimizations()` and, only on explicit
  user action, fire the `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` system intent. We do not use
  the direct "request" intent silently and never assume the grant. Alternative (auto-prompt at
  launch / silent grant) rejected: hostile UX and not how the OS allows it off a user gesture.
- **OEM guidance is informational and links out to dontkillmyapp.com per manufacturer.** Rationale:
  Samsung/Xiaomi/OnePlus/Huawei killers require per-vendor settings the app cannot toggle; we detect
  `Build.MANUFACTURER` to deep-link the right page and warn the steps can reset on firmware updates.

## Risks / Trade-offs

- [OEM process killers terminate the service despite foreground + exemption] → Mitigated by the
  device-setup-guidance surface (autostart/never-sleep steps) and `START_STICKY` re-creation; we
  explicitly warn the user that vendor settings can reset on firmware updates, so this is surfaced,
  not hidden.
- [A wake-lock leak would silently drain battery, the exact failure we are avoiding] → Mitigated by
  routing all locks through one helper that releases in `finally` and uses a hard timeout, plus a
  unit test asserting the idle service holds no lock and that the helper releases on both normal and
  exceptional paths.
- [Missing the 5s `startForeground` window throws `ForegroundServiceDidNotStartInTimeException`] →
  Mitigated by promoting to foreground synchronously on start before any other work.
- [`POST_NOTIFICATIONS` denied on API 33+ hides the persistent notification] → The service still
  runs; we degrade gracefully and the guidance surface explains how to re-enable it. No crash.
- [`BOOT_COMPLETED` is not delivered until the app has been launched at least once / on locked-boot
  devices it fires after unlock] → Acceptable; first launch starts the service anyway, and the
  receiver is best-effort. Verified on the VM in the on-device task.
- [Privacy] → This phase opens no socket and makes no network call, so it introduces no new data
  exposure; the only persistent artifact is the notification, which contains no user data.

## Migration Plan

Greenfield feature on a fresh branch; nothing to migrate. Rollback is reverting the branch; the
scaffold app remains launchable because the service is additive. The `specialUse` declaration and
new permissions are inert until the service is started.

## Open Questions

- None blocking. The notification's exact copy/actions and the guidance surface's visual placement
  are refined in the UI phase (6); this phase ships functional-but-minimal versions.
