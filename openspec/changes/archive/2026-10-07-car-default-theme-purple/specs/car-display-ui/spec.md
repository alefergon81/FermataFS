# Spec Delta

## ADDED Requirements

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
