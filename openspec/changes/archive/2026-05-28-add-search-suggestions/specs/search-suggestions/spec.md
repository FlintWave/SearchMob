## ADDED Requirements

### Requirement: A browser-consumable OpenSearch suggestions endpoint
The application SHALL serve `GET /suggest?q=<term>` on the embedded HTTP server, returning OpenSearch
Suggestions JSON: the two-element array `["<echoed query>", ["s1", "s2", ...]]` with content type
`application/x-suggestions+json`. The response body SHALL be built so the browser-controlled query and
every suggestion are correctly JSON-escaped. The `q` parameter SHALL be clamped to the same maximum
length as the search routes. A blank or empty `q` SHALL return `["", []]` without consulting any
suggestion source. The total number of suggestions returned SHALL be capped.

#### Scenario: Returns the OpenSearch suggestions shape
- **WHEN** a client requests `/suggest?q=kot` and the source offers "kotlin" and "kotlinx"
- **THEN** the response is `["kot",["kotlin","kotlinx"]]` with content type `application/x-suggestions+json`

#### Scenario: Blank query returns the empty pair
- **WHEN** a client requests `/suggest` with no `q`, an empty `q`, or a whitespace-only `q`
- **THEN** the response is `["", []]` and no suggestion source is consulted

#### Scenario: Query is length-capped
- **WHEN** a client requests `/suggest` with a `q` longer than the maximum query length
- **THEN** the term handed to the suggestion source is truncated to that maximum

#### Scenario: Special characters are escaped
- **WHEN** the query or a suggestion contains a quote or backslash
- **THEN** those characters are correctly escaped in the JSON response

### Requirement: Local history is an always-available suggestion source
The application SHALL provide suggestions from the user's opt-in, encrypted, on-device search history:
distinct past queries that prefix-match the term case-insensitively, ordered most-recent first, capped
to the requested limit. This source SHALL require no network. When history is disabled, locked, or
empty, or the term is blank, it SHALL return an empty list and SHALL NOT throw.

#### Scenario: Prefix match, case-insensitive, distinct, most-recent first
- **WHEN** history contains "kotlin coroutines", "kotlin flow", "KOTLIN flow", and "Kotlin sequences"
- **THEN** suggesting for "kot" returns "Kotlin sequences", "KOTLIN flow", "kotlin coroutines" (the two
  "flow" entries collapse to one, newest first)

#### Scenario: Limit respected
- **WHEN** more matching past queries exist than the requested limit
- **THEN** only the most-recent up-to-limit distinct queries are returned

#### Scenario: Empty when history is unavailable
- **WHEN** history is disabled, locked, or empty
- **THEN** the local source returns an empty list without throwing

### Requirement: Upstream web autocomplete is opt-in and off by default
The application SHALL provide a single boolean preference `upstreamSuggestionsEnabled` that controls
whether a web autocomplete source is consulted. It SHALL default to OFF (false) and SHALL be persisted
so it survives app restarts and reboots. When the preference is OFF, the upstream source SHALL NOT be
contacted and nothing about the partial query SHALL leave the device. When ON, the application SHALL
fetch DuckDuckGo's autocomplete endpoint through the shared privacy proxy (no cookies, stripped
headers, rotated User-Agent) using a short timeout and a bounded body read, and SHALL return an empty
list on any error or timeout so typing never hangs.

#### Scenario: Default is off
- **WHEN** the app is launched for the first time (no stored value)
- **THEN** upstream suggestions are off and only local history is used

#### Scenario: Upstream not contacted when off
- **WHEN** the preference is off and the user types
- **THEN** the upstream provider is never queried and nothing is sent off-device

#### Scenario: Parses upstream autocomplete when on
- **WHEN** the preference is on and the upstream returns `["kot", ["kotlin", "kotlin flow"]]`
- **THEN** the suggestions "kotlin" and "kotlin flow" are offered

#### Scenario: Fail-soft on error or timeout
- **WHEN** the upstream returns an error, malformed JSON, or does not respond within the short timeout
- **THEN** the upstream source returns an empty list rather than throwing or hanging

### Requirement: Local and upstream suggestions are merged local-first
The application SHALL merge local-history suggestions and (when enabled) upstream suggestions
local-first, SHALL de-duplicate them case-insensitively (keeping the local casing on a collision), and
SHALL cap the merged total. The local source SHALL always be queried; the upstream source SHALL be
queried only when the opt-in preference is on.

#### Scenario: Local first with case-insensitive de-duplication
- **WHEN** local offers "kotlin flow" and (enabled) upstream offers "Kotlin Flow" and "kotlin multiplatform"
- **THEN** the merged result lists "kotlin flow" first and drops the upstream case-insensitive duplicate

#### Scenario: Total is capped
- **WHEN** the combined local and upstream lists exceed the cap
- **THEN** only the first up-to-cap merged suggestions are returned

### Requirement: The suggestions endpoint is advertised in the OpenSearch descriptor
The OpenSearch descriptor SHALL include a Url entry of type `application/x-suggestions+json` whose
template points at `/suggest?q={searchTerms}` on the bound origin, alongside the existing html and json
Url entries, so a browser that adds SearchMob can offer address-bar autocomplete.

#### Scenario: Descriptor advertises the suggestions URL
- **WHEN** a browser fetches the OpenSearch descriptor
- **THEN** it contains a `application/x-suggestions+json` Url template targeting `/suggest?q={searchTerms}`
  on the bound port

### Requirement: The opt-in surfaces a clear privacy trade-off in Settings
Settings SHALL present a "Suggestions" section with a "Live suggestions from the web" toggle that is
OFF by default, accompanied by a subtitle explaining that, when on, what the user types is sent to
DuckDuckGo's suggestion service through the privacy proxy as they type, and that off keeps suggestions
to local history only. This opt-in SHALL NOT require a blocking confirmation dialog.

#### Scenario: Toggle and subtitle are shown
- **WHEN** the user opens Settings
- **THEN** a "Live suggestions from the web" toggle is shown OFF by default with a subtitle stating the
  trade-off

#### Scenario: Toggling persists immediately without a dialog
- **WHEN** the user turns the toggle on or off
- **THEN** the preference is persisted immediately with no blocking warning dialog
