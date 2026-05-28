## ADDED Requirements

### Requirement: Server exposes a valid OpenSearch description document
The server SHALL expose `GET /opensearch.xml` returning a well-formed, spec-compliant OpenSearch
description document with `Content-Type: application/opensearchdescription+xml`. The document SHALL
include at least a `ShortName`, a `Description`, an `InputEncoding`, and a search `Url` template.

#### Scenario: Descriptor is served and well-formed
- **WHEN** a client requests `GET /opensearch.xml`
- **THEN** the server responds `200` with an `application/opensearchdescription+xml` body that is
  well-formed XML using the OpenSearch description namespace and includes `ShortName`, `Description`,
  `InputEncoding`, and at least one `Url` element

#### Scenario: Descriptor identifies SearchMob
- **WHEN** the OpenSearch descriptor is parsed
- **THEN** its `ShortName` identifies SearchMob so the browser presents it as the SearchMob search engine

### Requirement: OpenSearch Url templates target the running loopback endpoint
The descriptor's search `Url` template(s) SHALL point at the running server's loopback origin and
actual bound port, using `{searchTerms}` for the query. An HTML results template SHALL target
`/search?q={searchTerms}` and, when a JSON template is provided, it SHALL target
`/api/search?q={searchTerms}&format=json`. The descriptor MUST reflect the actually-bound port, not a
hard-coded value, so it stays correct when port fallback occurs.

#### Scenario: HTML Url template points at /search on loopback
- **WHEN** the descriptor is fetched while the server is bound on `127.0.0.1:<port>`
- **THEN** it contains an HTML-type `Url` template equal to
  `http://127.0.0.1:<port>/search?q={searchTerms}`

#### Scenario: Url template reflects the actual bound port after fallback
- **WHEN** the server fell back to a non-default port and the descriptor is fetched
- **THEN** the `Url` template(s) reference that actual bound port, not the default port

### Requirement: Browser can add SearchMob and run a query
Using the served descriptor, a browser SHALL be able to add SearchMob as a search engine and execute
a query, with the resulting request reaching the server's `/search` endpoint and returning the
result provider's results.

#### Scenario: Add via OpenSearch and query
- **WHEN** a user adds SearchMob in the browser from `http://127.0.0.1:<port>/opensearch.xml` and then
  searches for a term using that engine
- **THEN** the browser issues `GET /search?q=<term>` to the loopback endpoint and renders the HTML
  results page returned by the server
