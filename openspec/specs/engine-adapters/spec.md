# engine-adapters Specification

## Purpose
TBD - created by archiving change add-metasearch-engine-core. Update Purpose after archive.
## Requirements
### Requirement: Engine Adapter SPI
The system SHALL define a single `EngineAdapter` interface that every search engine implements,
exposing a stable string `id`, a human-readable display name, the set of search categories it
supports, and a `suspend` function that accepts a normalized query plus per-request parameters and
returns parsed, normalized results. All adapter HTTP SHALL go through OkHttp and all HTML parsing
SHALL use Jsoup. Adding a new engine SHALL require only a new adapter implementation and SHALL NOT
require changes to the aggregator.

#### Scenario: An adapter exposes its identity and capabilities
- **WHEN** the engine registry enumerates a registered adapter
- **THEN** the adapter reports a stable non-empty `id`, a display name, and a non-empty set of
  supported categories
- **AND** the `id` is unique across all registered adapters

#### Scenario: An adapter returns normalized results for a query
- **WHEN** the aggregator invokes an adapter's `search` function with a query
- **THEN** the adapter performs its upstream request through the shared OkHttp client and returns a
  list of normalized results (title, url, snippet, source engine, position)

### Requirement: Per-engine timeout and fail-soft error handling
Each adapter invocation SHALL be bounded by a per-engine timeout, and an adapter SHALL NOT propagate
exceptions to the aggregator. A timeout, a network error, a non-success HTTP status, or unparseable
content SHALL be reported as a non-fatal engine failure that contributes no results, never as an
error that fails the overall query.

#### Scenario: Adapter exceeds its per-engine timeout
- **WHEN** an adapter's upstream request takes longer than the configured per-engine timeout
- **THEN** the invocation is cancelled at the timeout and recorded as a non-fatal failure
  contributing zero results
- **AND** the overall search still returns results from the other engines

#### Scenario: Adapter receives malformed or error response
- **WHEN** an engine returns a non-success status or content the adapter cannot parse
- **THEN** the adapter returns a failure result with no parsed entries rather than throwing
- **AND** the aggregator continues with the remaining engines

### Requirement: Default free engine set
The system SHALL ship adapters for the default free engines that require no API key: DuckDuckGo (via
its html/lite endpoint), Brave (html), Mojeek (html), Marginalia (free API), Mwmbl, and Wikipedia.
These engines SHALL be enabled by default and SHALL operate without any user-supplied credentials.

#### Scenario: Free engines work without any key
- **WHEN** the app performs a search with default configuration and no API keys supplied
- **THEN** each of DuckDuckGo, Brave, Mojeek, Marginalia, Mwmbl, and Wikipedia is queried and any
  that respond contribute results

#### Scenario: HTML engines parse their endpoint output
- **WHEN** a DuckDuckGo, Brave, or Mojeek adapter receives a saved sample HTML response
- **THEN** it extracts the result title, URL, and snippet for each entry via Jsoup

### Requirement: Google is excluded from the engine set
The system SHALL NOT include a Google scraping adapter or any adapter that queries Google search.
This exclusion is a permanent constraint, justified by Google's JavaScript wall, litigation risk,
and the risk of CAPTCHA-walling the user's own residential mobile IP; not a deferred feature.

#### Scenario: No Google adapter is registered
- **WHEN** the engine registry is enumerated
- **THEN** no registered adapter targets Google search

### Requirement: Optional bring-your-own-key adapters
The system SHALL provide optional adapters for the Brave Search API and the Mojeek API that activate
only when the user has supplied a corresponding API key. When a key is present, the adapter SHALL use
the injected key for that request; when absent, the adapter SHALL be inactive. This change SHALL only
consume an injected key and SHALL NOT itself persist or store keys.

#### Scenario: BYO-key adapter activates only with a key
- **WHEN** no API key is configured for an optional engine
- **THEN** that optional adapter is inactive and is not queried

#### Scenario: BYO-key adapter uses the injected key
- **WHEN** a valid API key is injected for the Brave or Mojeek API adapter
- **THEN** the adapter sends the request to the engine's API using that key and parses the JSON
  response into normalized results
- **AND** the corresponding free HTML adapter for that engine is not also queried, to avoid
  duplicate upstream load

### Requirement: Per-engine enable/disable configuration surface
The system SHALL expose a configuration surface allowing each engine to be individually enabled or
disabled, and the aggregator SHALL query only enabled engines. The toggle UI and persistence are out
of scope here; this change SHALL consume the configuration provided to it.

#### Scenario: Disabled engine is not queried
- **WHEN** an engine is marked disabled in the engine configuration
- **THEN** the aggregator does not invoke that engine's adapter for a search

#### Scenario: Enabled engines are queried
- **WHEN** a search runs with a set of enabled engines
- **THEN** every enabled engine's adapter is invoked exactly once for that query

### Requirement: Kagi is a bring-your-own-key engine
The application SHALL provide a Kagi Search API (v1) engine that is inactive until the user supplies a
Kagi API key. When active, it SHALL POST to `https://kagi.com/api/v1/search` with a JSON body
`{"query": "<terms>"}` and an `Authorization: Bearer <key>` header through the shared privacy-proxy
client, and parse the web results under `data.search[]` (each with `url`, `title`, `snippet`). It SHALL
be fail-soft like every other adapter and SHALL require no new Android permission. The key SHALL be
stored encrypted at rest like other BYO keys.

#### Scenario: Inactive without a key
- **WHEN** no Kagi key is configured
- **THEN** the Kagi engine contributes no results and Kagi is not contacted

#### Scenario: Parses Kagi search results
- **WHEN** Kagi returns a `data.search` array of result objects with `url`, `title`, and `snippet`
- **THEN** each becomes a result mapped to title, url, and snippet

### Requirement: BYO API keys apply on every search path
The application SHALL activate a bring-your-own-key engine on every search path once its key is set,
including both the in-app search and the browser-facing `/search` served by the foreground service.
The service SHALL build its engine registry from the per-engine enabled flags and the decrypted keys in
the encrypted store, so a configured Brave, Mojeek, or Kagi key takes effect without depending on the
in-app UI being open.

#### Scenario: A configured key activates the engine for the browser path
- **WHEN** the user has saved a valid BYO key for a keyed engine and runs a search through the browser `/search`
- **THEN** that engine contributes results to the browser search, not only to the in-app search

#### Scenario: No key leaves the keyed engine inactive everywhere
- **WHEN** no key is configured for a keyed engine
- **THEN** that engine stays inactive on both the in-app and browser search paths

