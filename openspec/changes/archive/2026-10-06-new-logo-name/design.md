# Design

## Context

`CarDisplayService` uses `@drawable/car_display`, which exists only as a legacy `layer-list` in `fermata/src/auto/res/drawable/`: `car_display_bg` (a gradient shape) under `car_display_l` (a 108dp vector, glyph scaled 2.25× and offset 27dp, so it occupies the central 54dp). The other Android Auto entries (`mirror`, `mirror_fs`, `media_service`) also have an `<adaptive-icon>` in `drawable-anydpi-v26/`. When the host gets a non-adaptive icon it draws it shrunk inside its own white "legacy" backdrop, which is the white border seen in the launcher. An adaptive icon lets the host mask the background layer to the full circle itself.

The label comes from `car_display_service_name`, which `fermata/build.gradle` maps to the `_release` / `_debug` strings in `fermata/src/main/res/values/strings.xml` (both `translatable="false"`, no translations). It is used only by the manifest.

FS Mirror (`MirrorServiceFS`) and UNI-TV (`CarDisplayService`) are both Car App Library navigation apps, unlocked by `<uses name="template"/>` in the auto `automotive_app_desc.xml`. So `template` must stay, and FS Mirror has to be hidden at the component level.

## Goals / Non-Goals

**Goals:** a full-bleed icon on API 26+ phones (every phone running current Android Auto; older phones keep the legacy icon, border included); the new label in debug and release; FS Mirror no longer listed in the AA launcher; docs that match (CLAUDE.md, `tools/INSTALL-friend.md`).

**Non-Goals:** redrawing the glyph or changing the colors; renaming the phone app, the package or the Java classes; renaming the `car_display*` resources; changing the Media entry or `automotive_app_desc.xml`; deleting the FS Mirror code; touching the projection services (`CarService`, `MirrorService`), which AA does not list for self-built packages.

## Decisions

- **Add `drawable-anydpi-v26/car_display.xml` as `<adaptive-icon>`** with `background = @drawable/car_display_bg`, `foreground = @drawable/car_display_l` and `monochrome = @drawable/car_display_l`, matching `mirror_fs.xml`. The existing glyph already fits the 66dp adaptive safe zone (it covers about 29–79 × 34–74dp, farthest corner about 32dp from the center, inside the 33dp radius), so it can be reused as is. The legacy `layer-list` stays as the fallback for API < 26.
  - Alternative: enlarge the gradient in the legacy layer-list or make it a circle. Rejected: the host still adds its own backdrop around non-adaptive icons, so the border would remain.
- **Change only the string values**, keeping the `car_display_service_name_*` resource names, so `build.gradle` and the manifest stay untouched for the label. Debug becomes "UNI-TV (debug)", following the existing "(debug)" convention.
- **Hide FS Mirror by setting `android:enabled="false"` on the `MirrorServiceFS` `<service>`** in the auto manifest. Package queries skip disabled components, so AA never sees it. The class stays and compiles (`MirrorActivity` still reads `MirrorServiceFS.sc`, which stays `null`), and re-enabling it is a one-attribute change.
  - Alternative: delete the `<service>` element or the class. Rejected: a larger diff and harder to bring back, for the same visible result.
- Update the `CarDisplayService` Javadoc ("Fermata Car" → "UNI-TV"), the CLAUDE.md Android Auto notes and the Portuguese guide `tools/INSTALL-friend.md`, which presents FS Mirror as the car feature. UNI-TV needs no mirroring permissions (spec "No mirroring permissions"), so the guide drops the overlay, write-settings, accessibility and "restricted settings" steps, and keeps KingInstaller, Shizuku and the Android Auto developer settings.
- **Verification build:** release universal APK with `-PAPP_ID_SFX=.car` (`me.aap.fermata.auto.car`). `.unit` is the build shared through the guide and may be installed on the same phone, so it is not overwritten. The debug label is checked on the `autoDebug` APK with `aapt2`, without installing it.
- **Who checks the head unit:** the DHU needs an interactive console and the phone's head unit server, so the user runs `tools\dhu.cmd` and confirms what the launcher shows. The agent checks what it can from the command line (`adb shell cmd package query-services`, `aapt2 dump`) and waits for the user's confirmation for the visual checks.

## Risks / Trade-offs

- [The user installs builds through KingInstaller; `adb install` or Android Studio would bypass it] → the APK is only copied to the phone (`adb push` to `/sdcard/Download/`) and the user installs it through KingInstaller. The release build uses `-PAPP_ID_SFX=.car`, because AA block-lists the bare `me.aap.fermata.auto`.
- [Other installed `me.aap.fermata.auto*` variants (`.unit`, `.debug`) still show their own "Fermata Car" and FS Mirror entries in the AA launcher, which can be mistaken for the new build's] → list the installed variants before the DHU check; FS Mirror's absence is checked per package with `query-services`, and on the DHU only entries whose label is new ("UNI-TV") are attributed to `.car` for sure. The user may uninstall the other variants for a clean launcher.
- [AA caches app lists per connection] → restart the DHU (or reconnect the car) after reinstalling, as CLAUDE.md already notes.
- [The glyph could look small or be cropped by the circular mask] → verify on the DHU; if needed, adjust only the `scaleX/Y`/`translate` of a dedicated foreground vector, keeping it inside the 66dp safe zone.
- [`tools\dhu.cmd` still grants FS Mirror's appops] → harmless; left as is.
