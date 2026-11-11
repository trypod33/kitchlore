package com.trypod33.kitchlore.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trypod33.kitchlore.data.local.AppDatabase

@Composable
fun RecipeListScreen(db: AppDatabase, onOpen: (Long) -> Unit) {
    var query by remember { mutableStateOf("") }
    val recipes by db.recipeDao().getAllRecipes().collectAsState(initial = emptyList())
    val trimmed = query.trim()
    val filtered = if (trimmed.isEmpty()) recipes else recipes.filter { it.title.contains(trimmed, ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        Text(
            "Kitchlore",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search recipes") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            singleLine = true,
        )
        when {
            filtered.isEmpty() && recipes.isEmpty() -> {
                Text(
                    "No recipes yet. Your Termux library imports on first launch.",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            filtered.isEmpty() -> {
                Text(
                    "No matches for \"$trimmed\"",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> {
                LazyColumn(Modifier.padding(horizontal = 20.dp)) {
                    items(filtered, key = { it.id }) { recipe ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onOpen(recipe.id) }) {
                            Row(Modifier.height(72.dp), verticalAlignment = Alignment.CenterVertically) {
                                RecipeThumbnail(recipe.originalImagePath, Modifier.fillMaxHeight().width(72.dp))
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        recipe.title,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val line = servingLine(recipe)
                                    if (line.isNotEmpty()) {
                                        Text(line, fontSize = 13.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
