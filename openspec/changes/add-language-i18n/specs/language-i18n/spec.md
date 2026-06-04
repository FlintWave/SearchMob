## ADDED Requirements

### Requirement: Ten-language UI with English fallback

The system SHALL translate every user-facing interface string into the active locale, drawn from
per-locale string resources keyed by the English source. The system SHALL ship ten locales — English
plus Chinese (Simplified), Hindi, Spanish, Arabic, French, Bengali, Portuguese, Indonesian, and
Urdu — and SHALL render the English source string when the active locale has no translation for it,
so a missing entry never shows a placeholder key.

#### Scenario: A translated string renders in the active locale

- **WHEN** the active language is Spanish and the interface shows the source string "Search the web"
- **THEN** the Spanish translation is rendered

#### Scenario: A missing translation falls back to English

- **WHEN** the active language has no entry for a given English source string
- **THEN** the English source string is rendered unchanged rather than a missing-key marker

### Requirement: Right-to-left layout for right-to-left languages

The system SHALL lay the interface out right-to-left when the active language is written right-to-left
(Arabic, Urdu) and left-to-right otherwise, on both the app and the served page. The served page SHALL
set the document language attribute for every locale and the direction attribute to `rtl` for
right-to-left locales.

#### Scenario: An Arabic served page is right-to-left

- **WHEN** the served page is rendered in Arabic
- **THEN** the document declares Arabic as its language and right-to-left direction, and the
  interface chrome is mirrored

#### Scenario: Switching the app language flips direction live

- **WHEN** the app language is switched from English to Arabic and then to Chinese
- **THEN** the layout direction becomes right-to-left for Arabic and left-to-right for Chinese without
  restarting the app

### Requirement: Persisted language choice that follows the OS by default

The system SHALL let the user choose the interface language and SHALL remember that choice across
launches. When no choice has been made, the system SHALL follow the device language if it is one of
the shipped locales, otherwise English. Choosing "Follow system" SHALL clear the saved language so the
device language applies again.

#### Scenario: First launch follows the device language

- **WHEN** the app starts with no saved language and the device language is a shipped locale
- **THEN** the interface starts in that device locale

#### Scenario: A saved choice persists across launches

- **WHEN** the user selects a language and relaunches the app
- **THEN** the interface starts in the selected language regardless of the device language

#### Scenario: Switching the language does not restart the app

- **WHEN** the user changes the language in Settings
- **THEN** the interface re-translates in place without the Activity being recreated

### Requirement: Per-request locale on the served page

The served page SHALL resolve the language for each request independently, in precedence order: the
owner's saved language, then the visitor's `Accept-Language` (first supported entry), then the device
language, then English.

#### Scenario: A visitor's Accept-Language is honoured when no language is pinned

- **WHEN** the owner has not pinned a language and a visitor requests the page with an
  `Accept-Language` naming a shipped locale first
- **THEN** the page renders in that locale

#### Scenario: A pinned language overrides the visitor header

- **WHEN** the owner has pinned a language and a visitor sends a different `Accept-Language`
- **THEN** the page renders in the owner's pinned language

### Requirement: Result tailoring through per-engine language and region parameters

The system SHALL tailor result requests to the active non-English locale by passing each capable
engine its documented language/region parameters (DuckDuckGo a region-language code; Brave a search
language, country, and UI language). For the English locale, and for any locale an engine has no code
for, the system SHALL omit those parameters and request results region-neutrally, exactly as before
this change. Engines that document no such parameter SHALL be left unchanged. This tailoring SHALL
affect only the request, not how results are ranked or filtered.

#### Scenario: A non-English locale tailors the engine request

- **WHEN** a search runs with the active language set to Spanish
- **THEN** DuckDuckGo receives its `kl` region-language code and Brave receives its
  country/search_lang/ui_lang parameters, while engines without such a parameter are unchanged

#### Scenario: English stays region-neutral

- **WHEN** a search runs with the active language set to English
- **THEN** no language/region parameters are added and the request is identical to the pre-change
  region-neutral request
