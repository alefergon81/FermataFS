# Design

## Context

- Ad skipping lives in `YoutubeMediaEngine.playing()`. It runs only when the page's `<video>` fires `playing`. It checks `.ad-showing` once and sets `currentTime = duration`. It is gated by `BuildConfig.AUTO && addon.skipAd()` (pref `YT_SKIP_ADD`, default true).
- The page JS is injected by `YoutubeWebView.attachListeners()` on every page load. Each run starts a `findVideo()` loop (every 1 s) that attaches `playing` / `pause` / `ended` listeners to each `<video>`, guarded by the `FermataAttached` attribute. The listeners report through `YoutubeJsInterface.event()`. `FermataJsInterface` posts `handleEvent` to the main thread (`App.get().run`).
- `YoutubeWebView` creates its own `YoutubeMediaEngine`, so there is one per page (phone page and car page). The engine reaches the view only through `web.getWebChromeClient()`; `setVideoView()` is a no-op.
- Fullscreen entry depends on the engine's `playing` path:
  - `playing()` creates a `Current` item and calls `cb.setEngine` / `onEngineStarted`;
  - `YoutubeFragment.onPlayableChanged()` then calls `chrome.enterFullScreen()`;
  - `YoutubeChromeClient.canEnterFullScreen()` also requires the YouTube engine in PLAYING/PAUSED.
- Fullscreen shows the WebView custom view. `YoutubeChromeClient.addCustomView()` puts it into child 0 of `YoutubeVideoView` (a black `FrameLayout`), and child 1 is the `VideoInfoView`. `VideoView.setClockPos()` adds or removes a `TextClock` as the last child, by index. Only the `<video>` element goes fullscreen (`v.webkitRequestFullscreen()`), so YouTube's page UI is not visible there.
- The ad's `ended` reaches `engine.ended()`, which sets `current = end` (whose next playable is `next`, i.e. `web.next()`) and calls `cb.onEngineEnded()`. The ad's `pause` reaches `engine.paused()`, which calls `cb.onPause()`.
- Module resources are loaded with `dynCtx(...)` + `SplitCompat.install(...)` elsewhere in this package (`YoutubeMediaEngine.contributeToMenu`).

## Goals / Non-Goals

**Goals:**
- Decide in the page, at event time, whether a video event belongs to an ad, so the Java side never has to reorder events.
- One status owner on the native side that turns those events into NONE / LOADING / SKIPPING_AD for the fullscreen view.

**Non-Goals:**
- A DOM overlay injected into the YouTube page.
- Network-level ad blocking.
- Any change to `VideoView` in `:fermata` or to other engines.
- Fixing the existing duplicate `findVideo()` chains per page load beyond what the new observer needs.

## Decisions

### 1. Ad state from a guarded MutationObserver on the player element
The injected script keeps its state on `window.FermataAd = { observer, player, ad, skip, savedMuted }`.
- `findVideo()` looks up `#movie_player` (fallback `.html5-video-player`). If it differs from `FermataAd.player`, the old observer is disconnected and a new one is attached to the `class` attribute of the new node. Re-running the script on another page load reuses the same `window.FermataAd`, so there is never more than one observer.
- `ad = player.classList.contains('ad-showing')`. Transitions only are reported as `JS_AD_STATE` with data `"1"` / `"0"`.
*Alternative:* polling `.ad-showing` in the 1 s loop. It is simpler, but it adds up to 1 s of black and was rejected for that reason.

### 2. Ad events are filtered in the page, at event time
The video listeners check the player's `ad-showing` class directly, at the moment the event fires. They do not use the observer's last report.
- `ended` / `pause` while in an ad: not reported.
- `playing` while in an ad: reported as a new event `JS_AD_PLAYING` instead of `JS_VIDEO_PLAYING`.
- Outside an ad: unchanged.
Because the decision and the event are on the same JS turn, a `JS_AD_STATE "0"` arriving first cannot let the ad's `ended` through.
*Alternative:* filtering in Java by the last `JS_AD_STATE`. It was rejected because the order of the class removal and `ended` is not guaranteed (review B2).
The filtering is installed only in the auto flavor. Mobile keeps today's listeners.

### 3. The ad's `playing` still starts the engine
`engine.adPlaying(url)` does what `playing()` does (create `Current`, `cb.setEngine`, `onEngineStarted`), so `onPlayableChanged` enters fullscreen during a pre-roll ad and `canEnterFullScreen` works. It skips the highest-quality change and leaves `qualityUrl` alone. When the requested video then fires `JS_VIDEO_PLAYING`, `playing()` runs as today. If the URL is unchanged, the existing `qualityUrl` check avoids a redundant quality change.
*Alternative:* ignoring the ad's `playing`. It was rejected because the pre-roll would then play inline, with no overlay (review B1).

### 4. Skip and mute run in JS, as soon as the duration is known
The script receives `skip` in its config. The flag is injected at attach time and re-sent from `YoutubeWebView.onPreferenceChanged` when `YoutubeAddon.skipAdChanged(prefs)`. While `ad && skip`:
- on ad start, and on `loadedmetadata` / `durationchange` / `playing`, set `currentTime = duration` when `duration` is finite, and click `.ytp-skip-ad-button, .ytp-ad-skip-button` if present;
- on ad start, save `video.muted` into `savedMuted` and set `muted = true`. On the ad's end, restore `savedMuted`. This uses the element property only, not YouTube's player volume API, so YouTube's stored volume preference is not touched.
The old `.ad-showing` block in `YoutubeMediaEngine.playing()` is removed.

### 5. Status computed by a pure helper, owned by the engine
`YoutubePlayerStatus` (no Android dependencies, unit-tested) holds `skipAd`, `ad`, `awaitingContent`, `waiting` and `paused`, and returns, in this order of precedence:

```
paused                         -> NONE
skipAd && (ad || awaitingContent) -> SKIPPING_AD
waiting                        -> LOADING
otherwise                      -> NONE
```

How the flags are set:
- `ad` follows `JS_AD_STATE`. When it goes from true to false, `awaitingContent` becomes true.
- `awaitingContent` is cleared by `JS_VIDEO_PLAYING`. This closes the gap between the ad end and the content start (review I3).
- `waiting` is set by `JS_VIDEO_WAITING` (`waiting`, `seeking`, `loadstart`) and cleared by `JS_VIDEO_PROGRESS`. The page sends `JS_VIDEO_PROGRESS` once per waiting period, on `seeked` / `canplay` / `playing` or on the first `timeupdate` whose `currentTime` advanced (review I4). It is also cleared by `JS_VIDEO_PLAYING`, `JS_VIDEO_PAUSED` and `JS_VIDEO_ENDED`.
- `paused` is set by `JS_VIDEO_PAUSED` and cleared by `JS_VIDEO_PLAYING` / `JS_AD_PLAYING`. Because ad pauses are filtered in the page, only pauses of the requested video set it.
- The `ad` flag is kept even when `skipAd` is off. In that case it only stops `ad` from being treated as SKIPPING_AD.

`YoutubeMediaEngine` owns the instance and a single pending `Runnable`. After each event it recomputes the status:
- SKIPPING_AD and NONE are applied immediately.
- LOADING is applied after `web.postDelayed(…, 500)`. Any change cancels the pending runnable.
The engine applies the status through `fullScreenView()`, which returns `((YoutubeChromeClient) web.getWebChromeClient()).getFullScreenView()` when the chrome client is in fullscreen and null otherwise. Each page's engine updates only its own view.

### 6. Fullscreen enter/leave
- `YoutubeChromeClient.addCustomView()` calls `engine.applyStatus()`, reached through `((YoutubeWebView) getWebView())`. Re-entering fullscreen during an ad shows the indicator again.
- `removeCustomView()` calls `engine.clearStatus()`, which cancels the pending runnable and sets the view to NONE. Neither call does anything when `!BuildConfig.AUTO`.

### 7. Overlay inside `YoutubeVideoView`, referenced by field
`init()` adds, after the info view, a centered, non-clickable `FrameLayout` overlay. It is stored in a field, so `setClockPos` adding a clock as the last child does not affect it. The overlay holds:
- **SKIPPING_AD:** a rounded semi-transparent dark scrim (a layout background, not part of the icon) with the `ic_ad_blocked` icon (white prohibition circle with "AD" drawn as paths) above the `skipping_ad` text. No spinner.
- **LOADING:** an indeterminate `ProgressBar` only, with no scrim and no text.
- **NONE:** overlay `GONE`.
The icon size is 20% of the view's shorter side, computed in `onSizeChanged`. The text size follows the icon size. Drawables and strings come from the view's context. If that is not the module context on the car activity, the code uses `dynCtx(getContext())` + `SplitCompat.install`, as `contributeToMenu` does. If the clock is at CENTER, it draws over the overlay, which is acceptable.

### 8. Auto-only
Everything new is gated by `BuildConfig.AUTO`: the observer, the filtering, `JS_AD_PLAYING`, the waiting events and the status calls. In the mobile flavor the injected script and the engine behave exactly as today.

### 9. Translations
New text is provided in English (`values`) and Portuguese (`values-pt`, which is already in `resConfigs`). `values-pt` in this module starts with only `skipping_ad` and `try_to_skip_ad`. Other strings of the module and other locales fall back to English. This is a deliberate scope limit.

## Risks / Trade-offs

- [YouTube renames `ad-showing` / `#movie_player`] → no observer, no filtering and no skip, which is the same as skipping disabled. The selectors are in one place in the script and match those already used by `setHighestVideoQuality()`.
- [Some ads ignore the seek (e.g. unskippable server-stitched ads)] → the indicator stays up and the ad is muted until it ends. That is better than black with ad audio.
- [A user pause during an ad is filtered out with the ad's own pauses] → the media session does not show paused during an ad. This is accepted because the user cannot see the ad anyway, and pause works again as soon as the video plays.
- [The ad's `playing` reports the ad as the current item for a moment] → title and duration may briefly be the ad's until the content's `playing` replaces `Current`. This is accepted in exchange for entering fullscreen.
- [The spinner flickers on micro-stalls] → 500 ms debounce.
- [Two pages (phone and car) each run an engine] → each engine only touches its own `YoutubeVideoView` via its own chrome client.

## Migration Plan

None. No stored data changes. `YT_SKIP_ADD` keeps its key and default. Rollback is a revert of the module changes.
