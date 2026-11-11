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
import com.trypod33.kitchlore.ui.EditRecipeScreen
import com.trypod33.kitchlore.ui.RecipeDetailRoute
import com.trypod33.kitchlore.ui.RecipeListScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as KitchloreApp
        setContent {
            KitchloreNav(app.database)
        }
    }
}

@Composable
fun KitchloreNav(db: AppDatabase) {
    MaterialTheme {
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
                else -> RecipeListScreen(db, onOpen = { screen = "detail:$it" })
            }
        }
    }
}
