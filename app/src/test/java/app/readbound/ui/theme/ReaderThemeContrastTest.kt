package app.readbound.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderThemeContrastTest {
    @Test fun lightControlsUseReadableSemanticPairs() = assertSemanticContrast(LightScheme)

    @Test fun darkControlsUseReadableSemanticPairs() = assertSemanticContrast(DarkScheme)

    private fun assertSemanticContrast(scheme: ColorScheme) {
        listOf(
            "background" to (scheme.onBackground to scheme.background),
            "surface" to (scheme.onSurface to scheme.surface),
            "surface variant" to (scheme.onSurfaceVariant to scheme.surfaceVariant),
            "primary" to (scheme.onPrimary to scheme.primary),
            "primary container" to (scheme.onPrimaryContainer to scheme.primaryContainer),
            "error container" to (scheme.onErrorContainer to scheme.errorContainer),
        ).forEach { (name, colors) ->
            val ratio = contrastRatio(colors.first, colors.second)
            assertTrue("$name contrast was $ratio", ratio >= 4.5f)
        }
    }

    private fun contrastRatio(foreground: Color, background: Color): Float {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        return (lighter + .05f) / (darker + .05f)
    }
}
