# Proposal

## Why

Large IPTV playlists cannot be opened in the TV screen. A real Xtream `m3u_plus` source of 92.5 MB with 329,320 entries (2,794 live channels, 36,382 movies, 290,147 series episodes, 177 groups, largest group 51,141 entries) runs out of memory: `TvM3uItem` keeps one `TvM3uTrackItem` per entry in memory (~300 MB), but the app heap is 256 MB. The first open crashes the app with `OutOfMemoryError` after ~15 s of parsing. The second open, from the cached file, fails again and causes an ANR. Download is not the problem: headers arrive in 1.2 s. Of the 14.4 s payload time, however, 9.5 s is spent posting 7,419 progress notifications on the network thread. These numbers were measured with temporary `M3U-PERF` logs on the target phone.

## What Changes

- **Import into a database.** A TV source's playlist is imported, when the source is opened, into a per-source SQLite database. The file is parsed as a stream and inserted in batches, so memory use no longer grows with playlist size. Only one import per source runs at a time.
- **Browse from the database.** Only the items of an opened folder are created in memory. The entries of a large group are released when the user leaves it.
- **Wait screen.** While a playlist is being imported, a wait screen with a percentage is shown. The list appears only when the import has finished, with no partial list. The import keeps running if the user navigates away, and its result is used on return.
- **Re-import only when needed.** A playlist is re-imported only when its content changed (checked by content hash, so an identical re-download does not trigger an import), when a source setting that affects imported data changed, or when the user refreshes or edits the source.
  - A new import replaces the previous one only when it completes with at least one entry. A failed or empty import, such as an HTML error page or a truncated file, keeps the last good list.
  - Re-importing invalidates the source's cached items, so no stale channel is shown or played.
- **Content kinds.** Every entry is classified as live TV, movie or series. The rules, in order: Xtream URL layout (`/movie/`, `/series/`, `/live/`), file extension, `SxxEyy` naming, duration, with live TV as the fallback. When a source has more than one kind, its root shows "TV", "Movies" and "Series" folders that contain the provider's groups. A source with only one kind looks as it does today.
- **Ids unchanged.** Group and channel ids stay the same as today, including the rule that only resolvable entries are numbered and the fallbacks by group name and by URL. Existing favorites, resume positions and per-item preferences keep working.
- **EPG.** EPG (XMLTV) matching is limited to live TV entries in mixed sources. Single-kind sources keep matching every entry, as today.
- **Download notifications.** Download progress notifications are rate-limited, so they no longer slow the download.
- **Errors.** Import failures, including running out of memory and empty results, are reported to the user with the source name, without crashing the app.
- **Cleanup.** The temporary `M3U-PERF` diagnostic logs are removed.

## Capabilities

### New Capabilities
- `iptv-playlist-import`: how TV sources download, import, classify and present M3U playlists: import storage and memory bounds, import triggers and change detection, wait screen, content-kind folders, id stability, EPG scope and error handling.

### Modified Capabilities
<!-- none: car-display-ui requirements are unaffected -->

## Impact

- **`modules/tv`**: `TvM3uItem`, `TvM3uGroupItem`, `TvM3uTrackItem`, `TvRootItem`, `TvFragment`, `XmlTv`, `TvM3uFile`, plus new database, importer and kind-folder classes and strings.
- **`fermata`**:
  - `media/lib`: a shared `M3uParser` and `ContentKindClassifier`; `M3uItem.Data` visibility; `M3uGroupItem` owner instead of the covariant `getParent()`; lazy-children support in `M3uGroupItem`; a hook to release the children of a browsable item.
  - `ui`: the loading indicator in `MainActivityDelegate`, and `MediaItemListViewAdapter`.
- **`depends/utils`**:
  - `HttpDownloadStatusListener` throttling;
  - removal of the diagnostic logs in `HttpFileDownloader` and `HttpConnection`;
  - `SQLite` delete/refcount fix and an option to open with write-ahead logging.
- **Storage**: one new SQLite file per TV source next to the cached `.m3u` and the EPG database, deleted with the source. If the system clears the cache, the playlist is re-imported.
- **Out of scope**: series → season → episode navigation, the Xtream `player_api.php` API, user-selectable content filters, paging inside very large groups, and the plain (non-TV) M3U playlist items of the Folders screen.
