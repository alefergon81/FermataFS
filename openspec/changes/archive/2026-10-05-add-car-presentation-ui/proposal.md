# Proposal

## Why

The only full-screen car experience available to self-built packages is FS Mirror (`MirrorServiceFS`). It mirrors the whole phone screen through MediaProjection and injects touches through an accessibility service, Xposed or su. That chain is fragile: it needs consent prompts and restricted permissions that HyperOS and Android 13+ block, it rotates the phone, it translates coordinates between mismatched screens, and it leaks phone UI to the car. The official "Fermata Auto" projection UI (`MainCarActivity`) solves this properly but never shows up for self-built packages. Rendering Fermata's own UI straight onto the Car App Library surface gives the full-screen experience (starting with the YouTube player) with none of the mirroring machinery.

## What Changes

- Add a new Car App Library service in the `auto` flavor, category NAVIGATION, with its own label (working name **"Fermata Car"**) and icon. It sits alongside FS Mirror, whose code and behavior stay unchanged (`MirrorServiceFS`/`MirrorDisplay` are not edited).
- Render onto the car surface through a private `VirtualDisplay` (no MediaProjection, no permission) that hosts an `android.app.Presentation`.
- Add a car host that implements `FermataActivity` (the role `MainCarActivity` plays for the official projection), including its own `FragmentController`-based fragment host and lifecycle, so a full `MainActivityDelegate` runs on the car display.
- Open the YouTube addon (`YoutubeFragment`) as the initial screen. Other fragments come in a later phase.
- Translate Car App surface gestures (click, scroll and fling in pan mode, scale) into touch events dispatched directly to the hosted view tree, with no accessibility or injection permissions.
- Provide a long-press substitute and text input that actually submits (voice when the microphone permission is already granted, the head unit's `SearchTemplate` otherwise), because the surface gives neither.
- Lay out content inside the host's visible/stable area so template controls do not cover it.
- Let the phone and car delegates coexist, with the car UI taking priority in the media session while it is active.

No **BREAKING** changes: the existing Media service, FS Mirror and `MainCarActivity` keep working as they do today.

## Capabilities

### New Capabilities
- `car-display-ui`: Fermata's own UI rendered directly onto the Android Auto navigation surface. Covers service registration, surface/display lifecycle, the activity host, gesture and text input, visible-area handling and coexistence with the phone UI and media session.

### Modified Capabilities
<!-- None: no existing specs in openspec/specs/. -->

## Impact

- **Code (`fermata/src/auto`)**: a new `CarAppService`, `Session`/`Screen`, display host, `Presentation`, `FermataActivity` host, fragment host and gesture bridge. The bridge is new code modeled on, but not shared with, `MirrorServiceFS`.
- **Code (`fermata/src/main`)**: small, targeted `MainActivityDelegate` changes so a second live instance is safe: a host-provided start fragment, no session-activity override from non-manifest hosts, and media priority taken from the delegate's own host rather than the global mirroring flag. There may also be guards where `isCarActivity()`/`isCarActivityNotMirror()` branches assume the AA projection IME.
- **Code (`modules/web`)**: views resolve their delegate through the context chain, so no wiring changes. The only change is in the YouTube engine: it may stop only its own playback, and car hosts get a separate resume position, so phone and car YouTube pages do not fight.
- **`MainCarActivity`**: its media notification target changes from its own unlaunchable class to `MainActivity` (a fix).
- **Manifest/resources (`auto`)**: the new `<service>` with the `androidx.car.app.category.NAVIGATION` intent filter, a label following the existing `_release`/`_debug` `resValue` pattern, and an icon. The existing `ACCESS_SURFACE`/`NAVIGATION_TEMPLATES` permissions and `<uses name="template">` already cover it.
- **Dependencies**: none new (`androidx.car.app` 1.7.0 and `androidx.fragment` already present).
- **Tooling/docs**: `tools/dhu.cmd` needs no extra grants for this mode. `CLAUDE.md` gets a note on the new service.
