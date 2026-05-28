## 1. Branch & dependencies

- [ ] 1.1 Create branch `feat/add-typo-tolerance` off `main`
- [ ] 1.2 Add kt-fuzzy (MIT) and Apache Commons Codec (Apache-2.0) to the version catalog + app build
- [ ] 1.3 Confirm no new Android permission is required

## 2. Bundled dictionary

- [ ] 2.1 Add a committed generation script under `tools/` that builds the dictionary from documented free-licensed sources (a CC0/MIT word-frequency list + a popular-names list)
- [ ] 2.2 Generate a compact compressed asset under `app/src/main/assets/dict/` (common English words with frequencies + names)
- [ ] 2.3 Record the asset's sources + licenses (NOTICE/README) for AGPL + F-Droid compliance

## 3. On-device corrector

- [ ] 3.1 Add `org.searchmob.correct.SpellCorrector` interface returning a correction + confidence (or none)
- [ ] 3.2 Add `Dictionary` loaded from the bundled asset, augmented at runtime from encrypted history; lazy index build off the main thread
- [ ] 3.3 Implement `OnDeviceSpellCorrector`: per-term edit-distance (Jaro-Winkler/Damerau) + Double Metaphone candidates, ranked by frequency x similarity, thresholded; never throws
- [ ] 3.4 Unit tests: known typos and phonetic name matches correct; correct queries are left unchanged (no false positives)

## 4. Upstream correction capture

- [ ] 4.1 Add `correction: String?` to `EngineResult.Success` and an open `parseCorrection(body): String? = null` on `HttpEngineAdapter`
- [ ] 4.2 Implement `parseCorrection` for `DuckDuckGoAdapter` and `MojeekAdapter` (verify selectors against live HTML on the VM)
- [ ] 4.3 `Aggregator` collects per-engine corrections and returns the results plus a consensus correction (most frequent; ties -> first)
- [ ] 4.4 Unit tests: `parseCorrection` against captured HTML fixtures; consensus selection

## 5. Wiring & UI

- [ ] 5.1 In `MetaSearchResultProvider.search`, prefer the upstream consensus correction, else a high-confidence on-device suggestion; expose optional `didYouMean` on the response
- [ ] 5.2 Zero-results + high confidence auto-searches the correction and reports "Showing results for X" with a link to the original; otherwise no auto-rewrite
- [ ] 5.3 Render a "Did you mean: X" banner in the in-app search screen; tapping re-runs the corrected query
- [ ] 5.4 Render the same banner in the browser-facing results HTML

## 6. Verify & ship

- [ ] 6.1 Run `./gradlew ktlintCheck lint test assembleDebug`; confirm green
- [ ] 6.2 Install on the `searchmob` emulator; a slightly misspelled actor name surfaces the banner and the corrected search returns the subject
- [ ] 6.3 Capture network traffic to confirm no new outbound call (corrector offline; correction parsed from the existing search response)
- [ ] 6.4 Open PR against `main`, confirm CI green, merge, then `openspec archive add-typo-tolerance`
