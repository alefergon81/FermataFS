# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Fermata is an Android audio/video/IPTV player (Java 17, Gradle + Android Gradle Plugin) with first-class Android Auto support. Package root: `me.aap.fermata`.

## Build

Requires the Android SDK (`ANDROID_SDK_ROOT` or `sdk.dir` in `local.properties`), JDK 17, and the NDK version pinned in `gradle/libs.versions.toml` (`ndkVersion`; needed by `modules/whisper`, which has native code under `src/main/cpp`). On Windows use `gradlew.bat`.

```bash
# Universal APK, auto flavor, single ABI (what CI does)
NO_GS=true ./gradlew fermata:packageAutoReleaseUniversalApk -PABI=arm64-v8a
# output: fermata/build/outputs/apk_from_bundle/autoRelease/

# AAB
./gradlew bundleAutoRelease -PAPP_ID_SFX=.your.suffix

# Debug build (applicationId gets .debug suffix)
./gradlew fermata:assembleMobileDebug -PABI=arm64-v8a

# Wrapper script (sets NO_GS, moves outputs to dist/): -c clean, -a also armv7, -b AAB
./build.sh
```

Build properties (`-P...`):
- `ABI`: comma-separated ABI filter (default `arm64-v8a,armeabi-v7a,x86_64`). Pass a single ABI to speed up builds.
- `APP_ID_SFX`: suffix appended after `.auto` in the auto flavor's applicationId (so side builds don't clash with an installed release).
- `APP_NAME`, `APP_ID`: override app label / base applicationId.

Environment: `NO_GS=true` disables Google Services. When it is set, or when `fermata/google-services.json` is missing, the `gdrive` module is excluded from the build.

Signing: `settings.gradle` writes `local.properties` (git-ignored) and generates `res/fermata.jks` with default credentials if no `storeFile` is configured. Both debug and release use this `release` signing config.

### Windows host
- Use Android Studio's bundled JDK (`C:\Program Files\Android\Android Studio\jbr`) as `JAVA_HOME` for command-line builds.
- `modules/whisper` needs Visual Studio with the C++ workload and a Windows SDK. ggml's `vulkan-shaders-gen` host tool is built with MSVC through `src/main/cpp/host-msvc.cmake.in`, which finds VS via `vswhere` and the SDK via the registry, so no vcvars/PATH setup is required. On Windows the ExternalProject dir is also shortened (`EP_BASE`) to stay under MAX_PATH.
- The first whisper build takes ~15 min (Vulkan shader compilation). If CMake state gets stale, delete `modules/whisper/.cxx`.
- whisper.cpp is pinned via `GIT_TAG` in that CMakeLists. Newer versions may require shader extensions (e.g. `GL_KHR_cooperative_matrix`) that the NDK's `glslc` does not support.

Versions (app version, SDK levels, all dependency versions) live in the version catalog `gradle/libs.versions.toml`. Add new dependencies there and reference them as `libs.xxx`.

## Running the auto flavor (Desktop Head Unit)

The Android Auto UI is not shown on the phone. It is projected to a head unit, which the Desktop Head Unit (DHU) emulates on the PC. The Android Studio "Automotive" emulator is Android Automotive OS, not Android Auto, so it does not apply here.

1. Install *Android Auto Desktop Head Unit Emulator* (SDK Manager → SDK Tools). It installs to `<sdk>/extras/google/auto/desktop-head-unit.exe`.
2. On the phone (Android Auto is under system Settings, with no launcher icon):
   - tap *Version* 10× to enable developer mode;
   - in ⋮ → *Developer settings*, enable **Unknown sources** (needed for sideloaded/debug builds);
   - in ⋮, choose **Start head unit server**.
3. Install the `autoDebug` variant (applicationId `me.aap.fermata.auto.debug`) from Android Studio.
4. Connect with `tools\dhu.cmd`. It checks the device, re-applies the HyperOS permissions below, forwards the port, runs the DHU and removes the forward on exit. Options: `-Serial <id>`, `-SkipPermissions`. To do it by hand:
   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" forward tcp:5277 tcp:5277
   & "$env:LOCALAPPDATA\Android\Sdk\extras\google\auto\desktop-head-unit.exe"
   ```
   Run the DHU in an interactive console. It reads commands from stdin and exits on EOF, so it cannot run as a background job. To stop, close it or type `quit`.

Troubleshooting:
- Android Auto logs are easiest to filter by uid: `adb logcat --uid=<gearhead uid>`.
- **DHU stuck on "waiting for phone"** with `PROJECTION_NOT_STARTED` / `timed out at stage FIRST_ACTIVITY_LAUNCHED` in the logs means the phone blocked Android Auto from starting an activity in the background. On Xiaomi/HyperOS (where Android Auto is hidden from app management), grant it via adb:
  ```
  adb shell "cmd appops set com.google.android.projection.gearhead 10021 allow; cmd appops set com.google.android.projection.gearhead 10020 allow; cmd appops set com.google.android.projection.gearhead SYSTEM_ALERT_WINDOW allow; dumpsys deviceidle whitelist +com.google.android.projection.gearhead"
  ```
- `CAR.VALIDATOR: Package DENIED; Uses for TEMPLATE not defined [<pkg>]` means the build does not declare `<uses name="template"/>` in `fermata/src/auto/res/xml/automotive_app_desc.xml`, so the Car App Library services (FS Mirror, `MirrorServiceFS`) are rejected. Upstream disabled this in 65a3c16 because it conflicted with the media service on Android Auto 17.3+.

## Tests

JUnit 4 unit tests live in `depends/utils/src/test` (async, collections, IO, net, VFS) and `fermata/src/test`. Dynamic-feature modules have flavors, and so does `:fermata`, so test task names include the flavor:

```bash
./gradlew :utils:testDebugUnitTest
./gradlew :utils:testDebugUnitTest --tests me.aap.utils.async.PromiseTest
./gradlew :fermata:testMobileDebugUnitTest --tests 'me.aap.fermata.media.sub.SubtitlesTest'
```

`unitTests.returnDefaultValues = true`, so Android stubs return defaults instead of throwing. There is no lint/format step in the build or CI.

## Architecture

### Gradle layout
- `:fermata` – the base `com.android.application` module. Flavors: `mobile` and `auto` (`BuildConfig.AUTO`). The auto flavor adds `fermata/src/auto` (Android Auto car app, screen mirroring/projection, Xposed/su-based event dispatching) and links `lib/auto/aauto.aar`.
- `:utils` (`depends/utils`) – in-house foundation library used everywhere: `FutureSupplier`/`Promise` async model, VFS abstraction, preferences (`PreferenceStore`), event broadcasting, HTTP/net, UI base classes (`ActivityBase`, `ActivityFragment`), and `DynamicModuleInstaller`. Prefer these utilities over adding new libraries. Async code uses `FutureSupplier` chains (`.main()`, `.onSuccess`, `.then`), not RxJava/coroutines.
- `modules/*` – every subdirectory is auto-included by `settings.gradle` as a **dynamic feature module** (`com.android.dynamic-feature`) and registered in `:fermata`'s `dynamicFeatures`. They depend on `:fermata` and `:utils`, are delivered on demand via Play Feature Delivery (`SplitInstallManager`), and are loaded by class name via reflection.

### Addon system
Addons are declared as data, not code. The root `build.gradle` holds an `ext.addons` list, and each module's `build.gradle` can declare its own `ext.addons`. In `gradle.projectsEvaluated` these are merged and generated into `BuildConfig.ADDONS` (an `AddonInfo[]`: module, class name, `R.string.addon_name_<name>`, icon drawable, order, settings/enabled/auto flags, `depends`).
At runtime, `addon/AddonManager` instantiates enabled addons with `Class.forName` and installs missing modules on demand. Addon types implement `FermataAddon` subinterfaces (`MediaLibAddon`, `FermataActivityAddon`, `FermataFragmentAddon`, `FermataToolAddon`, `FermataMediaServiceAddon`, ...).
**To add an addon:** create a module under `modules/`, add an `ext.addons` entry, and add the `addon_name_<name>` string and the icon drawable in `:fermata`.

### Other pluggable pieces loaded from modules
- **Media engines** – `media/engine/MediaEngineManager` picks among `MediaPlayerEngine` (built in) and the ExoPlayer/VLC engines in `modules/exoplayer` / `modules/vlc`, installing or uninstalling the module when the user toggles the engine.
- **Virtual file systems** – `vfs/FermataVfsManager` maps URI schemes to `VfsProvider` classes in `modules/sftp`, `smb`, and `gdrive` (each a `Provider extends VfsProviderBase`), loading or installing the module on demand.

### Media core (`fermata/src/main/java/me/aap/fermata/media`)
- `lib/` – the media library tree (`MediaLib`, `DefaultMediaLib`): folders, favorites, playlists, CUE and M3U items as `Item`/`BrowsableItem`/`PlayableItem` objects.
- `pref/` – per-item preferences (position, audio effects, speed, titles), which drive the "remember per folder/track" features.
- `service/FermataMediaService` – the `MediaBrowserService`/`MediaSession` used by the UI, Android Auto, and Android TV.
- `sub/` – subtitle parsing and scheduling (used by the `translate`/`subgen`/`whisper` addons).

### Localization
`resConfigs` in the root `build.gradle` lists the supported locales. A new translation needs both `values-<lang>/` resources and an entry in that list.
