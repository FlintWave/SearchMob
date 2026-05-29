## Why

Search history is stored encrypted on-device when the user opts in, but there is no way to see it,
remove individual entries, or move it to a new device. Users want to review what is saved, prune it,
and carry it over when they reinstall or switch phones. This builds on the existing encrypted history
store and keeps everything on-device.

## What Changes

- Add an in-app History screen, reachable from Settings, that lists saved queries newest-first with
  their time, lets the user delete a single entry, and clear all. It reads the encrypted store off the
  main thread and only shows entries while history is enabled (store-nothing default is unchanged; with
  history off the screen shows an empty/"turn it on" state).
- Add single-entry deletion to the history store (a new DAO delete plus the in-memory reference), in
  addition to the existing clear-all.
- Export history to a JSON file and import it from one via the Storage Access Framework document
  picker, for backup and new-device setup. Export writes the non-expired entries; import merges entries
  into the store (history must be on to import, matching the store-nothing contract). The file is plain
  JSON the user explicitly chooses a location for; nothing is sent anywhere.

## Impact

- Affected specs: `encrypted-history` (added viewing + export/import requirements).
- Affected code: new `ui/history/HistoryScreen.kt` + `HistoryViewModel.kt`, a `Routes.HISTORY`
  destination and a Settings entry point; `data/history/History.kt` + `HistoryDb.kt` +
  `SqlCipherHistoryStore.kt` (single-entry delete; `HistoryEntry` made serializable for JSON);
  `SearchMobViewModelFactory.kt` (build the history view model).
- No new Android permission (SAF gives per-file access without storage permissions); no new outbound
  network call (export/import is local file I/O the user initiates).
