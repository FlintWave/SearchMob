# always-on-service Specification

## Purpose
TBD - created by archiving change add-foreground-service. Update Purpose after archive.
## Requirements
### Requirement: Service runs as a specialUse foreground service
The application SHALL run its always-on backbone as a foreground `Service` declared with
`android:foregroundServiceType="specialUse"`, the `FOREGROUND_SERVICE` and
`FOREGROUND_SERVICE_SPECIAL_USE` permissions, and the
`android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` `<property>` element in its `<service>` manifest
entry. The service SHALL promote itself to the foreground with a persistent notification within the
OS-required window after being started.

#### Scenario: Manifest declares specialUse with the required property
- **WHEN** the built APK manifest is inspected
- **THEN** the service entry declares `android:foregroundServiceType="specialUse"`
- **AND** the manifest requests `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE`
- **AND** the service entry includes the `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property

#### Scenario: Service promotes to foreground on start
- **WHEN** the service is started
- **THEN** it calls `startForeground` with a persistent notification within the OS-required window
- **AND** the system does not throw a did-not-start-in-time exception

### Requirement: Persistent notification on a dedicated channel
The service SHALL post a persistent (ongoing) notification on its own `NotificationChannel` while
running, and that notification SHALL provide an action to stop the service. The notification SHALL
contain no user search data.

#### Scenario: Notification is shown while running
- **WHEN** the service is in the running state on a device where notifications are permitted
- **THEN** an ongoing notification is displayed on the service's dedicated notification channel

#### Scenario: Notification stop action stops the service
- **WHEN** the user taps the stop action on the service notification
- **THEN** the service stops, leaves the foreground, and its notification is removed

### Requirement: Service starts on app open and on device boot
The service SHALL be started when the app is opened, and SHALL also be started on device boot via a
`BroadcastReceiver` registered for `ACTION_BOOT_COMPLETED`, for which the app declares the
`RECEIVE_BOOT_COMPLETED` permission. Because the service is of type `specialUse`, starting it from
`BOOT_COMPLETED` is permitted on Android 15 (API 35).

#### Scenario: App open starts the service
- **WHEN** the user opens the app and the service is not already running
- **THEN** the service is started and transitions to the running state

#### Scenario: Device boot starts the service
- **WHEN** the device finishes booting and broadcasts `ACTION_BOOT_COMPLETED`
- **THEN** the boot receiver starts the foreground service
- **AND** the service reaches the running state without the user re-opening the app

### Requirement: Service uses START_STICKY restart semantics
The service SHALL return `START_STICKY` from `onStartCommand` so that, if the OS kills the process
under memory pressure, the system recreates the service. On recreation with a null intent the
service SHALL re-enter the running state.

#### Scenario: Service is recreated after an OS kill
- **WHEN** the OS kills the service under memory pressure and later recreates it with a null intent
- **THEN** the service re-establishes its foreground notification and returns to the running state

### Requirement: Observable service state model
The application SHALL expose the service lifecycle as an observable state with the values
`stopped`, `starting`, and `running`, from a single source of truth that both the UI and the
notification read. The state SHALL transition `stopped -> starting -> running` on start and to
`stopped` on stop.

#### Scenario: State advances through start
- **WHEN** the service is started from the stopped state
- **THEN** the observable state transitions to `starting` and then to `running`

#### Scenario: State returns to stopped on stop
- **WHEN** the service is stopped from the running state
- **THEN** the observable state transitions to `stopped`

#### Scenario: UI observes the running state
- **WHEN** the app UI subscribes to the service state while the service is running
- **THEN** the UI receives the `running` value and reflects it

### Requirement: User can stop the service
The user SHALL be able to stop the service both from the persistent notification and from within
the app. Stopping SHALL remove the foreground notification and set the observable state to
`stopped`.

#### Scenario: Stop from the app
- **WHEN** the user invokes the stop control inside the app
- **THEN** the service stops, the notification is removed, and the state becomes `stopped`

### Requirement: No wake-lock is held while the service is idle
The service SHALL be event-driven and SHALL NOT hold any wake-lock while idle. A wake-lock MAY only
be acquired as a short, timed `PARTIAL_WAKE_LOCK` for the duration of an actual unit of work, and it
MUST be released in a `finally` block so that it is released on both normal and exceptional
completion. While idle, the service holds no wake-lock so the CPU may sleep, yielding near-zero idle
battery drain.

#### Scenario: Idle service holds no wake-lock
- **WHEN** the service is running but performing no unit of work
- **THEN** the service holds no wake-lock
- **AND** the CPU is allowed to sleep during device idle/Doze

#### Scenario: Work-bounded wake-lock is released on success
- **WHEN** a unit of work acquires the timed partial wake-lock and completes normally
- **THEN** the wake-lock is released in the `finally` block after the work finishes

#### Scenario: Work-bounded wake-lock is released on error
- **WHEN** a unit of work acquires the timed partial wake-lock and the work throws an exception
- **THEN** the wake-lock is still released in the `finally` block

