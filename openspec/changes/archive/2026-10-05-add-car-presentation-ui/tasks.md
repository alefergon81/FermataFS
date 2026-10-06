# Tasks

## 1. Spike: Presentation + WebView on the car surface (D1, D5, D6, D8)

- [x] 1.1 Add a throwaway debug-only NAVIGATION `CarAppService`, registered next to `MirrorServiceFS`, that creates a private `VirtualDisplay` on the surface and shows a `Presentation` with a colored background and text, logging the visible and stable areas. Verify on the DHU (`tools\dhu.cmd`) that both the spike entry and FS Mirror appear in the AA launcher, that the content fills the surface at its size/DPI, and record both areas for `tools\dhu-uni-t.ini`
- [x] 1.2 Put a plain `WebView` on m.youtube.com in the spike Presentation and play a video inline and in full screen (custom view). Verify on the DHU that video frames render (not black) and audio plays
- [x] 1.3 Use `NavigationTemplate` with `Action.PAN` in the map action strip and two test actions in the regular strip. Dispatch `onClick` as DOWN/UP and `onScroll` as a naive drag to the decor view. Verify on the DHU that tapping a thumbnail opens it, that dragging scrolls the page, and whether the regular strip stays visible in pan mode
- [x] 1.4 Record the spike results in design.md: pass/fail per item, the D6 strip placement decision, and the D8 visible-vs-stable decision. Then delete the spike service. Verify `git diff` has no leftover spike code

## 2. Service, display host and lifecycle (D1, D4, D11)

- [x] 2.1 Add `car_display_service_name_release` ("Fermata Car") and `_debug` strings to `values/strings.xml`, the `resValue` entries in `fermata/build.gradle` for release and debug, and a new icon drawable. Verify `fermata:assembleAutoDebug -PABI=arm64-v8a` succeeds and the generated `car_display_service_name` resolves to the debug label
- [x] 2.2 Register the new `CarAppService` in `fermata/src/auto/AndroidManifest.xml` (NAVIGATION category, own label and icon). Verify on the DHU that "Fermata Car (debug)" and FS Mirror both appear in the AA launcher and that the Media entry still works
- [ ] 2.3 Implement the display host:
  - create the `VirtualDisplay` + `Presentation` on the first surface;
  - call `setSurface`/`resize` on surface changes;
  - detach on `onSurfaceDestroyed`;
  - release on Session destroy.

  Add a debug-only hook (an `adb shell am broadcast` handled only in debug builds) that re-runs the surface-change path with different dimensions. Verify on the DHU that:
  - switching to another AA app and back keeps the UI;
  - triggering the hook during YouTube playback relays out the UI without stopping playback;
  - restarting the DHU with `-Config none` after `tools\dhu-uni-t.ini` lays out a new session for the new size;
  - disconnecting leaves no crash in `adb logcat`.
- [x] 2.4 Implement the `Screen` with `NavigationTemplate`, `Action.PAN` in the map action strip, and the placeholder actions placed per the D6 spike decision, wired to the host's `SurfaceCallback`. Verify on the DHU that the template renders over the Presentation content and that the actions stay reachable after a drag

## 3. FermataActivity host, fragments and delegate (D2, D3, D9, D10)

- [x] 3.1 Implement `CarHostActivity` (a `ContextThemeWrapper` over the Presentation context with the Fermata theme) implementing `FermataActivity` and owning the lifecycle, ViewModel, saved-state, back-dispatcher and failing activity-result registries. It mirrors `MainCarActivity`, has a non-prompting `checkPermissions` and an `ACTION_MAIN` `getIntent()`. Verify on the DHU via a logcat line that `MainActivityDelegate.getActivityDelegate(view.getContext())` for a view in the Presentation resolves to the car host's delegate
- [ ] 3.2 Implement `CarFragmentHost extends FragmentHostCallback<CarHostActivity>` implementing the owner interfaces by delegation, and drive the `FragmentController` in the D3 order from the Session lifecycle, with the D3 readiness and replay rule. Verify on the DHU that:
  - logcat shows fragments go create→start→resume and back on disconnect;
  - logcat shows the delegate gets `onActivityCreate` then `onActivityResume` exactly once each, both on a normal open and with an artificial delay injected (debug only) into the service connection;
  - a fragment's `OnBackPressedCallback` receives the back action;
  - a fragment-scoped `ViewModel` survives a surface swap.
- [ ] 3.3 In `MainActivityDelegate`, compute the assistant priority from the delegate's own host (`getAppActivity().isCarActivity()`). Set the session activity as D9 says: its own class for real `Activity` hosts, and `MainActivity` (only if unset) for non-Activity hosts. Connect a `FermataMediaServiceConnection` and create the car delegate as `MainCarActivity.onCreate` does. Verify on the DHU that the Fermata UI appears and the AA media controls act on it; verify `:fermata:testMobileDebugUnitTest` still passes
- [ ] 3.4 Scope `ActivityDelegate.setContextToDelegate` to a fallback that is cleared only if it still points to the car delegate. Verify with the phone `MainActivity` open (on a non-YouTube screen) during a DHU session that both UIs work. Then verify that after disconnecting, the phone UI still works and tapping the media notification opens the phone activity. Repeat after first using FS Mirror in the same process
- [ ] 3.5 Add `FermataActivity.getStartFragmentId()` (default `ID_NULL`), consulted first in `defaultIntent()`. `CarHostActivity` returns the YouTube addon fragment id when it is enabled and installed. Verify on the DHU that YouTube opens directly (no other start screen flashes) and that with the addon disabled the normal start screen appears; verify the phone start screen is unchanged

- [ ] 3.6 Implement D11a:
  - the car host pauses a YouTube engine owned by another delegate when it becomes ready;
  - a YouTube engine stops or pauses the current engine only when it owns it;
  - car hosts use a separate resume-position key.

  Verify on the DHU with the phone playing a YouTube video that opening "Fermata Car" pauses the phone video, that pressing back on the phone page does not stop car playback, and that each side resumes at its own position after reopening

## 4. Input: gestures, long press, back/home, text (D5, D6, D7)

- [ ] 4.1 Implement the pure gesture-plan class (click, scroll with idle-UP, fling that continues an open drag, pinch, drag origin = last tap/pinch focus or visible-area center), taking timestamps as input, in the auto source set. Add JUnit tests in `fermata/src/testAuto/java` for each case including the origin and fling-merge rules. Verify with `./gradlew :fermata:testAutoDebugUnitTest`, and that `:fermata:testMobileDebugUnitTest` still compiles and passes
- [ ] 4.2 Wire `onClick`/`onScroll`/`onFling`/`onScale` through the plan to `MotionEvent`s on the Presentation decor view. Verify on the DHU that:
  - taps hit buttons;
  - after tapping a list, drags scroll that list;
  - flings carry momentum without an extra tap;
  - a pinch zooms a zoomable page on m.youtube.com;
  - FS Mirror's behavior is unchanged.
- [x] 4.3 Implement Back (delegate back navigation, staying put at the start screen) and Home (start fragment) in the D6 location. Verify on the DHU from a nested screen and from the start screen, including right after a drag
- [x] 4.4 Add the one-shot long-press toggle (next tap held longer than `getLongPressTimeout()`). Verify on the DHU that a media item's context menu opens and that the following tap is a normal tap
- [ ] 4.5 Implement `startInput`/`stopInput`/`isInputActive`/`createEditText`/`setTextInput` per D7, with voice only when enabled and `RECORD_AUDIO` is granted, otherwise `SearchTemplate`, and `onEditorAction(IME_ACTION_SEARCH)` fired on submit. Verify on the DHU that:
  - a YouTube search *runs* by keyboard;
  - a YouTube search runs by voice with the permission granted;
  - after `adb shell pm revoke <pkg> android.permission.RECORD_AUDIO` the keyboard is used with no phone prompt;
  - with the DHU's driving restriction, the UI does not hang.

## 5. Layout and YouTube polish (D8)

- [ ] 5.1 Apply the area chosen in 1.4 as root content padding, letting full-screen video mode ignore it. Verify with `tools\dhu-uni-t.ini` and `-Config none` that no control sits under the template's actions
- [ ] 5.2 Verify YouTube full-screen video on the car follows the addon's scale setting (contain/fill/cover/none) and that leaving full screen restores the page; fix any `setVideoMode` assumptions found in the car host

## 6. Car-branch audit (scoped to recording gaps)

- [x] 6.1 Audit every `isCarActivity()`/`isCarActivityNotMirror()` branch in `fermata/src/main` and `modules/*` for assumptions that only hold with `CarActivity` or the AA IME. Fix only those on the YouTube, start, back and home paths, and list the rest in design.md. The audit must explicitly cover:
  - the `VideoView` priority rule (`VideoView.java:372`, D9);
  - voice entry points outside `startInput` (`startVoiceAssistant`, the edit-text long-click) and their missing `RECORD_AUDIO` check (D7);
  - how non-web `createEditText` fields receive SearchTemplate results and editor actions (D7).
- [ ] 6.2 Smoke-test opening the other main screens (folders, favorites, playlists, IPTV/TV, web browser, settings) in the car UI. Fix crashes that would take down the car session, and record other gaps in design.md as follow-up work

## 7. Docs and integration

- [ ] 7.1 Update `CLAUDE.md` (Running the auto flavor) to describe the "Fermata Car" entry, that it needs no `dhu.cmd` grants (only `RECORD_AUDIO` for voice), the tap-to-aim drag behavior and the long-press toggle. Verify the documented steps reproduce on the DHU
- [ ] 7.2 Fresh-install check:
  1. `adb uninstall` the auto debug package;
  2. install it;
  3. run `tools\dhu.cmd -SkipPermissions`;
  4. open "Fermata Car".

  Verify that the UI renders and taps/drags work with no prompts on the phone. Then lock the phone and verify the car UI keeps updating (video keeps playing). Without ever opening the phone activity in this process, tap the media notification and verify the phone Fermata activity opens
- [ ] 7.3 End-to-end on the DHU and in the real car, starting with the phone on the YouTube screen playing a video: connect, open "Fermata Car" (phone video pauses), search and play a YouTube video in full screen, switch to the Media entry and back (state kept, car controls still drive the car player), then disconnect. Verify there are no crashes in logcat, that FS Mirror and Media still work, and that the phone media notification opens the phone activity
