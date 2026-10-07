# Tasks

> Every "verify on the DHU" step uses the **release** universal APK (`NO_GS=true ./gradlew fermata:packageAutoReleaseUniversalApk -PABI=arm64-v8a -PAPP_ID_SFX=.car`). It is only copied to the phone with `adb push <apk> /sdcard/Download/` (Git Bash: `MSYS_NO_PATHCONV=1`), never installed with `adb install` or Android Studio. The user installs it through KingInstaller, and testing waits for their confirmation. Debug builds (`assembleAutoDebug`, `assembleMobileDebug`) are compile checks only.

## 1. Status model

- [x] 1.1 Add the pure helper `yt/YoutubePlayerStatus` (no Android deps, like `CarDragTarget`) with the flags and precedence from design decision 5 (`paused` → NONE first, then `skipAd && (ad || awaitingContent)`, then `waiting`). Add `YoutubePlayerStatusTest` covering:
  - ad + skip → SKIPPING_AD;
  - ad + skip off → NONE;
  - ad + skip + paused → NONE;
  - ad ends before content plays → still SKIPPING_AD;
  - content playing → NONE;
  - waiting → LOADING;
  - waiting + paused → NONE;
  - progress clears waiting → NONE.
  Verify with `./gradlew :web:testAutoDebugUnitTest`.

## 2. Page script: ad state, filtering, skip, mute, buffering events

- [x] 2.1 Add `JS_AD_STATE`, `JS_AD_PLAYING`, `JS_VIDEO_WAITING` and `JS_VIDEO_PROGRESS` to `YoutubeJsInterface` and route them to `YoutubeMediaEngine` (`adState(boolean)`, `adPlaying(url)`, `waiting()`, `progress()`). Verify that the debug build logs each event in logcat when a video with a pre-roll ad starts.
- [x] 2.2 In `YoutubeWebView.attachListeners()` (auto flavor only), add the `window.FermataAd` state and the guarded observer from design decision 1. It is re-attached from `findVideo()` only when the player node changes, with the old observer disconnected. Verify on the DHU, after navigating between several videos, that each ad start/end is logged exactly once.
- [x] 2.3 Filter at event time (design decision 2): while the player has `ad-showing`, the `<video>` listeners send `JS_AD_PLAYING` instead of `JS_VIDEO_PLAYING` and drop `pause` / `ended`. Add the `waiting` / `seeking` / `loadstart` → `JS_VIDEO_WAITING` listeners, and `JS_VIDEO_PROGRESS` once per waiting period on `seeked` / `canplay` / `playing` / the first advancing `timeupdate`. Verify in logcat that no `Video ended` / `Video paused` is logged during an ad, and that the mobile build injects none of this.
- [x] 2.4 Move skip and mute into the script (design decision 4):
  - seek to `duration` once it is finite, and click the skip button if present;
  - save and mute `video.muted` on ad start, and restore it on ad end;
  - pass `skip` at injection, add `YoutubeAddon.skipAdChanged(prefs)`, and re-send the flag from `YoutubeWebView.onPreferenceChanged`;
  - remove the `.ad-showing` block from `YoutubeMediaEngine.playing()`.
  Verify on the DHU:
  - with the setting on, pre-roll and mid-roll ads are skipped with no ad audio and the video plays with sound;
  - with it off, ads play with sound;
  - toggling the setting while on the page takes effect on the next ad.

## 3. Media session

- [x] 3.1 Implement `YoutubeMediaEngine.adPlaying(url)` as in design decision 3: same `Current` / `setEngine` / `onEngineStarted` path as `playing()`, without the highest-quality change. Verify on the DHU, opening a video with a pre-roll ad from the list:
  - the player enters fullscreen during the ad;
  - the media controls never show ended or paused, and never jump to the next item;
  - after the ad, the title and duration are those of the requested video.

## 4. Fullscreen overlay

- [x] 4.1 Add the vector drawable `modules/web/src/main/res/drawable/ic_ad_blocked.xml` (white prohibition circle with "AD" as paths, no background) and the string `skipping_ad` ("Skipping ad…") in `values`. Create `values-pt/strings.xml` with `skipping_ad` ("Pulando anúncio…") and `try_to_skip_ad` ("Tentar pular anúncios"). Verify that `./gradlew fermata:assembleAutoDebug -PABI=arm64-v8a` builds and that the drawable renders in the Android Studio preview.
- [x] 4.2 In `YoutubeVideoView.init()`, add the overlay from design decision 7:
  - stored in a field;
  - centered and non-clickable, `GONE` by default;
  - the SKIPPING_AD composition (scrim, icon, text) and the LOADING composition (spinner only);
  - icon sized to 20% of the shorter side in `onSizeChanged`;
  - `setStatus(NONE|LOADING|SKIPPING_AD)`.
  Load resources via `dynCtx` + `SplitCompat.install` if the view's context is not the module context. Verify on the DHU that:
  - `getVideoInfoView()` still returns the info view;
  - changing the clock position does not break the overlay;
  - fullscreen touch gestures (seek/menu) still work.
- [x] 4.3 Wire the engine to the view (design decisions 5 and 6):
  - the engine owns the `YoutubePlayerStatus` instance and one pending `Runnable`;
  - SKIPPING_AD/NONE are applied immediately, and LOADING via `web.postDelayed(…, 500)`, cancelled on any change;
  - `applyStatus()` runs from `YoutubeChromeClient.addCustomView()` and `clearStatus()` from `removeCustomView()`, both no-ops when `!BuildConfig.AUTO`.
  Verify on the DHU that:
  - during a skipped ad, the icon + text show with no black gap until the video plays;
  - a seek into an unbuffered part shows the spinner;
  - a seek into a buffered part or a SponsorBlock skip leaves no spinner;
  - nothing shows while paused, and nothing reappears after leaving fullscreen;
  - leaving and re-entering fullscreen during an ad brings the indicator back.

## 5. Integration check

- [x] 5.1 Verify end to end:
  - `./gradlew :web:testAutoDebugUnitTest` and `./gradlew :fermata:testAutoDebugUnitTest :fermata:testMobileDebugUnitTest` pass;
  - `NO_GS=true ./gradlew fermata:packageAutoReleaseUniversalApk -PABI=arm64-v8a -PAPP_ID_SFX=.car` builds; copy the APK to `/sdcard/Download/` with `adb push` (no install), ask the user to install it via KingInstaller, and wait for confirmation before testing;
  - on the DHU, in UNI-TV, a video with a pre-roll ad opened from the list enters fullscreen and shows no plain black screen, both with skipping on (icon + text, then video) and off (ad visible and audible);
  - with the phone in Portuguese, the indicator and the setting are in Portuguese;
  - `./gradlew fermata:assembleMobileDebug -PABI=arm64-v8a` builds, and the mobile YouTube player behaves as before (no overlay, no skip, ad events unchanged).
