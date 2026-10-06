# Design

## Context

See proposal.md (Why). Relevant current state:

- `MirrorServiceFS` (auto flavor) is a Car App Library `CarAppService` in the NAVIGATION category. It enables pan mode (`setMapActionStrip(... Action.PAN ...)`, `MirrorServiceFS.java:160-162`) and receives a `SurfaceContainer` plus click/scroll/scale callbacks. It feeds them to `MirrorDisplay`, which draws a MediaProjection mirror of the phone into the surface and injects touches. Its drag synthesis starts every drag at the surface center or the last scale focus (`:94-95, 118-121, 146-147`).
- `MainCarActivity` (auto flavor) runs a full `MainActivityDelegate` on the car through `CarActivity` from `lib/auto/aauto.aar`. Per `javap` on the aar's `classes.jar` (signatures only; no source in the repo, so internals are unverified), `CarActivity` is a `ContextWrapper` subclass exposing `setContentView`/`findViewById`, `getSupportFragmentManager`, lifecycle callbacks and `getLayoutInflater`. That entry only appears for the official package.
- `MainActivityDelegate` talks to its host only through `AppActivity`/`FermataActivity`. `ActivityDelegate.getActivityDelegate(ctx)` checks the context itself, then walks `getBaseContext()` until it finds an `AppActivity`, and finally falls back to the static `contextToDelegate` (`ActivityDelegate.java:122-141`).
- `MainActivityDelegate.onActivityCreate` has these side effects:
  - It replaces the media session activity with `PendingIntent.getActivity(ctx, 0, new Intent(ctx, a.getClass()))` (`:242-243`).
  - It registers itself as an assistant with priority `isCarActivityNotMirror() ? 0 : 1` (`:244`). `isCarActivity()` is `AUTO && host.isCarActivity() || isMirroringMode()` (`:446-451`), so mirroring mode is a global flag.
  - It calls `checkPermissions(...)`, then picks the start screen: a restored `fragmentId`, a non-MAIN intent, or `defaultIntent()` (`:253-276`). `defaultIntent()` honors `getShowAddonOnStartPref()`, then the last played item, then folders (`:330-359`).
- The YouTube addon (`modules/web`, `yt/*`) is a `WebView` on m.youtube.com, and Chromium renders the video itself. Search submits only through `OnEditorActionListener` on `IME_ACTION_SEARCH`/`GO`/`DONE` (`FermataWebView.java:341-350`); `afterTextChanged` only fills the field (`:336-338`). The keyboard is requested through `getAppActivity().startInput(this)`.
- `startSpeechRecognizer` skips the `RECORD_AUDIO` check when `isCarActivityNotMirror()` (`MainActivityDelegate.java:845-847`). With the Car App Library, the platform `SpeechRecognizer` uses the phone microphone, so the permission matters.
- Service labels are `_release`/`_debug` string pairs selected through `resValue` in `fermata/build.gradle` (lines 36-52).
- The Car App Library is `androidx.car.app` 1.7.0, with `minCarApiLevel` 5. That gives `onClick` (API 5), `onScroll`/`onFling`/`onScale` (API 2, in pan mode) and `onVisibleAreaChanged`/`onStableAreaChanged`. androidx fragment is 1.9.0.

## Goals / Non-Goals

**Goals:**
- Reproduce what `CarActivity` gives `MainCarActivity`, using only public APIs, on top of a Car App Library surface.
- Reuse `MainActivityDelegate`, the fragments and the web/YouTube module unchanged wherever possible. `MainActivityDelegate` changes are limited to D9/D10, and `modules/web` changes to D11a.
- Keep the new service independent of FS Mirror: no edits to `MirrorServiceFS`/`MirrorDisplay`.

**Non-Goals:**
- Changing or refactoring FS Mirror, `MirrorService`, `CarService` or the media service.
- Rotary/D-pad input. Car App Library surfaces do not deliver key events to the app.
- Using the car microphone (`CarAudioRecord`). Voice uses the phone microphone only when its permission is already granted.
- Making every other Fermata screen work well in the car. Phase 3 records gaps and fixes only crashes on the start/back/home paths.
- Play Store compliance (video in a navigation app is against policy; this targets sideloaded builds).

## Decisions

### D1. Private VirtualDisplay + Presentation instead of drawing on the surface
Create `DisplayManager.createVirtualDisplay(name, w, h, dpi, carSurface, 0)` and show an `android.app.Presentation` on it. A private display owned by the app needs no permission, and the Presentation gives a real window with a hardware-accelerated View hierarchy, `SurfaceView`/WebView video compositing, and dialogs and popups bound to that display.
- *Alt: Canvas/GL drawing on the surface.* Rejected because it cannot host Views or WebView.
- *Alt: launch `MainActivity` on the virtual display (`ActivityOptions.setLaunchDisplayId`).* It would give a real FragmentActivity. Rejected because it depends on background activity starts, which HyperOS blocks; that is the same failure that breaks `ProjectionActivity` today.

Surface changes map to `VirtualDisplay.setSurface()` + `resize()`, so the Presentation and delegate survive surface swaps.

### D2. A `FermataActivity` host wrapping the Presentation context
`CarHostActivity` extends `ContextThemeWrapper` over the Presentation's context (with the Fermata theme from `MainActivityDelegate.setTheme`) and implements `FermataActivity`. All views are inflated with this context, so the context walk resolves the car delegate without touching `modules/web`.
- The host owns the actual `LifecycleRegistry`, `ViewModelStore`, `SavedStateRegistryController`, `OnBackPressedDispatcher` and an `ActivityResultRegistry` that always fails.
- It mirrors `MainCarActivity` for `isCarActivity()` (true) and for the no-op `setRequestedOrientation`/`recreate`.
- `startActivityForResult` fails.
- `checkPermissions` returns the current grant state (`ContextCompat.checkSelfPermission`) without prompting, so callers see real results instead of an exception.
- `getWindow()` returns the Presentation window.
- `getIntent()` returns an `ACTION_MAIN` intent.

### D3. Fragments via `FragmentController` + an owner-implementing `FragmentHostCallback`
androidx `FragmentManager.attachController` looks for owners on the `FragmentHostCallback` instance itself. So `CarFragmentHost extends FragmentHostCallback<CarHostActivity>` implements `LifecycleOwner`, `ViewModelStoreOwner`, `SavedStateRegistryOwner`, `OnBackPressedDispatcherOwner` and `ActivityResultRegistryOwner` by delegating to `CarHostActivity` (the same pattern as `FragmentActivity.HostCallbacks`). `onGetLayoutInflater` returns the host inflater, and `onFindViewById` searches the Presentation content.

Start order:
1. `savedStateController.performAttach()`
2. `performRestore(null)`
3. `controller.attachHost(null)`
4. `dispatchCreate()`
5. inflate content
6. `dispatchActivityCreated()`
7. `dispatchStart()`
8. `dispatchResume()`

Teardown runs in reverse and ends with `dispatchDestroy()` and `viewModelStore.clear()`. `getSupportFragmentManager()` returns `controller.getSupportFragmentManager()`.

**Readiness and replay.** The host is created at the first `onSurfaceAvailable` (it needs the Presentation context), and the delegate arrives asynchronously after the `FermataMediaServiceConnection` connects. The host's effective state is `min(Session state, ready)`, where ready means both the Presentation and the delegate exist. When it becomes ready, the host replays the steps above, plus the delegate's `onActivityCreate` and `onActivityResume`, in order, up to the Session's current state. After that it follows Session transitions directly. A Session transition that arrives before ready is only recorded, never dropped, so no callback is skipped or reordered.

### D4. Lifecycle source of truth: the Car App `Session`
The Session's `Lifecycle` drives the host lifecycle (D3, subject to the readiness rule there) and the delegate (`onActivityCreate`/`Resume`/`Pause`/`Destroy`). Surface availability is independent: losing the surface (for example when switching to another AA app) detaches the VirtualDisplay surface but keeps the host, the delegate, its state and its media priority alive. "Leaving the car UI" means the Session is destroyed (disconnect, or the host ends the session). Only then are things torn down, in reverse order.

### D5. Pan mode and the gesture bridge
The Screen always shows `NavigationTemplate` with `setMapActionStrip(Action.PAN)`, as `MirrorServiceFS` does, so the host delivers `onScroll`/`onFling`/`onScale`. A new gesture bridge in the auto flavor turns callbacks into an **event plan** (a list of `(action, downTime, eventTime, pointers)`). The plan is a pure Java class that takes timestamps as input, and a thin adapter turns it into `MotionEvent`s dispatched with `presentation.getWindow().getDecorView().dispatchTouchEvent(...)`. Same process, so no `INJECT_EVENTS` is needed. The bridge is new code modeled on the `MirrorServiceFS` algorithm; FS Mirror is not edited.
- **Drag origin.** It is the last `onClick` point or `onScale` focus, else the visible-area center (D8). This lets the user "aim" a drag by tapping the list first.
- **Click** becomes DOWN then UP at (x,y), closes any open drag first, and updates the origin.
- **Scroll** opens a drag (DOWN at the origin) if none is open, then MOVEs by the distances. An idle timer (500 ms) closes it with UP.
- **Fling.** If a drag is open, it appends a few fast MOVEs along the velocity and ends with UP, so `VelocityTracker` sees a fling. If none is open, it synthesizes a short drag from the origin with the same ending.
- **Scale** becomes a two-pointer pinch around the focus.

Coordinates are surface pixels, which equal display pixels, so no translation table is needed.

### D6. ActionStrip: back, home and long-press; placement during pan
The regular `ActionStrip` holds Back, Home and a long-press toggle (`home`/`back` drawables reused, plus a new one). The map action strip holds only `Action.PAN`. **Decided by the spike:** the regular strip stays visible in pan mode, so Back/Home/long-press stay in the regular `ActionStrip` and the map action strip holds only `PAN`.

**Added after the first DHU run (user request, 2026-10-05):**
- A **full-screen** action between Home and the long-press toggle, so the strip has 4 actions (the host maximum). It toggles the active screen's video full screen through `MainActivityFragment.toggleFullScreen()` (`WebBrowserFragment` implements it with its chrome client), and shows a `CarToast` when the screen has no video.
- **Home on the start screen** calls `MainActivityFragment.goHome()`, which reloads m.youtube.com for `YoutubeFragment` (and exits full screen). Showing the already active fragment did nothing, which looked broken.
- **Long-press toggle feedback:** arming it shows a `CarToast` as well as the yellow tint.
- The Presentation window background uses the theme's `colorBackground`, so the stable-area padding is not white.

The long-press toggle arms the next tap. That tap is dispatched as DOWN, a hold longer than `ViewConfiguration.getLongPressTimeout()`, then UP, which triggers the normal `onLongClick`. It disarms after one use.

### D7. Text input that submits
`startInput(TextWatcher w)`: if `w` is an `OnEditorActionListener`, the host keeps it as the submit target, matching what `FermataWebView` registers.
- **Voice** is used when voice control is enabled **and** `RECORD_AUDIO` is already granted (`checkSelfPermission`). It runs `startSpeechRecognizer`.
- **Head unit text entry** is used otherwise. The Screen pushes a `SearchTemplate`, and `onSearchSubmitted` delivers the text, then the screen pops.
- **Submit.** In both paths the host sets the text on its edit text, notifies `w.afterTextChanged`, **then calls `onEditorAction(editText, IME_ACTION_SEARCH, null)`**. That is what makes `FermataWebView` call `submitForm()`.
- **Cancel.** Cancellation (template popped, recognizer error) calls `stopInput()` and never blocks.

`createEditText` returns a non-focusable edit text whose click calls `startInput`, the same pattern as `CarEditText`. The `isCarActivityNotMirror()` bypass in `startSpeechRecognizer` stays for this path, because the host already checked the grant.

Other voice entry points (`startVoiceAssistant`, and the edit-text long-click that starts recognition) do not go through `startInput` and are not checked. Neither are non-web text fields created with `createEditText` (for example toolbar filters), whose SearchTemplate result is only set as text. All of these are listed for the task 6.1 audit and are out of the YouTube path.

### D8. Visible area as root padding
`onStableAreaChanged(Rect)` sets padding (the stable area always lies inside the visible area, so the spec requirement holds) on the host's root content view, and full-screen video (`setVideoMode`) ignores it. **Decided by the spike: the stable area drives the padding.** The visible area grows to the full width/height when the host auto-hides the action strip and shrinks again when it reappears (top inset 24 ↔ 88 px on the Uni-T profile), so padding from it would make the layout jump. The stable area stays fixed.

### D9. Coexistence: priority, session activity and the context fallback
- **Priority.** The delegate computes its assistant priority from its **own host** (`getAppActivity().isCarActivity()`), not from the global mirroring flag. The car host gets 0 and the phone `MainActivity` gets 1 even when FS Mirror ran earlier in the process. This is a small `MainActivityDelegate` change.
- **Session activity.** This is the only place that sets the media session activity (`MainActivityDelegate.java:242-243`). A real `Activity` host keeps today's behavior (an intent for its own class). A non-Activity host (the new `CarHostActivity`, and also `MainCarActivity`, whose `CarActivity` is a `ContextWrapper`) sets an intent for `MainActivity`, and only when no session activity is set yet. This way an AA cold start, where the phone activity never ran, still gets a working notification, and the car host never overrides the phone's. Today `MainCarActivity` sets an intent for its own non-manifest class, which cannot be launched, so this changes its behavior (it fixes it).
- **Video view priority.** `VideoView` registers with `isCarActivityNotMirror() ? 0 : 1` (`VideoView.java:372`), which still uses the global flag. YouTube is unaffected because Chromium renders the video itself. Aligning it with the host-based rule is listed for the task 6.1 audit.
- **Context fallback.** `ActivityDelegate.setContextToDelegate` is set by the car host only as a fallback and cleared on destroy only if it still points to the car delegate.
- **Media service connection.** The car delegate gets its own `FermataMediaServiceConnection`, like `MainCarActivity.onCreate`.

### D10. Start fragment provided by the host
`FermataActivity` gains `default @IdRes int getStartFragmentId() { return ID_NULL; }`. `defaultIntent()` consults it first, before `getShowAddonOnStartPref()`. `CarHostActivity` returns the YouTube addon's `FermataFragmentAddon.getFragmentId()` when `AddonManager` has it enabled and installed, and `ID_NULL` otherwise, which falls through to Fermata's normal start logic. Because the decision lives inside the existing single `defaultIntent()` path, nothing races and nothing double-shows. The host's `getIntent()` is `ACTION_MAIN`, so `onActivityCreate` goes straight to `defaultIntent()`.

### D11a. One active YouTube player across phone and car
Each `YoutubeWebView` creates its own `YoutubeMediaEngine`, but all of them share one `MediaSessionCallback`. `goBack()` stops whatever YouTube engine is current (`YoutubeWebView.java:87-90`), and `RESUME_POS` is a shared addon pref (`YoutubeFragment.java:105-111`). Two live YouTube pages would therefore fight. Decision:
- **Car takes over.** When the car delegate becomes ready (D3), if the current engine is a YouTube engine owned by another delegate, the car host pauses it through the media session (`onPause`). The phone page stays loaded and paused, and the car page becomes the only active YouTube player.
- **Ownership check.** A YouTube engine only stops or pauses the current engine if that engine belongs to its own WebView. A `goBack()` on the phone page therefore cannot stop the car's playback, and the reverse also holds.
- **Separate resume position.** The resume position uses a separate key for car hosts (for example `RESUME_POS` + `_car`), so the two UIs do not overwrite each other.

Only these engine-ownership and resume-key changes touch `modules/web`.

### D11. New service identity
There is a new `<service>` in `fermata/src/auto/AndroidManifest.xml` with a `androidx.car.app.CarAppService` action, the `androidx.car.app.category.NAVIGATION` category, label `@string/car_display_service_name` and its own icon. The label follows the existing pattern: `car_display_service_name_release` ("Fermata Car") and `_debug` strings in `values/strings.xml`, selected by `resValue` in `fermata/build.gradle`. It reuses the `ACCESS_SURFACE`/`NAVIGATION_TEMPLATES` permissions and `<uses name="template">`.

## Risks / Trade-offs

- [WebView video inside a Presentation on a private VirtualDisplay may render black or fail to go full screen] → The phase 1 spike validates this before any host work. Fallback: keep full-screen video in the inline player.
- [AA may not list two NAVIGATION `CarAppService`s from one package] → The spike registers its service next to `MirrorServiceFS` and checks both entries in the DHU launcher.
- [The host may hide the regular ActionStrip in pan mode] → D6 fallback to the map action strip, decided in the spike.
- [Synthetic drags hit the wrong view] → D5 origin rule (last tap or pinch focus). Documented as a limitation of the surface API, which reports no drag start point.
- [`FragmentHostCallback` lacks parts of a real activity (activity results, permission requests)] → The D2/D3 registry fails and `checkPermissions` returns state without prompting. The phase 3 audit records the affected screens.
- [Branches on `isCarActivity()` expect the AA projection IME or `CarActivity` APIs] → Phase 3 audit; the input paths are covered by D7.
- [Two `MainActivityDelegate` instances break shared state] → D9 covers priority, session activity and the context fallback, and D11a covers two YouTube pages. Tasks 3.4 and 7.3 test phone + car together, including both on the YouTube screen.
- [Session lifecycle racing surface availability and the async delegate] → D3 readiness and replay rule; task 3.2 tests it.
- [The DHU cannot resize the surface within a session] → task 2.3 exercises the resize path through a debug-only hook; real resizes are observed opportunistically in the car.
- [Voice needs `RECORD_AUDIO` on the phone, and the car UI cannot prompt for it] → D7 uses voice only when it is already granted, otherwise the head unit keyboard (which is unavailable while driving, so text entry while moving needs the permission granted beforehand on the phone).
- [Android Auto 17.3+ media/template conflict noted in CLAUDE.md] → Unchanged by this work; verify media still works in the real car.
- [`tools/dhu.cmd` grants appops to every `me.aap.fermata.auto*` package, which hides missing permissions] → Fresh-install checks run with `-SkipPermissions`.

## Migration Plan

This change is additive: ship the new service next to FS Mirror and compare them in the real car. Rollback means removing the `<service>` entry. The D9/D10/D11a changes are neutral for the phone `MainActivity`, with one exception: its YouTube page no longer stops a car-owned engine. They change `MainCarActivity` in two ways: it gets host-based priority (0, as before when not mirroring), and its session activity now points to `MainActivity` instead of its own unlaunchable class. Once the new UI covers the FS Mirror use cases, a separate change can remove FS Mirror and the mirroring machinery.

## Spike Results (tasks 1.1-1.3, 2026-10-05)

Run on the DHU with `tools\dhu-uni-t.ini` (AA car API level 8), phone Xiaomi 23049PCD8G (HyperOS, Android 15).

| Item | Result |
|---|---|
| Two NAVIGATION `CarAppService`s (spike + FS Mirror) listed in the AA launcher | Pass |
| Private `VirtualDisplay` (`FLAG_PRIVATE`, flags 0) + `Presentation`, no permission | Pass. Content fills the surface at its size and DPI. |
| Surface | 1816x696 @ 160 dpi (density 1.0) |
| Stable area | `[24,88][1792,684]`, constant |
| Visible area | Same as stable, but `[24,24][1792,684]` while the action strip is auto-hidden |
| `WebView` on m.youtube.com: inline video frames and audio | Pass |
| Full-screen video via `onShowCustomView` | Pass (frames render, Back leaves it through `onHideCustomView`) |
| `onClick` dispatched as DOWN/UP to the decor view | Pass (opens videos, presses page controls) |
| `onScroll` as a naive drag from the last tap | Dispatched and handled by the page. It showed the D5 origin problem: after tapping near a corner, the drag started there. |
| Regular action strip in pan mode | Stays visible → D6 keeps the regular strip |
| `onScale` | Delivered (focus + factor 2.0 per step) |
| `onFling` | Never delivered by the DHU in this run. The D5 fling path cannot be verified on the DHU and must be checked in the real car. |

Notes:
- `isHardwareAccelerated()` logged `false` because it was read before the root view was attached. Video rendering shows the window is accelerated, so no action is needed.
- Chromium logged harmless media/tile warnings and no crashes.

## Car-Branch Audit (task 6.1)

Every `isCarActivity()`/`isCarActivityNotMirror()` branch in `fermata/src/main` and `modules/*` was read against the car host (`CarHostActivity`: not a `CarActivity`, no AA projection IME). None on the YouTube, start, back or home paths needed a fix.

The branches that hold for the car host as they are:
- The `*_AA` preferences in `MainActivityPrefs` (theme, full screen, bars, sizes, grid, nav bar position). The car UI uses the Android Auto settings, as intended.
- In `MainActivityDelegate`:
  - `exitOnBackPressed()` returns false, so Back at the start screen stays put and shows no toast;
  - `setUncaughtExceptionHandler` is skipped;
  - window insets are skipped;
  - `recreate()` shows "please restart";
  - `isFullScreen()` follows the AA status preference.
- `FermataWebView.isCar` routes web text fields to `startInput`, which is the D7 path.
- `Utils.openUrl*` opens links in the in-app browser.
- `CastAddon` skips the route button; `VlcEngine` uses the AA audio delay; `SettingsFragment`/`MediaItemMenuHandler` show the AA variants.

Gaps recorded as follow-up work (not fixed here):
- **VideoView priority** (`VideoView.java:372`) still uses the global flag (`isCarActivityNotMirror()`). After FS Mirror runs in the same process, a phone `VideoView` also gets priority 0. YouTube is unaffected (Chromium renders its video). Follow-up: use `getAppActivity().isCarActivity()`, as the assistant priority now does.
- **Voice entry points outside `startInput`** skip the `RECORD_AUDIO` check through the `isCarActivityNotMirror()` bypass in `startSpeechRecognizer`:
  - `startVoiceAssistant` / `voiceSearch`;
  - the voice long-click that `MainActivityDelegate.createEditText` adds;
  - `SpeechRecognitionService.onStartListening` (`a.isCarActivity()`).

  Without the permission, the recognizer is expected to fail with an error rather than prompt (not verified on the car display). Follow-up: check the grant as `CarHostActivity.startInput` does.
- **Non-web `createEditText` fields** (toolbar filters, chat input) get the `SearchTemplate` text only as `setText`. Their own `OnEditorActionListener` is not fired, because the host cannot read it back from the field. Filters react to the text change, so this works for them; a chat "send" still needs its button.
- **Web permission requests** (`FermataChromeClient.onPermissionRequest`) are granted without checking app permissions on car hosts ("not supported by AA"). For the car host, `checkPermissions` now returns real grant state, so this could use it. Today a page microphone request without `RECORD_AUDIO` fails inside Chromium.
- **Nav bar "Restart"** (`NavBarMediator` `nav_exit`) calls `System.exit(0)` on car hosts, which also kills the phone UI and playback. This is existing AA behavior, kept unchanged.
- **Debug listener-leak check.** It produces false positives: `ListenerLeakDetector` (`onActivityDestroy`, debug builds only) scans every registered listener in the process. When the car delegate is destroyed while the phone `MainActivity` is alive, it reports the phone's listeners (`FoldersFragment`, `FloatingButton`, `MainActivity`, ...) as "Listener leaks detected!". This is log noise only; a follow-up could scope the check to the destroyed delegate.
- **Mirroring mode is global.** `checkMirroringMode` (on resume) and `isCarActivity()` still OR in `isMirroringMode()`. After FS Mirror, the phone `MainActivity` also counts as a car activity for these branches. The priority (D9) no longer depends on it.

## Open Questions

- Which icon the new entry uses (new drawable vs. a variant of `launcher`).
