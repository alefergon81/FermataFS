# car-display-ui Specification

## Purpose

Renders Fermata's own user interface directly on the Android Auto navigation surface, without mirroring the phone screen, so self-built packages get a full-screen car UI (starting with the YouTube player).

## Requirements

### Requirement: Separate car app entry
The auto flavor SHALL expose the direct-rendering car UI as its own Android Auto navigation app entry, with a label and icon distinct from FS Mirror. Debug and release builds SHALL use distinguishable labels. The existing Media service and FS Mirror entries SHALL remain available, with unchanged behavior.

#### Scenario: Both entries listed in the launcher
- **WHEN** the auto flavor is installed and the phone connects to a head unit
- **THEN** the Android Auto launcher lists the new entry ("Fermata Car") and the FS Mirror entry as separate apps

#### Scenario: Debug build label
- **WHEN** a debug build of the auto flavor is connected to a head unit
- **THEN** the new entry's label identifies it as the debug build

#### Scenario: Media service unaffected
- **WHEN** the user opens Fermata through the Android Auto media entry while the new entry exists
- **THEN** media browsing and playback behave as they did before this change

#### Scenario: FS Mirror unaffected
- **WHEN** the user opens the FS Mirror entry
- **THEN** mirroring, tapping and scrolling behave as they did before this change

### Requirement: No mirroring permissions
The car UI SHALL render and accept gestures without capturing the phone screen and without screen-capture consent, overlay, write-settings, accessibility or root/Xposed access.

#### Scenario: Fresh install without extra grants
- **WHEN** the app is freshly installed, no appops or special permissions are granted, and the user opens the new entry
- **THEN** the Fermata UI appears on the head unit, gestures work, and no screen-capture or accessibility prompt is shown on the phone

#### Scenario: Phone screen is independent
- **WHEN** the car UI is displayed and the phone is locked or showing another app
- **THEN** the head unit keeps showing and updating the Fermata UI, and nothing from the phone screen appears on it

### Requirement: Render on the car surface
The car UI SHALL be drawn to the surface the head unit provides, sized to its width, height and density, and SHALL follow surface replacement and resizing.

#### Scenario: Surface becomes available
- **WHEN** the head unit provides a drawing surface
- **THEN** the Fermata UI is laid out for that surface size and density and displayed

#### Scenario: Surface resized
- **WHEN** the head unit replaces the surface with one of a different size
- **THEN** the UI is laid out again for the new size without restarting playback

#### Scenario: Surface destroyed
- **WHEN** the head unit destroys the surface
- **THEN** rendering to it stops and no errors or crashes occur

### Requirement: Respect the visible area
The car UI SHALL keep its interactive content inside the visible area reported by the head unit, so template controls drawn over the surface do not cover it.

#### Scenario: Visible area reported smaller than the surface
- **WHEN** the head unit reports a visible area inset from the surface edges
- **THEN** the UI's controls and content lie within that area, and only background or full-screen video may extend outside it

### Requirement: YouTube as the initial screen
When a car UI session starts, it SHALL show the YouTube addon screen, including in-page browsing and full-screen video playback on the car display. When YouTube is unavailable, it SHALL show the screen Fermata would normally show on start.

#### Scenario: Open entry
- **WHEN** the user opens the new entry on the head unit
- **THEN** the YouTube screen is shown, exactly once (no flash of another start screen)

#### Scenario: Full-screen video
- **WHEN** the user starts a YouTube video and enters full screen
- **THEN** the video fills the car display, keeping its aspect ratio according to the YouTube addon's scale setting

#### Scenario: YouTube addon disabled
- **WHEN** the YouTube addon is disabled or its module is not installed
- **THEN** the car UI shows Fermata's normal start screen instead of failing

### Requirement: Tap gesture
A tap on the head unit SHALL activate the element under the tapped point, as a tap at the same place on the phone would.

#### Scenario: Tap a control
- **WHEN** the user taps a button on the head unit
- **THEN** that button is activated

### Requirement: Pinch gesture
A pinch on the head unit SHALL zoom content that supports zooming, around the pinch focus.

#### Scenario: Pinch
- **WHEN** the user pinches on content that supports zoom
- **THEN** the content zooms in or out around the pinch focus

### Requirement: Drag and fling gestures
Drags and flings SHALL scroll the content under the drag origin. The origin is the point of the most recent tap or pinch focus, or the center of the visible area if there was none. A page MAY redirect the origin when a drag starts; the YouTube watch page does so as described in "Drag target on the YouTube watch page". A fling during a drag SHALL continue that drag with momentum rather than start a new one.

#### Scenario: Scroll a list under the last tap
- **WHEN** the user taps inside a list and then drags vertically
- **THEN** that list scrolls in the drag direction by the dragged distance

#### Scenario: Fling after drag
- **WHEN** the user drags a list and releases with a flick
- **THEN** the list keeps scrolling with momentum and no extra tap is registered

#### Scenario: No redirect outside the YouTube watch page
- **WHEN** the user drags on any screen other than the YouTube watch page (for example YouTube home or search, IPTV, folders)
- **THEN** the drag starts at the last tap or pinch focus, or at the visible-area center if there was none, as before

### Requirement: Navigation actions stay reachable
Back, home and the long-press substitute SHALL stay reachable both in normal mode and while dragging/panning.

#### Scenario: Back while panning
- **WHEN** the user has just scrolled content and wants to go back
- **THEN** a back action is reachable without restarting the car UI

### Requirement: Long-press substitute
Because the head unit does not report long presses, the car UI SHALL provide an alternative way to open the actions a long press opens on the phone.

#### Scenario: Open item actions
- **WHEN** the user activates the long-press substitute and then taps an item
- **THEN** the item's context actions appear, as a long press would show them on the phone

#### Scenario: Substitute is one-shot
- **WHEN** the user has used the long-press substitute once
- **THEN** the next tap behaves as a normal tap

### Requirement: Text input
The car UI SHALL let the user enter and submit text (such as a YouTube search) without the phone keyboard. Voice input SHALL be offered when voice control is enabled and the microphone permission is already granted; otherwise the head unit's text entry SHALL be used.

#### Scenario: Voice search
- **WHEN** voice control is enabled, the microphone permission is granted, and the user activates the YouTube search field
- **THEN** speech recognition starts and the search for the recognized text is executed

#### Scenario: Head unit keyboard
- **WHEN** voice control is unavailable and the user activates the YouTube search field
- **THEN** the head unit's text entry opens and, on confirmation, the search for the entered text is executed

#### Scenario: Microphone permission missing
- **WHEN** voice control is enabled but the microphone permission was never granted
- **THEN** the head unit's text entry is used instead, and no permission prompt is shown on the phone

#### Scenario: Typing restricted while driving
- **WHEN** the head unit disables keyboard entry because the car is moving
- **THEN** the UI does not hang waiting for text, and voice input stays usable when available

### Requirement: Coexistence with the phone UI
The car UI SHALL run at the same time as the phone UI. While a car UI session exists it SHALL take priority for media session control; when the session ends it SHALL release that priority and leave the phone UI and media notification working.

#### Scenario: Phone app open during car session
- **WHEN** the Fermata phone activity is open and the car UI starts
- **THEN** both remain usable, and playback commands from the head unit act on the car UI's player

#### Scenario: Both UIs on the YouTube screen
- **WHEN** the phone shows the YouTube screen with a video playing and the car UI starts
- **THEN** the phone video pauses, the car YouTube player is the only one playing, and navigating on either page never stops the other page's playback

#### Scenario: Separate YouTube resume positions
- **WHEN** a video was left at different positions in the car UI and on the phone
- **THEN** each UI resumes at its own position

#### Scenario: Switching to another Android Auto app
- **WHEN** the user switches from the car UI to another Android Auto app (for example the Fermata media entry) and back
- **THEN** the car UI resumes on the same screen with its state, and it keeps media session priority meanwhile

#### Scenario: Car session ends
- **WHEN** the head unit disconnects or ends the car UI session
- **THEN** the car UI's resources are released and media session priority returns to the phone UI, if one is present

#### Scenario: Media notification after a car session
- **WHEN** a car UI session has ended and the user taps Fermata's media notification on the phone
- **THEN** the phone Fermata activity opens

#### Scenario: Media notification after a cold start by Android Auto
- **WHEN** Android Auto started the app process, the phone Fermata activity has never been opened, and the user taps the media notification
- **THEN** the phone Fermata activity opens

### Requirement: Back and home actions
The car UI SHALL offer back navigation within its own screens and a way to return to its start screen.

#### Scenario: Back inside the UI
- **WHEN** the user activates back while a nested screen is shown
- **THEN** the previous screen of the car UI is shown

#### Scenario: Back at the start screen
- **WHEN** the user activates back on the start screen
- **THEN** the car UI stays on the start screen and does not affect the phone UI

#### Scenario: Home
- **WHEN** the user activates home from any screen
- **THEN** the car UI shows its start screen

### Requirement: Drag target on the YouTube watch page
On the YouTube watch page, where the video column and the recommendations column scroll separately, a drag SHALL scroll the recommendations column when no tap or pinch has happened since the page's URL last changed. Otherwise it SHALL scroll the content under the origin. In full screen video and while the page is zoomed, no redirect SHALL apply.

#### Scenario: Drag after opening a video from another page
- **WHEN** the user taps a video thumbnail on YouTube home or search, the watch page opens in two columns, and the user drags vertically without tapping again
- **THEN** the recommendations column scrolls, wherever the thumbnail was

#### Scenario: Drag after opening a recommendation
- **WHEN** the user taps a recommendation, the new video's watch page shows in two columns, and the user drags vertically without tapping again
- **THEN** the recommendations column scrolls

#### Scenario: Scroll the video column to read comments
- **WHEN** the user taps the video player (or the description or comments, once visible) on the current watch page and then drags vertically
- **THEN** the video column scrolls, bringing the comments into view

#### Scenario: Back to the recommendations after tapping the player
- **WHEN** the user has tapped the player on the current watch page and wants to scroll the recommendations
- **THEN** a tap on the recommendations column (or opening another video) aims the next drag there

#### Scenario: Fling without a drag
- **WHEN** on the watch page the user flicks with no drag in progress and with no tap since the page opened
- **THEN** the recommendations column scrolls with momentum

#### Scenario: Fling keeps the redirected drag
- **WHEN** a redirected drag is in progress and the user flicks
- **THEN** the fling continues that drag in the recommendations column

#### Scenario: Full screen video
- **WHEN** the video is in full screen and the user drags
- **THEN** the drag starts at the last tap or the visible-area center, as before

#### Scenario: Zoomed page
- **WHEN** the user has pinch-zoomed the watch page and then drags
- **THEN** the drag starts at the last tap or pinch focus, as before

#### Scenario: Layout not known yet
- **WHEN** the page has not yet reported where the recommendations are (for example right after navigation)
- **THEN** the drag uses the origin as it is, without a redirect, and no error is shown

#### Scenario: Taps are not delayed
- **WHEN** the user taps a recommendation or the player on the watch page
- **THEN** the tap is delivered to the page immediately, with the same timing as before this change
