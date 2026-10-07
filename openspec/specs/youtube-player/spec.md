# youtube-player Specification

## Purpose
Defines how the YouTube addon's player behaves around playback, including automatic ad skipping in the auto flavor and the feedback shown while the fullscreen player waits for video.

## Requirements

### Requirement: Ad skipping
In the auto flavor, when "Try to skip advertising" is enabled, the player SHALL skip a YouTube ad as soon as the ad's length is known, without waiting for the ad to be detected through a later playback event. When the setting is disabled, ads SHALL play unchanged. The mobile flavor SHALL NOT skip ads.

#### Scenario: Pre-roll ad is skipped
- **WHEN** the setting is enabled and a video starts with a pre-roll ad
- **THEN** the ad is skipped without user action and the requested video starts playing

#### Scenario: Mid-roll ad is skipped
- **WHEN** the setting is enabled and an ad starts in the middle of a video
- **THEN** the ad is skipped and the video resumes where it was interrupted

#### Scenario: Setting disabled
- **WHEN** the setting is disabled and a video starts with an ad
- **THEN** the ad plays as YouTube serves it

### Requirement: Ad audio is muted while skipped
In the auto flavor, when "Try to skip advertising" is enabled, the player SHALL mute an ad's audio from the moment the ad is detected until the ad ends. It SHALL then restore the mute state the video had when the ad started.

#### Scenario: No ad sound
- **WHEN** the setting is enabled and an ad is being skipped
- **THEN** no audio from the ad is heard

#### Scenario: Mute state restored
- **WHEN** the video was unmuted before the ad and the ad ends
- **THEN** the requested video plays with sound

#### Scenario: Setting disabled keeps ad audio
- **WHEN** the setting is disabled and an ad plays
- **THEN** the ad's audio is not muted by the player

### Requirement: Ad events do not drive the media session
In the auto flavor, the end or pause of an ad SHALL NOT be reported to the media session, the media controls or Android Auto as the end or pause of the video. An ad starting to play SHALL still put the YouTube player in its playing state, so it enters fullscreen as it does for a video.

#### Scenario: Skipped ad does not end playback
- **WHEN** an ad is skipped or finishes before the requested video
- **THEN** the media session does not report the video as ended and does not advance to the next item

#### Scenario: Ad pause is not a user pause
- **WHEN** the page pauses the video element while an ad is shown or skipped
- **THEN** the media session does not switch to paused

#### Scenario: Pre-roll ad enters fullscreen
- **WHEN** the user opens a video that starts with a pre-roll ad
- **THEN** the player enters fullscreen while the ad is shown, as it does for a video without an ad

### Requirement: Ad skip indicator
In the auto flavor, when "Try to skip advertising" is enabled, the fullscreen player SHALL show an "ad blocked" icon (a prohibition sign with "AD" inside) and a localized "Skipping ad…" text, centered over the video area, instead of a plain black screen. This SHALL last from the moment an ad is detected until the requested video starts playing, with no black gap between the end of the ad and the start of the video.

#### Scenario: Indicator during ad skip
- **WHEN** the setting is enabled, the player is fullscreen and an ad is being skipped
- **THEN** the "ad blocked" icon and "Skipping ad…" text stay visible until the requested video plays

#### Scenario: No gap after the ad
- **WHEN** the ad ends and the requested video is still loading
- **THEN** the ad indicator stays visible, without a black screen in between, until the video plays

#### Scenario: No ad indicator when skipping is disabled
- **WHEN** the setting is disabled and an ad plays in fullscreen
- **THEN** no ad indicator is shown and the ad is visible

#### Scenario: Portuguese text
- **WHEN** the device language is Portuguese
- **THEN** the indicator text and the "Try to skip advertising" setting are shown in Portuguese

### Requirement: Loading indicator
In the auto flavor, while the fullscreen player waits for video data with no ad being skipped (initial load, buffering, or after a seek), it SHALL show a loading spinner centered over the video area. Waits shorter than about half a second SHALL NOT show the spinner. The spinner SHALL disappear as soon as the video advances again.

#### Scenario: Buffering shows spinner
- **WHEN** the fullscreen video stalls waiting for data for longer than about half a second
- **THEN** a spinner is shown until the video plays again

#### Scenario: Short wait shows nothing
- **WHEN** the video waits for data for less than about half a second
- **THEN** no spinner appears

#### Scenario: Seek into buffered data
- **WHEN** the user or a SponsorBlock skip seeks into an already buffered part of the video
- **THEN** no spinner is left on screen while the video plays

### Requirement: No indicator while paused or outside fullscreen
The player SHALL NOT show the ad or loading indicator while the user has paused the requested video, and SHALL NOT add either indicator to the inline (non-fullscreen) player or to the mobile flavor.

#### Scenario: Paused video
- **WHEN** the user pauses a fullscreen video
- **THEN** no indicator is shown over the paused frame

#### Scenario: Inline player unchanged
- **WHEN** a video plays in the YouTube page without fullscreen
- **THEN** the page looks as it did before this change, with YouTube's own controls and loading UI

#### Scenario: Leaving fullscreen hides the indicator
- **WHEN** an indicator is visible or about to be shown and the player leaves fullscreen
- **THEN** no indicator is shown afterwards

#### Scenario: Re-entering fullscreen during an ad
- **WHEN** the player re-enters fullscreen while an ad is still being skipped
- **THEN** the ad indicator is shown again
