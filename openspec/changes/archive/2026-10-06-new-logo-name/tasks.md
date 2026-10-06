# Tasks

Commands below are for Git Bash, from the repo root. `AAPT2` is `"$LOCALAPPDATA/Android/Sdk/build-tools/37.0.0/aapt2.exe"` (any installed build-tools version works). Set `JAVA_HOME` to Android Studio's JBR, as in CLAUDE.md. In PowerShell, use `$env:NO_GS='true'; .\gradlew.bat ...` and `Select-String` instead of `grep`.

## 1. Full-bleed icon

- [x] 1.1 Add `fermata/src/auto/res/drawable-anydpi-v26/car_display.xml` as an `<adaptive-icon>` (background `@drawable/car_display_bg`, foreground and monochrome `@drawable/car_display_l`), following `mirror_fs.xml`; verify `NO_GS=true ./gradlew.bat fermata:assembleAutoDebug -PABI=arm64-v8a` succeeds
- [x] 1.2 Verify with `$AAPT2 dump resources <autoDebug apk from fermata/build/outputs/apk/auto/debug/>` that `drawable/car_display` has both the default and the `anydpi-v26` config

## 2. UNI-TV label

- [x] 2.1 In `fermata/src/main/res/values/strings.xml`, set `car_display_service_name_release` to "UNI-TV" and `car_display_service_name_debug` to "UNI-TV (debug)"; rebuild `autoDebug` as in 1.1 and verify with `$AAPT2 dump resources <autoDebug apk> | grep -A1 car_display_service_name` that `car_display_service_name` points to `car_display_service_name_debug`, whose value is "UNI-TV (debug)" (the release value is checked in 4.1)
- [x] 2.2 Update the `CarDisplayService` Javadoc to call the entry "UNI-TV"; verify that `grep -rn "Fermata Car" fermata/src` returns nothing

## 3. Hide the FS Mirror entry

- [x] 3.1 Add `android:enabled="false"` to the `MirrorServiceFS` `<service>` in `fermata/src/auto/AndroidManifest.xml`; rebuild `autoDebug` and verify in `fermata/build/intermediates/merged_manifests/autoDebug/processAutoDebugManifest/AndroidManifest.xml` that `MirrorServiceFS` has `android:enabled="false"` and `CarDisplayService` / `FermataMediaService` do not
- [x] 3.2 Update `CLAUDE.md`: in "Running the auto flavor", step 3 builds the APK, copies it with `adb push` to `/sdcard/Download/` and installs it through KingInstaller (no Android Studio install); in Troubleshooting, the `<uses>` note says `template` → UNI-TV (`CarDisplayService`) and FS Mirror is disabled in the manifest, the projection note no longer says FS Mirror shows up, and the FS Mirror permissions note says it applies only if `MirrorServiceFS` is re-enabled; verify with `grep -n "FS Mirror\|Android Studio" CLAUDE.md` that every remaining mention matches this
- [x] 3.3 Update `tools/INSTALL-friend.md`: "O que funciona" lists Fermata Media Service and UNI-TV (Fermata's own interface in full screen, opening on YouTube, no special permissions) instead of FS Fermata Mirror and the "não aparece" line; drop step 5's mirroring permissions (overlay, modify settings, accessibility, restricted settings) but keep the battery item; step 7 opens UNI-TV; replace the mirroring warning; verify with `grep -n "Mirror\|espelh\|Acessibilidade" tools/INSTALL-friend.md` that nothing about mirroring remains

## 4. Integration check (APK copied, installed by the user, DHU run by the user)

Do not install the APK with `adb install` or Android Studio. Only copy it to the phone; the user installs it through KingInstaller. The user runs the DHU (interactive console) and confirms each visual check; the agent waits for that confirmation.

- [x] 4.1 Build `NO_GS=true ./gradlew.bat fermata:packageAutoReleaseUniversalApk -PABI=arm64-v8a -PAPP_ID_SFX=.car`; verify with `$AAPT2 dump resources <apk> | grep -A1 car_display_service_name` that the release label resolves to "UNI-TV"; copy it with `MSYS_NO_PATHCONV=1 adb push <apk> /sdcard/Download/` and verify with `MSYS_NO_PATHCONV=1 adb shell ls -l /sdcard/Download/` that the size matches; ask the user to install it through KingInstaller and wait for confirmation
- [x] 4.2 After the install, run `adb shell pm list packages me.aap.fermata.auto` and tell the user which other variants are installed (their "Fermata Car" / FS Mirror entries will still show in the launcher; the user may uninstall them first); verify with `adb shell cmd package query-services -a androidx.car.app.CarAppService` that `me.aap.fermata.auto.car` exposes `CarDisplayService` and not `MirrorServiceFS`
- [x] 4.3 Ask the user to run `tools\dhu.cmd` (with "Start head unit server" on the phone) and confirm, or send a screenshot: the launcher shows "UNI-TV" and the Fermata Media entry, and no FS Mirror from `.car`; the UNI-TV gradient fills the whole circle with no white border and the glyph is centered and uncropped
- [x] 4.4 Ask the user to confirm on the DHU that the Media entry browses and plays as before, and that UNI-TV opens on YouTube and plays a video
