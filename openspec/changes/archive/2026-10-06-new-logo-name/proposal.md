# Proposal

## Why

In the Android Auto launcher the "Fermata Car" entry shows its icon shrunk inside a white circle, while the other entries fill the whole circle with their gradient. The car entry should also be presented to the driver as "UNI-TV", and the FS Mirror entry, which UNI-TV replaces, should no longer be offered in the car.

## What Changes

- The car entry's icon keeps its current look (orange → blue gradient with the white screen/play glyph), but the gradient fills the whole launcher circle, with no white border. It gets an adaptive-icon variant (API 26+) like `mirror_fs` already has.
- The car entry's label changes from "Fermata Car" to "UNI-TV" (release) and from "Fermata Car (debug)" to "UNI-TV (debug)" (debug).
- **BREAKING (car):** the FS Mirror entry is no longer listed in the Android Auto launcher. Its code stays in the tree but is not exposed.
- The install guide for other users (`tools/INSTALL-friend.md`) and the CLAUDE.md notes are updated to match: UNI-TV instead of FS Mirror, and APKs are copied to the phone and installed through KingInstaller.
- The Fermata Media entry stays as it is, because it provides AA's now-playing widget and the steering-wheel media controls.
- Assumption: "nome da aplicação" refers to the car entry only. The phone app name ("Fermata Auto") and its launcher icon do not change. The projection services (`CarService`, `MirrorService`), which Android Auto does not list for self-built packages anyway, are left as they are.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `car-display-ui`: "Separate car app entry" is replaced by "Car app entries" (UNI-TV label, full-bleed icon, Media kept, FS Mirror not listed).

## Impact

- `fermata/src/auto/res/drawable-anydpi-v26/car_display.xml` (new adaptive icon).
- `fermata/src/main/res/values/strings.xml`: `car_display_service_name_release` / `_debug`.
- `fermata/src/auto/AndroidManifest.xml`: disable the `MirrorServiceFS` service.
- `CLAUDE.md`: the Android Auto notes (`template` → FS Mirror, FS Mirror permissions) and the "Running the auto flavor" install step, which switches from Android Studio to copying the APK and installing it through KingInstaller.
- `tools/INSTALL-friend.md` (Portuguese install guide): UNI-TV replaces FS Mirror as the car feature, and the mirroring permission steps go away.
- The `CarDisplayService` Javadoc.
- Only the auto flavor is affected.
