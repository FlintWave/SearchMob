## Why

Some users have a Kagi subscription and want their searches to use Kagi's index. Kagi offers a Search
API keyed by a personal token, which fits SearchMob's bring-your-own-key model (like the existing
Brave and Mojeek API engines): free engines by default, optional paid engines the user enables with
their own key. While adding it, fix a pre-existing gap: BYO API keys currently only take effect for
in-app searches because the foreground service builds its engine registry without the stored keys, so
keyed engines never activate on the browser-facing `/search`.

## What Changes

- Add `KagiApiAdapter`, a bring-your-own-key engine for the Kagi Search API
  (`GET https://kagi.com/api/v0/search?q=`, `Authorization: Bot <token>`). It parses the `data` array,
  keeps search-result objects (`t == 0`) and ignores related-search objects, and is inactive until the
  user supplies a key. Registered in the in-app and service engine lists.
- Add a Kagi entry to the BYO API-key settings section (key stored encrypted at rest like the others).
- Make BYO API keys apply on every search path: `MetaSearchResultProvider` takes a registry provider
  instead of a fixed registry, and the foreground service builds the registry per search with the
  per-engine enabled flags and decrypted keys from the encrypted store. So a configured Brave, Mojeek,
  or Kagi key now activates that engine for both the in-app search and the browser `/search`.

## Impact

- Affected specs: `engine-adapters` (added Kagi requirement + keys-on-all-paths requirement).
- Affected code: new `engine/adapters/KagiApiAdapter.kt`; `ui/AppDependencies.kt` (default adapters) and
  `service/SearchMobService.kt` (service adapter list + build registry from the encrypted store);
  `engine/MetaSearchResultProvider.kt` (registry provider); `ui/ApiKeyEngines` + `ui/settings/SettingsScreen.kt`
  (+ a string).
- No new Android permission. The Kagi endpoint is contacted only when the user has supplied a key and
  Kagi is enabled, and the request goes through the existing privacy-proxy client.
