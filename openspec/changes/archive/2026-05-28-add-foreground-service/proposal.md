## Why

SearchMob's core promise is **always-on**: the search service must be reachable on the phone at any
moment without the user re-launching the app, surviving reboots and the OS's aggressive process
reaping. The only Android mechanism that keeps a process alive indefinitely on a modern (API 35)
off-Play app is a `specialUse` foreground service. This phase stands up that always-on backbone,
and does it without betraying the **battery** goal: the service is event-driven and holds no
wake-lock while idle, so the CPU sleeps and idle drain is ~0. (The HTTP server and search engines
attach to this service in later phases; this change only owns the service lifecycle and a
placeholder running state.)

## What Changes

- Add a **foreground `Service` of type `specialUse`** that runs `START_STICKY`, posts a persistent
  notification on its own `NotificationChannel`, and is the long-lived host later phases attach the
  Ktor server to. The manifest declares the `<service>` with
  `android:foregroundServiceType="specialUse"` plus the required
  `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" .../>` element.
- **Auto-start the service** when the app is opened and on device boot via a `BroadcastReceiver`
  for `ACTION_BOOT_COMPLETED`. (On Android 15 / API 35, boot-started `specialUse` services are
  permitted, unlike `dataSync` which is restricted at boot.)
- Add an **observable status/state model** (`stopped` / `starting` / `running`) the UI and
  notification can observe (e.g. a `StateFlow`), and the ability to **stop the service** from both
  the notification action and the app.
- Enforce **battery discipline**: the service is event-driven and NEVER holds a wake-lock while
  idle. Only a short, timed `PARTIAL_WAKE_LOCK` may be acquired for the duration of an actual unit
  of work and MUST be released in a `finally` block. (No real work units exist yet in this phase;
  the discipline and helper are established here for later phases to use.)
- Add a **battery-optimization exemption flow**: detect `PowerManager.isIgnoringBatteryOptimizations()`
  and, if not exempt, offer the user the `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` system
  prompt. The exemption is never auto-granted; it is always a user-initiated choice.
- Add an **OEM autostart / never-sleep guidance surface** that links to dontkillmyapp.com per
  manufacturer (Samsung, Xiaomi, OnePlus, Huawei) and warns the user these settings can reset on
  firmware updates.

## Non-goals

- The embedded Ktor HTTP server, localhost binding, request pipeline, and OpenSearch descriptor;
  deferred to `add-local-search-server` (phase 3).
- Any actual search, engine fan-out, or network I/O; deferred to `add-metasearch-engine-core`
  (phase 4). This change performs no network requests.
- Any persisted/encrypted storage or search history; deferred to `add-encrypted-storage`
  (phase 5). The state model here is in-memory only.
- Rich settings/theming UI for the service controls. This phase exposes only the minimal controls
  (start/stop, exemption prompt, guidance link) needed to manage the lifecycle; the polished UI is
  `add-search-ui-and-theming` (phase 6).
- LAN / network-mode service exposure; deferred to `add-network-mode` (phase 8).
- Programmatically forcing or working around OEM autostart killers. Only user-facing guidance is
  in scope; auto-granting exemptions is explicitly out of scope.

## Capabilities

### New Capabilities
- `always-on-service`: the `specialUse` foreground service lifecycle, covering start on app-open and on
  boot, `START_STICKY` restart semantics, persistent notification, an observable
  stopped/starting/running state, user-initiated stop, and the no-idle-wake-lock battery
  discipline.
- `device-setup-guidance`: the user-facing reliability setup, covering the battery-optimization exemption
  detection and prompt, and the per-OEM autostart/never-sleep guidance surface.

### Modified Capabilities
<!-- None. This change only introduces new capabilities. -->

## Impact

- **New permissions:** `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`,
  `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, and `POST_NOTIFICATIONS`
  (runtime, API 33+, for the persistent notification).
- **New manifest entries:** the `<service android:foregroundServiceType="specialUse">` with its
  `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE">`, and a
  boot `<receiver>` listening for `android.intent.action.BOOT_COMPLETED`.
- **New code (in the `service/` package established by the scaffold):** the foreground service, a
  boot `BroadcastReceiver`, the service state model (`StateFlow`-backed), a timed wake-lock helper,
  a notification builder/channel, and a battery-optimization/OEM-guidance surface plus the Compose
  controls to drive them.
- **Dependencies introduced:** none beyond the AndroidX/Compose/coroutines stack already pulled in
  by the scaffold (the state model uses `kotlinx.coroutines` `StateFlow`).
- **No network calls, no engines, no persisted data** in this phase.
