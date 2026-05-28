## Why

SearchMob's embedded HTTP server binds to loopback (127.0.0.1) only, so it is reachable solely from
the device that runs it. Some users want to reach their on-device search server from another machine
on a trusted private network (for example a Tailscale network or a home LAN), the same way Tailscale
lets you share a node. This change adds a simple, explicit, default-off network mode that switches the
bind host from loopback to all interfaces, with a clear warning about the security trade-off because
the server has no authentication.

## What Changes

- Add a boolean preference `networkAccessEnabled`, default FALSE, persisted via the existing
  DataStore-backed `PreferencesStore` alongside the other plaintext-safe flags, exposed as an
  observable `Flow` plus a suspend setter on `PreferencesRepository`.
- Make the embedded server's bind host depend on the preference: loopback ("127.0.0.1") when off
  (unchanged from today), all interfaces ("0.0.0.0") when on. The foreground service observes the
  preference and rebinds (restarts) the embedded server on change without dropping the foreground
  service itself.
- Add a Settings toggle "Allow access from your network (advanced)", OFF by default. Turning it ON
  shows a Material3 warning dialog first; the preference is only persisted ON if the user confirms.
  Turning it OFF needs no confirmation.
- When network mode is ON, show the reachable `http://<device-LAN-IP>:<port>/` address in Settings
  with tap-to-copy (reusing the existing copy + snackbar pattern). The LAN IPv4 is found by
  enumerating `NetworkInterface` for a non-loopback, site-local IPv4 address; the no-network case is
  handled gracefully.
- Update `SECURITY.md` to reflect that network mode now exists, is off by default, is gated by an
  explicit warning, and that the server has no authentication when enabled.

## Capabilities

### New Capabilities
- `network-mode`: an opt-in, default-off toggle that exposes the local search server on the device's
  network (all interfaces) behind an explicit warning gate, with a copyable reachable address and a
  documented no-authentication security caveat.

### Modified Capabilities
<!-- None: this extends the loopback server's binding without changing its existing requirements. -->

## Impact

- New code: `bindHost(...)` selection + `boundHost`/`restart(...)` on `SearchServer`, a
  `NetworkAddress` helper (`lanIpv4Address` / `networkReachableUrl`), the `networkAccessEnabled`
  preference, the service preference observer, and the Settings toggle + warning dialog + address card.
- No new dangerous permissions: INTERNET already covers binding, and `NetworkInterface` is used
  instead of `WifiManager` to avoid `ACCESS_WIFI_STATE`.
- No new outbound network, no telemetry.

## Non-goals

- Authentication / access control for the server (out of scope; the warning documents the absence).
- TLS / HTTPS for the network-exposed server.
- Remote access beyond the device's own network (no port forwarding, no relay).
