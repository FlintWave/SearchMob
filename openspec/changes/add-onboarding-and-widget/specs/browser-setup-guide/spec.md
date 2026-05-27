## ADDED Requirements

### Requirement: Guide shows the live SearchMob URLs
The application SHALL provide an in-app browser-setup guide, reachable from Settings and from the
first-run wizard, that displays the SearchMob loopback URLs built from the actually-bound port: the
home/visit URL (`http://127.0.0.1:<port>/`) and the search-template URL
(`http://127.0.0.1:<port>/search?q=%s`). When the service/server is not running, the guide SHALL
indicate that and prompt the user to start it.

#### Scenario: URLs reflect the bound port
- **WHEN** the server is running on its bound port and the user opens the setup guide
- **THEN** the displayed URLs contain that exact port

#### Scenario: Server-not-running is handled
- **WHEN** the server is not running and the user opens the setup guide
- **THEN** the guide indicates the service must be running and offers to start it, rather than showing
  a stale or broken URL

### Requirement: One-tap copy of each URL
Each URL in the guide SHALL have a control that copies that URL to the system clipboard in one tap and
gives the user clear confirmation that it was copied.

#### Scenario: Copy the template URL
- **WHEN** the user taps the copy control next to the search-template URL
- **THEN** the exact `http://127.0.0.1:<port>/search?q=%s` string is placed on the clipboard
- **AND** the UI confirms the copy (e.g. a snackbar/toast or state change)

### Requirement: Browser-agnostic setup instructions
The guide SHALL lead with the generic, browser-agnostic method (visit the SearchMob page — which
advertises an OpenSearch descriptor — then add it from the browser's search-engine settings), since
that technique works across browsers. It SHALL additionally provide concise notes for the major
browser families rather than privileging any single browser: the Firefox family (Firefox, Fennec,
Mull, IronFox, etc.) and the Chromium family (Chrome, Brave, Edge, Vivaldi, Kiwi, etc.), plus a manual
fallback using the search-template URL that works in any browser that allows a custom engine. The
guide MUST NOT be Chrome-centric.

#### Scenario: Generic method is presented first
- **WHEN** the user views the setup guide
- **THEN** the primary instruction is the browser-agnostic "visit the page, then add SearchMob as a
  search engine in your browser's settings" flow, applicable regardless of which browser is used

#### Scenario: Browser-family notes are present and balanced
- **WHEN** the user views the setup guide
- **THEN** it includes specific notes for the Firefox family (including Fennec) and the Chromium family,
  and a manual fallback using the template URL, with no single browser presented as the default/assumed
  one
