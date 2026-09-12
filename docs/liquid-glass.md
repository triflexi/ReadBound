# Liquid Glass UI

ReadBound uses [Kyant's Backdrop](https://github.com/Kyant0/AndroidLiquidGlass)
(`io.github.kyant0:backdrop:2.0.1`, Apache-2.0) as its Android Liquid Glass renderer.
The repository's button, slider, toggle, bottom-tab and motion utilities are adapted
in `RepositoryLiquidComponents.kt` and `RepositoryLiquidMotion.kt` with Android
semantics and disabled-state support retained.

## Material and screens

`ui/LiquidGlass.kt` owns the material and shared controls. A quiet blue/mint
background, translucent tinted surfaces, directional edge light, capsule controls,
drag deformation and spring press feedback are shared by the library, notes,
application settings, reader controls, contents, selection, translation and Anki
editor. Primary actions use a restrained semantic container instead of an opaque
accent; icon, rail, selector, tag and checkbox controls use the same material.

Floating navigation, the settings header and reader panels sample a separately
recorded content layer with Backdrop's vibrancy, blur and lens effects. The glass
must never be recorded into the same layer it samples. Inline cards and controls
sample only the ambient background. Book pages keep their light/sepia/dark paper
and typography settings.

Dialogs and modal sheets occupy separate Android windows. They use denser frosted
surfaces instead of sampling an incorrectly aligned screen layer. On API 26–30,
all surfaces use the opaque-tint fallback; API 31–32 add blur, and API 33+ also
support lens refraction. Material input, switch, slider, dialog and sheet behavior
is retained for keyboard, accessibility and back/dismiss handling.

Themes use semantic foreground colors and animate between complete light/dark
palettes. Disabled controls keep an explicit readable foreground instead of
stacking Material and whole-control alpha. Navigation exposes selected tab
semantics. Theme and language choices use the demo's animated segmented control.

The app locale is supplied to Compose from the persisted preference. Switching
languages therefore recomposes in place instead of racing a DataStore write against
Activity recreation; the persisted preference also seeds the first frame so neither
locale nor theme flashes to defaults during launch.

## Build and verification

The project uses AGP 9.2.1, supported by the installed Android Studio 2026.1.1.
Use Android Studio's bundled JDK 21 and the checked-in Gradle wrapper:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew.bat :app:connectedDebugAndroidTest
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`.
`LiquidGlassTest` covers selected navigation, disabled submission, text entry,
accessible switch/slider actions in dark mode, and Anki draft submission.

## Verification for this change

- Debug APK assembled with the Android Studio JDK and AGP 9.2.1.
- 25 unit tests passed, including WCAG contrast checks for both semantic palettes;
  Android Lint: 0 errors, 31 warnings.
- All 8 instrumentation tests passed on the Android Studio API 37 emulator,
  including a full `MainActivity` startup regression test and component semantics.
- Manually switched Russian to English and light to dark on the running activity.
  The process stayed alive, text and colors updated in place, and no startup or
  Activity-result owner crash was logged.
