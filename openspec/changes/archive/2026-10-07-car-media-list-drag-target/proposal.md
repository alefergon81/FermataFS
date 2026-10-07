# Proposal

## Why

In the car UI, a drag starts at the last tap, or at the visible-area center when there was none. On IPTV and the other media list screens (folders, favorites, playlists), a playing video puts the screen in split mode: the list sits on the left (30% by default) and the video fills the right. The visible-area center falls inside the video. A drag that starts there, or after a tap on the toolbar or near the divider, does not scroll the list. On the video, a vertical drag changes the volume and a horizontal one seeks. Near the divider, a drag resizes the split. Whether the channel list scrolls depends on where the last tap happened, which is bad while driving.

## What Changes

- On media list screens (IPTV, folders, favorites, playlists) in the car UI, a new drag starts at the center of the visible part of the media list when its origin lies anywhere other than the list, the control panel or the navigation bar. That covers the video, the divider between list and video, the toolbar, the floating button, and the visible-area center used when there was no tap.
- A drag whose origin lies on the list keeps the current rule, so the list scrolls under the last tap as before. The divider and the floating button, which sit on top of the list, do not count as list.
- A drag whose origin lies on the control panel or the navigation bar keeps the current rule, so their own drag gestures (previous/next track, previous/next folder, seek, volume) still work.
- No redirect applies when the list is not shown (video mode, where the video fills the body) or while an overlay menu is open. Drags on the video then keep controlling volume and seek.
- Accepted consequence: while the list and the video are both shown, the car UI no longer reaches the video's volume/seek drags or the divider drag. Tapping the video switches to video mode, where the video drags work as before.
- Taps, pinches and the stored origin are unchanged. The redirect only moves where a new drag's DOWN lands, through the existing `getCarDragOrigin` hook.
- The phone UI and the YouTube redirect are unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `car-display-ui`: the "Drag and fling gestures" requirement now names two redirecting screens (the YouTube watch page and media lists). Its "No redirect outside the YouTube watch page" scenario now excludes media lists as well (OpenSpec cannot rename a scenario, so the name stays). A new requirement, "Drag target on media lists", defines the media list redirect.

## Impact

- `fermata/src/main`: `MediaLibFragment` overrides `getCarDragOrigin`, which covers `TvFragment` in `modules/tv` and `FoldersFragment`, `FavoritesFragment` and `PlaylistsFragment`. It reads the visible rects of the list, control panel, nav bar, divider and floating button when the drag opens. A pure helper, `MediaListDragTarget`, makes the decision and is tested with JUnit in `fermata/src/test`. `BodyLayout`/`SplitLayout` may need a public accessor for the divider views.
- `fermata/src/auto`: no change expected. `CarHostActivity` already forwards the hook to the active fragment and the gesture plan already applies it.
- `modules/tv`: no change; it inherits the override.
