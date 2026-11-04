# Changelog

All notable changes to SearchMob are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses Ubuntu-style date
versioning (`YY.MM.VV`).

## [26.12.0](https://github.com/FlintWave/SearchMob/compare/v26.11.1...v26.12.0) (2026-11-04)


### Features

* **about:** link to the desktop app from the About screen ([#44](https://github.com/FlintWave/SearchMob/issues/44)) ([2d4d4fb](https://github.com/FlintWave/SearchMob/commit/2d4d4fbfc083cdd79e5e3050563afbf0810355ab))
* bootstrap Android project scaffold ([d2118a0](https://github.com/FlintWave/SearchMob/commit/d2118a07ed9c2e6fb1d3f617ef9714b5cf342a2a))
* **data:** add Android encrypted-storage implementations + wiring ([5c5060b](https://github.com/FlintWave/SearchMob/commit/5c5060b7853b3019642d0cb344aca1fb255ab0c4))
* **data:** add JVM-verifiable encrypted-storage core ([bfcdea2](https://github.com/FlintWave/SearchMob/commit/bfcdea27f0ec90223d2054988a44f9679dbfe937))
* **engine:** add DuckDuckGo + Mwmbl adapters on a shared HTTP base ([c4f85b1](https://github.com/FlintWave/SearchMob/commit/c4f85b1a691eac4329502c9aaf444f47260a101c))
* **engine:** add metasearch engine backbone + Wikipedia adapter ([fc3fcb8](https://github.com/FlintWave/SearchMob/commit/fc3fcb8c85e78e2ed756e43ff7d5457876b1756f))
* **engine:** add Mojeek + Marginalia + BYO-key APIs; wire politeness ([5b27f60](https://github.com/FlintWave/SearchMob/commit/5b27f602cfda1760dfc7b625e289611b8fcd0035))
* **engine:** demote off-topic and wrong-language results in ranking ([#56](https://github.com/FlintWave/SearchMob/issues/56)) ([b50f047](https://github.com/FlintWave/SearchMob/commit/b50f047c2087990c8fdefbcd5a96f036bbcd3f35))
* **engine:** Kagi Search API engine (BYO key) + keys on all search paths ([#27](https://github.com/FlintWave/SearchMob/issues/27)) ([5b7121c](https://github.com/FlintWave/SearchMob/commit/5b7121cf5c91767882f89962f3f2304aa605249b))
* **engine:** owner-only per-engine status visibility ([#64](https://github.com/FlintWave/SearchMob/issues/64)) ([6a2ef51](https://github.com/FlintWave/SearchMob/commit/6a2ef516f2f40c73e4d994551201c53474b94d26))
* **history:** in-app history view with delete and JSON export/import ([#28](https://github.com/FlintWave/SearchMob/issues/28)) ([7f2e4da](https://github.com/FlintWave/SearchMob/commit/7f2e4da90d24d69e3a82d2c3c0c4967b3e451005))
* **i18n:** ten-language UI, RTL, served mirror, and result tailoring ([#62](https://github.com/FlintWave/SearchMob/issues/62)) ([d76721d](https://github.com/FlintWave/SearchMob/commit/d76721d07483ee55e1847d51216649f6107327be))
* **icon:** replace launcher icon with the magnifier/circle design ([#20](https://github.com/FlintWave/SearchMob/issues/20)) ([f2329e3](https://github.com/FlintWave/SearchMob/commit/f2329e34ea7f32cff37f1dc0c9bddea18cf09f88))
* **media:** media actions row + bounded canonical-platform promotion ([#65](https://github.com/FlintWave/SearchMob/issues/65)) ([a3b45b1](https://github.com/FlintWave/SearchMob/commit/a3b45b104229da4c8b23a7b61a06d09555d3bcd1))
* **network:** opt-in network mode (LAN/Tailscale) with warning gate ([#16](https://github.com/FlintWave/SearchMob/issues/16)) ([f9b3523](https://github.com/FlintWave/SearchMob/commit/f9b3523fe765955d66b8fa1d8dc5670184e2af8b))
* **onboarding:** add first-run wizard + browser-setup guide ([6b6e3a2](https://github.com/FlintWave/SearchMob/commit/6b6e3a2e60b112500602b0673fc1c3280e9fa03e))
* **ranking:** add AI-slop / low-quality domain filter ([#38](https://github.com/FlintWave/SearchMob/issues/38)) ([624c379](https://github.com/FlintWave/SearchMob/commit/624c3799ffdeac212ba9dc0027a68cef6ad0ab8b))
* **rank:** personalized ranking that learns from your clicks (Android A1) ([#48](https://github.com/FlintWave/SearchMob/issues/48)) ([b868f66](https://github.com/FlintWave/SearchMob/commit/b868f660fb8debe19db8ebf1f464d520bee0ac0c))
* **rank:** sample scopes + legal hardening (NOTICE, disclaimer, attribution) ([#31](https://github.com/FlintWave/SearchMob/issues/31)) ([2c52dc7](https://github.com/FlintWave/SearchMob/commit/2c52dc7fad9ceaa1f0e73fe86d2b43c676fb35a4))
* **rank:** sample scopes by default; scope selector before search; align trackers ([#41](https://github.com/FlintWave/SearchMob/issues/41)) ([e101e94](https://github.com/FlintWave/SearchMob/commit/e101e94629e8503bea94dd63ed3e6bcb7eb7065b))
* **results:** reveal a larger pool with infinite scroll ([#57](https://github.com/FlintWave/SearchMob/issues/57)) ([81c0f4c](https://github.com/FlintWave/SearchMob/commit/81c0f4ccb578fdf9a8d79f2e0024cae5ab5523a4))
* **scope:** apply a saved scope inline with a +name query token (served) ([#61](https://github.com/FlintWave/SearchMob/issues/61)) ([6dd17dd](https://github.com/FlintWave/SearchMob/commit/6dd17dd6cf116497c35dc28c20f125b63dbf3f9c))
* **search:** add search verticals (Web / News / Forums / Academic) ([#39](https://github.com/FlintWave/SearchMob/issues/39)) ([090f0da](https://github.com/FlintWave/SearchMob/commit/090f0da49488cd435f459ada7386004a01194d2f))
* **search:** commercial-parity features, privacy hardening, and cross-layer fixes ([#103](https://github.com/FlintWave/SearchMob/issues/103)) ([ceac8f3](https://github.com/FlintWave/SearchMob/commit/ceac8f3301f9d90c8806c89dc6a36b604c654779))
* **search:** contextual Wikipedia summary box (served page + core) ([#34](https://github.com/FlintWave/SearchMob/issues/34)) ([295ad9d](https://github.com/FlintWave/SearchMob/commit/295ad9de918cf265ee582769405dd782cb096a57))
* **search:** Google-style search operators, server security hardening, Material 3 web UI ([#96](https://github.com/FlintWave/SearchMob/issues/96)) ([df60723](https://github.com/FlintWave/SearchMob/commit/df60723b22f379dfdf03f952539202752edf8c2e))
* **search:** on-device result personalization (filter bubbles) ([#26](https://github.com/FlintWave/SearchMob/issues/26)) ([80b661c](https://github.com/FlintWave/SearchMob/commit/80b661cdfd673e555a0b001ab336302dd0a80c73))
* **search:** result sorting (freshest by date + relevance) ([#37](https://github.com/FlintWave/SearchMob/issues/37)) ([c766773](https://github.com/FlintWave/SearchMob/commit/c7667734423af66bd2da855904d7a5f515647271))
* **search:** typo and "similar sounding" correction ([#25](https://github.com/FlintWave/SearchMob/issues/25)) ([8754a7b](https://github.com/FlintWave/SearchMob/commit/8754a7b1c4c32a6dc82c6bb797ce7224c1b82192))
* **server:** add loopback-only Ktor search server ([bfddd44](https://github.com/FlintWave/SearchMob/commit/bfddd445bd2fce0863a8ec8ddb0140c2041b42cc))
* **server:** add media/history/update-check toggles + language to web Settings ([#71](https://github.com/FlintWave/SearchMob/issues/71)) ([b804ab0](https://github.com/FlintWave/SearchMob/commit/b804ab0be5180545713647cd51e2dead17ef0b8a))
* **server:** learn personalized ranking from served-page clicks (Android A2) ([#50](https://github.com/FlintWave/SearchMob/issues/50)) ([d0b6d8d](https://github.com/FlintWave/SearchMob/commit/d0b6d8dbf666ec9e63595c88b9925d04394759a8))
* **server:** per-engine enable/disable toggles on the web Settings page ([#74](https://github.com/FlintWave/SearchMob/issues/74)) ([3f8cbd3](https://github.com/FlintWave/SearchMob/commit/3f8cbd3b66dc0284574bb000aa9044147167b635))
* **server:** personalization controls (scope + ranking) in the served web UI ([#33](https://github.com/FlintWave/SearchMob/issues/33)) ([1a2445a](https://github.com/FlintWave/SearchMob/commit/1a2445af2be7a9d228081756045854b1634a96b8))
* **server:** personalization controls on the web Settings page ([#73](https://github.com/FlintWave/SearchMob/issues/73)) ([0d13916](https://github.com/FlintWave/SearchMob/commit/0d1391615f932927f378e23de2282c1f8358c938))
* **server:** professional themed browser pages + OpenSearch + stable port ([06f7df0](https://github.com/FlintWave/SearchMob/commit/06f7df000f940f0a166464dc0db14c9716e89566))
* **server:** served Settings page (preferences, rules, scopes, goggles, history) ([#40](https://github.com/FlintWave/SearchMob/issues/40)) ([9016486](https://github.com/FlintWave/SearchMob/commit/9016486956818ccaf0b8dd444f3e72fd8a2030c2))
* **service:** add specialUse always-on foreground service ([2435268](https://github.com/FlintWave/SearchMob/commit/24352683dfecd159a5293497b0ff070affa95641))
* **setup:** expose suggestion URL + correct browser directions ([#23](https://github.com/FlintWave/SearchMob/issues/23)) ([9c7b0d5](https://github.com/FlintWave/SearchMob/commit/9c7b0d5a3cfe1f7176e02cd7b3a570469eb402ec))
* **setup:** make browser-setup guide browser-agnostic (Firefox/Fennec-friendly) ([4667fa2](https://github.com/FlintWave/SearchMob/commit/4667fa2a3b62e42a159f60898573481475402dab))
* **storage:** wire encrypted history + API keys; archive specs; release 26.05.03 ([#24](https://github.com/FlintWave/SearchMob/issues/24)) ([0942236](https://github.com/FlintWave/SearchMob/commit/0942236fed023ba70c34c7ab4479d11eb6452202))
* **suggest:** add OpenSearch suggestions endpoint (local history + opt-in upstream) ([#17](https://github.com/FlintWave/SearchMob/issues/17)) ([1d06596](https://github.com/FlintWave/SearchMob/commit/1d06596f49de2358fde828ca85701ccfb77997b2))
* surface update availability and add one-tap APK install (Android A-parity) ([#54](https://github.com/FlintWave/SearchMob/issues/54)) ([794eceb](https://github.com/FlintWave/SearchMob/commit/794ecebf37c000721f5f0824b02f8125708e8608))
* **ui:** add a domain-rule "by hand" form to the app Settings ([#75](https://github.com/FlintWave/SearchMob/issues/75)) ([2df842d](https://github.com/FlintWave/SearchMob/commit/2df842d13e2369d9f572c0ad3337b165f9638cc1))
* **ui:** add About & privacy screen reachable from Settings ([916b82e](https://github.com/FlintWave/SearchMob/commit/916b82e756e1d32b1f3895f4a57158a61f48bf15))
* **ui:** add Compose search + settings UI, theming, and navigation ([b3186ba](https://github.com/FlintWave/SearchMob/commit/b3186badc5891e4fd912e478a99c1de0ae31d6ac))
* **ui:** edit a saved scope in the app Settings ([#77](https://github.com/FlintWave/SearchMob/issues/77)) ([52b18f0](https://github.com/FlintWave/SearchMob/commit/52b18f07c94821427460d60a5d8fd400cf657e03))
* **ui:** show the Wikipedia summary card in the in-app results ([#35](https://github.com/FlintWave/SearchMob/issues/35)) ([a049c12](https://github.com/FlintWave/SearchMob/commit/a049c12e832fae2629fc332c29873616b228de70))
* **ui:** show the Wikipedia summary thumbnail in the app ([#76](https://github.com/FlintWave/SearchMob/issues/76)) ([aa3066a](https://github.com/FlintWave/SearchMob/commit/aa3066ae267f9a46d56c792d6a43598fa2a4c738))
* **ui:** theme library, picker, and font-size control ([#58](https://github.com/FlintWave/SearchMob/issues/58)) ([7d00d49](https://github.com/FlintWave/SearchMob/commit/7d00d49ce6dab019c95be9d2eae52a8965a2d9f7))
* **update:** add opt-out launch-time GitHub update check ([#18](https://github.com/FlintWave/SearchMob/issues/18)) ([dc60c59](https://github.com/FlintWave/SearchMob/commit/dc60c5973d5b61616d1c6f6d8af092d2bdbe4fbd))
* **widget:** add Glance home-screen search widget with Search deep link ([c60704b](https://github.com/FlintWave/SearchMob/commit/c60704b1be6b7602cbf020a974c6f77bb3bc3395))
* **wizard:** add a "Show search suggestions" step to the browser setup guide ([#21](https://github.com/FlintWave/SearchMob/issues/21)) ([3b0b34d](https://github.com/FlintWave/SearchMob/commit/3b0b34d0a89c7b3a47ea3f8d1f733a3754dd98c4))


### Bug Fixes

* **build:** restore theme switching by re-aligning the Compose stack ([#98](https://github.com/FlintWave/SearchMob/issues/98)) ([c5105d5](https://github.com/FlintWave/SearchMob/commit/c5105d5fedc4c15c630ed88f8ad3f1f077c443a9))
* correct Kagi to v1 API, green the instrumented suite, docs + changelog ([#29](https://github.com/FlintWave/SearchMob/issues/29)) ([3e5b75e](https://github.com/FlintWave/SearchMob/commit/3e5b75e40a65905bc579c052aaadfd71d1765af1))
* **engines:** surface the official site for brand/navigational queries ([#81](https://github.com/FlintWave/SearchMob/issues/81)) ([dd56949](https://github.com/FlintWave/SearchMob/commit/dd56949a6d9c736502879d1b2105b101e9aeb897))
* **engine:** strip tracking params from the links results point to ([#32](https://github.com/FlintWave/SearchMob/issues/32)) ([d9412c5](https://github.com/FlintWave/SearchMob/commit/d9412c51bbab7145a88b5594c198dfcd844d49aa))
* **engine:** Wikipedia via OpenSearch (title match), not noisy full-text ([#72](https://github.com/FlintWave/SearchMob/issues/72)) ([7d57282](https://github.com/FlintWave/SearchMob/commit/7d57282b02f99ad6b6f10a5f7599e48a63923d4e))
* **i18n:** keep the Activity reachable through the locale override ([#63](https://github.com/FlintWave/SearchMob/issues/63)) ([cc1bdb9](https://github.com/FlintWave/SearchMob/commit/cc1bdb9bf3d13ca733cc83d9fbdc43abeff92820))
* **parity:** honor saved sort on browser route; keep did-you-mean; wire in-app summary ([#43](https://github.com/FlintWave/SearchMob/issues/43)) ([6d084d5](https://github.com/FlintWave/SearchMob/commit/6d084d502f7854522944e9c8c48e3f6661d3dd4f))
* **rank:** keep the official site on page one for known-item searches ([#87](https://github.com/FlintWave/SearchMob/issues/87)) ([df134dc](https://github.com/FlintWave/SearchMob/commit/df134dcdd6ab0ec0aa1ceb6ccca0424e992b84b0))
* **security:** audit hardening + correct maintainer identity/contact ([#14](https://github.com/FlintWave/SearchMob/issues/14)) ([65e5b3b](https://github.com/FlintWave/SearchMob/commit/65e5b3be8896268d9609cba0749977b7e9a53e48))
* **security:** gate Android network mode with an access token + DNS-rebind guard ([#42](https://github.com/FlintWave/SearchMob/issues/42)) ([7a00ad6](https://github.com/FlintWave/SearchMob/commit/7a00ad6c0a4cc339f7ea8ae4f3877726a7ebedca))
* **server:** accept opaque Origin on served owner edits (no-referrer CSRF) ([#67](https://github.com/FlintWave/SearchMob/issues/67)) ([715d2b9](https://github.com/FlintWave/SearchMob/commit/715d2b9d88582cd11c89d10abdb39695e2a03318))
* **server:** gracefully degrade when vault is locked during browser search ([#93](https://github.com/FlintWave/SearchMob/issues/93)) ([9934410](https://github.com/FlintWave/SearchMob/commit/9934410d2d5edfa99afdae6733f2c4fd467116ca))
* **server:** keep results when applying a scope/rule from the served page ([#70](https://github.com/FlintWave/SearchMob/issues/70)) ([d39602c](https://github.com/FlintWave/SearchMob/commit/d39602c103279296e96df134509f2db0b7238423))
* **server:** recover the results page when a scope filters everything out ([#82](https://github.com/FlintWave/SearchMob/issues/82)) ([f772112](https://github.com/FlintWave/SearchMob/commit/f7721121fae866cef420f38b277b8efc21a0e5b6))
* **service:** declare WAKE_LOCK permission for per-request wake-lock ([80bc358](https://github.com/FlintWave/SearchMob/commit/80bc358f41109b3ac72f11ccc8014750fb80b298))
* **settings:** make whole toggle row tappable; clarify OEM keep-alive button ([#22](https://github.com/FlintWave/SearchMob/issues/22)) ([471d0c5](https://github.com/FlintWave/SearchMob/commit/471d0c5de4f6d3fc20a1d9f638a19fbe13f12598))
* **settings:** picking a theme switches Material You off so the pick applies ([#100](https://github.com/FlintWave/SearchMob/issues/100)) ([e852615](https://github.com/FlintWave/SearchMob/commit/e852615ffae7271a082373c7f80605e2a8cce094))
* **ui:** persist preferences via DataStore (onboarding flag, theme, toggles) ([1e82bd9](https://github.com/FlintWave/SearchMob/commit/1e82bd9f6f615542b90a42cf8eea05e2fecd68f1))

## [Unreleased]

## 26.11.01 — 2026-11-04

### Build System
- Bump Gradle wrapper 9.7.1 → 9.8.2.
- Bump AGP 9.4.0 → 9.5.0, Kotlin 2.4.20 → 2.4.21, Ktor 3.5.2 → 3.6.1, OkHttp 4.12.0 → 4.12.1,
  SQLCipher 4.19.0 → 4.20.0, Datastore 1.2.1 → 1.3.0, commons-codec 1.22.1 → 1.23.0,
  KSP 2.3.12 → 2.3.13, core-ktx 1.19.0 → 1.19.1, navigation-compose 2.10.1 → 2.10.2.

### Continuous Integration
- Bump actions/setup-java 5.7.0 → 6.0.1, gradle/actions/setup-gradle 6.3.0 → 6.4.0,
  softprops/action-gh-release 3.0.2 → 3.0.3.

## 26.07.03 — 2026-07-11

### Added
- **Instant answers, computed entirely on-device.** A calculator (`2+2`, `sqrt(9)*3`), unit
  conversions (`10 km to miles`, `72 f to c`), number-base conversions (`0xff in decimal`), and
  percentages (`15% of 80`) now show an answer card above the results, both in the app and on the
  served pages. Date- and phone-number-shaped input (`2020-2021`, `555-1234`) is guarded so the
  card never mis-fires, and nothing leaves the device.
- **!bangs.** `!w`, `!gh`, `!yt`, `!osm` and friends jump straight to that site's own search,
  resolved from a curated on-device table. Bang terms never enter the metasearch fan-out, and
  unknown tags (`!important css`) are left alone rather than hijacked.
- **Search-as-you-type on the served pages.** A keyboard-navigable suggestions dropdown (ARIA
  listbox) over the existing `/suggest` endpoint — debounced, same-origin only, and stale fetches
  are aborted. Pressing `/` focuses the search box.
- The served results page shows the **result count and elapsed time**, serves a **favicon**, and
  the topbar links, sort selector, and did-you-mean link now **carry the active vertical and sort**
  instead of silently resetting them. The engine-status diagnostic also renders on empty result
  pages, exactly when it is needed most.

### Security
- **Wikipedia summary thumbnails no longer leak searches to Wikimedia.** The browser used to fetch
  the thumbnail from Wikimedia directly, revealing the user's IP plus the searched entity via the
  image filename. Thumbnails are now re-served through a loopback `/img` proxy scoped to
  `upload.wikimedia.org` (image-only, size-capped), and the served pages' CSP tightens from
  `img-src https:` to `img-src 'self' data:` (plus `connect-src 'self'` for the suggestions
  dropdown).
- **One User-Agent per logical search.** A 429 retry no longer switches browser identities seconds
  apart from the same IP; the UA pool is refreshed, and per-host politeness spacing is now
  process-wide and thread-safe (it was previously rebuilt per search, so it never actually
  applied).
- `WikiSummaryProvider` body reads are size-capped like every other fetch, and the OpenSearch
  descriptor now uses the request Host for network-mode visitors — without embedding the access
  token — so browser search integration works off-device.

### Fixed
- **Encrypted data can no longer be silently destroyed.** The bootstrap metadata file (the only
  copy of the wrapped key that unlocks preferences, API keys, and history) is now written
  atomically, and a present-but-corrupt file fails closed instead of quietly re-keying — which
  previously wiped all encrypted data unrecoverably.
- **Settings no longer goes permanently dead after backing out once.** ViewModels are created
  fresh per screen entry; the stale-instance bug also silently stopped history recording.
- A byte-trickling upstream can no longer hold a search open for minutes (whole-call HTTP
  timeout), and cancelled searches no longer flash a raw error.
- Vertical scoping no longer poisons engines that lack `site:`/`OR` syntax (Wikipedia, Marginalia,
  and Mwmbl now get the clean query while constraints stay locally enforced), and upstream spelling
  corrections no longer leak or double the vertical's `site:` clause.
- Ranking and parsing fixes: http/https and mobile-host variants dedup into one result, blank
  titles backfill from other engines, domain rules resolve most-specific-first, day-first slash
  dates and `after:2024-3-1` parse, DuckDuckGo's direct-href A/B variant parses instead of
  yielding zero results, and the spell corrector no longer silently rewrites brand names
  ("spotify" stays "spotify").
- App polish: onboarding survives rotation, handles system back, and routes "Open privacy
  settings" correctly; battery-exemption cards refresh on resume; the widget deeplink no longer
  re-fires on rotation; engine toggles are transactional; update-banner dismissal survives
  rotation; APK staging is off the main thread; all outbound `startActivity` calls are guarded;
  result cards show the source host; and the search field auto-focuses when opened idle.

### Changed
- Dependency updates: androidx.sqlite 2.7.0.

## 26.07.02 — 2026-07-02

### Fixed
- **Theme changes apply again.** 26.07.01's AndroidX Lifecycle 2.11 update pulled a newer Compose
  UI/runtime onto the classpath than the pinned Compose BOM provides, splitting the Compose stack
  across versions and breaking recomposition of preference-driven UI — selecting a theme (or theme
  mode) in Settings persisted but never repainted the app. Lifecycle is pinned back to 2.10.0, the
  last version aligned with the BOM, and is now held by the dependency bot until it can be bumped
  together with the Compose BOM.
- **Picking a theme now takes effect immediately.** Material You (on by default) overrides the two
  named-theme slots, so choosing a theme from the dropdowns persisted but changed nothing until you
  found the toggle. Selecting a theme now switches Material You off in the same step, since picking
  one is an explicit choice to use it; re-enabling Material You afterwards still works and keeps
  the chosen slots.

## 26.07.01 — 2026-07-02

### Added
- **Google-style search operators.** `"exact phrase"`, `-term`, `site:` / `-site:`, `intitle:`,
  `inurl:`, `filetype:` (or `ext:`), `before:` / `after:` dates, and `OR`. Operators the upstream
  engines understand are forwarded to them, and every structural filter is also enforced on-device
  over the merged results, so they behave consistently across engines — in the app and on the served
  page. The served home page gains a collapsible operator cheat sheet, and relevance ranking no
  longer lets scoping clauses (like the verticals' internal `site:` groups) pollute the match score.

### Security
- **Cross-site request forgery fix for the served pages.** A literal `Origin: null` — which an
  attacker page can forge on a cross-site POST — was treated as same-origin; it is now rejected, and
  the server sends `Referrer-Policy: same-origin` so its own forms carry a real origin (cross-origin
  navigation still leaks nothing).
- Every served response now carries `Content-Security-Policy`, `Cache-Control: no-store`, and
  `Permissions-Policy` headers, so queries never persist in browser caches and no external
  script/style/frame can ever load.
- The network-mode access token is compared in constant time and can be sent via
  `Authorization: Bearer` or `X-SearchMob-Token` headers instead of only the `?token=` query
  parameter (which stays for OpenSearch templates but leaks into browser history).

### Changed
- **The served pages are restyled to Material 3**: elevated search bar with focus states, rounded
  cards, pill buttons, chip state layers, and theme-derived colors in every palette.
- Dependency refresh: AndroidX Lifecycle 2.11, Ktor 3.5.1, Gradle 9.6.1, and updated CI actions;
  in-app string reads now go through Compose's configuration-aware resource APIs, so they update
  correctly on language changes.

## 26.06.06 — 2026-06-19

### Fixed
- **Searching for a site or tool by name now keeps its official page on the first screen.** Two
  ranking steps could bury the official site even after it had been ranked first: the freshness
  blend was reordering by list position instead of the real relevance score, so a single dated page
  (a news story, an encyclopedia entry) could jump ahead of the undated official homepage; and the
  built-in low-quality filter, which is on by default, was sourced from community lists that include
  the official sites of many well-known companies and developer hubs, so those sites were quietly
  pushed down. Freshness now rides on top of the real score and can only reorder results of similar
  relevance, and a curated allowlist keeps well-known destinations out of the filter. All on-device,
  no new requests.

## 26.06.05 — 2026-06-10

### Fixed
- **Searching for a site or tool by name now surfaces its official page.** A query like a framework
  or library name that its official site spells with a dot (for example "three.js") was scored as if
  it did not match and sank below forum posts that happened to contain the bare word. SearchMob now
  bridges those spellings, and when your query names a result's own domain it lifts that result
  toward the top, so the official page leads instead of hiding several screens down. It is pure
  on-device string work and adds no new requests.
- **A scope that filters out every result no longer looks like a blank page.** When you apply a
  scope on the results page in a browser and it matches nothing, the page now tells you the scope
  hid the results and offers a one-click "Clear scope" to bring them back, instead of an empty page
  with no hint of what happened or how to undo it.

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
