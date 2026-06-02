## ADDED Requirements

### Requirement: Opt-in click personalization

The system SHALL learn a ranking adjustment from the owner's implicit click feedback only when the
owner has explicitly enabled personalization. It SHALL be off by default, offered as a recommended
step in the first-run wizard and toggleable in Settings. While disabled, the system SHALL NOT record
any engagement signal and SHALL NOT alter ranking.

#### Scenario: Disabled by default

- **WHEN** a fresh profile runs a search and clicks a result
- **THEN** no engagement signal is recorded and result order is unchanged from the base ranking

#### Scenario: Enabled via the wizard or settings

- **WHEN** the owner enables personalization
- **THEN** subsequent owner clicks update the learned model and later searches reflect the bounded
  personalized order

### Requirement: Click-greater-than-skip-above learning signal

The system SHALL, when personalization is enabled and the owner clicks a result at displayed
position p, record a positive observation for the clicked result's host and a negative observation
for each distinct host displayed above position p that was not clicked. Hosts displayed below
position p SHALL be ignored. The same updates SHALL be mirrored per query-term for each query token.

#### Scenario: A click teaches click over skip-above

- **WHEN** the owner clicks the result at position 3 of a displayed list
- **THEN** the host at position 3 gains a click count, the distinct hosts at positions 1 and 2 each
  gain a skip count, and hosts below position 3 are unchanged

### Requirement: Bounded personalized ranking

The system SHALL apply the learned model as a pass between the relevance sort and the user
domain-rule pass. The adjustment SHALL be a bounded multiplicative boost so that engine consensus
remains the primary signal and explicit user pin/raise/lower/block rules always take precedence. The
system SHALL include exploration, cold-start gating, and time decay so personalization cannot
collapse result diversity or act on weak evidence.

#### Scenario: A preferred domain rises within bounds

- **WHEN** the owner has repeatedly clicked a given domain for similar queries
- **THEN** that domain moves up on later searches but by a bounded amount and never above an
  explicitly pinned result

#### Scenario: Cold start does not distort ranking

- **WHEN** too few clicked queries or too few impressions for a domain have accumulated
- **THEN** the base ranking order is returned unchanged

### Requirement: Owner-only training and personalization

The system SHALL train the model and apply personalized ordering only for the owner (the loopback
client and the native app). Network/LAN clients SHALL never train the model, SHALL never receive
personalized ordering, and SHALL have no way to read or influence the learned state.

#### Scenario: A LAN client cannot influence the model

- **WHEN** a non-loopback client runs searches and clicks results in network mode
- **THEN** the owner's learned model is unchanged and that client's results are not personalized

#### Scenario: Served browser clicks train only via the owner endpoint

- **WHEN** the owner clicks a result on the served page through the owner-only click endpoint
- **THEN** the model updates, and the endpoint redirects only to the server-recorded destination for
  that result so it cannot be used as an open redirect

### Requirement: Encrypted, persistent, portable model

The system SHALL persist the learned model encrypted so it survives restarts and updates. When the
vault is unavailable or locked, personalization SHALL be absent without error. The system SHALL let
the owner export, import, and reset the model using a portable JSON format shared with the desktop
app so the learned state can be backed up and moved between devices.

#### Scenario: Model survives a restart

- **WHEN** the owner has trained the model and restarts the app
- **THEN** the learned preferences are still applied

#### Scenario: Cross-platform portability

- **WHEN** a model exported by the desktop app is imported on Android
- **THEN** it loads and produces identical boosts for the same inputs
