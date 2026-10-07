# Design

## Context

- The origin hook is in place since the archived `car-youtube-drag-target` change. `CarGesturePlan.openDrag` asks an `OriginResolver` for the DOWN point of every new drag (the first `scroll`, or a `fling` with no open drag). `CarHostActivity.getDragOrigin` forwards the call to the active fragment's `MainActivityFragment.getCarDragOrigin(x, y, originTime)`, which returns null by default. Coordinates are car surface pixels, which equal the pixels of the Presentation decor view (the touch target). Only `YoutubeFragment` overrides the hook today.
- `TvFragment` (`modules/tv`), `FoldersFragment`, `FavoritesFragment` and `PlaylistsFragment` extend `MediaLibFragment`. Its root view is the `MediaItemListView` (a `RecyclerView`). `getListView()` is `requireView()` and throws when there is no view.
- `BodyLayout` (a `SplitLayout`) has three modes, set in `setMode`:
  - `FRAME`: only the list (`SwipeRefresh` visible, video `GONE`).
  - `BOTH`: a video plays on an engine that supports split mode. The list is in a `SwipeRefresh` on one side of a guideline (30% by default, a preference) and the `VideoView` is on the other. `split_line` and `split_handle` (`wrap_content`) are centered on the guideline, so half of the handle lies over the list column. They are declared after the `SwipeRefresh`, so they get touches first. `body_layout_land.xml` puts the list left of the video, and `body_layout.xml` (portrait) puts it above. The rule below does not depend on which.
  - `VIDEO`: the `SwipeRefresh` is `GONE`. A `GONE` view is not laid out again and keeps its old bounds.
- `main_activity.xml` puts the toolbar above the body and the control panel and nav bar below it, outside the body. The `FloatingButton` is drawn over the body and can be dragged.
- Drag gestures outside the list:
  - `VideoView` → `ControlPanelView.onScroll`: horizontal seeks, vertical adjusts the volume, two-finger vertical changes brightness. A single tap on the video in `BOTH` switches to `VIDEO` (`ControlPanelView.onTouch`).
  - `ControlPanelView` itself: `onSwipeLeft/Right` (previous/next track), `onSwipeUp/Down` (previous/next folder), `onScroll` (seek/volume). `FermataNavBarView` forwards its swipes and scrolls to the control panel.
  - `SplitLayout.onTouch` on the handle moves the guideline and saves the preference.
- `MediaLibFragment` does not override `toggleFullScreen()`, so the car's full-screen action does nothing on these screens. For them, "the video fills the screen" means `VIDEO` mode, with or without the bars hidden.
- `ActivityDelegate.getActiveMenu()` returns the open `OverlayMenu`, or null.
- The pull-to-refresh of the `SwipeRefresh` follows `BodyLayout.canChildScrollUp` → `ActivityFragment.canScrollUp()`, which is true by default. Only `FoldersFragment` overrides it (`getScrollY() > 0`), so only Folders refreshes on a downward drag at the top.
- `fermata/src/test` holds JUnit 4 tests with `unitTests.returnDefaultValues = true`, so `Rect`/`RectF` are stubs there. `Log.d` is a no-op in release builds (`BuildConfig.D` false).

## Goals / Non-Goals

**Goals:**
- Decide synchronously when the drag opens, from the current layout. No snapshot or caching.
- Keep the decision rule a pure function over floats, unit-testable without Android types.
- One implementation for every `MediaLibFragment` screen.
- Keep the drag gestures of the control panel and the nav bar.

**Non-Goals:**
- Changing the YouTube rule, the gesture plan, `CarHostActivity` or the phone UI.
- Per-screen tuning (for example aiming at the playing item's row).
- Changing pull-to-refresh. In Folders, a downward drag on a list scrolled to the top still refreshes, as after a tap on the list today.

## Decisions

### D1. Override the hook in `MediaLibFragment`
`MediaLibFragment.getCarDragOrigin` collects the rects (D2) and calls the pure helper (D3). The four screens inherit it, and `modules/tv` needs no change.
- *Alternative:* override only in `TvFragment`. Rejected: the other three screens have the same split layout and the same bug (user's choice in explore).
- *Alternative:* a generic rule in `CarHostActivity` ("find the largest scrollable view"). Rejected: it would also act on screens that do not want it (YouTube, settings) and is harder to reason about. The fragment knows its own list.

### D2. Read visible rects synchronously, in surface pixels
A small local function returns a view's visible rect as `float[4]` {left, top, right, bottom}, or null. It is null when the view is null, when `isShown()` is false, or when `getGlobalVisibleRect(Rect)` returns false. `isShown()` is required because `getGlobalVisibleRect` ignores visibility, and a `GONE` list keeps its old bounds in `VIDEO` mode. The rects are relative to the root view, which in the car is the Presentation decor view, so they are in the same space as `x`/`y` (the YouTube redirect relies on the same space through `getLocationInWindow`).

Views read:
- the list: `getView()` checked with `instanceof MediaItemListView`, not `getListView()`, which throws;
- keep zones (their own drag gestures are kept): the control panel and the nav bar of the main activity delegate;
- blockers (drawn over the list; dragging them must not happen by accident): the divider `split_handle` and `split_line`, and the `FloatingButton`. `SplitLayout` exposes them as `protected`, so a public accessor on `BodyLayout` (or `SplitLayout`) is added.

- *Alternative:* `getLocationInWindow` plus width/height, as `YoutubeWebView` does. Rejected: `getGlobalVisibleRect` also covers clipping by ancestors (the list's column) in one call. Task 2.2 checks the result on the DHU.

### D3. A pure rule: `MediaListDragTarget.resolve`
A package-private final class `MediaListDragTarget` in `me.aap.fermata.ui.fragment` with one static method:

```
@Nullable static float[] resolve(float x, float y, @Nullable float[] list,
                                 float[][] keep, float[][] blockers, boolean menuOpen)
```

Null entries in `keep`/`blockers` are ignored. The rule, in order:
1. null when `list` is null or empty, or when `menuOpen` is set;
2. null when the origin lies in any `keep` rect;
3. null when the origin lies in `list` and in no `blockers` rect;
4. otherwise the center of `list`.

Edges are inclusive. `originTime` is not used: the list is the only scrollable content on these screens, so geometry alone decides. The no-tap case needs no special handling, because the visible-area center then lies on the video (redirected) or on the list (kept).
- *Alternative (user's option B in review):* redirect every origin outside the list, the control panel and the nav bar included. Rejected: it removes the control panel's track/folder swipes and seek/volume drags in the car, even in `FRAME` mode.
- *Alternative:* keep the origin's y and only move x into the list. Rejected: the origin may lie above or below the list (toolbar). The center is a safe point inside it.
- *Alternative:* a time-based rule like YouTube's. Rejected: there is no page navigation to compare against.
- `float[]`, not `PointF`/`RectF`, for the same unit-test reason as the YouTube helper.

### D4. Gate on the car
The hook is only called by the car UI, so the phone never reaches it. The override still returns null when `BuildConfig.AUTO` is false, as an explicit guard that costs nothing.

## Risks / Trade-offs

- [In `BOTH` mode, drag gestures on the video (volume, seek, brightness) and on the divider are no longer reachable in the car] → accepted (proposal). Tapping the video switches to `VIDEO` mode, where they work; the car has its own volume controls.
- [A tap on the video in `BOTH` switches to `VIDEO` only after the double-tap timeout (about 300 ms, `onSingleTapConfirmed`). A drag started within that window is redirected to the list, and the mode then changes under it] → the drag lands on a list that becomes `GONE`, so it does nothing, which is no worse than today's volume change. Not worth extra handling.
- [The list is empty (empty folder)] → the drag lands on the empty list and does nothing.
- [`getGlobalVisibleRect` coordinates differ from surface pixels on some host] → task 2.2 checks by effect on the DHU (no logs in release). The fallback is `getLocationInWindow`, which the YouTube redirect already relies on.
- [A future screen adds another scrollable view next to the list] → its drags would be redirected to the list. That screen can override the hook again.

## Migration Plan

Additive. Rollback means removing the `MediaLibFragment` override; the default returns null and the old rule applies.
