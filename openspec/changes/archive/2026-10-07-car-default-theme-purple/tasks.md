# Tasks

## 1. Default car theme

- [x] 1.1 In `fermata/src/main/java/me/aap/fermata/ui/activity/MainActivityPrefs.java`, change the default of `THEME_AA` from `THEME_DARK` to `THEME_PURPLE`, leaving `THEME_MAIN` (Classic) unchanged; verify by reading the diff, which must touch only that line
- [x] 1.2 Add `fermata/src/testAuto/java/me/aap/fermata/ui/activity/CarThemeDefaultTest.java` asserting `THEME_AA.getDefaultValue().getAsInt() == THEME_PURPLE` and `THEME_MAIN.getDefaultValue().getAsInt() == THEME_CLASSIC`; verify with `./gradlew :fermata:testAutoDebugUnitTest --tests 'me.aap.fermata.ui.activity.CarThemeDefaultTest'` passing

## 2. Check in the head unit

- [x] 2.1 Build the auto release universal APK (`-PABI=arm64-v8a -PAPP_ID_SFX=.car`), clear the app's data on the phone (or install fresh through KingInstaller), connect with `tools\dhu.cmd` and open UNI-TV; verify the toolbars, menus and buttons use the Purple theme and the phone UI still opens in Classic
- [x] 2.2 In the car UI, open Settings → Interface → Theme; verify Purple is shown as selected, choose Dark, restart the DHU, and verify the car UI comes back in Dark
