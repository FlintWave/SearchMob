## ADDED Requirements

### Requirement: Outbound requests carry no cookies
Every outbound request to an upstream engine SHALL send no `Cookie` header, and the HTTP client
SHALL NOT store or replay cookies received from engines. No engine SHALL be able to set a persistent
identifier via cookies.

#### Scenario: No cookie header is sent
- **WHEN** any engine adapter makes an upstream request
- **THEN** the outbound request contains no `Cookie` header

#### Scenario: Engine-set cookies are not stored or replayed
- **WHEN** an engine response includes a `Set-Cookie` header
- **THEN** the cookie is discarded and is not sent on any subsequent request

### Requirement: Outbound requests carry no referrer or user identifier
Every outbound request SHALL omit the `Referer` header and SHALL carry no user, device, or install
identifier. The upstream engine SHALL see only the on-device search request with no information tying
it to the user.

#### Scenario: No referrer is sent
- **WHEN** any engine adapter makes an upstream request
- **THEN** the outbound request contains no `Referer` header

#### Scenario: No user or device identifier is sent
- **WHEN** any engine adapter makes an upstream request
- **THEN** the request includes no user id, device id, install id, or other identifier in its headers
  or query parameters

### Requirement: Rotated User-Agent per request
Outbound requests SHALL present a User-Agent drawn from a curated pool of common browser User-Agent
strings, and the value SHALL be rotated/randomized per request rather than being a fixed
SearchMob-identifying string, so requests are not trivially fingerprintable as coming from one app
install.

#### Scenario: User-Agent is from the rotation pool
- **WHEN** an engine adapter makes an upstream request
- **THEN** the `User-Agent` header is one of the curated pool values and is not a fixed
  SearchMob-identifying string

#### Scenario: User-Agent varies across requests
- **WHEN** multiple outbound requests are made
- **THEN** the User-Agent is selected per request from the pool rather than being constant for the
  session

### Requirement: Privacy proxy cannot be bypassed by adapters
The no-cookie, no-referrer, no-identifier, rotated-User-Agent policy SHALL be enforced at a single
shared HTTP client chokepoint that every adapter uses. An individual adapter SHALL NOT be able to
opt out of or override the privacy policy.

#### Scenario: All adapters share the proxied client
- **WHEN** any registered adapter issues an upstream request
- **THEN** the request passes through the shared privacy-enforcing client and the policy is applied
  regardless of which adapter made the call

### Requirement: Politeness and rate-limit awareness
The system SHALL apply per-engine politeness (a minimum spacing between requests to the same engine
host and short backoff on rate-limit responses, e.g. HTTP 429 or 503) together with the bounded
outbound concurrency, to reduce the chance that a single residential mobile IP trips upstream
bot-detection.

#### Scenario: Requests to one engine are spaced
- **WHEN** multiple requests would be sent to the same engine host in quick succession
- **THEN** they are spaced by at least the configured minimum interval for that engine

#### Scenario: Backoff on rate-limit response
- **WHEN** an engine returns a rate-limit response (HTTP 429 or 503)
- **THEN** subsequent requests to that engine are delayed by a backoff interval rather than retried
  immediately
