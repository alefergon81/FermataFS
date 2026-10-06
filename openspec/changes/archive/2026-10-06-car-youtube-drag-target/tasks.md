# Tasks

## 1. Spike: watch-page structure on the DHU

- [x] 1.1 On the DHU, open a watch page in the car UI out of full screen and inspect the WebView with `chrome://inspect`. Record in design.md (Context, replacing the "Not yet verified" item):
  - which element scrolls each column, and whether either is the document/`body`;
  - whether the D4 rule ("largest scrollable element not containing `<video>`") picks the recommendations;
  - which navigation events fire when opening a recommendation (for example `yt-navigate-finish`, `popstate`);
  - whether `doUpdateVisitedHistory` fires on that navigation (from a temporary log).

  Revise D4/D5 if the findings contradict them. Verify that design.md holds the findings and that `openspec validate car-youtube-drag-target --strict` passes.

## 2. Origin hook in the gesture plan

- [x] 2.1 In `CarGesturePlan`, record `originTime` (`-1` when there is no origin) and add the optional pure-Java resolver `(originX, originY, originTime) -> float[2] or null`. Apply it only in `openDrag`, leaving the stored origin unchanged (D1). Update the class Javadoc on the drag origin rule. Add tests to `fermata/src/testAuto/java/me/aap/fermata/auto/CarGesturePlanTest.java` for:
  - a redirected DOWN with no tap (the resolver gets `-1`);
  - a redirected DOWN after a tap (the resolver gets the tap time);
  - a null result keeps the origin;
  - a fling with no open drag is redirected;
  - a fling that continues an open drag does not call the resolver;
  - the next tap still sets the origin;
  - a tap's DOWN/UP times are unchanged by the resolver (no added delay).

  Verify with `./gradlew :fermata:testAutoDebugUnitTest`.

## 3. Fragment hook and car wiring

- [ ] 3.1 Add `@Nullable float[] getCarDragOrigin(float x, float y, long originTime)` to `MainActivityFragment`, returning null by default, with Javadoc on the coordinate space and the time base (D2). Forward it through `CarHostActivity` for the active fragment, and install it as the plan's resolver via `CarDisplayScreen`/`CarDisplaySession`/`CarTouchDispatcher`. Verify that:
  - `./gradlew fermata:assembleAutoDebug -PABI=arm64-v8a` builds;
  - `./gradlew :fermata:testAutoDebugUnitTest :fermata:testMobileDebugUnitTest` pass;
  - on the DHU, with the `autoDebug` variant (including the `web` module) installed from Android Studio, drags on YouTube home and on IPTV behave as before (spec scenario "No redirect outside the YouTube watch page").

## 4. Pure redirect rule

- [x] 4.1 Add the pure helper in `modules/web/src/main` (D5). Inputs:
  - the `pageOrigin` flag;
  - the `watch`/`scale`/`target` snapshot;
  - the visible bounds;
  - the `fullScreen` flag.

  Output: the clipped target center, or null. Add JUnit tests in `modules/web/src/test` for every branch:
  - no snapshot, not a watch page, no target;
  - full screen;
  - zoomed (`scale` not 1);
  - `!pageOrigin` redirects;
  - `pageOrigin` keeps the origin (revised in 5.3: the player rule was dropped);
  - clipping to the visible bounds.

  Verify with `./gradlew :web:testAutoDebugUnitTest`.

## 5. YouTube layout snapshot and override

- [x] 5.1 In `YoutubeWebView`, record `urlChangeTime` and clear the snapshot both in `doUpdateVisitedHistory` and on the script's `state-navigatestart` event (D3). Add the `YoutubeJsInterface` snapshot event: parse it into decor-view pixels (add the `getLocationInWindow` offset) and store it with the visible bounds (D4). Verify on the DHU with a debug log that a recommendation tap logs the URL change before the next snapshot, record in design.md whether `doUpdateVisitedHistory` fired for the `pushState`, and that the parsed rects match the columns seen in `chrome://inspect`.
- [ ] 5.2 Write the idempotent script (D4: watch flag, `visualViewport.scale`, `<video>` rect, target rect). It reports `state-navigatestart` as a navigation event and sends a snapshot on `state-navigateend`, on `resize`, on debounced capture-phase `scroll`, and about 1 s after `touchend`. Inject it from `YoutubeWebView.pageLoaded` only when `isCar()`. Verify on the DHU that:
  - the logged snapshot updates after opening a recommendation, after scrolling the video column and after a pinch;
  - the phone YouTube page does not inject the script (no snapshot log in the phone UI).
- [ ] 5.3 Override `getCarDragOrigin` in `YoutubeFragment`. It computes `pageOrigin` from `originTime` and `urlChangeTime`, reads `chrome.isFullScreen()`, and calls the helper from 4.1. Verify on the DHU every scenario of the spec requirement "Drag target on the YouTube watch page" except the two fling scenarios (the DHU does not deliver `onFling`). Exit full screen first where a scenario needs the two-column page.

## 6. Integration in the car

- [ ] 6.1 In the real car (Uni-T head unit), repeat the 5.3 scenarios, plus "Fling without a drag" and "Fling keeps the redirected drag". Also check that:
  - full screen and a zoomed page do not redirect;
  - an open comments panel or a stacked layout falls back without errors (design Risks).

  Tune the debounce and post-tap delays if needed and record the values in design.md.
