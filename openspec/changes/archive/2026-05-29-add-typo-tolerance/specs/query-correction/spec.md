## ADDED Requirements

### Requirement: Upstream engine spelling correction is captured
The application SHALL capture an upstream engine's own spelling correction ("did you mean" or "showing
results for X") from the response it already fetched, without making an additional network request. The
HTTP adapter base SHALL expose an optional correction-parsing hook that defaults to no correction, and
the HTML engines that surface a correction (DuckDuckGo, Mojeek) SHALL implement it. Capturing or
failing to capture a correction SHALL NOT affect the result list and SHALL NOT throw.

#### Scenario: Correction parsed from a response that has one
- **WHEN** an engine response contains a "showing results for <X>" / "did you mean <X>" block
- **THEN** the adapter reports `<X>` as the correction alongside its result items

#### Scenario: No correction when absent
- **WHEN** an engine response contains no correction block
- **THEN** the adapter reports no correction and the result items are unaffected

### Requirement: A consensus correction is chosen across engines
The aggregator SHALL collect the corrections reported by the queried engines and select a single
consensus correction: the most frequently reported one, resolving ties to the first seen. When no
engine reports a correction, the consensus SHALL be absent.

#### Scenario: Most frequent correction wins
- **WHEN** two engines report "john depp" and one reports "jon depp"
- **THEN** the consensus correction is "john depp"

#### Scenario: No consensus when none reported
- **WHEN** no engine reports a correction
- **THEN** there is no consensus correction

### Requirement: An on-device corrector proposes corrections offline
The application SHALL provide an on-device spelling and phonetic corrector that proposes a corrected
query for a likely-misspelled input using only local data. It SHALL combine edit-distance similarity
(suited to short strings and names) and phonetic ("similar sounding") matching against a local
dictionary, rank candidates by a combination of corpus frequency and similarity, and propose a
correction only when its confidence exceeds a threshold. It SHALL NOT make any network request and
SHALL return no suggestion (never throw) for blank input, in-dictionary terms, or low-confidence cases.

#### Scenario: A misspelled term is corrected
- **WHEN** the user searches a term that is one or two edits from a high-frequency dictionary entry
- **THEN** the corrector proposes that entry as the correction

#### Scenario: A similar-sounding term is corrected
- **WHEN** the user searches a name that is spelled differently but sounds like a dictionary name
- **THEN** the corrector proposes the dictionary name via phonetic matching

#### Scenario: Correct input is left alone
- **WHEN** the user searches a term that is already in the dictionary
- **THEN** the corrector proposes no correction

#### Scenario: Offline and fail-soft
- **WHEN** the corrector runs
- **THEN** it consults only local data, issues no network request, and returns no suggestion rather than throwing on any internal error

### Requirement: The corrector dictionary is bundled and privately augmented
The corrector's dictionary SHALL be a compact asset bundled with the app, built from documented
free-licensed sources by a committed, reproducible generation script. At runtime the dictionary SHALL
be augmented with the user's own opt-in, encrypted on-device search history so corrections improve for
terms the user actually searches, without anything leaving the device. When history is disabled or
empty, only the bundled dictionary SHALL be used.

#### Scenario: History augments the dictionary
- **WHEN** history is enabled and the user has previously searched a term not in the bundled dictionary
- **THEN** that term is available as a correction candidate

#### Scenario: Bundled dictionary alone when history is unavailable
- **WHEN** history is disabled or empty
- **THEN** the corrector still proposes corrections from the bundled dictionary

### Requirement: A "did you mean" suggestion is surfaced to the user
The search response SHALL carry an optional correction ("did you mean"), chosen by preferring the
upstream consensus correction and otherwise a high-confidence on-device suggestion. The application
SHALL surface it as a non-intrusive banner in both the in-app search screen and the browser-facing
results page; activating the banner SHALL re-run the search with the corrected query. The application
SHALL NOT silently rewrite the query, except that when the original query returns zero results and the
correction is high-confidence it MAY search the correction automatically and report "Showing results
for X" with a link back to the original query.

#### Scenario: Banner offered, results still for the original query
- **WHEN** a correction is available and the original query returned results
- **THEN** the original results are shown with a "Did you mean: X" banner that re-runs the search when activated

#### Scenario: Auto-search only when the original is empty
- **WHEN** the original query returns zero results and a high-confidence correction exists
- **THEN** the corrected query is searched and the page reports "Showing results for X" with a link back to the original

#### Scenario: No banner when there is no correction
- **WHEN** neither an upstream nor an on-device correction is available
- **THEN** no "did you mean" banner is shown
