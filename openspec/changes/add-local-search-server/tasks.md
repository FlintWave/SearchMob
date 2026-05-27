## 1. Branch & dependencies

- [x] 1.1 Create branch `feat/add-local-search-server` off `main`
- [x] 1.2 Add Ktor versions to `gradle/libs.versions.toml`: `ktor-server-core`, `ktor-server-cio`,
  `ktor-server-html-builder`, `ktor-server-content-negotiation`, `ktor-serialization-kotlinx-json`
  (+ `kotlinx-serialization` plugin), and test-only `ktor-server-test-host`
- [x] 1.3 Apply the Kotlin serialization plugin and add the Ktor server dependencies to
  `app/build.gradle.kts` (test-host as `testImplementation`)

## 2. Result provider contract (stub)

- [x] 2.1 Define `SearchResult` data model (at least title + URL, plus snippet/source fields) in `server/`
- [x] 2.2 Define the `SearchResultProvider` interface (`suspend fun search(query: String): List<SearchResult>`)
- [x] 2.3 Implement `StubSearchResultProvider` returning deterministic placeholder results that echo the query

## 3. Server bootstrap (loopback-only, configurable port)

- [x] 3.1 Implement a Ktor CIO server factory that binds host `127.0.0.1` (never `0.0.0.0`) on a
  configurable port (default `8787`)
- [x] 3.2 Implement port-fallback: on bind failure for the configured port, bind the next available
  loopback port and expose the actually-bound port
- [x] 3.3 Install content negotiation (kotlinx JSON); install NO request/access logging (privacy default)
- [x] 3.4 Implement graceful shutdown (bounded grace period + hard timeout) that releases the socket

## 4. Routes

- [x] 4.1 `GET /healthz` — return a 2xx status/liveness response
- [x] 4.2 `GET /search?q=...` — server-rendered HTML results page (Ktor HTML DSL) from the provider;
  handle missing `q` with a valid empty/prompt page
- [x] 4.3 `GET /api/search?q=...&format=json` — JSON results (Content-Type `application/json`) from the provider
- [x] 4.4 `GET /opensearch.xml` — serve a spec-compliant OpenSearch descriptor
  (`application/opensearchdescription+xml`) with `ShortName`/`Description`/`InputEncoding` and `Url`
  template(s) built from the actual bound loopback origin + port (HTML `/search?q={searchTerms}`,
  JSON `/api/search?q={searchTerms}&format=json`)

## 5. Service integration & battery discipline

- [x] 5.1 Add a narrow service-provided hook to acquire/release a short timed wake-lock; wrap request
  handling so the wake-lock is acquired before handling and released in `finally`
- [x] 5.2 Start the server from the foreground service's start path; call graceful shutdown from its
  stop/`onDestroy` path
- [x] 5.3 Confirm no wake-lock is held while idle (server running, no request in flight)

## 6. Tests

- [x] 6.1 `testApplication` route tests: `/healthz` 2xx; `/search` returns `text/html` with stub
  results and handles missing `q`; `/api/search?format=json` returns `application/json` with the
  query + results array
- [x] 6.2 Binding test: assert the server is reachable on `127.0.0.1` but NOT on the device's
  routable (non-loopback) IP on the same port
- [x] 6.3 Port-fallback test: with the default port occupied, assert the server binds another port and
  reports it
- [x] 6.4 Graceful-shutdown test: assert in-flight request drains and the port is released within the
  bounded timeout
- [x] 6.5 OpenSearch XML test: assert `/opensearch.xml` is well-formed, uses the OpenSearch namespace,
  includes required elements, and its `Url` template references the actual bound loopback port
- [x] 6.6 Provider-substitution test: a fake provider injected into the server is served by `/search`
  and `/api/search` without route changes

## 7. On-device / VM verification

- [ ] 7.1 Install the build on the emulator/VM; start the service and confirm `GET /healthz` and
  `GET /search?q=test` succeed from the device (e.g. adb shell curl to `127.0.0.1:<port>`)
- [ ] 7.2 Point the system browser at `http://127.0.0.1:<port>/opensearch.xml`, add SearchMob as a
  search engine via OpenSearch, run a query, and confirm the HTML results page (stub results) renders
- [ ] 7.3 Confirm the listener is loopback-only from the device (no LAN exposure) and that stopping the
  service stops the server (port released)

## 8. Validate, review & merge

- [x] 8.1 Run `openspec validate add-local-search-server --strict` and fix any issues
- [x] 8.2 Run `./gradlew lint test` (and the route/integration tests) and confirm green
- [ ] 8.3 Open PR, confirm CI green, merge to `main`
- [ ] 8.4 `openspec archive add-local-search-server`
