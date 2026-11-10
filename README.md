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


.ru/to/410014661137336)
