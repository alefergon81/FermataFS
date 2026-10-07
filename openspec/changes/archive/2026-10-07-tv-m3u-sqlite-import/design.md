# Design

## Context

See proposal.md (Why) for the measured failure. How it works today:

- **Parsing.** `TvM3uItem` extends `M3uItem`. `M3uItem.data` is a `FutureRef<Data>` whose `parse()` reads the whole cached `.m3u` on one `App.execute` thread. It builds every `M3uGroupItem`/`TvM3uTrackItem` plus a `uriToTrack` map. An entry is numbered, and its group created, only when `vfs.resolve(line, dir).get(null)` is non-null. The id carries `file.getRid().toString()`, not the raw line. Its `catch (Exception)` does not catch `OutOfMemoryError`. `ifFail` turns any failure into an empty `Data`, so the UI never sees an error.
- **Ids.**
  - Groups: `tvm3ug:<gid>:<srcId>:<group name>`.
  - Channels: `tvm3ut:<gid>:<tid>:<srcId>:<rid>`, with `gid = -1` for ungrouped entries.
  - `TvRootItem.getItem()` resolves them through `M3uItem.getGroup(gid, name)` (by gid, falling back to the name) and `getTrack(gid, tid, uri)` (by `uriToTrack`, falling back to position). Both scan `Data.items`.
- **Ownership.** `M3uGroupItem.getParent()` is overridden as `M3uItem` with a cast. `M3uTrackItem.getM3uItem()` assumes the parent is the `M3uItem` or a group. `M3uItem.Data` has a package-private constructor.
- **Item cache.** `DefaultMediaLib` keeps items in a weak-reference id cache (`addToCache` asserts that ids are unique in debug builds). `BrowsableItemBase.getChildren()` wraps `listChildren()` in a `LoadChildren` promise (`setProgress(list, 1, 2)` before metadata) and keeps it in the `CHILDREN` field.
- **Futures.** `FutureSupplier.then()` drops progress and propagates `cancel()` upstream. `fork()` gives a promise that does not cancel its source.
- **UI loading.** `MediaItemListViewAdapter.setParent()` calls `setChildren` on every progress event and passes `getChildren().main()` to `MainActivityDelegate.setContentLoading()`. That method cancels the previous loading future and shows an indeterminate `ContentLoadingProgressBar`.
- **Eager EPG load.** The `TvM3uItem` constructor calls `xmlTv.get()`, which awaits `getData()`. Parsing therefore starts as soon as `TvRootItem` lists the sources.
- **EPG.** `XmlTv.load()` walks every child of the source to build local `tvg-id`/name → track maps when it parses an XMLTV file. `TvM3uTrackItem.buildMeta()`/`getEpg()` call `XmlTv` for every track that is shown.
- **SQLite helper.** `me.aap.utils.db.SQLite` keeps one `SQLiteDatabase` per file, with a refcount, behind a `PromiseQueue` that runs on the shared `App` executor. `SQLite.delete()` acts only on open databases, closes them regardless of the refcount, and leaves `-wal`/`-shm` files.
- **Downloads.** `M3uFileSystem.load()` → `HttpFileDownloader.download()` with `MAX_AGE = 24 h`. `HttpDownloadStatusListener.onProgress()` builds and posts a notification for every 8 KB write on the network thread.

## Goals / Non-Goals

**Goals:**
- Memory proportional to the folder being viewed, not to the playlist.
- Reopening an unchanged playlist does no parsing.
- Keep today's ids, fallbacks and the behavior of small, single-kind sources.

**Non-Goals:**
- Changing the plain `M3uItem` used by the Folders screen. It keeps parsing into memory, through the shared parser.
- Paging inside very large groups, and search or sorting through the database.

## Decisions

### D1. One SQLite database per TV source, populated by a streaming importer
New classes in `modules/tv/.../m3u`: `TvM3uDb` (schema and queries) and `TvM3uImporter`. The database file is `M3uFile.getCacheDir()/<id>.playlist.db`, that is `<cache>/tvm3u/<id>.playlist.db`, exposed as `TvM3uFile.getPlaylistDbFile()`.

Schema:

```
meta(key TEXT PRIMARY KEY, value TEXT)
     -- schema_version, content_crc32, content_length, file_length, file_mtime,
     -- content_checked_at, video_flag, generation,
     -- header attrs (playlist name, cover, logo base, catchup*, tvg url),
     -- kinds_present, entry_count, group_count
groups(gid INTEGER PRIMARY KEY, name TEXT, kind INTEGER, count INTEGER)
entries(gid INTEGER, tid INTEGER, seq INTEGER, kind INTEGER, name TEXT, rid TEXT,
        logo TEXT, tvg_id TEXT, tvg_name TEXT, album TEXT, artist TEXT,
        genre TEXT, duration INTEGER, type INTEGER,
        catchup TEXT, catchup_days TEXT, catchup_source TEXT,
        PRIMARY KEY (gid, tid)) WITHOUT ROWID
index ix_entries_rid ON entries(rid, seq); index ix_entries_kind ON entries(kind, gid, tid)
index ix_groups_name ON groups(name)
```

The importer and `M3uItem.parse()` share a callback-based `M3uParser` extracted from today's `parse()`. It lives in `fermata/media/lib` and covers the line grammar, header attributes and per-entry fields, so both paths produce the same fields.

Id assignment is a separate, pure `M3uIdAssigner` (also in `fermata/media/lib`) that both paths use:
- input: the parsed entry and the `VirtualResource` from `vfs.resolve(line, dir)`;
- `null` resource → the entry is skipped and consumes no number;
- otherwise: `gid` is assigned at the group's first resolved entry, `tid` is the position within that group (or among ungrouped entries), and `rid = resource.getRid().toString()`. `seq` is the entry's position among all resolved entries (playlist order), used to reproduce the old `uriToTrack` order.

This is today's rule, now testable on the JVM. Fallback logic (A3) and stored `rid` use the same normalized string as the old ids.

During the import, memory holds only the current entry, the `group name → (gid, count, kind)` map (177 entries in the reference list) and the ungrouped counter. Rows are inserted with prepared statements in transactions of ~5,000. Header attributes go to `meta`. `setTvgUrl()` is still called, so EPG configuration is unchanged.

*Alternatives:*
- Lazy in-memory index of byte offsets: every open still scans 92 MB, and id resolution needs a full index.
- `largeHeap`: postpones the failure.
- Xtream `player_api`: only fits Xtream panels. It is out of scope.

### D2. Atomic swap, crash leftovers and change detection
**Import tables.** Each import first drops any leftover `entries_new`/`groups_new` (and their `ix_*_new` indexes) from a killed process. It then creates and fills them. Indexes are built after the inserts, with distinct names (`ix_entries_rid_new`, …).

**Swap.** One transaction does:
1. drop the old tables (which drops their indexes);
2. `ALTER TABLE … RENAME` the new ones;
3. drop and recreate the indexes under the final names;
4. update `meta`.

An import is accepted only if `entry_count > 0`. Otherwise it is a failure (D8) and the previous tables stay. That covers HTML error pages, empty bodies and files without `#EXTM3U` that yield no entries.

**Stamp.** `(content_crc32, content_length, video_flag, schema_version)`, computed over the cached file as stored (raw bytes). The CRC32 pass costs about 1 s for 92 MB and runs on the import executor. It runs only when the file's `length`/`lastModified` differ from `meta.file_length`/`meta.file_mtime`, so an untouched file costs nothing. A re-downloaded but identical file is detected by the CRC and is not re-imported.

**`content://` sources** have no local file. They are re-checked only when `meta.content_checked_at` is older than the source's max age (24 h, the same cadence as downloads) or when the import is forced. The check hashes the content while streaming it from the `ContentResolver`, and imports only when the hash differs. During that pass the progress total is unknown, so the bar stays indeterminate.

**Forced import.** The flag lives in the `TvM3uImport` registry (D5), keyed by the playlist DB file, not in the `TvM3uItem`, so it survives the item being recreated. `TvM3uItem.refresh()`, which `TvFragment` calls after a source is edited and which the refresh action of an open source calls, sets the flag. Refreshing the TV root (`TvRootItem.refresh()` → every source) does not set it: it only clears cached children, so the next open revalidates the stamp. This avoids re-importing every source at once.

**Unreadable database or schema mismatch** → the importer drops every table and index through its own connection (D5) and re-imports into the same file. The file is not deleted, which avoids deleting a database that is still in use. Only an unopenable file (corrupt header) is deleted with `SQLite.delete`, after the read handle is closed (D9).

*Alternative:* write a temp DB file and rename it. Rejected because `SQLite` caches handles per file.

### D3. `TvM3uItem` reads from the database, with explicit cache invalidation
`TvM3uItem` replaces the `M3uItem` data path. `M3uItem.Data` gets a protected constructor, and `getData()` stays overridable.

- **`getData()`** → `TvM3uImport.ensure(source)` (D5), then a light `Data` with the title, subtitle (counts from `meta`/`groups`), cover and root children (D4). `uriToTrack` is not built (empty map). The `FutureRef` stores `importFuture.fork()`, so downstream cancellation never reaches the import.
- **`getGroup(gid, name)`** → the `groups` row by `gid`. When its name differs from `name`, the row by `name` (`ix_groups_name`), as today. The `TvM3uGroupItem` comes from `DefaultMediaLib.getOrCreateCachedItem`.
- **`getTrack(gid, tid, uri)`** → the row by `(gid, tid)`. When `uri != null` and the row's `rid` differs, the row by `rid`, ordered like the old `uriToTrack`: `WHERE rid=? ORDER BY (tid = ?) DESC, seq LIMIT 1`. That is the first entry with the same `tid`, otherwise the first in playlist order. It creates the `TvM3uTrackItem`, and its group if needed, through the cache, without loading siblings.
- **`TvM3uGroupItem.listChildren()`** → `SELECT … WHERE gid=? ORDER BY tid`, creating the items through the cache. The subtitle uses `groups.count`. `M3uGroupItem.tracks` becomes lazily filled. `M3uGroupItem.getTrack(int)` is replaced by an async lookup for TV groups.
- **Invalidation.** Each import has an increasing `generation` (kept in `meta` and in the `TvM3uItem`). After a swap, and in `TvM3uItem.refresh()`, a new `DefaultMediaLib.removeFromCacheIf(Predicate<String> id)` removes the source's cached items. A `TvM3uIds.belongsTo(id, srcId)` helper parses the srcId field exactly for each scheme:
  - `tvm3ug:<gid>:<srcId>:…`
  - `tvm3ut:<gid>:<tid>:<srcId>:…`
  - `tvm3uk:<kind>:<srcId>`
  - `tvm3ue:` + a track id (EPG/archive items)

  An exact field match, unlike a substring, never confuses source 1 with source 12. Then the root `Data` and the `CHILDREN` of the source are cleared. Items still referenced elsewhere, such as the playing track, keep working as detached objects. A later lookup by id creates fresh items from the new import, so the debug `addToCache` assertion cannot fire.
- **`TvM3uItem.refresh()`** overrides `M3uItem.refresh()`. It does not remove the `TvM3uItem` itself from the cache, so the same source object (and its read handle) stays in place. It sets the registry's force flag, invalidates as above and clears its `FutureRef<Data>`.
- **Failed imports.** When the import fails, `getData()` completes with the fallback `Data` (D8), and the `FutureRef<Data>` is then cleared. The next `getChildren()` calls `ensure()` again, which retries, as the spec requires.

### D4. Content kinds and folders
`ContentKindClassifier.classify(url, title, duration)` sits next to `M3uParser`, so the `:fermata` JUnit setup tests both. It is pure and applies the spec's rule order:
- Path check: case-insensitive on the URL path. `/movie/` → movie, `/series/` → series, `/live/` → live TV.
- VOD extensions: `mp4 mkv avi mov wmv flv m4v mpg mpeg webm`.
- Episode regex: `(?i)\bS\d{1,3}\s*E\d{1,4}\b|\b\d{1,2}x\d{2,3}\b`.

A group's kind is the kind of its first entry (`groups.kind`). Every entry keeps its own `kind`, which `TvM3uTrackItem` also exposes for D6.

When `kinds_present` has more than one value, the root lists `TvM3uKindItem` folders:
- id `tvm3uk:<kind>:<srcId>`, dispatched in `TvRootItem.getItem()`; `isChildItemId()` already matches the `tvm3u` prefix;
- localized names `tv_kind_live/movies/series`;
- children: groups of that kind and ungrouped entries of that kind;
- preferences are inherited from the `TvM3uItem`, like groups do today.

Group and track ids do not include the folder.

**Owners.** `M3uGroupItem` and `M3uTrackItem` both get an explicit owner `M3uItem`, passed in new constructors next to the old ones:
- `M3uGroupItem(id, parent BrowsableItem, owner M3uItem, name, gid)`;
- `M3uTrackItem(…, parent BrowsableItem, owner M3uItem, …)`.

`getM3uItem()` returns that owner. The covariant `M3uItem getParent()` override in `M3uGroupItem` is removed, and its callers (`getIconUri()`, `M3uItem`/`TvM3uGroupItem` code) switch to `getM3uItem()`. Parents in a TV source:
- a group's parent is the `TvM3uItem`, or its `TvM3uKindItem` in a mixed source;
- a grouped entry's parent is its group;
- an ungrouped entry's parent is the `TvM3uItem`, or its `TvM3uKindItem`.

The old constructors set the owner from the parent, so the base `M3uItem` (Folders screen) is unchanged.

`TvM3uKindItem extends BrowsableItemBase implements TvItem`, which `TvAddon.isSupportedItem` requires.

**`kinds_present`** is the set of `groups.kind` values plus the kinds of ungrouped entries. A kind that occurs only inside a group of another kind therefore never creates an empty folder.

### D5. Import execution, single-flight and progress
**`TvM3uImport`** (in `modules/tv`) is a process-wide registry keyed by the playlist DB file:
- `ensure(source, force)` returns the in-flight import promise for that file, or starts one. That gives single-flight per source, even if the `TvM3uItem` is recreated.
- Imports run on one dedicated single-thread executor for TV imports. Imports of different sources queue behind each other, so the phone never parses two huge files at once.
- **Connections.** Reads and imports use separate connections, so an import never blocks reads.
  - **Reads:** the `TvM3uItem` owns a read handle, `SQLite.get(playlistDbFile)`, a per-file serial `PromiseQueue` on the shared `App` executor. It is opened lazily and closed in `TvRootItem.itemRemoved` or when the item is collected. Every query (D3, D6, the stamp check) goes through it.
  - **Imports:** the importer opens its own `SQLiteDatabase` connection on the import executor, writes, swaps and closes it.
  - **WAL.** Both connections enable write-ahead logging. Reads of the importing source's previous tables, and every other source, stay responsive during an import, and readers see the new tables after the swap commits.

**Trigger.** `TvM3uItem` no longer calls `xmlTv.get()` in its constructor. `getData()` and `getXmlTv()` start the import lazily. Listing sources (`TvRootItem.listChildren()`, the media browser root) builds `TvM3uItem`s without importing. `M3uItem.getName()`, `getIconUri()` and `buildSubtitle()` all call `getData()`, so `TvM3uItem` overrides all three to read `meta` (playlist name, cover, counts) through the read handle when a database exists. Without one, they fall back to `TvM3uFile.getName()`, no icon and an empty subtitle, and never call `getData()`.

**Progress.** Progress travels through a separate observable, not through the children future. The registry creates a per-source `ImportState` as soon as `ensure()` is called. It has the states `CHECKING`, `IMPORTING(done, total)`, `DONE` and `FAILED(error)`, and keeps the last error until a listener consumes it. `TvM3uItem.getLoadProgress()` returns that state while it is `CHECKING`/`IMPORTING`, or `FAILED` with an unconsumed error, and `null` otherwise. Listeners are called on the main thread with the current state on subscribe and on every change.
- `CHECKING` shows the indeterminate bar.
- `IMPORTING` shows the percentage.
- `FAILED` delivers the error once.
- `done` is raw bytes read from the cached file, counted under any `GZIPInputStream`.
- The total is the stored file length; both are normalized to 0..1000 to stay in `int`.

`:fermata` gets a small `LoadProgress` interface for this. `MediaItemListViewAdapter.setParent()` first calls `getChildren()`, which runs `ensure()` and so creates the state. It then asks the parent for its progress through a new optional `BrowsableItem.getLoadProgress()`, which returns `null` by default. When present, it calls a new `MainActivityDelegate.setContentLoading(future, progress)` that shows a determinate bar and an "Importing playlist… NN%" label. Every other load keeps today's indeterminate bar.

**Download progress.** A playlist download can take a while (92 MB), so it also reports progress. `M3uFileSystem.load()` chains an optional per-file `StatusListener` (`createProgressListener(file)`, null by default) after the notification listener. `TvM3uFileSystem` returns one that publishes `DOWNLOADING(done, total)` on the source's `ImportState` and on a process-wide downloads state (active while any TV playlist download runs), throttled to one update per 250 ms. When the download ends, the source's state returns to `CHECKING` if a check or import is in flight, otherwise to `DONE`. The downloads state is shown by `TvRootItem.getLoadProgress()` (the list of sources waits for downloads) and by the add-source flow in `TvM3uFileSystemProvider.select()`. The UI shows the horizontal bar with "Downloading playlist… NN%", or an indeterminate bar with the downloaded amount when the length is unknown. `LoadProgress.Listener.onLoadProgress` gets a stage (download or import) and the byte count for this.

The children future of a TV source completes only after the import. The adapter therefore never receives partial lists from it. `LoadChildren`'s existing `setProgress(list, 1, 2)` happens after the import, with the real list.

**Navigation.** `setContentLoading` cancels the previous loading future, which today is `getChildren().main()`. `getChildren()` returns the `LoadChildren` promise stored in `BrowsableItemBase.CHILDREN`. Cancelling it marks it done, and the next `getChildren()` returns that cancelled future (`isDone(c)`), so the list stays empty. Two changes fix this:
1. `MediaItemListViewAdapter.setParent()` passes `getChildren().fork().main()` to `setContentLoading` and to its consumer, so cancellation stops only the fork.
2. As a defensive measure, `BrowsableItemBase.getChildren()`/`getUnsortedChildren()` treat a cancelled `CHILDREN` value as absent and call `listChildren()` again.

The import itself is held by the registry, and `getData()` stores a `fork()` of it, so no UI cancellation reaches it. When the user returns, `ensure()` returns the same promise, or the completed database.

### D6. EPG scope
- **`XmlTv.load()`** no longer walks `getUnsortedChildren()`. It asks `TvM3uItem` for the "EPG entries": all entries when `kinds_present` has one value, otherwise `WHERE kind = LIVE`. It builds its id/name maps from those rows, and creates `TvM3uTrackItem`s through the cache only for rows that the XMLTV actually matches (in `addChannel`). The maps stay local to `load()`, as today, so nothing is pinned after the parse.
- **`TvM3uTrackItem.getEpg()` / `buildMeta()`** skip `XmlTv` (empty EPG) when the source is mixed and the entry is not live. A 51k series group therefore makes no EPG lookups.
- **`XmlTv.updateTrack()`** for live tracks created later keeps resolving through the EPG database by tvg id or name, as today.

### D7. Rate-limited download notifications
`HttpDownloadStatusListener.onProgress()` posts only when at least 500 ms have passed since the last post. `onSuccess`/`onFailure` always post or cancel. This applies to every download that uses the listener (playlist, EPG and other `Utils.createDownloader` callers), which matches the spec's wording.

### D8. Error handling
The import task catches `Throwable`, including `OutOfMemoryError`, and treats `entry_count == 0` as a failure. On failure it:
1. rolls back and drops the `*_new` tables;
2. logs the error;
3. fails the import promise with an `IOException` whose message is the localized `tv_import_failed` string formatted with the source name (`TvM3uFile.getName()`).

The UI and the media browser share `getChildren()`, so the failure does not travel through the children future. `TvM3uItem.getData()` always completes successfully: with the previous import's data when one exists, otherwise with an empty `Data`. The error is published on the `LoadProgress` observable (D5) as `onError(Throwable)`. `MediaItemListViewAdapter`, which subscribed through `getLoadProgress()`, shows it with `UiUtils.showAlert`, the same alert it uses for load failures today. Then:
- **UI path:** the alert names the source, and the list shows the last good import or nothing. The children future is reset, so the next open retries.
- **Media browser path** (`DefaultMediaLib.getChildren` → `FermataMediaService.onLoadChildren`): it does not observe progress, so it simply receives the last good list or an empty one. That is better than today's `sendResult(null)` on a failure.

### D9. `SQLite` helper fixes
- `SQLite.delete(file)` deletes the file and its `-journal`, `-wal` and `-shm` siblings, even when the DB is not open.
- When the DB is open, `delete` marks it for deletion and deletes the files when the last reference closes, instead of closing a handle that is still in use.
- `SQLite.get()` can enable write-ahead logging on open (D5).
- `XmlTv` keeps using `SQLite.get(file)`, with unchanged behavior.

### D10. Releasing large groups
New `BrowsableItem.releaseChildren()`, a no-op by default. `MediaItemListViewAdapter.setParent()` calls it on the previous parent when that parent differs from the new one and its `isReleasable()` is true. `mediaItemChanged()` re-sets the same parent, so it never releases. `TvM3uGroupItem` and `TvM3uKindItem` return true and clear `CHILDREN` and `tracks`. The track items themselves are only weakly cached, so items still in use (the playing track, favorites UI) survive, and the others are collected. Reopening the group queries the database again.

### D11. Diagnostic logs
All `M3U-PERF` code in `HttpFileDownloader`, `HttpConnection`, `M3uItem` and `TvM3uItem` is removed. The baseline numbers live in proposal.md.

## Risks / Trade-offs

- [Opening a 51,141-entry group creates ~51k items at once, which takes seconds] → It is measured in the final device check. Paging is an Open Question, and ids would not change if it is added.
- [Import time of 329k rows on a phone, estimated at tens of seconds] → It runs once per content change, with progress shown. Batched transactions, a `WITHOUT ROWID` primary key and indexes built after the inserts keep it low.
- [Android Auto media browser: no wait screen; the first browse of an un-imported source waits for the import, and a 51k-child reply is large] → `MediaBrowserService` delivers children through `ParceledListSlice`, which splits large lists across transactions. The import wait is a one-off. Both are checked on the DHU (tasks 7.2).
- [A provider reorders groups or channels] → The name fallback for groups and the rid fallback for channels, as today.
- [Classifier misfiles entries in non-Xtream lists] → Live TV is the fallback. Single-kind sources keep the old layout and EPG. The rule order is unit-tested.
- [A detached item (playing track from a replaced import) shows old metadata until playback ends] → Acceptable. The next lookup returns the new item.
- [Two connections to one SQLite file] → WAL mode is required on both. The importer's swap is one transaction, and readers that run a query during the swap see either the old or the new tables, never a mix.
- [A crash mid-import leaves `*_new` tables] → They are dropped at the start of the next import (D2).
- [The diagnostic run's second open showed parsing starting 5 ms after a download started] → The importer only reads the file after the `M3uFileSystem.load()` future completes. The downloader renames from `.incomplete`, so partial reads cannot happen. Task 2.6 checks this.

## Migration Plan

- No data migration. On the first open after the update there is no `.playlist.db`, so the source is imported once, with ids identical to the old ones.
- `TvM3uFile.cleanUp()` (source removal) deletes the playlist database through the fixed `SQLite.delete`.
- Rollback: an older build ignores the `.playlist.db` file and parses the `.m3u` as before. It has the same OOM on huge lists, but small lists are unaffected.

## Open Questions

- Should groups above ~1,000 entries be split into range subfolders ("1–1000", …) for car usability? Decide after measuring the 51k group in task 7.2. Adding them does not change ids or the database.
