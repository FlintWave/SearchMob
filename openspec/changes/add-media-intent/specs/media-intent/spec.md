## ADDED Requirements

### Requirement: Detect a query's media category from the resolved entity

The system SHALL detect a media category (Music, Film & TV, Books, or Games) for a query from the
type of the entity the app already resolves for its contextual summary, using no additional network
call. The system SHALL detect a category only for a confidently resolved media entity; a query with
no such entity SHALL produce no category, no actions row, and no ranking change.

#### Scenario: A media entity is categorized

- **WHEN** a query resolves to an entity whose description identifies it as a film
- **THEN** the query is categorized as Film & TV

#### Scenario: A non-media query is left unchanged

- **WHEN** a query does not resolve to a media entity
- **THEN** no category is detected, no actions row is shown, and the ranking is unchanged

### Requirement: Canonical-platform actions row

The system SHALL, for a detected media category, present an actions row of canonical destinations for
the entity — the entity's reference (Wikipedia) page and per-platform links built locally from the
entity name — leading with free/open options. The links SHALL be constructed on the device with no
tracking or affiliate parameters and SHALL NOT be fetched at search time.

#### Scenario: The row lists canonical platforms

- **WHEN** a query is categorized as Music
- **THEN** an actions row labeled "Listen on" is shown with the entity's Wikipedia page followed by
  music platforms, free/open options first, each a locally-built link

### Requirement: Bounded canonical-platform promotion

The system SHALL give a bounded, positive promotion to results whose host is a canonical platform for
the detected category, applied after relevance and before the user's domain rules, so the user's
pin/raise/lower/block rules still take precedence and a canonical platform never outranks strong
engine consensus.

#### Scenario: A canonical platform is nudged up but pins still win

- **WHEN** a category is detected and a canonical-platform result is present
- **THEN** that result is lifted by at most a small bounded amount, and any pinned/raised/blocked
  result keeps the precedence the user's rules give it

### Requirement: Toggle

The system SHALL provide a setting (default on) that controls the whole feature; when off, no actions
row is shown and no media promotion is applied.

#### Scenario: The feature is turned off

- **WHEN** the media setting is off
- **THEN** no actions row is shown and the ranking carries no media promotion
