package com.trypod33.kitchlore.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Theme settings live here: light or dark, plus which season's palette. */
object ThemePrefs {
    private fun prefs(c: Context) = c.getSharedPreferences("kitchlore_settings", Context.MODE_PRIVATE)

    fun mode(c: Context): String = prefs(c).getString("theme_mode", "system") ?: "system"
    fun season(c: Context): String = prefs(c).getString("season", "auto") ?: "auto"
    fun setMode(c: Context, v: String) = prefs(c).edit().putString("theme_mode", v).apply()
    fun setSeason(c: Context, v: String) = prefs(c).edit().putString("season", v).apply()
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    themeMode: String,
    season: String,
    onThemeMode: (String) -> Unit,
    onSeason: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(
                "Settings",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            SectionLabel("Theme")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingChip("System", themeMode == "system") { onThemeMode("system") }
                SettingChip("Light", themeMode == "light") { onThemeMode("light") }
                SettingChip("Dark", themeMode == "dark") { onThemeMode("dark") }
            }
            Text(
                "Dark keeps every palette's colors and just flips the surfaces.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            SectionLabel("Season palette")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingChip("Auto", season == "auto") { onSeason("auto") }
                SettingChip("Spring", season == "spring") { onSeason("spring") }
                SettingChip("Summer", season == "summer") { onSeason("summer") }
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingChip("Autumn", season == "autumn") { onSeason("autumn") }
                SettingChip("Winter", season == "winter") { onSeason("winter") }
            }
            Text(
                "Auto follows the calendar month. The seasonal colors stay in both light and dark.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun SettingChip(label: String, selected: Boolean, onTap: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onTap),
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 0.dp, top = 18.dp, bottom = 8.dp),
    )
}
