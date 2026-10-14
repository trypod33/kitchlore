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
import com.trypod33.kitchlore.data.OcrParser
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
fun RecipeHuntRoute(db: AppDatabase, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var importingId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var hits by remember { mutableStateOf<List<MealDb.Hit>>(emptyList()) }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Recipe hunt", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Chicken with rice, fried rice, eggs benedict...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(
            onClick = {
                val q = query.trim()
                if (q.isEmpty()) return@Button
                loading = true
                message = null
                hits = emptyList()
                scope.launch {
                    val result = withContext(Dispatchers.IO) { MealDb.search(q) }
                    hits = result
                    if (result.isEmpty()) message = "Nothing found for \"$q\". Try fewer words."
                    loading = false
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (loading) "Searching..." else "Search the internet") }
        message?.let { Text(it, fontSize = 15.sp) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(hits, key = { it.id }) { hit ->
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
        }
    }
}
