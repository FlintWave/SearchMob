# home-screen-widget Specification

## Purpose
TBD - created by archiving change add-onboarding-and-widget. Update Purpose after archive.
## Requirements
### Requirement: Home-screen search widget
The application SHALL provide an Android home-screen app widget that presents a tappable search
affordance (a search box/bar). The widget SHALL be addable from the launcher's widget picker and
render with the app's branding in both light and dark system themes.

#### Scenario: Widget is available and renders
- **WHEN** the user opens the launcher widget picker and adds the SearchMob widget
- **THEN** the widget appears on the home screen showing a tappable search bar

#### Scenario: Widget respects system theme
- **WHEN** the system is in dark mode (or light mode)
- **THEN** the widget renders legibly in that theme

### Requirement: Widget launches in-app search
Tapping the widget's search affordance SHALL open SearchMob's in-app Search screen (via a deep link /
launch intent), ready for the user to enter or run a query. The widget MUST NOT perform searches
itself or display query results on the home screen (privacy: no query data on the launcher surface).

#### Scenario: Tapping the widget opens Search
- **WHEN** the user taps the widget's search bar
- **THEN** SearchMob opens directly to the Search screen

#### Scenario: No query data on the home screen
- **WHEN** the widget is on the home screen
- **THEN** it shows only a static search affordance and no query text, history, or results

