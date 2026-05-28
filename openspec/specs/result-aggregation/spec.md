# result-aggregation Specification

## Purpose
TBD - created by archiving change add-metasearch-engine-core. Update Purpose after archive.
## Requirements
### Requirement: Parallel bounded fan-out
The aggregator SHALL query all enabled engines concurrently using coroutines under bounded
concurrency, so the total search latency approaches that of the slowest single engine (capped by its
timeout) rather than the sum of all engines. The number of simultaneous outbound requests SHALL be
capped by a configurable maximum-concurrency limit.

#### Scenario: Engines are queried concurrently
- **WHEN** a search runs with multiple enabled engines
- **THEN** the engines are invoked concurrently rather than sequentially
- **AND** the overall latency is bounded by the slowest responding engine (or its timeout), not the
  sum of all engine latencies

#### Scenario: Concurrency is bounded
- **WHEN** more engines are enabled than the maximum-concurrency limit
- **THEN** no more than the configured maximum number of upstream requests are in flight at once

### Requirement: Partial results on engine failure
A search SHALL succeed and return whatever results are available even when one or more engines fail,
time out, or return nothing. A single slow or broken engine SHALL NOT block or fail the overall
query.

#### Scenario: One engine times out, others succeed
- **WHEN** one engine exceeds its timeout while others return results
- **THEN** the search returns the merged results from the successful engines and omits the timed-out
  engine's contribution

#### Scenario: An engine throws or errors
- **WHEN** one engine's adapter encounters an error
- **THEN** the overall search still completes successfully with results from the remaining engines

#### Scenario: All engines fail
- **WHEN** every enabled engine fails or returns nothing
- **THEN** the search completes and returns an empty result set rather than raising an error

### Requirement: Normalized result model
The system SHALL represent every result with a normalized model containing at least a title, a URL, a
snippet, the source engine id, and the result's position within that engine's results. Adapters SHALL
emit results in this shape so downstream dedup and ranking operate uniformly.

#### Scenario: Results carry source attribution and position
- **WHEN** an engine returns results
- **THEN** each normalized result records the originating engine id and its position in that engine's
  ranking

### Requirement: Deduplication by normalized URL
The aggregator SHALL deduplicate results by a normalized form of their URL so the same destination
appearing in multiple engines collapses into a single result. URL normalization SHALL at minimum
lowercase the host, drop a leading `www.`, remove a trailing slash, and strip known tracking query
parameters. A deduplicated result SHALL retain the set of engines that contributed it and each
engine's rank for it.

#### Scenario: Same URL from two engines collapses to one
- **WHEN** two engines return the same destination URL (differing only by tracking params, trailing
  slash, `www.`, or host case)
- **THEN** the output contains a single result for that URL recording both contributing engines

#### Scenario: Distinct URLs are preserved
- **WHEN** two results point to genuinely different URLs
- **THEN** both appear as separate results in the output

### Requirement: Deterministic merge and ranking
The aggregator SHALL merge results across engines and produce a single ranking using a deterministic
algorithm (reciprocal rank fusion) that boosts results endorsed by multiple engines. For a fixed set
of engine inputs, the output ordering SHALL be byte-stable, with ties broken deterministically, so
ranking is unit-testable without network access.

#### Scenario: Multi-engine agreement ranks higher
- **WHEN** a result is returned by several engines and another comparable result by only one engine
- **THEN** the multi-engine result ranks above the single-engine result under reciprocal rank fusion

#### Scenario: Ranking is deterministic
- **WHEN** the same fixed set of per-engine result lists is ranked twice
- **THEN** the produced ordering is identical both times, including tie-breaking order

