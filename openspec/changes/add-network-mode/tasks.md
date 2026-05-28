## 1. Branch & preference

- [x] 1.1 Create branch `feat/add-network-mode` off `main`
- [x] 1.2 Add a `networkAccessEnabled` boolean preference (default FALSE) and key to `UserPreferences`
- [x] 1.3 Expose it as an observable `Flow` plus a suspend setter on `PreferencesRepository`
- [x] 1.4 Confirm no new dangerous permission is added (INTERNET already present; no `ACCESS_WIFI_STATE`)

## 2. Server binding

- [x] 2.1 Add a pure `bindHost(networkAccessEnabled)` helper (false -> 127.0.0.1, true -> 0.0.0.0)
- [x] 2.2 Make `SearchServer.start(...)` bind on the selected host and expose `boundHost`
- [x] 2.3 Add `SearchServer.restart(...)` to switch hosts cleanly
- [x] 2.4 Have the foreground service observe the preference and rebind without dropping the service

## 3. LAN address

- [x] 3.1 Implement `lanIpv4Address()` via `NetworkInterface` (non-loopback, site-local IPv4)
- [x] 3.2 Implement `networkReachableUrl(port, lanIp)` returning the `http://<ip>:<port>/` URL or null
- [x] 3.3 Handle the no-network case gracefully

## 4. Settings UI

- [x] 4.1 Add a "Allow access from your network (advanced)" toggle row, OFF by default
- [x] 4.2 Show a Material3 warning dialog before enabling; persist ON only on Confirm; Cancel leaves OFF
- [x] 4.3 Turning OFF requires no confirmation
- [x] 4.4 When ON, show the reachable address with tap-to-copy (reuse copy + snackbar), or a no-address state

## 5. Docs & tests

- [x] 5.1 Update `SECURITY.md` scope to reflect default-off, warning-gated network mode with no auth
- [x] 5.2 Unit-test bind-host selection (false -> 127.0.0.1, true -> 0.0.0.0)
- [x] 5.3 Unit-test the preference default (false) and round-trip
- [x] 5.4 Unit-test the warning gate (confirm enables, cancel does not, off persists immediately)

## 6. Verify

- [x] 6.1 `./gradlew --no-daemon ktlintFormat ktlintCheck lint test assembleDebug` BUILD SUCCESSFUL
- [x] 6.2 `openspec validate add-network-mode --strict`
