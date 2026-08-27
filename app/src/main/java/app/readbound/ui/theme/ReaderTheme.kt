package app.readbound.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.readbound.R
import app.readbound.settings.ReaderPreferences
import app.readbound.settings.ReaderTheme
import app.readbound.settings.effectiveReaderTheme

object ReaderColors {
    val Primary = Color(0xFF5B55D8)
    val AccentContainer = Color(0xFFE7E4FF)
    val Lime = Color(0xFFC9F36A)
    val Coral = Color(0xFFFF806C)
    val AppBackground = Color(0xFFF7F6FF)
    val Surface = Color.White
    val SurfaceMuted = Color(0xFFEFEFFA)
    val SurfaceDark = Color(0xFF1E2030)
    val TextPrimary = Color(0xFF202231)
    val TextSecondary = Color(0xFF696B7A)
    val Outline = Color(0xFFD8D7E4)
    val ReaderText = Color(0xFF342F29)
    val ReaderMuted = Color(0xFF857D72)
    val ReaderBackground = Color(0xFFFBF6EA)
    val HighlightYellow = Color(0xFFFFE58F)
    val HighlightGreen = Color(0xFFD4F1E1)
    val HighlightBlue = Color(0xFFDCEAFF)
    val HighlightCoral = Color(0xFFFFD7CF)
    val Success = Color(0xFF2E7D5B)
}

val Manrope = FontFamily(Font(R.font.manrope, weight = FontWeight.Normal))
val Literata = FontFamily(Font(R.font.literata, weight = FontWeight.Normal))

private val AppTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.1).sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
)

private val LightScheme = lightColorScheme(
    primary = ReaderColors.Primary,
    onPrimary = Color.White,
    primaryContainer = ReaderColors.AccentContainer,
    onPrimaryContainer = ReaderColors.Primary,
    background = ReaderColors.AppBackground,
    onBackground = ReaderColors.TextPrimary,
    surface = ReaderColors.Surface,
    onSurface = ReaderColors.TextPrimary,
    surfaceVariant = ReaderColors.SurfaceMuted,
    onSurfaceVariant = ReaderColors.TextSecondary,
    outlineVariant = ReaderColors.Outline,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFBEB9FF),
    onPrimary = Color(0xFF272071),
    primaryContainer = Color(0xFF3E397E),
    onPrimaryContainer = Color(0xFFE7E4FF),
    background = Color(0xFF171821),
    onBackground = Color(0xFFF2F0FA),
    surface = Color(0xFF202230),
    onSurface = Color(0xFFF2F0FA),
    surfaceVariant = Color(0xFF2A2C3B),
    onSurfaceVariant = Color(0xFFC5C3D0),
    outline = Color(0xFF8E8C9A),
    outlineVariant = Color(0xFF454756),
)

@Composable
fun ReaderAppTheme(preferences: ReaderPreferences, content: @Composable () -> Unit) {
    val dark = effectiveReaderTheme(preferences, isSystemInDarkTheme()) == ReaderTheme.DARK
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = AppTypography, content = content)
}
