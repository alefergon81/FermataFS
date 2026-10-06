# Proposal

## Why

The car surface API reports no drag start point, so Fermata Car starts every drag at the last tap (or the visible-area center). On the YouTube watch page the video column and the recommendations column scroll separately. To scroll the recommendations the user must first tap them, and that tap opens another video. Without such a tap, the drag starts wherever the last tap happened, usually on the thumbnail that opened the video, on the previous page. The column it scrolls is then a matter of luck. Both are bad while driving.

## What Changes

- On the YouTube watch page in the car UI, a drag scrolls the recommendations column when there is no origin on the current page. A tap or pinch made before the last page (URL) change does not count as an origin on the current page.
- A drag whose origin was set on the current page keeps the current rule and scrolls the content under the origin. That includes a tap on the player, which is how the user reaches the comments: in the car layout the player fills the whole video column (found on the DHU, see design.md).
- In full screen video, and on all other pages and screens (YouTube home/search, IPTV, folders), the current rule is kept.
- Taps are dispatched as before, with no added delay. No "aim" mode or tap-delay window is added.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `car-display-ui`: the "Drag and fling gestures" requirement allows a page to redirect the drag origin. A new requirement defines the redirect on the YouTube watch page.

## Impact

- `fermata/src/auto`: `CarGesturePlan` (an origin hook applied when a drag opens; it also records when the origin was set), `CarTouchDispatcher`, `CarDisplayScreen`/`CarDisplaySession`/`CarHostActivity` (asking the active fragment for a drag origin), JUnit tests in `fermata/src/testAuto`.
- `fermata/src/main`: an overridable hook on `MainActivityFragment` that returns no redirect by default. Dynamic feature modules can override it; the auto flavor cannot reference module classes directly.
- `modules/web` (YouTube): an injected script that reports the page layout, recording of URL changes, the fragment override, and a pure redirect rule with JUnit tests in `modules/web/src/test`.
- The phone UI and FS Mirror are unchanged.
