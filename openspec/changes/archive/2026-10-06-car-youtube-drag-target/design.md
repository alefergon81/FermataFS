# Design

## Context

- The car surface callbacks give no drag start point: `onScroll(dx, dy)` and `onFling(vx, vy)` carry deltas only, while `onClick` and `onScale` carry coordinates. This holds for the Car App Library 1.7.0 in use. See the archived `add-car-presentation-ui` design, D5. The DHU never delivers `onFling` (same design, spike results), so fling behavior is verified with unit tests and in the real car only.
- `CarGesturePlan` (pure Java, `fermata/src/auto`) opens a drag with DOWN at `getOriginX/Y()` (the last tap or pinch focus, else the visible-area center). `openDrag` is the only place a drag's DOWN is created, both for the first `scroll` and for a `fling` with no open drag. `CarTouchDispatcher` passes `uptimeMillis()` timestamps and turns the plan into `MotionEvent`s on the Presentation decor view (`CarDisplaySession.getTouchTarget()`). Surface pixels equal decor-view pixels.
- `CarHostActivity` reaches the active fragment through `MainActivityDelegate.getActiveFragment()`, as it already does for home and full screen.
- YouTube is `YoutubeFragment` (module `web`, a dynamic feature) showing `m.youtube.com` in a `YoutubeWebView`. `:fermata` cannot reference module classes, so the hook must live in `:fermata` and be overridden in the module.
- When a YouTube item starts playing on a page other than home, `YoutubeFragment.onPlayableChanged` enters full screen by itself (`chrome.enterFullScreen()`). The two-column watch page is visible only outside full screen.
- The web module already has a JS-to-Java bridge: `FermataJsInterface.event(int, String)`, delivered on the main thread via `App.get().run`, with event codes extended by `YoutubeJsInterface` (`JS_LAST + n`). `YoutubeWebView.pageLoaded` (called from `FermataWebClient.onPageFinished`) is where scripts are injected (SponsorBlock, focus highlight). `FermataWebView.isCar()` is `protected`.
- YouTube navigates as a single-page app, so `onPageFinished` does not fire on every video change. `WebViewClient.doUpdateVisitedHistory` fires on every history change, including `pushState`.
- The WebView supports pinch zoom (`setSupportZoom`/`setBuiltInZoomControls`).
- **Spike results (task 1.1, DHU, 2026-10-06).** Watch page out of full screen, WebView viewport 1768x486 CSS px, `devicePixelRatio` 1, `visualViewport.scale` 1. There are three vertical scrollers:
  - the **document** (`html`, `scrollingElement`). It contains the player (`body > #player-container-id`, 1268 px wide at x 0). Scrolling it, which is what "scrolling the comments" did, moves the player up, off screen;
  - **`ytm-app`**, the whole app (1768 px wide, x 0). It is scrollable, overlaps the player, and does *not* contain the `<video>`, because the player is a sibling of `ytm-app` in `body`;
  - **`div.related-items-container`**, the recommendations (x 1268, 500x438). It stays in place while the document scrolls.

  The original D4 rule ("largest scrollable element not containing `<video>`") picks `ytm-app` (area 1.75M px versus 0.22M), which is wrong. D4 now also requires the target to lie beside the player (see D4). Navigation to a recommendation fires, on `window`: `state-navigatestart`, then `history.pushState` with the new URL, then the Navigation API `navigate` event, `state-change` and `state-navigateend` (about 600 ms after the start). `yt-navigate-finish` does **not** fire on m.youtube.com. Task 5.1 (DHU) confirmed that `doUpdateVisitedHistory` fires for every such `pushState`, while the script's `state-navigatestart` listener fired only on the first navigation of the session. The history callback is therefore the reliable signal, and the script event is a redundant extra.

## Goals / Non-Goals

**Goals:**
- Choose the drag target synchronously when the drag opens. `onScroll` must not wait for JavaScript.
- Keep the plan's rules and the redirect rule unit-testable without Android types.
- Avoid depending on YouTube CSS class names or element ids.

**Non-Goals:**
- Native screens (IPTV, folders). The hook makes them possible later, but nothing overrides it now.
- Changing tap, long-press, pinch or fling semantics, or the stored origin. The redirect only changes where a new drag's DOWN lands.
- Redirecting while zoomed or in full screen.

## Decisions

### D1. An origin hook in the plan, applied only when a drag opens
`CarGesturePlan` records `originTime`: the timestamp of the tap or pinch that set the origin, or `-1` if there is none. It gets an optional resolver, a small pure-Java interface whose method takes `(originX, originY, originTime)` and returns a `float[2]`, or null for "keep the origin". `openDrag` calls it before placing DOWN. The resolver does not change the stored origin or `originTime`. A fling that continues an open drag never calls it.
- *Alternative:* resolve in `CarTouchDispatcher`. Rejected: the open/continue decision lives in the plan, so the resolver belongs there and gets tested there.
- *Alternative:* return `PointF`. Rejected: with `unitTests.returnDefaultValues = true`, `PointF` is a stub in unit tests, so its fields stay 0.

### D2. The fragment owns the decision, via a `MainActivityFragment` hook
`MainActivityFragment` gets `@Nullable float[] getCarDragOrigin(float x, float y, long originTime)`, which returns null by default. Coordinates are decor-view (surface) pixels; `originTime` is `uptimeMillis()`-based, or `-1`. `CarHostActivity` forwards the call to the active fragment, and `CarDisplayScreen`/`CarDisplaySession` install it as the plan's resolver. The method matches existing hooks such as `toggleFullScreen`/`goHome`.

### D3. The origin belongs to a page: compare with the last URL change
`YoutubeWebView` records `urlChangeTime = uptimeMillis()` and clears the layout snapshot (D4) in `doUpdateVisitedHistory` (via the web client), and also when the script reports `state-navigatestart` (task 1.1 found that YouTube navigates with `pushState` between `state-navigatestart` and `state-navigateend`). On the fragment side, `originTime < urlChangeTime` (including `-1`) means "no origin on this page". Tapping a thumbnail therefore always precedes the URL change it causes, so the drag that follows is redirected.
- *Alternative:* reset the plan's origin on navigation. Rejected: the plan has no navigation knowledge, and the fragment already has the timestamp to compare against.

### D4. YouTube keeps a layout snapshot pushed by JavaScript
`onScroll` is synchronous and `evaluateJavascript` is not. So, in the car only, `YoutubeWebView.pageLoaded` injects an idempotent script (guarded by a window flag). The script reports a snapshot through a new `YoutubeJsInterface` event:
- `watch`: whether `location.pathname` is `/watch`;
- `scale`: `visualViewport.scale`;
- `player`: the bounding rect of the `<video>` element (used by the script to find the target, and logged; the Java rule does not use it);
- `target`: the bounding rect of the largest vertically scrollable element (computed `overflow-y` `auto`/`scroll` and `scrollHeight > clientHeight`) that does not contain the `<video>` and lies beside the player. "Beside" means its horizontal range does not overlap the player's (`left >= player.right` or `right <= player.left`). On the spike page this excludes the document (it contains the video) and `ytm-app` (it overlaps the player), leaving `div.related-items-container`.

Rects are CSS pixels multiplied by `devicePixelRatio`. Java adds the WebView's `getLocationInWindow` offset to get decor-view pixels, and also keeps the WebView's visible bounds.

The script reports:
- on `state-navigateend` on `window` (m.youtube.com's navigation-end event, from task 1.1);
- on `resize`;
- on capture-phase `scroll`, debounced about 150 ms, because the player moves when the video column scrolls;
- about 1 s after each `touchend`, to cover layout that settles after a tap-triggered navigation.

Synthetic car taps reach the page as touch events, so the module needs no Java-side tap notification. Drags also end with `touchend`; the extra report they cause is harmless.
- *Alternative:* query on demand and hold the first `onScroll` until the answer arrives. Rejected: it adds latency and queuing to the gesture path, and a JS answer after navigation can still be stale.
- *Alternative:* select the containers by YouTube class or id. Rejected: brittle. The structural rule (scrollable, does not contain the video) survives redesigns better.

### D5. The redirect rule as a pure function in the web module
A pure helper in `modules/web/src/main` (tested in `modules/web/src/test`; the root build gives every module JUnit) takes:
- a `pageOrigin` flag (`originTime > urlChangeTime`, with `originTime` -1 meaning no origin);
- the snapshot (`watch`, `scale` and the `target` rect as plain floats);
- the visible bounds;
- a `fullScreen` flag.

It returns the target center, clipped to the visible bounds, or null. The order of checks:
1. null when there is no snapshot, when it is not a watch page, when there is no target, when `fullScreen` is set, or when `scale` is not 1 (within a small epsilon);
2. otherwise, when `!pageOrigin`, the target center;
3. otherwise null.

**Revised after the DHU check of task 5.3.** The first version also redirected drags whose origin was inside the player rect. On the car layout the player fills the whole video column (rect y 48 to 761, beyond the visible bottom), so every tap in that column counted as "on the player", and the comments could not be reached. The user chose to drop the player rule: only "no tap on this page" redirects, and a tap on the player aims the next drag at the video column. Trade-off: after tapping the player (for example to pause), the user taps the recommendations column (or opens another video) before scrolling it.

`YoutubeFragment.getCarDragOrigin` gathers these from its `YoutubeWebView` (including `chrome.isFullScreen()`) and calls the helper. It is plain floats, not `RectF`/`PointF`, for the same unit-test reason as D1. It lives in the module because the rule is YouTube-specific.

## Risks / Trade-offs

- [A YouTube layout where the recommendations do not lie beside the player or do not scroll on their own (for example a narrower surface that stacks the columns)] → no element qualifies as the target, so the helper returns null and the drag uses the old rule. On the DHU's wide surface they do lie beside it (task 1.1).
- [The snapshot is stale right after navigation] → `doUpdateVisitedHistory` clears it, so the drag uses the old rule until the next report, which arrives within about 1 s.
- [The largest non-video scroller is not the recommendations (for example an open comments panel)] → acceptable: the comments panel is then what the user is looking at. To be checked on the DHU.
- [Scroll-event reporting costs CPU in the page] → debounced and installed only in the car.
- [Zoom changes the mapping between CSS and view pixels] → the redirect is off while `visualViewport.scale` is not 1.
- [`doUpdateVisitedHistory` does not fire for YouTube's `pushState`] → the script also reports `state-navigatestart` through the bridge, and Java records `urlChangeTime` on that event too (D3). The tap that triggers navigation is dispatched before `state-navigatestart`, so either signal orders correctly after it. Task 5.1 checks which signal arrives.

## Migration Plan

Additive. Without an override, every fragment returns null and behavior is unchanged. Rollback means removing the YouTube override or the script install.

## Open Questions

- The exact debounce and post-tap delays. Tune them on the DHU or in the car; they do not change the approach.
