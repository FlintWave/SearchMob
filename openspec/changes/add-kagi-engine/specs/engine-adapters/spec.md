## ADDED Requirements

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
