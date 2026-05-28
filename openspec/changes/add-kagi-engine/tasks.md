## 1. Kagi adapter

- [ ] 1.1 Create branch `feat/add-kagi-engine` off `main`
- [ ] 1.2 Add `KagiApiAdapter` (BYO key, `Authorization: Bot <token>`, parse `data[]` where `t == 0`)
- [ ] 1.3 Register it in the in-app default adapters and the service adapter list
- [ ] 1.4 Add `ApiKeyEngines.KAGI` and a Settings BYO-key row + label string
- [ ] 1.5 Unit test: parse a sample Kagi response (keep results, drop related-search objects)

## 2. BYO keys on all search paths

- [ ] 2.1 Change `MetaSearchResultProvider` to take a registry provider instead of a fixed registry
- [ ] 2.2 Build the service's registry per search from the encrypted engine config (enabled + decrypted keys)
- [ ] 2.3 Confirm the in-app path still applies keys (it already rebuilds the registry per search)

## 3. Verify & ship

- [ ] 3.1 Run `./gradlew ktlintCheck lint test assembleDebug`; confirm green
- [ ] 3.2 On the emulator: the Kagi key row appears, a saved key persists (encrypted) across a restart
- [ ] 3.3 Open PR against `main`, confirm CI green, merge, then `openspec archive add-kagi-engine`
