package com.ayaz.viewer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ayaz.viewer.data.ThemeMode

object AyazColors {
    val Blue = Color(0xFF4F7CFF)
    val BlueDeep = Color(0xFF2F5BFF)
    val Purple = Color(0xFF8B5CF6)
    val PdfRed = Color(0xFFFF5A5F)
    val ExcelGreen = Color(0xFF1FB57A)
    val WhatsAppGreen = Color(0xFF25D366)
    val TelegramBlue = Color(0xFF2AABEE)

    val brandGradient: Brush get() = Brush.linearGradient(listOf(BlueDeep, Color(0xFF5B4DF2), Purple))
}

/** Material renk şemasında olmayan ek tonlar. */
@Immutable
data class AyazExtraColors(
    val card: Color,
    val cardBorder: Color,
    val subtle: Color,
    val viewerBackground: Color,
    val isDark: Boolean,
)

val LocalAyazColors = staticCompositionLocalOf {
    AyazExtraColors(Color(0xFF16161C), Color(0xFF24242D), Color(0xFF8E8E9A), Color(0xFF0B0B0F), true)
}

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = AyazColors.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1C2647),
    onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = AyazColors.Purple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2A1F4A),
    onSecondaryContainer = Color(0xFFEADDFF),
    tertiary = Color(0xFF22D3EE),
    background = Color(0xFF050507),
    onBackground = Color(0xFFF2F2F5),
    surface = Color(0xFF0B0B0F),
    onSurface = Color(0xFFF2F2F5),
    surfaceVariant = Color(0xFF17171D),
    onSurfaceVariant = Color(0xFFA3A3AF),
    surfaceContainerLowest = Color(0xFF050507),
    surfaceContainerLow = Color(0xFF0E0E13),
    surfaceContainer = Color(0xFF121218),
    surfaceContainerHigh = Color(0xFF18181F),
    surfaceContainerHighest = Color(0xFF1F1F27),
    outline = Color(0xFF34343F),
    outlineVariant = Color(0xFF24242D),
    error = Color(0xFFFF5C6C),
    onError = Color.White,
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF3B63F0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE5FF),
    onPrimaryContainer = Color(0xFF0D1C55),
    secondary = Color(0xFF7C4DFF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE5FF),
    onSecondaryContainer = Color(0xFF26135C),
    tertiary = Color(0xFF0891B2),
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF0F1115),
    surface = Color(0xFFF5F6FA),
    onSurface = Color(0xFF0F1115),
    surfaceVariant = Color(0xFFE9EBF2),
    onSurfaceVariant = Color(0xFF5B6070),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FAFC),
    surfaceContainer = Color(0xFFF1F2F7),
    surfaceContainerHigh = Color(0xFFEBEDF3),
    surfaceContainerHighest = Color(0xFFE4E6EE),
    outline = Color(0xFFC9CCD6),
    outlineVariant = Color(0xFFE2E4EA),
    error = Color(0xFFD92D43),
    onError = Color.White,
)

private val AyazTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AyazShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun isAppInDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun AyazTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme(mode)
    val scheme = if (dark) DarkScheme else LightScheme
    val extra = if (dark) {
        AyazExtraColors(
            card = Color(0xFF111116),
            cardBorder = Color(0xFF1F1F27),
            subtle = Color(0xFF8E8E9A),
            viewerBackground = Color(0xFF0B0B0F),
            isDark = true,
        )
    } else {
        AyazExtraColors(
            card = Color.White,
            cardBorder = Color(0xFFE6E8EE),
            subtle = Color(0xFF6B7080),
            viewerBackground = Color(0xFFE9EBF0),
            isDark = false,
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalAyazColors provides extra) {
        MaterialTheme(colorScheme = scheme, typography = AyazTypography, shapes = AyazShapes, content = content)
    }
}

val MonoNumberStyle = TextStyle(fontWeight = FontWeight.Medium)
