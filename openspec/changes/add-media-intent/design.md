# Design: media-intent (Android)

## Detection

`MediaIntent.detectCategory(description)` scans the entity's Wikipedia short description (the summary
the app already fetches — no extra network call) for a type cue, ordered by specificity ("video game"
beats bare "game"; "graphic novel"/"comic" go to Books), and maps it to a `MediaCategory`. Returns
null for non-media entities, so a person who is not a performer, a place, or a concept never gets a
row or a ranking change. Ported 1:1 from the desktop `engines/media_intent.py`.

## Actions row

`MediaIntent.buildActionsRow(category, entityName, wikipediaUrl)` returns the entity's Wikipedia
article followed by per-platform deep links built locally from the URL-encoded entity name. Curated
per-category lists lead with free/open options. Nothing is fetched; the links are static and
tracker-free. Rendered in-app (a compact, horizontally-scrolling row in `SearchScreen`) and on the
served page (a `.actions-row` of `rel=noopener noreferrer` links). The verb label
("Listen/Watch/Read/Play on") is localized; brand names are not.

## Promotion

`MediaIntent.promoteMedia(results, category, urlOf, boost=3)` stably lifts results whose host is in
the category's platform set by at most `boost` slots — a positive mirror of the AI-slop downrank.
Applied in `MetaSearchResultProvider.aggregateRanked` after sort/personalization and before
`DomainRanker.apply`, so pin/raise/lower/block still win and a canonical platform never leaps over
engine consensus.

## Threading

The summary is fetched concurrently with the metasearch. Its task is passed into `aggregateRanked`
when the media toggle is on; the category is detected there (before the rules pass) for promotion,
and again in `searchWithCorrection` to build the row on `SearchOutcome.actionsRow`. The in-app
`SearchUiState.Results` carries it to the UI; the served route reads it off the outcome. A
`mediaActionsEnabled` provider lambda gates the whole feature (wired to the pref).

## Testing

- `MediaIntentTest`: category mapping (+ non-media null; "video game" beats "game"); actions-row
  construction (Wikipedia first, free/open first, URL-encoded); host-in-category (subdomains);
  promotion bounded + stable; no-match identity.
- `MediaIntentRouteTest`: the served row renders for an outcome that carries one, absent otherwise.

## Privacy / parity

- The only network call is the Wikipedia lookup already made for the summary; deep links are static
  and tracker-stripped. A Settings toggle (default on) controls the whole feature.
- Mirrors the desktop `media-intent` change: same detection, category mapping, platform lists, and
  URL templates.
