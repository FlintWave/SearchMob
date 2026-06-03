## ADDED Requirements

### Requirement: Lexical query-match relevance

The system SHALL compute an on-device lexical match score between the query's content terms and each
result's title and snippet, and fold it into the aggregated ranking so that results matching the
query rank above near-tied results that do not. Content terms SHALL be derived by Unicode-aware
tokenization with stopword removal and light ASCII-gated stemming, and a result that omits the
query's head (subject) term entirely SHALL be penalized.

#### Scenario: An off-topic near-tie is demoted

- **WHEN** two results have near-equal engine-consensus scores and one omits the query's subject term
- **THEN** the result that omits the subject term ranks below the one that contains it

#### Scenario: Singular and plural match

- **WHEN** the query term is "keyboard" and a result title says "keyboards"
- **THEN** the result is treated as matching the term

### Requirement: Demotion-only relevance blend

The system SHALL apply the relevance signal as a multiplicative factor capped at 1.0, so a
well-matching result keeps its full consensus weight and engine consensus still orders the good
matches. A keyword-stuffed title SHALL NOT be promoted above a result several engines agree on; only
weak or wrong-language matches SHALL be sunk, toward a non-zero floor rather than removed.

#### Scenario: Keyword stuffing is not promoted

- **WHEN** a single-engine result repeats the query terms in its title and a multi-engine consensus
  result also matches the query
- **THEN** the consensus result still ranks at or above the keyword-stuffed result

### Requirement: Language-relative result demotion

The system SHALL demote a result whose dominant letter script differs from the query's dominant
script, computed relative to the query so it works in any language. A result whose dominant script
matches the query, or a query with no letters, SHALL NOT be penalized for language.

#### Scenario: Wrong-script result is demoted in any language

- **WHEN** a Latin-script query returns a result dominated by Cyrillic text, or a Cyrillic query
  returns a Latin-dominated result
- **THEN** that result is demoted relative to results in the query's own script

### Requirement: Language-agnostic implementation

The system SHALL tokenize and match in a way that is not English-specific: tokenization SHALL cover
letters of any script, English stemming SHALL be gated to ASCII so non-Latin words are never altered,
and the absence of a stopword list for a language SHALL degrade to matching on all tokens rather than
failing.

#### Scenario: Non-Latin query still matches

- **WHEN** the query and a relevant result are both written in a non-Latin script
- **THEN** the lexical signal still recognizes the shared terms and does not score the result zero
