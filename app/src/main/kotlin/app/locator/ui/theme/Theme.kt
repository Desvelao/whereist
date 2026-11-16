package app.locator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.locator.R

/**
 * "Answer first" direction: near-black teal surfaces, one teal accent, the location is the biggest line on every
 * row. Manrope for headings, Inter for everything else (both bundled in res/font so the app stays offline).
 *
 * Surface roles: surfaceContainerLow = cards, surfaceContainerHigh = bars and sheets,
 * surfaceContainerHighest = icon tiles, outline = button borders, outlineVariant = dividers.
 */
private val Teal = Color(0xFF4FD1C5)
private val Ink = Color(0xFF0E1B1A)
private val Muted = Color(0xFF93ADAA)
private val Soft = Color(0xFFD6E4E1)
private val Snow = Color(0xFFEAF4F2)

val LocatorDark: ColorScheme = darkColorScheme(
    primary = Teal, onPrimary = Ink,
    primaryContainer = Color(0xFF1F3A38), onPrimaryContainer = Snow,
    secondary = Teal, onSecondary = Ink,
    secondaryContainer = Color(0xFF1F3A38), onSecondaryContainer = Snow,
    background = Ink, onBackground = Snow,
    surface = Ink, onSurface = Snow,
    // surfaceVariant must differ from the container colours, or Material picks the muted text colour on panels.
    surfaceVariant = Color(0xFF1A2E2D), onSurfaceVariant = Muted,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Color(0xFF152726),
    surfaceContainer = Color(0xFF152726),
    surfaceContainerHigh = Color(0xFF1B3130),
    surfaceContainerHighest = Color(0xFF1F3A38),
    outline = Color(0xFF3B5E5B), outlineVariant = Color(0xFF2A4543),
    error = Color(0xFFFF8A8A), onError = Ink,
)

val LocatorLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF00695F), onPrimary = Color.White,
    primaryContainer = Color(0xFFD4EFEB), onPrimaryContainer = Ink,
    secondary = Color(0xFF00695F), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4EFEB), onSecondaryContainer = Ink,
    background = Color(0xFFF1F7F6), onBackground = Ink,
    surface = Color(0xFFF1F7F6), onSurface = Ink,
    surfaceVariant = Color(0xFFE3EEEC), onSurfaceVariant = Color(0xFF3F5654),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFE8F1EF),
    surfaceContainerHighest = Color(0xFFD9E8E5),
    outline = Color(0xFF6B8582), outlineVariant = Color(0xFFC7D8D5),
    error = Color(0xFFB3261E), onError = Color.White,
)

/** Colours Material has no role for: a weak GPS fix and other warnings. */
@Immutable
class LocatorExtras(val warning: Color, val onWarning: Color, val warningContainer: Color)

private val DarkExtras = LocatorExtras(Color(0xFFF2B84B), Color(0xFF2A1D00), Color(0xFF3A2E10))
private val LightExtras = LocatorExtras(Color(0xFF8A5A00), Color.White, Color(0xFFFFE9BF))
private val LocalExtras = staticCompositionLocalOf { DarkExtras }

val MaterialTheme.extras: LocatorExtras
    @Composable @ReadOnlyComposable get() = LocalExtras.current

private fun manrope(weight: Int) =
    Font(R.font.manrope, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

private fun inter(weight: Int) =
    Font(R.font.inter, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

private val Heading = FontFamily(manrope(600), manrope(800))
private val Body = FontFamily(inter(400), inter(500), inter(600))

private val base = Typography()

val LocatorTypography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = Heading, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = Heading, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = Heading, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = base.titleLarge.copy(fontFamily = Heading, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.4.sp),
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

/** True when [mode] means dark right now (SYSTEM follows the phone). */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun LocatorTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtras provides if (dark) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (dark) LocatorDark else LocatorLight,
            typography = LocatorTypography,
            content = content,
        )
    }
}
