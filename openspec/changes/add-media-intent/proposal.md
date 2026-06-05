## Why

When a query is about a piece of media (a film, musician, album, song, book, or video game), the most
useful results are the canonical places to watch, listen, read, or play it, plus the reference page
for it. Generic web ranking buries these, and the query often has no cue word. This recognizes media
intent from the entity the app already resolves and surfaces the right platforms — neutrally and
transparently — bringing Android to parity with the desktop app.

## What Changes

- Detect the media type from the entity's Wikipedia short description (the summary the app already
  fetches, so no extra network call); map it to a category (Music / Film & TV / Books / Games).
  Resolved-entity-only: no confident entity means no row and no ranking change.
- Inject an actions row of canonical destinations for the entity, built locally from the entity name
  ("Listen/Watch/Read/Play on"), leading with free/open options and the entity's Wikipedia article.
- Promote canonical-platform results already in the ranked list for the detected category — a
  bounded, positive mirror of the AI-slop downrank, after relevance and before the user's rules.
- A Settings toggle (default on) controls the whole feature; brand names are not translated.

## Capabilities

### New Capabilities
- `media-intent`: detect a query's media category from entity data and surface the canonical platforms
  for it, by promoting matching results and injecting an actions row, neutral and toggleable.

### Modified Capabilities
<!-- None in contract; promotion is a new positive pass beside the existing AI-slop pass, and the
actions row is a new card beside the existing Wikipedia summary card. -->

## Non-goals

- A full knowledge graph or recommendations: only the detected entity's canonical platforms.
- Affiliate links, tracking, or sponsored ordering: never. Ordering is fixed and disclosed.
- Promoting only paid services: free/open options lead each category.
- Cue-only rows: per the user's choice, the row appears for resolved entities only.

## Impact

- New: `engine/MediaIntent.kt` (category detection, platform lists + deep-link builders, bounded
  promotion). Modified: `engine/MetaSearchResultProvider.kt` + `server/SearchOutcome` (thread the
  row + promotion through); `ui/search/SearchScreen.kt` (in-app row); `server/SearchServer.kt`
  (served row); the `media_actions_enabled` pref + Settings toggle; `res/values/strings.xml`
  (+ authored locales). No new dependencies, no new outbound calls, no telemetry.
