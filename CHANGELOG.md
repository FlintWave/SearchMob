# Changelog

All notable changes to SearchMob are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses Ubuntu-style date
versioning (`YY.MM.VV`).

## [Unreleased]

### Fixed
- **Searching for a site or tool by name now surfaces its official page.** A query like a framework
  or library name that its official site spells with a dot (for example "three.js") was scored as if
  it did not match and sank below forum posts that happened to contain the bare word. SearchMob now
  bridges those spellings, and when your query names a result's own domain it lifts that result
  toward the top, so the official page leads instead of hiding several screens down. It is pure
  on-device string work and adds no new requests.

## 26.06.04 — 2026-06-10

### Added
- **Edit a saved scope in the app, not just create or delete it.** Each scope in Settings now has an
  Edit button that opens the scope form pre-filled with its domains and keywords, so you can adjust
  one in place. This matches the browser Settings page, where each scope was already editable.
- **The Wikipedia summary card now shows its thumbnail in the app.** When a search shows the
  contextual Wikipedia card, the app now displays the article's thumbnail image next to it, matching
  the search pages you open in a browser. The image is fetched through the same privacy path as
  everything else (no cookies, no stable identifier, via the proxy).
- **More of the app's settings are now on the browser Settings page too.** The owner-only Settings
  page you open in a browser gained the controls it was missing next to the app: a language selector,
  a toggle for the films/music/books/games quick links, a "save search history" toggle, and a
  "check for updates" toggle. Changing your language there also re-tailors your search results, the
  same as in the app.
- **Result personalization controls on the browser Settings page.** The owner-only Settings page in
  your browser can now turn the learned click-personalization on or off, and export, import, or reset
  the model, matching the app. The model stays encrypted on your device and never leaves it.
- **Turn individual search engines on or off from the browser Settings page.** The owner-only
  Settings page now lists the search engines with a checkbox each, matching the app, so you can drop
  one you don't want without opening the app. (Bring-your-own API keys are still set in the app only.)
- **Add a per-site rule by hand in the app Settings.** The in-app Settings now has a field to type a
  site and block, lower, raise, or pin it directly, matching the browser Settings page, instead of
  only being able to create rules from the menu on a result.

### Fixed
- **Much better search results: Wikipedia no longer floods results with unrelated articles.** The
  Wikipedia engine was doing a full-text search that returned ten pages matching any single word in
  your query, so a multi-word, non-entity search came back with unrelated encyclopedia articles that
  merely shared one word with the query, instead of anything relevant. It now uses Wikipedia's
  title-matching endpoint (matching the desktop app): it contributes an article only when your query
  actually names one, and adds nothing otherwise. Entity searches (a person, place, or thing) still
  get the right Wikipedia article, and the rest of your results are no longer drowned out.
- **Applying a scope or rule from the browser no longer throws away your results.** On a SearchMob
  search page in your browser, changing the scope (or blocking/raising/lowering/pinning a result's
  site) used to send you to the home page with an empty search box, losing the results you were
  looking at. It now returns you to the same search with the new ranking applied, so you keep your
  results instead of starting over.

## 26.06.03 — 2026-06-05

### Added
- **SearchMob now speaks ten languages.** The whole interface translates into English, Chinese,
  Hindi, Spanish, Arabic, French, Bengali, Portuguese, Indonesian, and Urdu, in both the app and the
  served page. Pick your language in Settings; the app remembers it and follows your device language
  on first launch, switching live with no restart. Arabic and Urdu lay out right-to-left. Choosing a
  language also tailors results to it for the engines that support it. Translating result pages
  themselves is not part of this; SearchMob translates its own interface and asks the engines for
  language-appropriate results.
- **See which search engines responded.** Each search shows an unobtrusive "N of M engines
  responded" line, tap to expand the per-engine detail, so when results look thin you can tell
  whether an engine was simply quiet or actually failed (timed out or was blocked for your network).
  It is computed entirely on your device, never stored or sent anywhere, and shown only to you, never
  to other people on your network.
- **Quick links for films, music, books, and games.** When a search is about a film, musician,
  album, song, book, or video game, SearchMob shows a row of canonical places to watch, listen, read,
  or play it, leading with free and open options (Bandcamp, Open Library, GOG, and the like) and the
  entity's Wikipedia article, and nudges those platforms up in the results. It only recognizes media
  from the Wikipedia lookup the summary card already makes, so it adds no new requests; every link is
  built on your device. Turn it off in Settings.
- **Themes, text size, and a high-contrast option.** Pick from a library of named themes (a curated
  slate plus two accessibility themes), choose which one fills the light slot and which fills the
  dark slot, and step the text size up or down. The quick light/dark/follow-system control now swaps
  between your two chosen themes, and Material You dynamic color stays an independent option. It all
  applies in the app and on the search pages you open in a browser, and your choices stay on this
  device. Mirrors the desktop app.
- **Inline scope tokens on the search pages.** On a SearchMob search page in your browser, add a
  `+name` word to a query to run that one search through a saved scope, for example
  `mechanical keyboards +research`. The token is matched by the scope name's first word
  (case-insensitive), applied to that search only, and stripped from the query; an unmatched `+word`
  stays an ordinary term, and your saved scope selection is left unchanged. Mirrors the desktop app.

### Changed
- **More results, loaded as you scroll.** The results list no longer stops at the first screenful.
  It now keeps the full ranked set and reveals more as you scroll to the bottom, both in the app and
  on the search pages you open in a browser, with no extra request and nothing stored.

### Fixed
- **Changing the scope from the browser no longer says "Forbidden."** On a SearchMob search page in
  your browser, applying a scope (or a per-result block/raise/lower/pin rule) submitted a form that
  the browser labels with an opaque origin because every page is sent with a no-referrer policy. The
  server misread that as a cross-site request and rejected it. These owner-only edits now go through,
  while a genuine cross-site post is still blocked and the controls stay loopback-only.

## [26.06.02] - 2026-06-03

### Changed
- **More relevant results.** Ranking now weighs how well each result actually matches what you typed,
  not just how many engines returned it, so off-topic pages that one engine happened to rank highly
  get pushed down instead of sitting near the top. Results written in a different alphabet than your
  search (say, a stray English page for a Russian query) are demoted too. It works in any language,
  runs entirely on your device, and still lets engine agreement and your own pin, raise, lower, and
  block rules decide between good matches. This brings Android in line with the desktop app.

## [26.06.01] - 2026-06-03

### Added
- **You now actually get told when an update is out.** When SearchMob is open and a newer release is
  available, it posts a system notification and shows a banner across the top of the app; the served
  search pages show the same banner (only to you, on this device, never to other people on your
  network). The check still runs about once a day through the privacy proxy and stays opt-out.
- **One-tap update.** Tapping **Update** (in the banner or the notification) downloads the new APK,
  verifies it against the release's published SHA-256 checksums, and hands it to the system package
  installer (which shows its usual install confirmation). Falls back to the release page if there is
  no usable asset or the download fails.

## [26.06.00] - 2026-06-02

### Added
- **Personalized ranking that learns from your clicks.** SearchMob can now quietly move the sites
  you tend to click higher, and it gets better the more you search. It learns on-device from a
  position-bias-resistant "clicked over skipped-above" signal, applies a bounded boost (so engine
  consensus stays primary and your pin/raise/lower/block rules always win), and includes exploration,
  a cold-start gate, and time decay so it never collapses result diversity or acts on weak evidence.
- **Opt-in, recommended, and private.** Off by default, offered as a recommended step in the setup
  wizard and a toggle on the **Result ranking** settings page. What it learns is encrypted on your
  device, never leaves it, and is never trained by, nor applied for, other people on your network.
  You can **export, import, and reset** the learned model; the format is shared with SearchMob
  Desktop, so you can move it between devices.
- **Learns from the served browser page too.** Clicking a result from a browser search through the
  local server trains the model the same way the in-app results do, through an owner-only redirect
  that only ever sends you to the result you clicked. Network clients get plain links and are never
  tracked or personalized.
- **Setup wizard re-appears once after a feature update** (framed as "what's new") when an update
  adds a new opt-in setting, so existing users discover it instead of only fresh installs.

## [26.05.05] - 2026-05-31

### Added
- **Search verticals**: Web / News / Forums / Academic category tabs, in the app and on the served
  page (`?vertical=`). Each is a scoped `site:` search over the same engines with a sensible default
  sort; no new third-party API.
- **Settings in the browser**: an owner-only (loopback) Settings page on the served server mirroring
  the app — default sort, AI-slop filter, Wikipedia summary, suggestions, full domain-rule and scope
  management, Goggles import, and search-history view/clear.
- **Sample scopes installed by default** (no "add them" step); the scope selector appears in the app
  before a search and on the served home page.
- **Link to the desktop app** from the About screen.

### Changed
- The URL tracker-stripping list now matches the desktop app (adds `mc_cid`, `_hsenc`, `_hsmi`,
  `ref_src`, `yclid`).

### Fixed
- **Network mode is now authenticated.** When network mode is on (binding all interfaces), off-device
  clients must present a per-install access token to reach the search routes, the `Host` header is
  checked against a DNS-rebind allowlist, and every response carries `Referrer-Policy: no-referrer`
  (plus `X-Content-Type-Options`/`X-Frame-Options`) with `rel="noopener noreferrer"` on result links.
  Loopback (the device's own browser and all in-app use) is unaffected.
- **Parity fixes**: the served browser route now honors your saved default sort on the Web vertical;
  the in-app empty-results state keeps the "did you mean" suggestion; the contextual Wikipedia
  summary card now appears in the in-app results too (was served-page only).
- **Accessibility**: served pages declare `<html lang>`, search inputs and the Sort/Scope selects
  have accessible names / associated labels, the vertical bar is a labeled `nav` with `aria-current`
  on the active tab (and a contrast-corrected active chip), plus a focus ring and reduced-motion
  support; the "did you mean" banner and the Wikipedia card now announce correctly in TalkBack.
- The bundled AI-slop blocklist load is now size-bounded against a corrupt/oversized asset.

## [26.05.04] - 2026-05-29

### Added
- **Contextual Wikipedia summary box.** For entity-like queries, a short knowledge-panel card from
  the related Wikipedia article now appears above the results, both in the app and on the served
  page (title, description, lead extract, link). Fail-soft and confidence-gated; adds at most one
  extra request to Wikipedia (already a search engine here) through the privacy proxy. Toggle in
  Settings.
- **Personalization controls in the browser.** The served results page now offers the same
  scope/ranking tools as the app: per-result Block / Lower / Raise / Pin by domain and a scope
  selector. Edits persist to the encrypted store and apply on the next search. The editing routes
  are loopback-only (a device on the network can search but cannot change the owner's rules) and are
  same-origin guarded against CSRF.

### Fixed
- **Result links are stripped of tracking parameters before you tap them.** The tracker list
  (`utm_*`, `fbclid`, `gclid`, ...) was only applied to de-duplication; the surfaced link now drops
  trackers too, in the app and on the served page.

## [26.05.03] - 2026-05-28

### Added
- **Typo and "similar sounding" correction.** A misspelled query now surfaces a "Did you mean"
  suggestion, taken from the upstream engines' own correction when they offer one, otherwise from a
  fully on-device corrector (Double Metaphone phonetics + Jaro-Winkler/Damerau edit distance over a
  bundled dictionary, enriched by your own search history). No new outbound calls.
- **Result personalization ("filter bubbles").** Raise, lower, pin, or block any site from an inline
  menu on each result; scope searches with named lenses (include/exclude domains and keywords); and
  import a subset of Brave Goggles rule files. All rules are stored encrypted on-device, applied
  locally to results, and can be exported/imported as JSON.
- **Kagi Search API engine** as a bring-your-own-key option, alongside the existing Brave and Mojeek
  API engines. The key is stored encrypted at rest.
- **In-app search history.** View saved queries with their time, delete individual entries or clear
  all, and export/import history as JSON for moving to a new device (Storage Access Framework; no
  storage permission required).

### Changed
- **Encrypted storage is now wired into the live app.** Search history (SQLCipher) and BYO API keys
  (AES-256-GCM) are persisted encrypted at rest via a process-wide store shared by the UI and the
  foreground service. Bring-your-own keys now also take effect on the browser-facing `/search`, not
  only the in-app search.

### Security
- While **network mode** is on (the search server is reachable by other devices on the network), the
  `/suggest` endpoint no longer serves your local search history as autocomplete, so your history is
  not exposed to other users on your network. History recording, viewing, and export are unaffected.
  (Browser/network searches were never recorded to begin with; only your in-app searches are.)

### Fixed
- The OpenSpec specs for the implemented work were archived and the encrypted-storage wiring landed
  with a fix for a main-thread database access and a now-shared history store made thread-safe.

## [26.05.02] - 2026-05-28

### Added
- Browser-consumable OpenSearch **suggestions** endpoint (`/suggest`), sourced from local encrypted
  history with an explicit, default-off opt-in to upstream (DuckDuckGo) autocomplete via the privacy
  proxy.
- **Opt-out launch-time update check** against GitHub Releases (on by default, routed through the
  privacy proxy, throttled to about once a day), with a Settings toggle.

### Changed
- Toolchain bump (Kotlin/AGP/Gradle, `compileSdk 36`). New launcher icon and onboarding/setup polish.

## [26.05.01] - 2026-05-28

### Added
- First published release: the private, always-on metasearch app (foreground service + loopback HTTP
  server + OpenSearch descriptor), metasearch over free engines with the privacy proxy, opt-in network
  mode (LAN/Tailscale) behind a warning, and security-audit hardening.

[26.05.03]: https://github.com/FlintWave/SearchMob/releases/tag/v26.05.03
[26.05.02]: https://github.com/FlintWave/SearchMob/releases/tag/v26.05.02
[26.05.01]: https://github.com/FlintWave/SearchMob/releases/tag/v26.05.01
