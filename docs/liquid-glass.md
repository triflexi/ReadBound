# Liquid Glass UI

ReadBound uses [Kyant's Backdrop](https://github.com/Kyant0/AndroidLiquidGlass)
(`io.github.kyant0:backdrop:1.0.6`, Apache-2.0) as its Android Liquid Glass renderer.
The Android-only 1.0.6 release matches the project's Kotlin 2.3 / Compose toolchain.
No upstream demo component source is copied into the application.

## Material and screens

`ui/LiquidGlass.kt` owns the material and shared controls. A quiet blue/mint
background, translucent tinted surfaces, directional edge light, capsule controls
and spring press feedback are shared by the library, notes, application settings,
reader controls, contents, selection, translation and Anki editor.

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

Themes use semantic foreground colors; saturated actions keep an almost opaque
tint for contrast. Navigation exposes selected tab semantics. The reader settings
sheet scrolls on short screens, and theme choices wrap in application settings.

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
- 23 unit tests passed; Android Lint: 0 errors, 33 warnings.
- Emulator API 37: inspected the library and settings in both themes, imported a
  test book, checked reader backdrop blur, and verified the corrected sheet anchoring.
- Initial UI run: text input, switch/slider and Anki submission tests passed.
  The navigation test selector was corrected; a short-sheet regression test was
  added. The final UI suite was not rerun at the user's request to finish promptly.
