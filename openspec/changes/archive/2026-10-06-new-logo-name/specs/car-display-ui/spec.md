# Spec Delta

## ADDED Requirements

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

## REMOVED Requirements

### Requirement: Separate car app entry
**Reason**: The car UI entry is renamed "UNI-TV" and the FS Mirror entry is no longer listed, so the requirement that both appear side by side no longer holds.
**Migration**: See "Car app entries", which also defines "the new entry" used by the remaining requirements. The Media entry is unchanged; FS Mirror is no longer offered in the car.
