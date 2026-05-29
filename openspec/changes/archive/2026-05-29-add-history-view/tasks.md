## 1. Store

- [ ] 1.1 Create branch `feat/add-history-view` off `main`
- [ ] 1.2 Add single-entry delete to `HistoryStore` + the SQLCipher DAO + the in-memory reference
- [ ] 1.3 Make `HistoryEntry` serializable for JSON export/import
- [ ] 1.4 Unit tests: delete one entry; JSON encode/decode round-trip

## 2. Screen

- [ ] 2.1 Add `HistoryViewModel` reading the encrypted store off the main thread (entries newest-first + enabled state)
- [ ] 2.2 Add `HistoryScreen`: list entries with per-entry delete + clear-all; empty/"turn on history" state when disabled
- [ ] 2.3 Add `Routes.HISTORY` and a Settings entry point; build the view model in the factory

## 3. Export / import

- [ ] 3.1 Export history to a JSON file via the SAF `CreateDocument` picker
- [ ] 3.2 Import history from a JSON file via the SAF `OpenDocument` picker (merge entries; requires history on)

## 4. Verify & ship

- [ ] 4.1 Run `./gradlew ktlintCheck lint test assembleDebug`; confirm green
- [ ] 4.2 On the emulator: view recorded queries, delete one, clear all; export to a file and import it back
- [ ] 4.3 Confirm no new permission and no outbound call (SAF file I/O only)
- [ ] 4.4 Open PR against `main`, confirm CI green, merge, then `openspec archive add-history-view`
