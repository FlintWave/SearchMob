## ADDED Requirements

### Requirement: Query input and submission
The search UI SHALL present a text input for the user's query and a submit affordance. When the
user submits a non-empty query, the app SHALL dispatch it to the metasearch core (in-process
aggregator or the localhost endpoint) and transition the results surface into its loading state.
The app SHALL NOT dispatch a search for an empty or whitespace-only query.

#### Scenario: User submits a non-empty query
- **WHEN** the user enters a non-empty query and taps submit (or the keyboard search action)
- **THEN** the app dispatches the query to the metasearch core and shows the loading state

#### Scenario: User submits an empty query
- **WHEN** the user submits an empty or whitespace-only query
- **THEN** the app does not dispatch a search and the results surface is unchanged

### Requirement: Aggregated results rendering
The results screen SHALL render each aggregated result with its title, snippet, and source-engine
attribution. Each result MUST display which engine(s) it came from so the user can see the
provenance of the aggregation.

#### Scenario: Results render with attribution
- **WHEN** the metasearch core returns a non-empty list of aggregated results
- **THEN** each result is rendered with its title, snippet, and the source-engine attribution

### Requirement: Tap to open a result
The results screen SHALL allow the user to open a result by tapping it, launching the result's URL
in the user's chosen handler (e.g. an `ACTION_VIEW` intent). The app SHALL NOT send the query or any
user identifier to the opened destination beyond the result URL itself.

#### Scenario: User taps a result
- **WHEN** the user taps a rendered result
- **THEN** the app opens the result's URL via the system's view handler

### Requirement: Loading state
The results surface SHALL show an explicit loading indicator while a search is in flight, distinct
from the empty and error states.

#### Scenario: Search in flight shows loading
- **WHEN** a search has been dispatched and no response has yet been received
- **THEN** the results surface shows the loading indicator and not the empty or error state

### Requirement: Empty-results state
When a completed search returns zero results, the results surface SHALL show an explicit empty
state that is distinct from both the loading and error states.

#### Scenario: Completed search returns no results
- **WHEN** a search completes successfully with an empty result set
- **THEN** the results surface shows the empty state, not a spinner or an error

### Requirement: Error state with retry
When a search fails (e.g. all engines error or the request fails), the results surface SHALL show an
explicit error state and SHALL offer the user a way to retry the search via a retry control and/or
pull-to-refresh. Triggering retry SHALL re-dispatch the most recent query.

#### Scenario: Search fails and user retries
- **WHEN** a search fails and the user triggers retry (button or pull-to-refresh)
- **THEN** the app re-dispatches the most recent query and returns to the loading state

#### Scenario: Error state is distinct
- **WHEN** a search fails
- **THEN** the results surface shows the error state, distinct from the loading and empty states

### Requirement: Results UI privacy
The search UI SHALL NOT include any analytics, telemetry, or device-identifier collection, and SHALL
NOT transmit the query to any party other than the engine fan-out performed by the metasearch core.
The UI SHALL honor the store-nothing default: it MUST NOT persist the query or its results unless
search history is enabled in settings.

#### Scenario: No analytics or extra transmission
- **WHEN** the user performs a search
- **THEN** the UI emits no analytics/telemetry and sends the query only through the engine fan-out

#### Scenario: Store-nothing default is honored
- **WHEN** the user performs a search while search history is disabled
- **THEN** the app does not persist the query or its results to disk
