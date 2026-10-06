# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
