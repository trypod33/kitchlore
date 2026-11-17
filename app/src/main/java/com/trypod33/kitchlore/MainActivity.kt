package com.trypod33.kitchlore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.trypod33.kitchlore.data.local.AppDatabase
import com.trypod33.kitchlore.ui.CookModeRoute
import com.trypod33.kitchlore.ui.KitchloreTheme
import com.trypod33.kitchlore.ui.SettingsScreen
import com.trypod33.kitchlore.ui.CategoryRoute
import com.trypod33.kitchlore.ui.LegalRoute
import com.trypod33.kitchlore.ui.ThemePrefs
import com.trypod33.kitchlore.ui.WebImportRoute
import com.trypod33.kitchlore.ui.RecipeHuntRoute
import com.trypod33.kitchlore.ui.DocImportRoute
import com.trypod33.kitchlore.ui.EditRecipeScreen
import com.trypod33.kitchlore.ui.RecipeDetailRoute
import com.trypod33.kitchlore.ui.RecipeListScreen
import android.content.Intent
import com.trypod33.kitchlore.ui.ScanRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as KitchloreApp
        // Share-to-Kitchlore: a shared link opens straight into the import screen
        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        val initialScreen = if (sharedText != null && sharedText.startsWith("http")) "import:" + sharedText else "list"
        setContent {
            var themeMode by remember { mutableStateOf(ThemePrefs.mode(this)) }
            var season by remember { mutableStateOf(ThemePrefs.season(this)) }
            KitchloreTheme(themeMode, season) {
                KitchloreNav(
                    app.database,
                    initialScreen,
                    themeMode,
                    season,
                    onThemeMode = { ThemePrefs.setMode(this, it); themeMode = it },
                    onSeason = { ThemePrefs.setSeason(this, it); season = it },
                )
            }
        }
    }
}

@Composable
fun KitchloreNav(
    db: AppDatabase,
    initialScreen: String = "list",
    themeMode: String,
    season: String,
    onThemeMode: (String) -> Unit,
    onSeason: (String) -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
            var screen by remember { mutableStateOf(initialScreen) }
            when {
                screen.startsWith("cook:") -> {
                    val idStr = screen.substringAfter("cook:")
                    CookModeRoute(db, idStr.toLong(), onBack = { screen = "detail:$idStr" })
                }
                screen.startsWith("edit:") -> {
                    val idStr = screen.substringAfter("edit:")
                    EditRecipeScreen(
                        db = db,
                        recipeId = idStr.toLong(),
                        isNew = idStr == "0",
                        onDone = { screen = if (idStr == "0") "list" else "detail:$idStr" },
                        onCancel = { screen = if (idStr == "0") "list" else "detail:$idStr" },
                    )
                }
                screen.startsWith("detail:") -> {
                    val idStr = screen.substringAfter("detail:")
                    RecipeDetailRoute(
                        db = db,
                        recipeId = idStr.toLong(),
                        onBack = { screen = "list" },
                        onCook = { screen = "cook:$idStr" },
                        onEdit = { screen = "edit:$idStr" },
                    )
                }
                screen == "hunt" -> RecipeHuntRoute(
                    db = db,
                    onBack = { screen = "list" },
                    onOpen = { screen = "detail:$it" },
                    onOpenWeb = { screen = "import:$it" },
                )
                screen.startsWith("import:") -> WebImportRoute(
                    db = db,
                    initialUrl = screen.substringAfter("import:"),
                    onBack = { screen = "list" },
                    onOpen = { screen = "detail:$it" },
                )
                screen.startsWith("category:") -> CategoryRoute(
                    db = db,
                    tag = screen.substringAfter("category:"),
                    onBack = { screen = "list" },
                    onOpen = { screen = "detail:$it" },
                )
                screen == "settings" -> SettingsScreen(
                    onBack = { screen = "list" },
                    themeMode = themeMode,
                    season = season,
                    onThemeMode = onThemeMode,
                    onSeason = onSeason,
                    onLegal = { screen = "legal" },
                )
                screen == "legal" -> LegalRoute(onBack = { screen = "settings" })
                screen == "scan" -> ScanRoute(
                    db = db,
                    mode = "camera",
                    onDone = { screen = "list" },
                    onBack = { screen = "list" },
                )
                screen == "photo" -> ScanRoute(
                    db = db,
                    mode = "photo",
                    onDone = { screen = "list" },
                    onBack = { screen = "list" },
                )
                screen == "doc" -> DocImportRoute(
                    db = db,
                    onDone = { screen = "list" },
                    onBack = { screen = "list" },
                )
                else -> RecipeListScreen(
                    db = db,
                    onOpen = { screen = "detail:$it" },
                    onScan = { screen = "scan" },
                    onPhoto = { screen = "photo" },
                    onDoc = { screen = "doc" },
                    onHunt = { screen = "hunt" },
                    onSettings = { screen = "settings" },
                    onCategory = { screen = "category:$it" },
                )
            }
        }
    }
