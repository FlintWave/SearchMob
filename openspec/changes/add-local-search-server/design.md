## Context

Phase 2 (`add-foreground-service`) stood up a `specialUse` foreground service that stays alive,
survives reboot, and is event-driven (no idle wake-lock). It currently does nothing useful — it has
no surface to serve. This change adds the HTTP surface SearchMob is *for*: a localhost endpoint that
the system browser and other on-device apps can target, mirroring how a user would point at a local
SearXNG instance.

The real metasearch engine (parallel engine fan-out, dedup, ranking, privacy proxying) is the next
phase (`add-metasearch-engine-core`). To keep this change shippable and testable on its own branch,
the routes are backed by a **stub result provider** that returns deterministic placeholder results.
The engine phase will swap the stub for the real implementation behind an unchanged HTTP contract,
so the route tests written here continue to hold.

## Goals / Non-Goals

**Goals:**
- An embedded Ktor (CIO) server **bound only to `127.0.0.1`**, started/stopped by the foreground
  service, with graceful shutdown.
- A configurable port (default `8787`) that falls back to the next free port when the default is busy,
  and reports the actual bound port (so the UI/OpenSearch descriptor can reference it).
- Four routes — `GET /search` (HTML), `GET /api/search` (JSON), `GET /opensearch.xml` (descriptor),
  `GET /healthz` (status) — backed by a `SearchResultProvider` interface with a stub implementation.
- A browser-addable OpenSearch descriptor pointing at the localhost endpoint.
- Per-request **short timed wake-lock** brokered through the service; no wake-lock while idle.
- No request logging and no query persistence.

**Non-Goals:**
- Any network/LAN/Tailscale binding, TLS, or authentication (deferred `add-network-mode`).
- Real search engines, fan-out, dedup, or ranking (`add-metasearch-engine-core`).
- Query persistence/history (`add-encrypted-storage`).
- Compose results UI (`add-search-ui-and-theming`).

## Decisions

- **Ktor CIO engine, not Netty/Jetty.** Rationale: CIO is the pure-Kotlin coroutine engine with the
  smallest footprint and no servlet-container baggage — appropriate for an embedded on-device server
  where binary size and battery matter. This is also the locked context decision. Alternatives
  (Netty/Jetty) rejected: heavier dependency trees, JVM-server oriented.
- **Bind explicitly to host `127.0.0.1`, never `0.0.0.0`.** Rationale: this is the privacy/security
  boundary — the listener must be reachable only from the device itself, so even on an untrusted
  Wi-Fi network nothing is exposed. The bind host is hard-coded to loopback in this change (not a
  user setting); opening it up is the explicit, opt-in `add-network-mode` change. A test asserts the
  server is unreachable on the device's routable IP.
- **Configurable port (default `8787`) with free-port fallback.** Rationale: another app may already
  hold the default port. On bind failure for the configured port, probe sequentially (or request an
  OS-assigned ephemeral port) and bind the first free one on loopback; expose the actual bound port so
  the OpenSearch descriptor and UI can use it. Alternative (fail hard if port busy) rejected: would
  make the always-on service brittle.
- **Lifecycle owned by the foreground service.** Rationale: the context locks "lifecycle owned by the
  foreground service." The service starts the server in its start path and calls graceful shutdown in
  its stop/`onDestroy` path. Graceful shutdown uses Ktor's stop with a short grace + timeout so
  in-flight loopback requests drain but shutdown can't hang the service teardown. Alternative
  (independent server lifecycle) rejected: risks a listener outliving or starting without the service.
- **`SearchResultProvider` interface + stub now, real engine later.** Rationale: lets this change ship
  and be tested independently while fixing the HTTP contract the engine phase plugs into. The stub
  returns a small set of deterministic placeholder results (echoing the query) so HTML/JSON rendering
  and OpenSearch flow are fully testable without network access.
- **HTML via Ktor's HTML DSL; JSON via content negotiation + kotlinx.serialization.** Rationale:
  server-rendered HTML keeps the browser path dependency-light and avoids shipping a template engine;
  kotlinx.serialization is the idiomatic Kotlin JSON choice and avoids reflection-based binders.
- **Per-request timed wake-lock brokered by the service.** Rationale: context mandates event-driven
  battery behavior — acquire a *short timed* `PARTIAL_WAKE_LOCK` for the duration of request handling
  and release it in `finally`. Idle = no wake-lock. Because loopback traffic is not Doze-gated, an
  idle listener costs ~0 battery, so this is the only wake-lock the server needs. The server calls a
  narrow service-provided hook (acquire/release) rather than touching `PowerManager` directly, keeping
  wake-lock ownership in the service.
- **No logging, no persistence by default.** Rationale: store-nothing privacy default. The server
  installs no access/request logging; query strings are held only in-memory for the duration of the
  request. Storage is a deliberate later opt-in (`add-encrypted-storage`).
- **OpenSearch descriptor references the live loopback origin and port.** Rationale: the `Url`/template
  in `opensearch.xml` must point at `http://127.0.0.1:<actual-port>/search?q={searchTerms}` so a
  browser that adds it queries the running server. The JSON variant template uses
  `/api/search?q={searchTerms}&format=json`.

## Risks / Trade-offs

- [A future careless change could switch the bind host to `0.0.0.0` and silently expose the service on
  the LAN] → Mitigation: hard-code the loopback host in this change, add a route/binding test asserting
  the server is **not** reachable on the device's non-loopback IP, and document loopback-only as a
  named security boundary in the spec so the regression is caught.
- [Graceful shutdown could hang service teardown if a request stalls] → Mitigation: bound the stop with
  a short grace period + hard timeout; loopback requests are local and fast, and the stub provider does
  no I/O, so normal drain is sub-second.
- [Browsers vary in how strictly they parse OpenSearch descriptors / whether they accept an `http://`
  localhost engine] → Mitigation: emit a spec-compliant `OpenSearchDescription` (correct namespace,
  `ShortName`, `Description`, `InputEncoding`, and `Url` templates) and validate XML well-formedness in
  a unit test; verify add-and-query manually against the system browser in the on-device task.
- [Port fallback means the OpenSearch descriptor's port can change between runs] → Mitigation: serve the
  descriptor dynamically from the actually-bound port rather than a hard-coded constant, so a
  re-added/refetched descriptor always matches the running server.
- [Android version restrictions] → No new permissions and no FGS changes; the bind is loopback-only so
  there is no `INTERNET`-exposed surface and no Doze interaction for incoming requests. The per-request
  timed wake-lock reuses the service's existing power-management approach.

## Open Questions

- None blocking. Exact default port (`8787`) and the wake-lock timeout duration are tunable constants;
  the chosen defaults are placeholders that can be adjusted without changing the contract. Whether to
  also serve a JSON OpenSearch `Url` template (alongside the HTML one) is included by default and can be
  trimmed if a target browser rejects it.
