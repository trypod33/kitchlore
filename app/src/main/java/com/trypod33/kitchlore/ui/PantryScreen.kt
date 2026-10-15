package com.trypod33.kitchlore.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trypod33.kitchlore.data.HtmlRecipeParser
import com.trypod33.kitchlore.data.KeylessSearch
import com.trypod33.kitchlore.data.fetchPage
import com.trypod33.kitchlore.data.insertWebRecipe
import com.trypod33.kitchlore.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pantry mode: list what is on hand, then match it against the local
 * library and keyless web search. Pantry items live in the app's own
 * shared preferences, one per line, nothing leaves the phone.
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
fun PantryRoute(db: AppDatabase, onBack: () -> Unit, onOpen: (Long) -> Unit, onOpenWeb: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(loadPantry(context)) }
    var entry by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var importingUrl by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var localMatches by remember { mutableStateOf<List<Pair<com.trypod33.kitchlore.data.local.entity.Recipe, Int>>>(emptyList()) }
    var webHits by remember { mutableStateOf<List<KeylessSearch.Result>>(emptyList()) }

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
                            r to pantry.count { p -> names.any { matchesPantryWord(it, p) } }
                        }.filter { it.second > 0 }.sortedByDescending { it.second }
                    }
                    val web = withContext(Dispatchers.IO) {
                        val counts = HashMap<String, Int>()
                        val byUrl = HashMap<String, KeylessSearch.Result>()
                        pantry.take(4).forEach { p ->
                            WebHunt.searchSmart(p + " recipe").hits.forEach { h ->
                                counts[h.url] = (counts[h.url] ?: 0) + 1
                                byUrl.putIfAbsent(h.url, h)
                            }
                        }
                        counts.entries.sortedByDescending { it.value }.take(12)
                            .mapNotNull { e -> byUrl[e.key] }
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
            items(webHits, key = { "web-" + it.url }) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.height(72.dp).fillMaxWidth().clickable(enabled = importingUrl == null) {
                            importingUrl = r.url
                            message = "Bringing in ${r.title}..."
                            scope.launch {
                                val parsed = withContext(Dispatchers.IO) {
                                    fetchPage(r.url)?.let { HtmlRecipeParser.parse(it) }
                                }
                                if (parsed != null && (parsed.ingredients.isNotEmpty() || parsed.steps.isNotEmpty())) {
                                    val newId = insertWebRecipe(db, context, parsed, r.url)
                                    if (newId > 0) {
                                        onOpen(newId)
                                    } else {
                                        message = "Save failed, try again."
                                        importingUrl = null
                                        loading = false
                                    }
                                } else {
                                    message = "That page hides its recipe. Opening the import screen for it."
                                    onOpenWeb(r.url)
                                }
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(start = 12.dp).size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    r.title.trim().take(1).uppercase(),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Column(Modifier.padding(12.dp)) {
                            Text(r.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(hostOfUrl(r.url) + "  \u00B7  tap to add", fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
            }
            if (loading && localMatches.isEmpty() && webHits.isEmpty()) {
                item { Text(" ", fontSize = 1.sp) }
            }
        }
    }
}

private fun hostOfUrl(url: String): String = try {
    java.net.URI(url).host?.removePrefix("www.") ?: url
} catch (t: Throwable) {
    url
}

@Composable
private fun LocalRow(recipe: com.trypod33.kitchlore.data.local.entity.Recipe?, count: Int, onOpen: () -> Unit) {
    if (recipe == null) return
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.height(72.dp).clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecipeThumbnail(recipe.originalImagePath, Modifier.height(72.dp).fillMaxWidth(0.22f))
            Column(Modifier.padding(12.dp)) {
                Text(recipe.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("matches $count of your items", fontSize = 13.sp)
            }
        }
    }
}

/** Whole-word pantry match, so "pepper" does not ride on "bell pepper" alone standing in for every pepper dish, and plurals still count. */
internal fun matchesPantryWord(name: String, pantryItem: String): Boolean {
    val pattern = Regex("\\b" + Regex.escape(pantryItem.lowercase().trim()) + "(es|s)?\\b")
    return pattern.containsMatchIn(name.trim())
}
