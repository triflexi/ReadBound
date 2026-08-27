package app.readbound.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderThemeResolverTest {
    @Test fun systemThemeOverridesStoredSepiaWhenEnabled() {
        val preferences = ReaderPreferences(followSystemTheme = true, theme = ReaderTheme.SEPIA)
        assertEquals(ReaderTheme.DARK, effectiveReaderTheme(preferences, systemDark = true))
        assertEquals(ReaderTheme.LIGHT, effectiveReaderTheme(preferences, systemDark = false))
    }

    @Test fun explicitThemeWinsWhenSystemFollowingIsDisabled() {
        val preferences = ReaderPreferences(followSystemTheme = false, theme = ReaderTheme.SEPIA)
        assertEquals(ReaderTheme.SEPIA, effectiveReaderTheme(preferences, systemDark = true))
    }
}
