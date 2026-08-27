package app.readbound.settings

fun effectiveReaderTheme(preferences: ReaderPreferences, systemDark: Boolean): ReaderTheme =
    if (preferences.followSystemTheme) {
        if (systemDark) ReaderTheme.DARK else ReaderTheme.LIGHT
    } else {
        preferences.theme
    }
