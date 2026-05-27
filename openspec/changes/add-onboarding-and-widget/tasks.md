## 1. Branch & dependencies

- [ ] 1.1 Create branch `feat/add-onboarding-and-widget` off `main`
- [ ] 1.2 Add `androidx.glance:glance-appwidget` (+ `glance-material3`) to the version catalog + app build
- [ ] 1.3 No new dangerous permissions; confirm manifest adds only the widget `<receiver>` + metadata

## 2. Browser-setup guide

- [ ] 2.1 Implement a `setupUrls(port)` helper producing the visit URL and `…/search?q=%s` template; source the port from `LocalServerState` (handle not-running)
- [ ] 2.2 Implement the guide Compose screen: show both URLs, one-tap copy-to-clipboard each (with confirmation), per-browser steps (Chrome, Firefox, manual), and an "Open in browser" action
- [ ] 2.3 Make the guide reachable from Settings
- [ ] 2.4 Unit-test `setupUrls` (correct template + port) and the clipboard payload

## 3. First-run wizard

- [ ] 3.1 Implement a persisted "onboarding completed" flag via the `PreferencesStore`
- [ ] 3.2 Implement the wizard host (pager) with Skip + Next/Back; show only when not completed
- [ ] 3.3 Welcome page; permissions page (notifications + battery-opt, reflecting current state, user-initiated prompts); default-search page (embeds the browser-setup guide + Open-in-browser); optional privacy page (history/zero-knowledge entry)
- [ ] 3.4 Gate app entry on the flag in `MainActivity`/nav; wire Skip/Finish to persist the flag
- [ ] 3.5 Unit-test the completed-flag gating and wizard step/state logic

## 4. Home-screen widget

- [ ] 4.1 Implement a Glance `GlanceAppWidget` + `GlanceAppWidgetReceiver`; register the `<receiver>` + `appwidget-provider` metadata + preview
- [ ] 4.2 Render a tappable search bar with SearchMob branding; legible in light/dark
- [ ] 4.3 Tapping launches an `actionStartActivity`/deep link into the Search screen (define `searchmob://search` or an intent extra; handle it in `MainActivity`/nav)
- [ ] 4.4 Ensure no query data is rendered on the widget; static affordance only
- [ ] 4.5 Test the deep-link/launch routing (unit/instrumentation where feasible)

## 5. Verify, validate, PR & merge

- [ ] 5.1 `./gradlew --no-daemon ktlintCheck lint test assembleDebug` green
- [ ] 5.2 On-device/VM: complete + skip the wizard (verify it doesn't reappear), copy a URL and paste it, add the widget and tap it to open Search
- [ ] 5.3 `openspec validate add-onboarding-and-widget --strict`
- [ ] 5.4 Open PR, confirm CI green, merge to `main`, then `openspec archive add-onboarding-and-widget`
