# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
