package com.trypod33.kitchlore.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import java.util.Calendar

/**
 * The app dresses for the month: spring greens, summer teals, autumn
 * terracotta, winter blues. The season comes from the calendar or the
 * user's pick in Settings, and every palette has a light and a dark take
 * so photos and cards keep their warmth in both.
 */
@Composable
fun KitchloreTheme(themeMode: String, season: String, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val month = remember { Calendar.getInstance().get(Calendar.MONTH) } // 0 = January
    val key = when (season) {
        "spring", "summer", "autumn", "winter" -> season
        else -> when (month) {
            2, 3, 4 -> "spring"
            5, 6, 7 -> "summer"
            8, 9, 10 -> "autumn"
            else -> "winter"
        }
    }
    val scheme = when (key) {
        "spring" -> if (dark) springDark() else springPalette()
        "summer" -> if (dark) summerDark() else summerPalette()
        "autumn" -> if (dark) autumnDark() else autumnPalette()
        else -> if (dark) winterDark() else winterPalette()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private fun autumnPalette(): ColorScheme = lightColorScheme(
    primary = Color(0xFFB05B26),
    onPrimary = Color.White,
    secondary = Color(0xFF8A5A33),
    onSecondary = Color.White,
    tertiary = Color(0xFFD29A5B),
    background = Color(0xFFFBF3E9),
    onBackground = Color(0xFF3B2A1C),
    surface = Color(0xFFFBF3E9),
    onSurface = Color(0xFF3B2A1C),
    surfaceVariant = Color(0xFFF1E0CE),
    onSurfaceVariant = Color(0xFF5C4630),
)

private fun autumnDark(): ColorScheme = darkColorScheme(
    primary = Color(0xFFE08A4C),
    onPrimary = Color(0xFF2A1305),
    secondary = Color(0xFFC79A6B),
    onSecondary = Color(0xFF241505),
    tertiary = Color(0xFFE5B87E),
    onTertiary = Color(0xFF241505),
    background = Color(0xFF201710),
    onBackground = Color(0xFFF3E7D9),
    surface = Color(0xFF201710),
    onSurface = Color(0xFFF3E7D9),
    surfaceVariant = Color(0xFF3D2E20),
    onSurfaceVariant = Color(0xFFD9C4AC),
)

private fun winterPalette(): ColorScheme = lightColorScheme(
    primary = Color(0xFF3F6E8C),
    onPrimary = Color.White,
    secondary = Color(0xFF6E8CA0),
    onSecondary = Color.White,
    tertiary = Color(0xFF8FB7C9),
    background = Color(0xFFF4F7FA),
    onBackground = Color(0xFF22323D),
    surface = Color(0xFFF4F7FA),
    onSurface = Color(0xFF22323D),
    surfaceVariant = Color(0xFFE2EAF1),
    onSurfaceVariant = Color(0xFF3D4E59),
)

private fun winterDark(): ColorScheme = darkColorScheme(
    primary = Color(0xFF8FB7C9),
    onPrimary = Color(0xFF0E1A21),
    secondary = Color(0xFFA9C2CF),
    onSecondary = Color(0xFF0E1A21),
    tertiary = Color(0xFFC4DAE4),
    onTertiary = Color(0xFF0E1A21),
    background = Color(0xFF111B21),
    onBackground = Color(0xFFE4EDF2),
    surface = Color(0xFF111B21),
    onSurface = Color(0xFFE4EDF2),
    surfaceVariant = Color(0xFF26343C),
    onSurfaceVariant = Color(0xFFC4D4DD),
)

private fun springPalette(): ColorScheme = lightColorScheme(
    primary = Color(0xFF4E8A57),
    onPrimary = Color.White,
    secondary = Color(0xFF7FA86B),
    onSecondary = Color.White,
    tertiary = Color(0xFFB9C46A),
    background = Color(0xFFF7FBF3),
    onBackground = Color(0xFF24331F),
    surface = Color(0xFFF7FBF3),
    onSurface = Color(0xFF24331F),
    surfaceVariant = Color(0xFFE4F0DC),
    onSurfaceVariant = Color(0xFF3E4C33),
)

private fun springDark(): ColorScheme = darkColorScheme(
    primary = Color(0xFF93C79A),
    onPrimary = Color(0xFF0E2012),
    secondary = Color(0xFFB0CC96),
    onSecondary = Color(0xFF16220E),
    tertiary = Color(0xFFD4DE9B),
    onTertiary = Color(0xFF1E2410),
    background = Color(0xFF121A10),
    onBackground = Color(0xFFE6F0DE),
    surface = Color(0xFF121A10),
    onSurface = Color(0xFFE6F0DE),
    surfaceVariant = Color(0xFF293622),
    onSurfaceVariant = Color(0xFFC8D8BC),
)

private fun summerPalette(): ColorScheme = lightColorScheme(
    primary = Color(0xFF2E8C86),
    onPrimary = Color.White,
    secondary = Color(0xFF48A6A0),
    onSecondary = Color.White,
    tertiary = Color(0xFFF2A65A),
    background = Color(0xFFF6FBFA),
    onBackground = Color(0xFF1C332F),
    surface = Color(0xFFF6FBFA),
    onSurface = Color(0xFF1C332F),
    surfaceVariant = Color(0xFFDFF0EE),
    onSurfaceVariant = Color(0xFF2E453F),
)

private fun summerDark(): ColorScheme = darkColorScheme(
    primary = Color(0xFF7BC4BE),
    onPrimary = Color(0xFF07201E),
    secondary = Color(0xFF96CFC9),
    onSecondary = Color(0xFF07201E),
    tertiary = Color(0xFFF2B67E),
    onTertiary = Color(0xFF241505),
    background = Color(0xFF0F1B1A),
    onBackground = Color(0xFFDFEDEB),
    surface = Color(0xFF0F1B1A),
    onSurface = Color(0xFFDFEDEB),
    surfaceVariant = Color(0xFF243634),
    onSurfaceVariant = Color(0xFFC2D8D5),
)
