# Design: inline scope token (Android)

Mirrors the desktop `add-scope-token` change (same first-word + normalized-fallback matching, same
per-request, non-persistent application). This change covers the Android served endpoints; the
desktop change covers the CLI plus the desktop served endpoints.

## Parser (`engine/rank/ScopeToken.kt`)

A pure object:

```
ScopeToken.parse(query: String, rules: RankingRules): Pair<String, String?>
```

Returns `(cleanedQuery, scopeName)`; `scopeName` is null when nothing matched (and `cleanedQuery ==
query`). Algorithm matches `scope_token.py` exactly: split on whitespace, walk tokens left to right,
a candidate is `+<rest>`; match precedence is first-word (`name.split()[0].lowercase()`) across all
scopes, then normalized whole-name (lowercased, alphanumeric-only). The first matching candidate
wins: that one token is dropped, the rest rejoined with single spaces. Fast path: no `+` returns the
query untouched.

## Wiring (`server/SearchServer.kt`, provider)

`/search` and `/api/search` already load `RankingRules` and run them through `DomainRanker` inside
`MetaSearchResultProvider`. The token is layered transiently:
1. Load rules, `val (cleaned, scope) = ScopeToken.parse(rawQuery, rules)`.
2. `SearchResultProvider.searchWithCorrection` gains an `activeLensOverride: String?` param; when set,
   the provider uses `rules.copy(activeLens = override)` for that call only — the saved selection and
   the `/scope` POST persistence are untouched.
3. The engines, summary, and correction run on `cleaned`; the search box and the JSON `query` echo
   `rawQuery` so the token round-trips on a re-search.

The in-process UI repository delegates with the override defaulted to null, so the Compose app is
unaffected (it keeps its scope selector). MCP has no equivalent on Android.

## Testing

- `ScopeTokenTest`: the same truth table as the desktop parser suite (first-word, case-insensitivity,
  normalized fallback, unmatched pass-through, first-token-wins, mid-query token, no-`+`, token-only).
- `ScopeTokenRouteTest`: against a real loopback server, a matched token hands the provider the
  cleaned query plus the scope override and echoes the original in the JSON; an unmatched token passes
  through with no override; the saved active scope stays null.
- Parity: the matching rules match the desktop parser.
