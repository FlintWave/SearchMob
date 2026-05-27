## 1. Branch & permissions/manifest

- [ ] 1.1 Create branch `feat/add-foreground-service` off `main`
- [ ] 1.2 Add permissions to `AndroidManifest.xml`: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `POST_NOTIFICATIONS`
- [ ] 1.3 Declare the `<service>` with `android:foregroundServiceType="specialUse"` and the nested `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="..."/>` element
- [ ] 1.4 Declare the boot `<receiver>` (exported, `BOOT_COMPLETED` intent filter)

## 2. Service state model

- [ ] 2.1 Define a `ServiceState` enum/sealed type (`Stopped`, `Starting`, `Running`) in the `service/` package
- [ ] 2.2 Implement a single-source-of-truth holder exposing a `StateFlow<ServiceState>` for UI and notification to observe
- [ ] 2.3 Implement state transitions: `Stopped -> Starting -> Running` on start, `-> Stopped` on stop

## 3. Foreground service

- [ ] 3.1 Implement the foreground `Service` in `service/`: create the dedicated `NotificationChannel`, build the ongoing notification (with a stop action), and `startForeground(...)` synchronously on start
- [ ] 3.2 Return `START_STICKY` from `onStartCommand` and handle null-intent recreation by re-entering the `Running` state
- [ ] 3.3 Implement stop handling (from notification action and from in-app control): leave foreground, remove notification, set state `Stopped`
- [ ] 3.4 Wire the service to publish to the `StateFlow` holder on every transition

## 4. Battery discipline (wake-lock helper)

- [ ] 4.1 Implement a `withWorkWakeLock { ... }` helper that acquires a timed `PARTIAL_WAKE_LOCK`, runs the block, and releases in `finally`
- [ ] 4.2 Ensure the idle service acquires no wake-lock (helper is only invoked around a unit of work; no work units exist yet this phase)

## 5. Auto-start (app open + boot)

- [ ] 5.1 Start the service via `ContextCompat.startForegroundService(...)` when the app is opened (if not already running)
- [ ] 5.2 Implement the boot `BroadcastReceiver` to start the foreground service on `ACTION_BOOT_COMPLETED`, doing minimal work on the broadcast thread

## 6. Device-setup guidance

- [ ] 6.1 Implement battery-optimization detection via `PowerManager.isIgnoringBatteryOptimizations()` and surface exempt/not-exempt state to the UI
- [ ] 6.2 Implement the user-initiated exemption prompt firing `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` only on explicit user action (never auto)
- [ ] 6.3 Implement the OEM autostart/never-sleep guidance surface linking to dontkillmyapp.com per `Build.MANUFACTURER` (Samsung/Xiaomi/OnePlus/Huawei + generic fallback), with the firmware-reset warning
- [ ] 6.4 Add minimal Compose controls (start/stop, exemption prompt, guidance link) observing the service `StateFlow`

## 7. Unit tests

- [ ] 7.1 Unit-test the state machine: `Stopped -> Starting -> Running` on start and `-> Stopped` on stop
- [ ] 7.2 Unit-test the wake-lock helper releases on both normal completion and on thrown exception, and that an idle path acquires no lock
- [ ] 7.3 Unit-test the OEM guidance mapping selects the correct dontkillmyapp.com target per manufacturer and falls back generically for unlisted vendors

## 8. On-device / VM verification

- [ ] 8.1 Build and install the debug APK on the Android VM/emulator; grant `POST_NOTIFICATIONS`
- [ ] 8.2 Verify the persistent notification shows and the service stays running; confirm the stop action stops it from the notification and from the app
- [ ] 8.3 Reboot the VM/device and confirm the service auto-starts from `BOOT_COMPLETED` without re-opening the app
- [ ] 8.4 Verify with `adb shell dumpsys power` that the idle running service holds no wake-lock (~0 idle CPU); confirm the battery-optimization prompt appears only on the user action
- [ ] 8.5 Confirm the OEM guidance surface opens the correct dontkillmyapp.com page and shows the firmware-reset warning

## 9. Validate & merge

- [ ] 9.1 Run `openspec validate add-foreground-service --strict` and fix any issues
- [ ] 9.2 Open PR against `main`, confirm CI green
- [ ] 9.3 Merge to `main`, then run `openspec archive add-foreground-service`
