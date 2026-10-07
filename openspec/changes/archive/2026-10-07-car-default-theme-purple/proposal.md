# Proposal

## Why

The car UI uses the Dark theme by default. Its toolbar, menu and button color (`#4E6AA2` on `#1C1B1F`) has a contrast of about 3.2:1, which is hard to read on the head unit. The Purple theme was tried in the car and reads well (light lilac `#D9C9FB` on `#3D2562`, about 8:1), so it should be what a new install shows.

## What Changes

- The default theme of the car UI (the Android Auto interface theme setting) changes from Dark to Purple.
- The phone UI's default theme (Classic) does not change.
- A user who has already chosen a car theme keeps it. Installs that never changed the car theme also switch to Purple, since the default applies whenever no choice was saved. This branch does not migrate them back to Dark.
- The setting keeps offering every theme, so Dark is still available.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `car-display-ui`: adds a requirement that the car UI opens with the Purple theme until the user picks another car theme.

## Impact

- `fermata/src/main/java/me/aap/fermata/ui/activity/MainActivityPrefs.java`: default value of `THEME_AA`.
- Auto flavor only. `THEME_AA` is read only for car activities (`getThemePref(true)`), so the mobile flavor and the phone UI are unaffected.
- No new dependencies, resources or strings.
