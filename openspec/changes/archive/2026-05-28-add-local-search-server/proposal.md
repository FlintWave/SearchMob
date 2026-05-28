## Why

SearchMob's whole value is being a search service you can actually *use* from the phone. Beyond
a Compose screen, it offers a localhost endpoint that the system browser and other on-device apps can
point at, exactly like a local SearXNG instance. The **always-on** foreground service exists
(phase 2) but has no HTTP surface; this change gives the service something to serve. It also
serves the **private** goal: the endpoint is loopback-only (no network exposure, no logging, no
persistence), and the **customizable** goal: a browser can add SearchMob as a first-class search
engine via an OpenSearch descriptor.

## What Changes

- Add an embedded **Ktor (CIO engine)** HTTP server **bound only to `127.0.0.1`** on a configurable
  port (default `8787`, with automatic fallback to the next free port if busy). The listener is
  loopback-only and MUST NOT bind any routable interface; LAN/network exposure is the deferred
  `add-network-mode` change. This is a stated security boundary.
- Make the **foreground service own the server lifecycle**: the server starts when the service
  starts and shuts down gracefully (drains in-flight requests, releases the socket) when the
  service stops.
- Add routes:
  - `GET /search?q=...`: server-rendered **HTML** results page suitable for a browser.
  - `GET /api/search?q=...&format=json`: **JSON** results API.
  - `GET /opensearch.xml`: an **OpenSearch description document** so a browser can add SearchMob
    as a search engine pointing at the localhost endpoint.
  - `GET /healthz`: lightweight liveness/status endpoint.
- Use a **stub/placeholder result provider** behind the routes for now. The real metasearch engine
  arrives in the later `add-metasearch-engine-core` change and will replace the stub behind the
  same HTTP contract; this change defines and tests that contract.
- **Battery discipline:** serving a request acquires a *short timed* wake-lock via the service and
  releases it in `finally`; idle = no wake-lock. Loopback traffic is not Doze-gated, so an idle
  listener costs ~0 battery.
- **Privacy:** the server logs nothing by default and persists no queries (storage is phase 5).

## Non-goals

- **No network/LAN/Tailscale exposure, no TLS, no auth.** The listener is loopback-only;
  network mode is the deferred `add-network-mode` change.
- **No real search engines / fan-out / ranking.** The routes serve a stub provider; engines are
  `add-metasearch-engine-core`.
- **No query persistence or history.** Store-nothing here; encrypted history is `add-encrypted-storage`.
- **No Compose results UI.** That is `add-search-ui-and-theming`. This change is the HTTP surface only.
- **No changes to the foreground-service contract** beyond hooking server start/stop and timed
  wake-lock acquisition into the existing lifecycle.

## Capabilities

### New Capabilities
- `local-http-server`: an embedded Ktor/CIO HTTP server bound only to loopback, whose lifecycle is
  owned by the foreground service, exposing search (HTML + JSON) and `/healthz` routes, with
  configurable port + fallback, graceful shutdown, per-request timed wake-lock, no logging, and an
  enforced no-network-exposure security boundary.
- `opensearch-integration`: a valid OpenSearch description document served at `/opensearch.xml`
  that a browser can add as a search engine and then query against the localhost endpoint.

### Modified Capabilities
<!-- None. The server consumes the existing foreground-service lifecycle without changing its spec. -->

## Impact

- New code (packages established in scaffold): `server/`, holding the Ktor server bootstrap, route handlers
  (`/search`, `/api/search`, `/healthz`, `/opensearch.xml`), HTML/JSON renderers, a
  `SearchResultProvider` interface plus a stub implementation, and the service hook that starts/stops
  the server and brokers the timed wake-lock.
- Modified code: the phase-2 foreground service gains start/stop calls into the server and exposes a
  short timed wake-lock acquire/release used per request.
- Dependencies introduced: **Ktor server core + CIO engine** (`io.ktor:ktor-server-core`,
  `io.ktor:ktor-server-cio`), Ktor HTML DSL or equivalent for server-rendered pages
  (`io.ktor:ktor-server-html-builder`), Ktor content negotiation + a JSON serializer
  (`io.ktor:ktor-server-content-negotiation` + `kotlinx.serialization`), and `ktor-server-test-host`
  for `testApplication` route tests.
- Permissions: none new. Reuses the existing foreground-service permissions; no `INTERNET`-facing
  bind (loopback only). No persisted data, no telemetry, no device identifiers.
