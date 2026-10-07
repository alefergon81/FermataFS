# iptv-playlist-import Specification

## Purpose

Defines how TV (IPTV) sources download, import and present M3U playlists, so that playlists of any size open reliably, reopen quickly, and keep the user's favorites and positions.

## Requirements

### Requirement: Bounded memory import
Importing a TV source playlist SHALL NOT keep one in-memory item per playlist entry. Peak memory used by an import SHALL NOT grow with the number of entries. Opening a folder SHALL only create items for that folder's direct children.

#### Scenario: Very large playlist imports under the default heap
- **WHEN** a TV source whose playlist has about 330,000 entries (≈92 MB) is opened on a device with a 256 MB app heap limit
- **THEN** the import completes, the source's root folder is shown, and the app neither crashes nor becomes unresponsive

#### Scenario: Opening a group creates only its channels
- **WHEN** the user opens a group of an imported source
- **THEN** only that group's entries are loaded as items, and entries of other groups are not loaded

### Requirement: Large groups are released when left
When the user leaves a group whose entries were loaded, the group SHALL release its list of entries, so that memory does not accumulate across the groups visited. Entries that are still in use, such as the one playing, SHALL stay valid.

#### Scenario: Browsing several large groups in turn
- **WHEN** the user opens a 51,000-entry group, goes back, and opens two other groups of 30,000 entries each in turn
- **THEN** the app does not run out of memory, and reopening the first group lists its entries again

#### Scenario: Playing item survives leaving its group
- **WHEN** a channel of a large group is playing and the user leaves that group
- **THEN** playback, its title and its favorite state are unaffected

### Requirement: Import on demand, one at a time per source
A TV source's playlist SHALL be imported when the source's contents or one of its items are requested, not when the list of sources is shown. At most one import per source SHALL run at a time; further requests during an import SHALL wait for that import.

#### Scenario: Listing sources does not import
- **WHEN** the TV screen or the Android Auto media browser lists the TV sources
- **THEN** no playlist import starts until a source is opened or one of its items is resolved

#### Scenario: Concurrent requests share one import
- **WHEN** a source is opened in the UI while a favorite of the same source is being resolved
- **THEN** a single import runs and both requests receive its result

### Requirement: Import wait screen
While a TV source playlist is being imported, the source SHALL show a wait indicator with the import progress as a percentage, and its contents SHALL appear only when the import has finished, with no partial list. Loading of other folders SHALL keep today's indicator without a percentage.

#### Scenario: Progress is shown during import
- **WHEN** the user opens a TV source whose playlist must be imported
- **THEN** a wait indicator with an increasing percentage is shown until the import finishes, and then the source's contents are shown

#### Scenario: Ordinary folders show no percentage
- **WHEN** the user opens a group, a kind folder or any non-TV folder
- **THEN** no import percentage is shown

### Requirement: Download wait indicator
While a TV source playlist is being downloaded, when a source is added, when a source is opened, or when the list of sources is shown, the screen SHALL show a download indicator with the percentage downloaded, or the amount downloaded when the size is not known, so that the app does not look stuck.

#### Scenario: Adding a source with a large playlist
- **WHEN** the user adds a TV source whose playlist takes a while to download
- **THEN** a "Downloading playlist" indicator with an increasing percentage or amount is shown until the download finishes

#### Scenario: Opening a source whose playlist is downloaded again
- **WHEN** the user opens a TV source whose playlist is downloaded again (for example after its daily max age)
- **THEN** the download indicator is shown during the download, followed by the import indicator if the playlist changed

### Requirement: Import survives navigation
Navigating away from a source during its import SHALL NOT cancel the import. When the user returns, the result of the running or completed import SHALL be used, without starting a new one.

#### Scenario: Leaving and returning during an import
- **WHEN** the user opens a source that is importing, navigates back before it finishes, and opens the source again
- **THEN** the UI stays responsive, the same import continues, and the source's contents appear when it finishes without a second import

#### Scenario: Other sources during an import
- **WHEN** one source is importing and the user opens another, already imported source
- **THEN** the other source's contents open without waiting for the import

### Requirement: Import only when the playlist changed
A TV source SHALL reuse its previous import, without parsing the playlist again, when the playlist content and the source settings that affect imported data are unchanged since that import. A changed content, a changed setting, or a missing or unreadable import SHALL cause a new import. Refreshing or editing the source SHALL force a new import.

#### Scenario: Unchanged playlist opens without parsing
- **WHEN** a previously imported source is opened again and the playlist was not downloaded again (max age not reached or answered as not modified)
- **THEN** the contents are shown from the previous import, with no import wait indicator

#### Scenario: Identical re-download is not re-imported
- **WHEN** the playlist is downloaded again (for example after its daily max age) and its content is byte-identical to the imported one
- **THEN** no import runs and the previous import is used

#### Scenario: Changed playlist is re-imported
- **WHEN** the downloaded playlist content differs from the one that was imported
- **THEN** the playlist is imported again before its contents are shown

#### Scenario: Local content source
- **WHEN** a source points to a `content://` playlist and the user opens it
- **THEN** the playlist is imported the first time and re-imported only when its content changes

#### Scenario: Import storage was cleared
- **WHEN** the import storage of a source is missing, for example because the system cleared the app cache
- **THEN** the playlist is imported again, and favorites keep resolving after the import

#### Scenario: User refreshes or edits the source
- **WHEN** the user refreshes a source or saves changes to it
- **THEN** the playlist is imported again

#### Scenario: Refreshing the TV root
- **WHEN** the user refreshes the TV screen's root, which lists all sources
- **THEN** no source is re-imported unless its playlist content or settings changed

### Requirement: Atomic replacement of an import
A new import SHALL replace the previous import of the same source only after it has completed successfully with at least one entry. A failed import, or one with zero entries, SHALL leave the previous import in use.

#### Scenario: Failed re-import keeps the old list
- **WHEN** a re-import fails partway through, or the app process is killed during it
- **THEN** the source still shows the contents of its last successful import, and the next open retries the import

#### Scenario: Provider returns an error page
- **WHEN** the downloaded playlist is an HTML page or an empty or truncated file that yields zero entries
- **THEN** the import is treated as failed, an error naming the source is shown, and the previous list remains

### Requirement: Re-import invalidates cached items
After a new import replaces the previous one, items of that source SHALL be read from the new import. No group, kind folder or entry SHALL keep showing or playing data of the replaced import.

#### Scenario: Channel moved within the playlist
- **WHEN** a re-import changes the channel at a given group and position, and the user browses that group again
- **THEN** the group lists the channels of the new import, and selecting one plays its new URL

### Requirement: Content kind classification
Each playlist entry SHALL be classified as live TV, movie or series, where the first matching rule wins:
1. An Xtream-style URL path: `/movie/` → movie, `/series/` → series, `/live/` → live TV.
2. A video-on-demand file extension (`.mp4`, `.mkv`, `.avi` and similar) → movie, or series when the title has a season/episode marker.
3. A season/episode marker in the title (`S01E02`, `1x02`) → series.
4. A positive `#EXTINF` duration → movie.
5. Otherwise → live TV.

#### Scenario: Xtream URLs
- **WHEN** entries have URLs `http://h/movie/u/p/1.mkv`, `http://h/series/u/p/2.mp4` and `http://h/u/p/3.ts`
- **THEN** they are classified as movie, series and live TV

#### Scenario: Non-Xtream VOD file with episode marker
- **WHEN** an entry has URL `http://h/files/show.mkv` and title `Show S02E05`
- **THEN** it is classified as series

#### Scenario: Unknown entry
- **WHEN** an entry has URL `http://h/stream/abc` and `#EXTINF:-1` with no season/episode marker
- **THEN** it is classified as live TV

### Requirement: Content kind folders
When an imported source contains more than one content kind, its root SHALL show one folder per present kind ("TV", "Movies", "Series", in that order, localized). Each folder SHALL hold that kind's provider groups, in playlist order, and its ungrouped entries. A group SHALL take the kind of its first entry. A source with a single kind SHALL show its groups and entries at its root, as before.

#### Scenario: Mixed Xtream source
- **WHEN** a source contains live channels, movies and series
- **THEN** its root shows "TV", "Movies" and "Series" folders, and each folder contains the provider groups of that kind

#### Scenario: Live-only source keeps today's layout
- **WHEN** a source contains only live TV entries
- **THEN** its root shows the provider groups directly, with no "TV" folder

### Requirement: Stable item ids
Ids of a TV source's groups and entries SHALL be the ids the previous version produced for the same playlist. Only entries whose location can be resolved get a number, entries are numbered by position within their group, and groups are numbered in the order of their first such entry. Existing favorites, resume positions and per-item preferences SHALL keep resolving to the same items.

#### Scenario: Favorite created before the change
- **WHEN** a channel and a movie of a small mixed playlist were added to favorites with a build before this change and the app is updated
- **THEN** after the source is imported, both favorites open and play the same items, and the channel's resume position is kept

#### Scenario: Group moved by the provider
- **WHEN** a favorite group's number changed because the provider reordered groups, but its name is unchanged
- **THEN** the favorite resolves to the group with that name

#### Scenario: Channel moved by the provider
- **WHEN** a favorite channel's position changed but its URL is unchanged
- **THEN** the favorite resolves to the channel with that URL

#### Scenario: Id resolution without browsing
- **WHEN** an item id of a source is resolved directly (favorites, resume, Android Auto media browser) without browsing its folders first
- **THEN** the item is found from the import without loading the source's other groups

### Requirement: EPG scope
In a source with more than one content kind, EPG (XMLTV) data SHALL be matched only against live TV entries. Movie and series entries SHALL NOT be loaded for EPG matching, and SHALL NOT query EPG when shown. In a single-kind source, every entry SHALL be matched, as before.

#### Scenario: EPG on a mixed source
- **WHEN** a mixed source with an EPG URL is opened
- **THEN** live TV channels show their EPG as before, and EPG loading does not load movie or series entries

#### Scenario: Opening a large VOD group
- **WHEN** the user opens a 51,000-entry series group of a mixed source
- **THEN** no EPG lookup is made for its entries

#### Scenario: Single-kind source
- **WHEN** a source whose entries were all classified as movies has an EPG URL
- **THEN** its entries are matched against the EPG as before this change

### Requirement: Rate-limited download progress
Download progress notifications SHALL be updated at most twice per second per download, plus once when the download completes or fails.

#### Scenario: Large playlist download
- **WHEN** a 92 MB playlist is downloaded
- **THEN** the progress notification is updated at most twice per second, instead of once per received buffer

### Requirement: Import errors do not crash the app
A failure while importing a playlist, including running out of memory or a zero-entry result, SHALL be shown in the UI as an error message that names the source. The app SHALL keep running. Clients without an error UI, such as the Android Auto media browser, SHALL receive the last successful import, or no items if there is none.

#### Scenario: Unparseable playlist in the UI
- **WHEN** a playlist cannot be imported and the user opened the source in the app
- **THEN** an error naming the source is shown, the app keeps running, and the source shows its last successful import, or is empty if there is none

#### Scenario: Failure through Android Auto media browser
- **WHEN** a source's import fails while Android Auto browses it
- **THEN** the browser receives the last successful import's items, or an empty list, and the app keeps running
