# Tasks

> Every device or DHU check uses the **release** universal APK (`$env:NO_GS='true'; .\gradlew.bat fermata:packageAutoReleaseUniversalApk -PABI=arm64-v8a -PAPP_ID_SFX=.car`). The APK is only copied with `adb push <apk> /sdcard/Download/`, never installed with `adb install` or Android Studio. The user installs it through KingInstaller, and checks wait for their confirmation.

## 1. Shared parsing, ids, classification and model hooks (`:fermata`, `:utils`)

- [x] 1.1 Extract the line grammar of `M3uItem.parse()` into a callback-based `M3uParser` in `fermata/src/main/java/me/aap/fermata/media/lib/`, and make `M3uItem.parse()` use it. Verify with `.\gradlew.bat :fermata:testMobileDebugUnitTest --tests 'me.aap.fermata.media.lib.M3uParserTest'`. The test covers quoted attrs, `#EXTGRP`, ungrouped entries, header `catchup*`/`url-tvg`/`url-logo`, `#EXTALB/ART/GENRE/IMG`, CRLF and blank lines.
- [x] 1.2 Add the pure `M3uIdAssigner` (D1): unresolved entries consume no number, `gid` at the group's first resolved entry, `tid` per group or ungrouped, `rid` string. Make `M3uItem.parse()` use it. Verify that `M3uIdAssignerTest` passes on a fixture with unresolved lines, interleaved groups and ungrouped entries. It runs the old parse algorithm (copied into the test as the reference) and the new path on the same fixture, and compares every group and track id.
- [x] 1.3 Add `ContentKindClassifier` (spec rule order, D4 extension list and regex). Verify that `ContentKindClassifierTest` covers every spec scenario (Xtream paths including `/live/`, `.mkv` + `S02E05`, `1x02`, positive duration, unknown → live) and passes.
- [x] 1.4 Model changes in `fermata/media/lib`:
  - `M3uItem.Data` gets a protected constructor;
  - `M3uGroupItem` and `M3uTrackItem` get owner constructors and `getM3uItem()`, and the covariant `M3uGroupItem.getParent()` is removed (callers updated);
  - `BrowsableItem.releaseChildren()`, `isReleasable()` and `getLoadProgress()` get defaults;
  - `DefaultMediaLib.removeFromCacheIf(Predicate<String>)` is added;
  - `BrowsableItemBase.getChildren()`/`getUnsortedChildren()` treat a cancelled `CHILDREN` as absent;
  - `MediaItemListViewAdapter.setParent()` uses `getChildren().fork().main()`, then `getLoadProgress()`, and releases the previous parent only when it differs and `isReleasable()`.

  Verify that the `:fermata` unit tests pass, and that on the device (release APK) a Folders-screen `.m3u` with groups still browses, shows group icons and plays. Also verify that leaving a slow-loading folder and reopening it shows its contents, not an empty list.
- [x] 1.5 Fix `me.aap.utils.db.SQLite` (D9): delete closed DBs and `-wal`/`-shm`/`-journal`, defer deletion while referenced, and add an option to open with write-ahead logging. Verify with a `:utils` JVM test of the file-deletion path (`.\gradlew.bat :utils:testDebugUnitTest`) and that the EPG still loads on the device in task 5.1.
- [x] 1.6 Throttle `HttpDownloadStatusListener.onProgress()` to one post per 500 ms, while `onSuccess`/`onFailure` always post or cancel. Verify with the temporary `M3U-PERF` counters on the 92 MB download: `onProgress` time is under 1 s and payload time drops well below the 14.4 s baseline. Record both numbers in this task.
  - 2026-10-07 run (92,502,676 bytes, chunked): `onProgress` 10,551 calls, 152 ms total. Payload 13,862 ms (6.36 MB/s), so the payload did not drop below the 14.4 s baseline.

## 2. Playlist database and importer (`modules/tv`)

- [x] 2.1 Add `TvM3uFile.getPlaylistDbFile()` (`<cache>/tvm3u/<id>.playlist.db`). On source removal, cancel any in-flight import, close the source's read handle, then delete the file in `TvM3uFile.cleanUp()` via `SQLite.delete`. Verify that removing a source deletes the `.playlist.db` and its `-wal`/`-shm`, including when the DB was never opened in this process (`adb shell ls` of the cache dir).
- [x] 2.2 Implement `TvM3uDb`: D1 schema (including `seq` and the `meta` keys), the D2 `*_new` creation, leftover cleanup and swap, drop-all-and-reimport on a schema mismatch, plus the queries for root listing, group by gid, group by name, entries by gid, entry by (gid, tid), entry by rid (`ORDER BY (tid = ?) DESC, seq`), EPG entries and meta. Verify each query against the reference import in 2.6.
- [x] 2.3 Implement `TvM3uImporter` on the dedicated executor, with its own WAL connection:
  - streams the file through `M3uParser`, `vfs.resolve`, `M3uIdAssigner` and `ContentKindClassifier`;
  - inserts in ~5,000-row transactions and builds indexes after the inserts;
  - counts raw bytes for progress and computes CRC32 while streaming;
  - rejects `entry_count == 0`;
  - catches `Throwable` with rollback.

  Verify with 2.6.
- [x] 2.4 Implement the D2 stamp check: fast `file_length`/`file_mtime` check, then CRC32, the `video_flag`, `schema_version` and forced-import flag (kept in the registry), the `content://` path (re-checked after the max age or when forced), and re-import of an unreadable DB. Verify with 2.6 that a reopen logs no import, and that touching the file's mtime without changing its content logs a CRC check but no import.
- [x] 2.5 Implement the `TvM3uImport` registry: single-flight per DB file, force flag, and the `ImportState` observable (`CHECKING`/`IMPORTING` 0..1000/`DONE`/`FAILED` with a retained error, listeners on the main thread and the current state on subscribe). Verify on the device with a temporary log that opening a source while one of its favorites is resolved logs a single import.
- [x] 2.6 Device check of the reference playlist (temporary logs). Confirm:
  - no OOM, and record the import time;
  - `groups`/`entries` counts match the file (329,320 entries, 177 groups, minus unresolved lines);
  - the import starts only after the download future completes;
  - a reopen skips the import;
  - an HTML response (point a test source at an HTML URL) fails with zero entries and keeps the previous tables;
  - after a successful import, change the source URL to force a re-import and kill the app mid-import: the previous tables are still used, and the next open drops the `*_new` leftovers and retries.

## 3. Browsing from the database (`modules/tv`)

- [x] 3.1 `TvM3uItem`:
  - `getData()` goes through `TvM3uImport.ensure()` and stores a `fork()`;
  - a light `Data` with root children (kind folders or single-kind groups and entries) and no `uriToTrack`;
  - `setTvgUrl()` from `meta`;
  - the constructor stops calling `xmlTv.get()`, which loads lazily;
  - `getName()`, `getIconUri()` and `buildSubtitle()` are overridden to read `meta` or `TvM3uFile`, never `getData()`;
  - the `FutureRef<Data>` is cleared after a failed import, so the next open retries.

  Verify that a live-only source shows the same root as before, and that listing sources (TV screen and DHU media browser) logs no import.
- [x] 3.2 `getGroup(gid, name)` with the name fallback, and `getTrack(gid, tid, uri)` with the rid fallback, both as single-row queries through `getOrCreateCachedItem`. `TvM3uGroupItem.listChildren()` and its subtitle become lazy. Verify on the device that opening one group creates only its items (temporary created-item counter), and that a favorite group still resolves after renumbering. To test that, edit a local copy of a small playlist to swap two groups and point a test source at it.
- [x] 3.3 Add `TvM3uKindItem` (`tvm3uk:<kind>:<srcId>`, `implements TvItem`) as the parent of its groups and ungrouped entries (with the `TvM3uItem` as owner), its dispatch in `TvRootItem.getItem()`, preference inheritance, and `kinds_present` from group kinds plus ungrouped entry kinds. Add the strings `tv_kind_live`, `tv_kind_movies`, `tv_kind_series` in English and in each locale that `modules/tv` already translates. Verify that a mixed source shows "TV / Movies / Series" in that order, each with only its groups, and that kind folders and groups show sensible subtitles and descriptions.
- [x] 3.4 Re-import invalidation (D3): increase the generation, add `TvM3uIds.belongsTo` (exact srcId parse for `tvm3ug`/`tvm3ut`/`tvm3uk`/`tvm3ue`) with `removeFromCacheIf` after the swap and in `TvM3uItem.refresh()`, which keeps the source item, sets the registry force flag and clears its `FutureRef<Data>`. Verify with a JVM test of `belongsTo` (source 1 vs 12, every scheme). Then, on the device (release APK):
  1. play a channel;
  2. edit the source so the playlist changes (local test file with the channel moved);
  3. save and browse the group again: the new channel list is shown, selecting a channel plays its new URL, and no stale item appears;
  4. refresh the TV root: the log shows no re-import.
- [x] 3.5 `releaseChildren()` for `TvM3uGroupItem` and `TvM3uKindItem`, called from `MediaItemListViewAdapter.setParent()`. Verify on the device that opening the 51k group, going back and opening two ~30k groups in turn does not OOM, and that a channel playing from the first group keeps playing.
- [x] 3.6 Id compatibility. Make a small mixed fixture playlist (live, movie and series entries in groups, some ungrouped, one unresolvable line) and host it, or push it, as a source. Then:
  1. with a release APK built from `main` before this change (copied to `/sdcard/Download/`, installed by the user), favorite a channel and a movie, and play the channel to save a position;
  2. copy the new release APK to `/sdcard/Download/` and ask the user to install it through KingInstaller;
  3. confirm both favorites open the same items and the position is kept;
  4. resolve a `tvm3ut:` id through the DHU media browser without browsing first.

## 4. Wait screen (`:fermata` UI)

- [x] 4.1 Add `MainActivityDelegate.setContentLoading(future, LoadProgress)` with a determinate bar and the "Importing playlist… NN%" label (new `:fermata` string, in English and every locale in `resConfigs`), indeterminate during `CHECKING`, and the alert on `FAILED`. Wire it from `MediaItemListViewAdapter.setParent()` when `getLoadProgress()` is non-null. Verify in the app UI, both on the phone and in the UNI-TV entry on the DHU (the media browser has no wait screen), that the percentage advances during an import and the list appears only at the end, and that opening a group or a Folders-screen folder shows only the indeterminate bar.
- [x] 4.2 Verify in the UNI-TV entry on the DHU that:
  - navigating back during an import keeps the UI responsive;
  - the import keeps running (a temporary log shows it finishing);
  - returning shows the result, not an empty list, without a second import;
  - another already imported source opens normally while the import runs.
- [x] 4.3 Download indicator (requested during the device check): add the `DOWNLOADING` stage to `LoadProgress`/`ImportState`, the `M3uFileSystem.createProgressListener()` hook, the process-wide downloads state used by `TvRootItem` and the add-source flow, and the "Downloading playlist… NN%" / "… N MB" strings (`:fermata`, English and every locale in `resConfigs`). Verify on the phone that adding the reference source shows the download percentage, and that opening a source after deleting its cached `.m3u` (`adb shell rm`) shows the download indicator and then the import percentage.

## 5. EPG

- [x] 5.1 `XmlTv.load()` uses the D6 "EPG entries", and `TvM3uTrackItem.getEpg()`/`buildMeta()` skip EPG for non-live entries of mixed sources. Verify on the device that:
  - live channels of the mixed source show EPG;
  - the created-item count during EPG load is no more than the number of matched live channels;
  - opening a series group logs no EPG lookups;
  - a single-kind test source still gets EPG for all entries.

## 6. Error handling

- [x] 6.1 Implement D8: catch `Throwable`, treat zero entries as a failure, use the `tv_import_failed` string with the source name (`modules/tv` strings, English and the module's translated locales), fall back to the previous import, and give the media browser the last good list or an empty one. Verify on the device that:
  - an HTML/empty playlist with a previous import shows an alert naming the source and keeps the old list;
  - without a previous import, it shows the alert and an empty source;
  - reopening the failed source retries the import (log);
  - through the DHU media browser it returns the old list or an empty list, without a crash.

## 7. Cleanup and integration

- [x] 7.1 Remove all `M3U-PERF` code from `HttpFileDownloader`, `HttpConnection`, `M3uItem` and `TvM3uItem`, and every temporary log added in this change. Verify that `git grep -n "M3U-PERF"` returns nothing.
- [x] 7.2 Run `.\gradlew.bat :fermata:testMobileDebugUnitTest :utils:testDebugUnitTest`, then build with `$env:NO_GS='true'; .\gradlew.bat fermata:packageAutoReleaseUniversalApk -PABI=arm64-v8a -PAPP_ID_SFX=.car`, copy the APK to `/sdcard/Download/` and ask the user to install it through KingInstaller (no `adb install`). Verify on the DHU, end to end:
  - first open imports with progress;
  - reopen is instant;
  - TV/Movies/Series navigation works;
  - a live channel plays with EPG;
  - a movie plays;
  - the media browser lists the source and plays a channel.

  Record the time to open the largest (51k) group for the paging open question.
