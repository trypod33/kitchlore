package com.trypod33.kitchlore.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trypod33.kitchlore.data.KeylessSearch
import com.trypod33.kitchlore.data.OcrParser
import com.trypod33.kitchlore.data.HtmlRecipeParser
import com.trypod33.kitchlore.data.MealDbCacheEntry
import com.trypod33.kitchlore.data.fetchPage
import com.trypod33.kitchlore.data.insertWebRecipe
import com.trypod33.kitchlore.data.RecipeCache
import com.trypod33.kitchlore.data.local.AppDatabase
import com.trypod33.kitchlore.data.local.entity.Ingredient
import com.trypod33.kitchlore.data.local.entity.Recipe
import com.trypod33.kitchlore.data.local.entity.Step
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Recipe hunt: search the free TheMealDB catalog over the internet (no key,
 * no account) and file the picked meal into the library with its photo,
 * ingredients and steps, ready to edit like any other recipe.
 */
object MealDb {
    private const val BASE = "https://www.themealdb.com/api/json/v1/1"

    data class Hit(
        val id: String,
        val name: String,
        val category: String? = null,
        val area: String? = null,
        val thumb: String? = null,
    )

    private fun get(url: String): JSONObject? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.inputStream.use { JSONObject(String(it.readBytes(), Charsets.UTF_8)) }
    } catch (t: Throwable) {
        null
    }

    private fun hitsFrom(json: JSONObject?): List<Hit> {
        val meals = json?.optJSONArray("meals") ?: return emptyList()
        return (0 until meals.length()).map { i ->
            val m = meals.getJSONObject(i)
            Hit(
                id = m.optString("idMeal"),
                name = m.optString("strMeal"),
                category = m.optString("strCategory").ifBlank { null },
                area = m.optString("strArea").ifBlank { null },
                thumb = m.optString("strMealThumb").ifBlank { null },
            )
        }.filter { it.id.isNotBlank() && it.name.isNotBlank() }
    }

    fun search(query: String): List<Hit> =
        hitsFrom(get("$BASE/search.php?s=${URLEncoder.encode(query, "UTF-8")}"))

    /** Words that match everything and nothing, stripped before falling back to smaller queries. */
    private val STOP_WORDS = setOf(
        "recipe", "recipes", "easy", "best", "simple", "quick", "healthy",
        "homemade", "authentic", "delicious", "tasty", "with", "and", "the",
        "a", "for", "of", "how", "to", "make",
    )

    data class SmartResult(val exact: Boolean, val hits: List<Hit>)

    /**
     * The catalog is small and exact-phrase search misses real dishes, so the
     * query walks down: whole phrase, stop-word-stripped phrase, then token
     * pairs ("braised pork", "pork chops"), then single words. Anything past
     * the whole phrase is a related match and the UI says so.
     */
    fun searchSmart(query: String): SmartResult {
        val q = query.trim()
        if (q.isEmpty()) return SmartResult(true, emptyList())
        val exact = search(q)
        if (exact.isNotEmpty()) return SmartResult(true, exact)
        val tokens = q.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() && it !in STOP_WORDS }
        val cleaned = tokens.joinToString(" ")
        if (cleaned.isNotEmpty() && !cleaned.equals(q.lowercase(), true)) {
            val cleanedHits = search(cleaned)
            if (cleanedHits.isNotEmpty()) return SmartResult(false, cleanedHits)
        }
        val seen = LinkedHashMap<String, Hit>()
        for (i in 0 until tokens.size - 1) {
            search(tokens[i] + " " + tokens[i + 1]).forEach { seen.putIfAbsent(it.id, it) }
            if (seen.size >= 8) return SmartResult(false, seen.values.toList())
        }
        for (t in tokens) {
            search(t).forEach { seen.putIfAbsent(it.id, it) }
            if (seen.size >= 8) break
        }
        return SmartResult(false, seen.values.toList())
    }

    fun byIngredient(ingredient: String): List<Hit> =
        hitsFrom(get("$BASE/filter.php?i=${URLEncoder.encode(ingredient, "UTF-8")}"))

    /** Files the meal into the library (photo downloaded into the app's own photos dir) and returns the new recipe id, or -1 on failure. */
    suspend fun import(db: AppDatabase, context: Context, id: String): Long = withContext(Dispatchers.IO) {
        try {
            val json = get("$BASE/lookup.php?i=$id") ?: return@withContext -1L
            val meals = json.optJSONArray("meals") ?: return@withContext -1L
            if (meals.length() == 0) return@withContext -1L
            val m = meals.getJSONObject(0)

            var photoPath: String? = null
            val thumb = m.optString("strMealThumb").ifBlank { null }
            if (thumb != null) {
                try {
                    val bytes = URL(thumb).openConnection().apply {
                        connectTimeout = 10000
                        readTimeout = 10000
                    }.getInputStream().use { it.readBytes() }
                    val dir = File(context.filesDir, "photos")
                    dir.mkdirs()
                    val f = File(dir, "import-${System.currentTimeMillis()}.jpg")
                    f.writeBytes(bytes)
                    photoPath = f.absolutePath
                } catch (t: Throwable) {
                    photoPath = null
                }
            }

            val tags = listOfNotNull(
                m.optString("strCategory").ifBlank { null },
                m.optString("strArea").ifBlank { null },
            ).joinToString(", ")

            val recipe = Recipe(
                title = m.optString("strMeal").ifBlank { "Imported recipe" },
                baseServings = 4,
                sourceType = "web",
                sourceNote = "TheMealDB",
                originalImagePath = photoPath,
                tagsCsv = tags,
            )
            val rid = db.recipeDao().insert(recipe)

            val ings = mutableListOf<Ingredient>()
            for (i in 1..20) {
                val name = m.optString("strIngredient$i").trim()
                if (name.isEmpty() || name.equals("null", ignoreCase = true)) continue
                val measure = m.optString("strMeasure$i").trim().takeIf { it.isNotBlank() && !it.equals("null", true) } ?: ""
                var amount: Double? = null
                var unit: String? = null
                val toks = measure.split(Regex("\\s+")).filter { it.isNotBlank() }
                var c = 0
                while (c < toks.size) {
                    val v = OcrParser.parseAmount(toks[c])
                    if (v != null) {
                        amount = (amount ?: 0.0) + v
                        c++
                    } else break
                }
                unit = toks.getOrNull(c)?.takeIf { it.isNotBlank() && it.all { ch -> ch.isLetter() } }
                ings.add(
                    Ingredient(
                        recipeId = rid,
                        name = name,
                        rawText = (measure + " " + name).trim(),
                        amount = amount,
                        unit = unit,
                        sortOrder = ings.size,
                    )
                )
            }
            if (ings.isNotEmpty()) db.ingredientDao().insertAll(ings)

            val stepLines = m.optString("strInstructions")
                .split(Regex("\\r?\\n"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
            if (stepLines.isNotEmpty()) {
                db.stepDao().insertAll(
                    stepLines.mapIndexed { idx, text -> Step(recipeId = rid, stepNumber = idx + 1, instructionText = text) }
                )
            }
            rid
        } catch (t: Throwable) {
            -1L
        }
    }
}

/** A photo loaded from a URL, with a quiet placeholder while it loads or if it cannot. */
@Composable
fun WebThumb(url: String?, modifier: Modifier = Modifier) {
    var bmp by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val bytes = URL(url).openConnection().apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                }.getInputStream().use { it.readBytes() }
                bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (t: Throwable) {
                bmp = null
            }
        }
    }
    val b = bmp
    if (b != null) {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {}
    }
}

@Composable
fun RecipeHuntRoute(db: AppDatabase, onBack: () -> Unit, onOpen: (Long) -> Unit, onOpenWeb: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var importingId by remember { mutableStateOf<String?>(null) }
    var importingUrl by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var dbHits by remember { mutableStateOf<List<MealDb.Hit>>(emptyList()) }
    var exactMatch by remember { mutableStateOf(true) }
    var webResults by remember { mutableStateOf<List<KeylessSearch.Result>>(emptyList()) }
    var webBlocked by remember { mutableStateOf(false) }
    var offlineHits by remember { mutableStateOf<List<MealDbCacheEntry>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Recipe hunt", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Chicken with rice, braised pork chops...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val q = query.trim()
                    if (q.isEmpty()) return@Button
                    loading = true
                    message = null
                    searched = false
                    dbHits = emptyList()
                    webResults = emptyList()
                    offlineHits = emptyList()
                    webBlocked = false
                    scope.launch {
                        val smart = withContext(Dispatchers.IO) { MealDb.searchSmart(q) }
                        dbHits = smart.hits
                        exactMatch = smart.exact
                        if (!smart.exact && smart.hits.isEmpty()) {
                            // Keyless web search, best effort, honest when blocked
                            val web = withContext(Dispatchers.IO) { KeylessSearch.search(q) }
                            webResults = web
                            if (web.isEmpty()) {
                                webBlocked = true
                                offlineHits = RecipeCache.searchOffline(context, q)
                            }
                        }
                        searched = true
                        loading = false
                    }
                },
                enabled = !loading,
                modifier = Modifier.weight(1f),
            ) { Text(if (loading) "Searching..." else "Search") }
            OutlinedButton(onClick = { onOpenWeb("") }, enabled = !loading) { Text("Paste link") }
        }
        message?.let { Text(it, fontSize = 15.sp) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (searched && !exactMatch && dbHits.isNotEmpty()) {
                item {
                    Text("No exact match, showing related", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            items(dbHits, key = { "db-" + it.id }) { hit ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.height(88.dp).clickable(enabled = importingId == null) {
                            importingId = hit.id
                            message = "Importing ${hit.name}..."
                            scope.launch {
                                val newId = MealDb.import(db, context, hit.id)
                                if (newId > 0) {
                                    onOpen(newId)
                                } else {
                                    message = "Import failed, check your connection."
                                    importingId = null
                                }
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WebThumb(hit.thumb, Modifier.width(88.dp).fillMaxHeight())
                        Column(Modifier.padding(12.dp)) {
                            Text(hit.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            val sub = listOfNotNull(hit.category, hit.area).joinToString(" \u00B7 ")
                            if (sub.isNotEmpty()) Text(sub, fontSize = 13.sp)
                        }
                    }
                }
            }
            if (webResults.isNotEmpty()) {
                item {
                    Text("More options", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                }
            }
            items(webResults, key = { "web-" + it.url }) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = importingUrl == null && importingId == null) {
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
                                        }
                                    } else {
                                        message = "That page hides its recipe. Opening the import screen for it."
                                        onOpenWeb(r.url)
                                    }
                                }
                            }
                            .padding(12.dp)
                    ) {
                        Text(r.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                        Text(hostOf(r.url) + "  \u00B7  tap to add", fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
            if (webBlocked) {
                item {
                    Text(
                        "Web search would not answer an automated request right now, so these come from the built-in catalog.",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text("Offline picks", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                }
            }
            items(offlineHits, key = { "off-" + it.id }) { e ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.height(72.dp).clickable(enabled = importingId == null) {
                            importingId = e.id
                            message = "Importing ${e.name}..."
                            scope.launch {
                                val newId = MealDb.import(db, context, e.id)
                                if (newId > 0) {
                                    onOpen(newId)
                                } else {
                                    message = "Needs a connection to import. Open in browser instead?"
                                    importingId = null
                                }
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WebThumb(e.thumb, Modifier.width(72.dp).fillMaxHeight())
                        Column(Modifier.padding(12.dp)) {
                            Text(e.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(e.category, fontSize = 13.sp)
                        }
                    }
                }
            }
            if (searched && !loading && dbHits.isEmpty() && webResults.isEmpty() && offlineHits.isEmpty()) {
                item {
                    Text("Nothing found for that. Try fewer words, or paste a link to a recipe page.", fontSize = 15.sp)
                }
            }
        }
    }
}

private fun hostOf(url: String): String = try {
    java.net.URI(url).host?.removePrefix("www.") ?: url
} catch (t: Throwable) {
    url
}
