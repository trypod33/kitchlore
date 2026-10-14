package com.trypod33.kitchlore.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import java.util.Calendar

/**
 * The app dresses for the month: spring greens, summer teals, autumn
 * terracotta, winter blues. All palettes are warm, soft light themes so
 * photos and cards keep their warmth in every season.
 */
@Composable
fun KitchloreTheme(content: @Composable () -> Unit) {
    val month = remember { Calendar.getInstance().get(Calendar.MONTH) } // 0 = January
    val scheme = when (month) {
        2, 3, 4 -> springPalette()
        5, 6, 7 -> summerPalette()
        8, 9, 10 -> autumnPalette()
        else -> winterPalette()
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
