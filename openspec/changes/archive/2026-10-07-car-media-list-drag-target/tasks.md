# Tasks

## 1. Pure drag target rule

- [x] 1.1 Add the package-private final class `MediaListDragTarget` with `static float[] resolve(float x, float y, float[] list, float[][] keep, float[][] blockers, boolean menuOpen)` in `fermata/src/main/java/me/aap/fermata/ui/fragment` (design D3), with Javadoc on the rule and the coordinate space. Add `MediaListDragTargetTest` in `fermata/src/test/java/me/aap/fermata/ui/fragment` covering:
  - a null or empty list rect (zero width or height) returns null;
  - `menuOpen` returns null;
  - an origin inside the list rect, and on its edges, returns null;
  - an origin in a keep rect (control panel below the list) returns null;
  - an origin inside the list but also in a blocker rect (divider half over the list, floating button) returns the list center;
  - origins on the video (right of the list), on the toolbar (above it) and at the visible-area center over the video return the list center;
  - null entries in `keep`/`blockers` are ignored.

  Verify with `./gradlew :fermata:testMobileDebugUnitTest --tests 'me.aap.fermata.ui.fragment.*'` and `./gradlew :fermata:testAutoDebugUnitTest --tests 'me.aap.fermata.ui.fragment.*'`.

## 2. Media list override

- [x] 2.1 Override `getCarDragOrigin` in `MediaLibFragment` (D1, D2, D4):
  - return null unless `BuildConfig.AUTO`;
  - get the list from `getView()` with an `instanceof MediaItemListView` check, never `getListView()`;
  - read each visible rect as null unless the view is non-null, `isShown()` and `getGlobalVisibleRect` returns true;
  - keep zones: the control panel and nav bar from the main activity delegate. Blockers: the divider's handle and line (add a public accessor on `BodyLayout`/`SplitLayout`) and the floating button;
  - `menuOpen` is `getActiveMenu() != null`;
  - log the original and redirected points with `Log.d`, as `YoutubeWebView` does.

  Verify that `./gradlew fermata:assembleMobileDebug -PABI=arm64-v8a` and `./gradlew fermata:assembleAutoDebug -PABI=arm64-v8a` build, and that the task 1.1 tests still pass.
- [x] 2.2 Build the release universal APK with `NO_GS=true ./gradlew fermata:packageAutoReleaseUniversalApk -PABI=arm64-v8a -PAPP_ID_SFX=.car`. Push it with `MSYS_NO_PATHCONV=1 adb push <apk> /sdcard/Download/` (Git Bash) and ask the user to install it through KingInstaller, then restart the DHU (`tools\dhu.cmd`), because AA reads the app list only when it connects. `Log.d` is off in release builds, so verify by effect. Open IPTV with a channel playing in split mode and check that:
  - a drag with no tap scrolls the channel list and does not change the volume. This also shows that the rects are in surface pixels;
  - each spec scenario of "Drag target on media lists" that the DHU can show passes ("Fling without a drag" needs the real car; see 3.1). That includes the divider (a tap on its left half, then a horizontal drag) and the control panel (a tap on it, then a horizontal swipe changes the track);
  - in video mode (a tap on the video), a tap on the right half of the video followed by a vertical drag changes the volume, so there is no redirect;
  - on Folders with a local video in split mode the same scenarios hold. With the list scrolled to the top, a downward drag after a tap on the video refreshes the folder, as a drag on the list does today;
  - YouTube home and the YouTube watch page behave as before (spec scenario "No redirect outside the YouTube watch page" and the YouTube requirement).

  If the rects do not match surface pixels, switch to `getLocationInWindow` (design Risks) and record the finding in design.md.

## 3. Integration in the car

- [ ] 3.1 In the real car (Uni-T head unit), with a channel playing in split mode, repeat the 2.2 checks plus "Fling without a drag": a flick with no prior tap must scroll the channel list without changing the volume.
