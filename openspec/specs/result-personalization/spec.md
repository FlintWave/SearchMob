# result-personalization Specification

## Purpose
TBD - created by archiving change add-result-personalization. Update Purpose after archive.
## Requirements
### Requirement: Per-domain ranking rules
The application SHALL let the user assign a ranking rule to a domain: Block, Lower, Raise, Pin, or
Normal. Rules SHALL be applied locally after results are aggregated, as a deterministic, order-
preserving reordering: Blocked domains are removed; Pinned domains are placed first; Raised domains sort
above Normal; Lowered domains sort last; within each bucket the prior (relevance) order is preserved.
Normal clears any rule. The rules SHALL apply to both the in-app search and the browser-facing search.

#### Scenario: Block removes a domain
- **WHEN** a domain is Blocked and a later search returns a result from it
- **THEN** that result is absent from the results

#### Scenario: Pin and raise and lower reorder
- **WHEN** one domain is Pinned, another Raised, and another Lowered
- **THEN** the pinned domain's results come first, raised above normal, and lowered last, each bucket keeping its prior order

#### Scenario: Normal clears a rule
- **WHEN** a domain previously Blocked is set to Normal
- **THEN** its results appear again in their normal position

### Requirement: Lenses scope a search
The application SHALL support named lenses, each an include and/or exclude set of domains plus optional
include/exclude keywords matched against a result's title and snippet. When a lens is active, results
SHALL be filtered to its included domains (if any are specified) and have its excluded domains and
excluded keywords removed; an include-keyword set SHALL require a match. The active lens SHALL be
user-selectable and SHALL persist until changed.

#### Scenario: Include set restricts results
- **WHEN** a lens includes only `github.com` and `stackoverflow.com` and is active
- **THEN** only results from those domains are shown

#### Scenario: Exclude set and keywords remove results
- **WHEN** a lens excludes `pinterest.com` or the keyword "sponsored"
- **THEN** results from that domain or whose title/snippet contains that keyword are removed

#### Scenario: Selecting and clearing a lens persists
- **WHEN** the user selects a lens and later relaunches the app
- **THEN** the same lens is still active until they change or clear it

### Requirement: Brave Goggles subset import
The application SHALL import a subset of the Brave Goggles rule format from a local file or pasted text:
`site=` targets with simple wildcards and the `$boost`, `$downrank`, and `$discard` actions, mapping
boost to Raise, downrank to Lower, and discard to Block. Parsing SHALL be fail-soft: unsupported
directives are ignored and a malformed file SHALL NOT crash the app. Importing from a URL, if offered,
SHALL be a one-time user-initiated fetch.

#### Scenario: Goggle actions map to ranking
- **WHEN** an imported goggle has `$boost,site=dev.to` and `$discard,site=spam.example`
- **THEN** dev.to results are raised and spam.example results are removed

#### Scenario: Malformed goggle is safe
- **WHEN** an imported goggle contains unsupported or malformed lines
- **THEN** those lines are ignored and the supported rules still apply

### Requirement: Rules are stored encrypted on-device and never sent upstream
All personalization rules (per-domain rules, lenses, the active lens, and imported goggles) SHALL be
persisted in the DEK-encrypted preferences store on the device. Ranking SHALL be applied locally to
results already fetched; no rule and no derived signal SHALL be sent to any upstream engine or other
network service.

#### Scenario: Rules persist across restart
- **WHEN** the user sets rules and relaunches the app
- **THEN** the rules are still in effect

#### Scenario: Nothing leaves the device
- **WHEN** a search runs with rules in effect
- **THEN** the upstream requests are the normal engine fan-out and carry no ranking rules or preference data

### Requirement: Rules are manageable, portable, and adjustable inline
The application SHALL provide an inline control on each result to set its domain's rule (Block, Lower,
Raise, Pin, Normal) with immediate effect on the visible results, and a Settings section to view and
edit all domain rules and lenses and to import goggles. The full rule set SHALL be exportable and
importable as JSON for backup and sharing.

#### Scenario: Inline rule takes effect immediately
- **WHEN** the user blocks a result's domain from the inline control
- **THEN** that domain's results are removed from the current list without re-fetching

#### Scenario: Export then import round-trips
- **WHEN** the user exports the rules and imports the same JSON on a fresh install
- **THEN** the same domain rules, lenses, and goggles are restored

