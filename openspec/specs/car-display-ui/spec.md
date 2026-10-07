# car-display-ui Specification

## Purpose

Renders Fermata's own user interface directly on the Android Auto navigation surface, without mirroring the phone screen, so self-built packages get a full-screen car UI (starting with the YouTube player).

## Requirements

### Requirement: Car app entries
The auto flavor SHALL expose the direct-rendering car UI as an Android Auto navigation app entry labeled "UNI-TV"; other requirements call it "the new entry". On phones running Android 8.0 (API 26) or later, its icon's background SHALL fill the launcher's whole icon shape, with no border of another color. Debug and release builds SHALL use distinguishable labels. The Fermata Media entry SHALL remain available, unchanged. The FS Mirror entry SHALL NOT be listed.

#### Scenario: Launcher lists UNI-TV and Media only
- **WHEN** the auto flavor is installed and the phone connects to a head unit
- **THEN** the Android Auto launcher lists the "UNI-TV" entry and the Fermata Media entry of that package, and no FS Mirror entry of that package

#### Scenario: Icon fills the launcher shape
- **WHEN** the Android Auto launcher shows the "UNI-TV" entry on a phone running Android 8.0 or later
- **THEN** its gradient background fills the whole circle, with no white border, and the white screen/play glyph stays fully visible and centered

#### Scenario: Debug build label
- **WHEN** a debug build of the auto flavor is connected to a head unit
- **THEN** the entry's label identifies it as the debug build ("UNI-TV (debug)")

#### Scenario: Media service unaffected
- **WHEN** the user opens Fermata through the Android Auto media entry while the UNI-TV entry exists
- **THEN** media browsing and playback behave as they did before this change

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
Drags and flings SHALL scroll the content under the drag origin. The origin is the point of the most recent tap or pinch focus, or the center of the visible area if there was none. A page MAY redirect the origin when a drag starts; the YouTube watch page and the media list screens do so as described in "Drag target on the YouTube watch page" and "Drag target on media lists". A fling during a drag SHALL continue that drag with momentum rather than start a new one.

#### Scenario: Scroll a list under the last tap
- **WHEN** the user taps inside a list and then drags vertically
- **THEN** that list scrolls in the drag direction by the dragged distance

#### Scenario: Fling after drag
- **WHEN** the user drags a list and releases with a flick
- **THEN** the list keeps scrolling with momentum and no extra tap is registered

#### Scenario: No redirect outside the YouTube watch page
- **WHEN** the user drags on a screen that is neither the YouTube watch page nor a media list screen (for example YouTube home or search)
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

### Requirement: Drag target on media lists
On the media list screens (IPTV, folders, favorites, playlists), while the media list is shown, a drag SHALL start at the center of the list's visible part unless its origin lies on the list, the control panel or the navigation bar. Controls drawn over the list (the divider, the floating button) do not count as the list. No redirect SHALL apply when the list is not shown or while an overlay menu is open.

#### Scenario: Drag with no tap while a channel plays
- **WHEN** a channel plays with the channel list on the left and the video on the right, no tap or pinch has happened yet, and the user drags vertically
- **THEN** the channel list scrolls and the volume does not change

#### Scenario: Origin on the video while the list is shown
- **WHEN** the list and the video are both shown and the origin lies on the video (for example the user tapped the video, which showed it alone, then went back to the split view without tapping again), and the user drags vertically or horizontally
- **THEN** the list scrolls, and neither the volume nor the playback position changes

#### Scenario: Drag after tapping the toolbar
- **WHEN** the user taps the toolbar on a media list screen and then drags vertically
- **THEN** the media list scrolls

#### Scenario: Split stays put
- **WHEN** the list and the video are both shown, the last tap was on the divider between them (including its part over the list), and the user drags horizontally
- **THEN** the divider does not move and the drag goes to the list

#### Scenario: Floating button stays put
- **WHEN** the last tap was on the floating button drawn over the list and the user drags
- **THEN** the floating button does not move and the list scrolls

#### Scenario: Drag after tapping a list item
- **WHEN** the user taps an item in the media list and then drags vertically
- **THEN** the drag starts at the tapped point and the list scrolls, as before

#### Scenario: Control panel gestures kept
- **WHEN** the user taps the control panel or the navigation bar on a media list screen and then drags or swipes on it
- **THEN** the drag starts at the tapped point and the control panel's gesture (previous/next track, previous/next folder, seek or volume) applies, as before

#### Scenario: Video fills the body
- **WHEN** the video fills the body (video mode, with or without the bars hidden), so the media list is not shown, and the user drags
- **THEN** the drag starts at the last tap or the visible-area center, and the video's drag gestures (volume, seek) work as before

#### Scenario: Overlay menu open
- **WHEN** an overlay menu (for example an item's context menu) is open on a media list screen and the user drags
- **THEN** the drag starts at the last tap or the visible-area center, as before

#### Scenario: Fling without a drag
- **WHEN** on a media list screen the user flicks with no drag in progress and the origin lies on the video
- **THEN** the media list scrolls with momentum

#### Scenario: Taps are not affected
- **WHEN** the user taps the video, a control or a list item on a media list screen
- **THEN** the tap is delivered at the tapped point with the same timing as before this change

### Requirement: Default car theme
Until the user chooses a theme for the Android Auto interface, the car UI SHALL use the Purple theme. A car theme the user has chosen SHALL be kept. The phone UI's theme SHALL NOT depend on the car theme.

#### Scenario: Fresh install
- **WHEN** the auto flavor is freshly installed and the user opens the "UNI-TV" entry
- **THEN** the car UI's toolbars, menus, buttons and background use the Purple theme

#### Scenario: Theme chosen by the user
- **WHEN** the user has chosen another theme (for example Dark or Black) in the Android Auto interface settings
- **THEN** the car UI keeps using that theme after the app is updated or restarted

#### Scenario: Phone UI unchanged
- **WHEN** the auto flavor is freshly installed and the user opens Fermata on the phone
- **THEN** the phone UI uses its own default theme, not Purple

#### Scenario: Every theme still selectable
- **WHEN** the user opens the theme setting of the Android Auto interface
- **THEN** all themes are listed, Purple is shown as the selected one, and choosing Dark applies it to the car UI
