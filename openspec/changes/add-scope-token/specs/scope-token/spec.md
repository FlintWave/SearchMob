## ADDED Requirements

### Requirement: Inline scope token selects a saved scope for one served search

The system SHALL parse a served-search query for a whitespace-delimited token beginning with `+`.
When a token matches one of the user's defined scopes, the system SHALL apply that scope to that
single request and SHALL remove the token from the query sent to the engines. The match SHALL be
case-insensitive against the scope name's first word, falling back to a normalized (lowercased,
alphanumeric-only) match of the whole scope name when no first-word match is found. When more than
one token matches, the first matching token in the query SHALL win and exactly one scope SHALL be
applied.

#### Scenario: A matching token applies its scope

- **WHEN** a request to `/search` or `/api/search` carries `mechanical keyboards +research` and a
  scope whose name begins with "Research" exists
- **THEN** that scope filters the results for this request and the engines receive
  `mechanical keyboards` with the token removed

#### Scenario: First matching token wins

- **WHEN** the query contains two tokens that each match a different scope
- **THEN** only the scope matched by the earlier token is applied and only that token is removed

### Requirement: Unmatched served tokens stay in the query

The system SHALL leave a `+word` token in the served query unchanged when it does not match any
defined scope, so ordinary `+term` input is preserved.

#### Scenario: An unknown token is treated as a search term

- **WHEN** a request carries `rust +tokio` and no scope matches "tokio"
- **THEN** no scope is applied and the engines receive `rust +tokio` unchanged

### Requirement: Inline served scope is per-request and never persisted

The system SHALL apply the token-selected scope to the current request only and SHALL NOT change the
saved active scope. A later request without a token SHALL use the saved scope (or none) as before.
The original query text SHALL be echoed back (in the search box and the JSON `query`) so the token
round-trips on a re-search.

#### Scenario: The saved scope is untouched and the token round-trips

- **WHEN** a request runs with a `+name` token
- **THEN** the saved active scope is unchanged, and the echoed query keeps the original text
  including the token
