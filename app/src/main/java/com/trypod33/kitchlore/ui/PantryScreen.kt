package com.trypod33.kitchlore.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trypod33.kitchlore.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pantry mode: list what is on hand, then match it against the local
 * library and the free TheMealDB catalog. Pantry items live in the app's
 * own shared preferences, one per line, nothing leaves the phone.
 */
private const val PANTRY_KEY = "items"

private fun loadPantry(context: Context): List<String> =
    (context.getSharedPreferences("kitchlore_pantry", Context.MODE_PRIVATE)
        .getString(PANTRY_KEY, "") ?: "")
        .split("|").map { it.trim() }.filter { it.isNotBlank() }

private fun savePantry(context: Context, items: List<String>) {
    context.getSharedPreferences("kitchlore_pantry", Context.MODE_PRIVATE)
        .edit().putString(PANTRY_KEY, items.joinToString("|")).apply()
}

@Composable
private fun PantryChip(label: String, onRemove: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
            Text("  x", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier
                .clickable(onClick = onRemove)
                .padding(end = 12.dp, top = 6.dp, bottom = 6.dp))
        }
    }
}

@Composable
fun PantryRoute(db: AppDatabase, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(loadPantry(context)) }
    var entry by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var localMatches by remember { mutableStateOf<List<Pair<com.trypod33.kitchlore.data.local.entity.Recipe, Int>>>(emptyList()) }
    var webHits by remember { mutableStateOf<List<MealDb.Hit>>(emptyList()) }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Pantry", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = entry,
                onValueChange = { entry = it },
                label = { Text("Add what you have") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedButton(
                onClick = {
                    val e = entry.trim()
                    if (e.isNotEmpty() && items.none { it.equals(e, ignoreCase = true) }) {
                        items = items + e
                        savePantry(context, items)
                    }
                    entry = ""
                },
            ) { Text("Add") }
        }
        if (items.isNotEmpty()) {
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                items.chunked(4).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowItems.forEach { item ->
                            PantryChip(item, onRemove = {
                                items = items.filterNot { it.equals(item, ignoreCase = true) }
                                savePantry(context, items)
                            })
                        }
                    }
                }
            }
        }
        Button(
            onClick = {
                if (items.isEmpty()) {
                    message = "Add a few things first."
                    return@Button
                }
                loading = true
                message = null
                localMatches = emptyList()
                webHits = emptyList()
                scope.launch {
                    val pantry = items
                    val local = withContext(Dispatchers.IO) {
                        val ings = db.ingredientDao().getAllOnce().groupBy { it.recipeId }
                        val recipes = db.recipeDao().getAllRecipesOnce()
                        recipes.map { r ->
                            val names = (ings[r.id] ?: emptyList()).map { it.name.lowercase() }
                            r to pantry.count { p -> names.any { it.contains(p.lowercase()) } }
                        }.filter { it.second > 0 }.sortedByDescending { it.second }
                    }
                    val web = withContext(Dispatchers.IO) {
                        val counts = HashMap<String, Int>()
                        val byId = HashMap<String, MealDb.Hit>()
                        pantry.take(3).forEach { p ->
                            MealDb.byIngredient(p).forEach { h ->
                                counts[h.id] = (counts[h.id] ?: 0) + 1
                                byId.putIfAbsent(h.id, h)
                            }
                        }
                        counts.entries.sortedByDescending { it.value }.take(12)
                            .mapNotNull { e -> byId[e.key] }
                    }
                    localMatches = local
                    webHits = web
                    if (local.isEmpty() && web.isEmpty()) message = "No matches yet. Try broader items like \"chicken\" or \"rice\"."
                    loading = false
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (loading) "Matching..." else "Find recipes") }
        message?.let { Text(it, fontSize = 15.sp) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (localMatches.isNotEmpty()) {
                item {
                    Text("In your library", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            items(localMatches, key = { "local-" + it.first.id }) { (recipe, count) ->
                LocalRow(recipe = recipe, count = count, onOpen = { onOpen(recipe.id) })
            }
            if (webHits.isNotEmpty()) {
                item {
                    Text("From the internet", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            items(webHits, key = { "web-" + it.id }) { hit ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.height(80.dp).clickable {
                            if (loading) return@clickable
                            loading = true
                            message = "Importing ${hit.name}..."
                            scope.launch {
                                val newId = MealDb.import(db, context, hit.id)
                                if (newId > 0) onOpen(newId) else { message = "Import failed."; loading = false }
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WebThumb(hit.thumb, Modifier.width(80.dp).fillMaxHeight())
                        Column(Modifier.padding(12.dp)) {
                            Text(hit.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            hit.category?.let { Text(it, fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalRow(recipe: com.trypod33.kitchlore.data.local.entity.Recipe?, count: Int, onOpen: () -> Unit) {
    if (recipe == null) return
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.height(72.dp).clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecipeThumbnail(recipe.originalImagePath, Modifier.width(72.dp).fillMaxHeight())
            Column(Modifier.padding(12.dp)) {
                Text(recipe.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("matches $count of your items", fontSize = 13.sp)
            }
        }
    }
}
