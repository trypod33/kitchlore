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
import com.trypod33.kitchlore.ui.PantryRoute
import com.trypod33.kitchlore.ui.RecipeHuntRoute
import com.trypod33.kitchlore.ui.DocImportRoute
import com.trypod33.kitchlore.ui.EditRecipeScreen
import com.trypod33.kitchlore.ui.RecipeDetailRoute
import com.trypod33.kitchlore.ui.RecipeListScreen
import com.trypod33.kitchlore.ui.ScanRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as KitchloreApp
        setContent {
            KitchloreTheme {
                KitchloreNav(app.database)
            }
        }
    }
}

@Composable
fun KitchloreNav(db: AppDatabase) {
    Surface(Modifier.fillMaxSize()) {
            var screen by remember { mutableStateOf("list") }
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
                )
                screen == "pantry" -> PantryRoute(
                    db = db,
                    onBack = { screen = "list" },
                    onOpen = { screen = "detail:$it" },
                )
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
                    onPantry = { screen = "pantry" },
                )
            }
        }
    }
