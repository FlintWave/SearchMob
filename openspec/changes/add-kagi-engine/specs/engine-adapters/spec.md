## ADDED Requirements

### Requirement: Kagi is a bring-your-own-key engine
The application SHALL provide a Kagi Search API engine that is inactive until the user supplies a Kagi
API token. When active, it SHALL query `https://kagi.com/api/v0/search` with the query and an
`Authorization: Bot <token>` header through the shared privacy-proxy client, parse the response `data`
array, keep search-result objects (`t == 0`) as title/url/snippet, and ignore non-result objects (such
as related searches). It SHALL be fail-soft like every other adapter and SHALL require no new Android
permission. The token SHALL be stored encrypted at rest like other BYO keys.

#### Scenario: Inactive without a key
- **WHEN** no Kagi token is configured
- **THEN** the Kagi engine contributes no results and Kagi is not contacted

#### Scenario: Parses Kagi search results
- **WHEN** Kagi returns a `data` array containing result objects (`t == 0`) and a related-search object (`t == 1`)
- **THEN** only the result objects become results, mapped to title, url, and snippet

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
