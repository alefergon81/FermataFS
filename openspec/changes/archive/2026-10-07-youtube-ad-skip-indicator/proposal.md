# Proposal

## Why

In the auto flavor the YouTube player skips ads by seeking to the end of the ad, but only after the ad has loaded and started playing. In fullscreen the user sees only a black screen while the ad loads, is skipped and the real video buffers, which looks like the app is slow or frozen. The same black screen shows during ordinary buffering, because the fullscreen view shows only the raw `<video>` and none of YouTube's own loading UI.

## What Changes

- The fullscreen YouTube view (auto flavor) shows a status overlay instead of plain black:
  - from the moment an ad is detected until the requested video plays: an "ad blocked" icon (a "no" sign with "AD" inside) and the text "Skipping ad…";
  - while the video is loading or buffering, with no ad: a spinner.
  The overlay is never shown while the user has paused the video.
- Ad detection is event-driven: the page reports when the player enters and leaves the ad state, instead of checking for it only on the video `playing` event. The skip runs as soon as the ad's duration is known, which shortens the black gap.
- While an ad is being skipped, its audio is muted, and the previous mute state is restored when the ad ends.
- In the auto flavor, an ad's `ended` and `pause` events no longer reach the media session, so the end of an ad no longer counts as the end of the video. An ad's `playing` still starts the YouTube engine, so the player enters fullscreen during a pre-roll ad as it does today.
- The ad part of the overlay and the mute follow the existing "Try to skip advertising" setting. The buffering spinner is always on in the auto flavor.
- New strings in English and Portuguese, plus the Portuguese translation of the existing `try_to_skip_ad`. Other locales fall back to English.

Out of scope: the mobile flavor (no ad skipping there today, no overlay, no ad-event filtering), the inline (non-fullscreen) player, which keeps YouTube's own page UI, and blocking ad requests at the network level.

## Capabilities

### New Capabilities
- `youtube-player`: behavior of the YouTube addon's player, starting with ad skipping and the loading/ad-skip status shown in fullscreen.

### Modified Capabilities
<!-- none -->

## Impact

- `modules/web`, package `me.aap.fermata.addon.web.yt`:
  - `YoutubeWebView` (injected JS: ad-state observer, ad-event filtering, early skip and mute, buffering events; re-sending the skip flag on preference change);
  - `YoutubeJsInterface` (new events);
  - `YoutubeMediaEngine` (owns the player status and its timer, ad `playing` path, removal of the old skip block);
  - `YoutubeVideoView` (overlay view);
  - `YoutubeChromeClient` (apply the status on entering fullscreen, reset it on leaving);
  - `YoutubeAddon` (`skipAdChanged(prefs)` helper);
  - new `YoutubePlayerStatus` helper and `YoutubePlayerStatusTest`.
- Resources in `modules/web`: drawable `ic_ad_blocked`, string `skipping_ad` (`values`), new `values-pt/strings.xml`.
- No new dependencies. No change to the mobile flavor's behavior or to other media engines.
