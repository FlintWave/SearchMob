## ADDED Requirements

### Requirement: Server binds only to loopback
The embedded HTTP server SHALL bind exclusively to the loopback address `127.0.0.1` and MUST NOT
listen on any routable network interface (e.g. `0.0.0.0` or the device's LAN IP). Reachability from
outside the device is a deliberate, separate, opt-in concern (the deferred `add-network-mode`
change); until then loopback-only binding is an enforced security boundary.

#### Scenario: Reachable on loopback
- **WHEN** a client on the device issues a request to `http://127.0.0.1:<port>/healthz` while the
  server is running
- **THEN** the request succeeds and returns a status response

#### Scenario: Not reachable on a routable interface
- **WHEN** a client attempts to connect to the server using the device's non-loopback (LAN) IP
  address on the same port
- **THEN** the connection is refused or not served, because the listener is bound only to `127.0.0.1`

### Requirement: Server lifecycle is owned by the foreground service
The HTTP server SHALL be started by the foreground service when the service starts and SHALL be shut
down when the service stops. The server MUST NOT run independently of the service.

#### Scenario: Server starts with the service
- **WHEN** the foreground service starts
- **THEN** the HTTP server begins listening on loopback and `GET /healthz` succeeds

#### Scenario: Server stops with the service
- **WHEN** the foreground service is stopped or destroyed
- **THEN** the HTTP server stops listening and the loopback port is released

### Requirement: Server shuts down gracefully
On stop, the server SHALL shut down gracefully — allowing in-flight requests a bounded grace period
to complete — and MUST release its listening socket. Shutdown SHALL be bounded by a hard timeout so
it cannot hang service teardown.

#### Scenario: In-flight request drains on shutdown
- **WHEN** the server is asked to stop while a request is being handled
- **THEN** the in-flight request is allowed to complete within the grace period before the socket closes

#### Scenario: Shutdown completes within a bounded time
- **WHEN** the server is asked to stop
- **THEN** shutdown completes within its configured grace-plus-timeout window and the port becomes free

### Requirement: Configurable port with fallback
The server SHALL listen on a configurable TCP port (default `8787`). When the configured port is
already in use, the server SHALL fall back to an available port rather than failing to start, and
SHALL expose the actually-bound port so other components (UI, OpenSearch descriptor) can reference it.

#### Scenario: Default port is used when free
- **WHEN** the default port is available and the server starts
- **THEN** the server binds the default port and reports it as the bound port

#### Scenario: Falls back when the configured port is busy
- **WHEN** the configured port is already in use at start time
- **THEN** the server binds an available port instead of failing, and reports that bound port

### Requirement: Search route returns a server-rendered HTML results page
The server SHALL expose `GET /search?q=<query>` that returns a server-rendered HTML results page
suitable for display in a browser. The page SHALL present results obtained from the configured
result provider for the given query.

#### Scenario: HTML results page for a query
- **WHEN** a browser requests `GET /search?q=privacy`
- **THEN** the server responds `200` with `Content-Type: text/html` and an HTML page listing the
  result provider's results for `privacy`

#### Scenario: Missing query is handled
- **WHEN** a browser requests `GET /search` with no `q` parameter
- **THEN** the server responds with a valid HTML page (e.g. an empty-results or prompt page) rather
  than an error or crash

### Requirement: JSON search API returns structured results
The server SHALL expose `GET /api/search?q=<query>&format=json` that returns the result provider's
results as a structured JSON document with `Content-Type: application/json`.

#### Scenario: JSON results for a query
- **WHEN** a client requests `GET /api/search?q=privacy&format=json`
- **THEN** the server responds `200` with `Content-Type: application/json` and a JSON body containing
  the query and an array of result objects (each with at least a title and URL)

### Requirement: Health endpoint reports server status
The server SHALL expose a lightweight `GET /healthz` endpoint that returns a successful status
response when the server is running, suitable for liveness checks.

#### Scenario: Health check while running
- **WHEN** a client requests `GET /healthz` while the server is listening
- **THEN** the server responds with a `2xx` status indicating it is healthy

### Requirement: Per-request timed wake-lock with no idle wake-lock
While handling a request, the server SHALL acquire a short timed wake-lock through the foreground
service and SHALL release it when the request completes (including on error). While idle (no request
in flight) the server MUST hold no wake-lock.

#### Scenario: Wake-lock held only during request handling
- **WHEN** the server handles a request
- **THEN** a short timed wake-lock is acquired before handling and released after the response is sent,
  including when handling fails

#### Scenario: No wake-lock while idle
- **WHEN** the server is running but no request is in flight
- **THEN** no wake-lock attributable to the server is held

### Requirement: Server logs nothing and persists no queries by default
By default the server SHALL NOT write request/access logs and SHALL NOT persist query strings or
results to any durable store. Query data MAY exist only in memory for the duration of a request.

#### Scenario: No access logging by default
- **WHEN** requests are served
- **THEN** no request/access log entries containing query content are written by default

#### Scenario: No query persistence
- **WHEN** a search request is handled and completes
- **THEN** no query string or result is written to durable storage by the server

### Requirement: Search routes are backed by a replaceable result provider
The search routes SHALL obtain results from a `SearchResultProvider` abstraction. In this change a
stub provider returning deterministic placeholder results SHALL satisfy that abstraction; the HTTP
contract MUST NOT depend on the stub so a later change can substitute the real metasearch engine
without changing the routes.

#### Scenario: Stub provider serves placeholder results
- **WHEN** a search request is handled and the stub provider is configured
- **THEN** the route returns the deterministic placeholder results from the provider in both HTML and
  JSON forms

#### Scenario: Provider is substitutable behind the routes
- **WHEN** a different implementation of the result provider is supplied to the server
- **THEN** the same `/search` and `/api/search` routes serve that implementation's results without
  route changes
