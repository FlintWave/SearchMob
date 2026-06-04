## Why

Scopes (lenses) are a saved, sticky selection: the user picks one in the app or the served scope
selector and it filters results until they change it. But the search pages opened in a browser — and
any script hitting the served `/search` / `/api/search` endpoints — have no ergonomic way to say
"run *this one* search through a scope" without first flipping the persistent selector. For one-off
use, the natural place to put the scope is in the query itself.

This adds an inline, additive scope token on the served endpoints: appending a `+name` word to a
query applies the matching scope to that single search only, without changing the saved selection.
It mirrors the desktop `add-scope-token` change so both apps behave identically, and it feels like
the `+term` operator users already expect from a search box: an unmatched `+word` is left in the
query as an ordinary term.

## What Changes

- Parse a served-search query for a whitespace-delimited `+token`. Match it (case-insensitively)
  against the user's defined scopes by the scope name's first word, falling back to a normalized
  full-name match. The first matching token wins and is stripped from the query; the matched scope is
  applied to that request only and is never persisted.
- An unmatched `+word` is left untouched so ordinary `+must-have` style terms keep working.
- Wire the parser into the served `/search` and `/api/search` routes. The engines, contextual
  summary, and correction run on the cleaned query; the original text is echoed in the box and in the
  JSON `query` so the token round-trips on a re-search.

## Capabilities

### New Capabilities
- `scope-token`: an inline `+name` query token on the served endpoints that applies a matching saved
  scope to a single search, additive and non-persistent, leaving unmatched tokens in the query, and
  matching the desktop behaviour.

### Modified Capabilities
<!-- None in contract. The saved scope selector (result-personalization) keeps its meaning; the
inline token is a transient, per-request overlay that does not touch the persisted active scope. -->

## Non-goals

- Changing the persisted scope: the token never writes the active scope; the sticky selector in the
  app and served UI is unaffected.
- The in-app search box: this change covers the served endpoints (parity with desktop's CLI + served
  surfaces). The Compose app keeps its scope selector.
- Inventing scopes from a token: a token only selects an already-defined scope; an unknown token
  stays a search term.
- Operator syntax beyond a single leading-`+` word per match.

## Impact

- New: `engine/rank/ScopeToken.kt` — the pure `ScopeToken.parse(query, rules)` parser.
- Modified: `server/SearchServer.kt` (`/search` + `/api/search` parse the query and search the
  cleaned text, echoing the original); `server/SearchResultProvider.kt` +
  `engine/MetaSearchResultProvider.kt` (an `activeLensOverride` that applies `rules.copy(activeLens=…)`
  transiently for the request).
- No new dependencies, no new outbound calls, no telemetry. Scope definitions are read from the
  existing local ranking store; the token only chooses among them for one search.
