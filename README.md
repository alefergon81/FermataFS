## !!!Be aware of scam!!! The sites like https :// fermata-auto . com  are 100% scam. This app is free and opensource!


## About this fork

This is a personal fork of [Fermata](https://github.com/AndreyPavlenko/Fermata).
It adds a full-screen car UI for Android Auto and a few other fixes.
The work lives on the `uni_t_fullscreen` branch.

### UNI-TV: Fermata on the Android Auto screen
- **Fermata's own UI in the car.** A Car App Library *navigation* app (`CarDisplayService`)
  creates a private `VirtualDisplay` on the car surface and runs a `FermataActivity` on it.
  Navigation apps are the only ones that get the full screen. It needs no screen capture,
  overlay permission, accessibility service or root, and it replaces the old FS Mirror,
  which is now disabled.
- **Touch input.** Taps, drags, flings, pinches and long presses from the head unit are
  turned into `MotionEvent`s. The car doesn't report where a drag starts, so drags are aimed
  at the right target: the media list, or the recommendations on the YouTube watch page.
- **Text input** uses voice when the microphone permission is granted. Otherwise it uses the
  head unit's search keyboard.
- **YouTube in the car** opens by default. Ads are skipped, and a skip or buffering indicator
  is shown instead of a black screen.
- The launcher entry is named **UNI-TV** and has a full-bleed icon. The car theme defaults
  to Purple.

### IPTV
- **Huge playlists.** A TV source's M3U/Xtream playlist is streamed into a per-source SQLite
  database, and items are created only for the folder being opened. Lists with hundreds of
  thousands of entries open without running out of memory.

### Fixes
- Chunked HTTP responses whose size line was split across reads are now parsed correctly.
- `SubtitlesTest.testScheduler` is now deterministic.

### Building and testing on Windows
- `modules/whisper` builds on Windows hosts: the Vulkan shader tool is built with MSVC, NDK
  paths no longer depend on the host, the path stays under MAX_PATH, and whisper.cpp is
  pinned to a known version.
- `tools/dhu.cmd` runs the Android Auto Desktop Head Unit. It checks the device, grants the
  Android Auto permissions HyperOS needs, forwards the port and uses a screen profile that
  matches the target car (`tools/dhu-uni-t.ini`).
- `tools/INSTALL-friend.md` is an install guide in Portuguese for sharing the APK.

### Development workflow
- `CLAUDE.md` has the build, test and architecture notes.
- Features are specified with [OpenSpec](openspec/): the current specs are in
  `openspec/specs` and past changes in `openspec/changes/archive`.

> Note: the Android Auto parts are tested with the Desktop Head Unit. Some items still need
> to be checked in a real car (see the archived changes' `tasks.md`).


## Fermata Media Player
[![Download the latest APK](https://img.shields.io/github/v/release/AndreyPavlenko/Fermata?label=Download%20APK&logo=github&style=for-the-badge)](https://github.com/AndreyPavlenko/Fermata/releases/latest)

## About
Fermata Media Player is a free, open source audio, video and TV player with a simple and intuitive interface. It is focused on playing media files organized in folders and playlists.

Supported features:

* Play media files organized in folders
* IPTV addon with support for XMLTV EPG and Catchup
* Remembers the last played track and position for each folder
* Support for favorites and playlists
* Support for CUE and M3U playlists
* Support for bookmarks
* Audio effects: Equalizer, Bass/Volume Boost and Virtualizer
* Configure audio effects for individual tracks and folders
* Configure playback speed for individual tracks and folders
* Customizable titles and subtitles
* Support for Android Auto
* Support for Android TV
* Show favorites and playlists on Android TV home screen
* Pluggable media engines: MediaPlayer, ExoPlayer and VLC
* Video player with support for subtitles and audio streams (VLC Engine only)

## Building the project
* Download and install the latest Android SDK or Android Studio from https://developer.android.com/studio/
* Set the environment variable ANDROID_SDK_ROOT pointing to the SDK directory
```bash
export ANDROID_SDK_ROOT=<path to android SDK>
```

### Clone the repository
```bash
git clone --recurse-submodules https://github.com/AndreyPavlenko/Fermata.git
cd Fermata
```

### Build AAB
```bash
./gradlew bundleAutoRelease -PAPP_ID_SFX=.type.your.pkg.sfx.here
find $PWD -name *.aab
```

### Build APK
```bash
./gradlew bundleAutoRelease -PAPP_ID_SFX=.type.your.pkg.sfx.here
find $PWD -name *.apk
```

### Building in docker
```bash
docker run -ti --name Fermata andreypavlenko/fermata
```
Enter the requested key alias and password, when prompted.
Build the required package using the above commands.
To copy the built package to the host machine, open a new terminal and run:
```bash
docker cp Fermata:/home/mobiledevops/Fermata/fermata/build/outputs/bundle/autoRelease/ .
```


## Donation
If you like the application, please consider making a donation:

[PayPal](https://www.paypal.com/donate/?hosted_button_id=NP5Q3YDSCJ98N)

[CloudTips](https://pay.cloudtips.ru/p/a03a73da)

[Yandex Money](https://money.yandex.ru/to/410014661137336)
