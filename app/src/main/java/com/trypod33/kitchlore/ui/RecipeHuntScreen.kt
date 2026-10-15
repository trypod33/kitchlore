package com.trypod33.kitchlore.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trypod33.kitchlore.data.HtmlRecipeParser
import com.trypod33.kitchlore.data.KeylessSearch
import com.trypod33.kitchlore.data.fetchPage
import com.trypod33.kitchlore.data.insertWebRecipe
import com.trypod33.kitchlore.data.local.AppDatabase
import com.trypod33.kitchlore.data.local.entity.Recipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

/**
 * Recipe hunt over keyless web search: a query goes to public search engines
 * (no API keys, no accounts), results come back as a list, and a tap imports
 * the recipe through the same page parser the paste-a-link flow uses. The
 * query walks down from the whole phrase to stop-word-stripped and token-pair
 * tries, so real dishes still surface when the exact phrase comes up empty.
 * Anything past the whole phrase is a related match and the UI says so.
 */
object WebHunt {
    /** Words that match everything and nothing, stripped before falling back to smaller queries. */
    private val STOP_WORDS = setOf(
        "recipe", "recipes", "easy", "best", "simple", "quick", "healthy",
        "homemade", "authentic", "delicious", "tasty", "with", "and", "the",
        "a", "for", "of", "how", "to", "make",
    )

    data class SmartResult(val exact: Boolean, val hits: List<KeylessSearch.Result>)

    fun searchSmart(query: String): SmartResult {
        val q = query.trim()
        if (q.isEmpty()) return SmartResult(true, emptyList())
        val exact = KeylessSearch.search(q)
        if (exact.isNotEmpty()) return SmartResult(true, exact)
        val tokens = q.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() && it !in STOP_WORDS }
        val cleaned = tokens.joinToString(" ")
        if (cleaned.isNotEmpty() && !cleaned.equals(q, ignoreCase = true)) {
            val cleanedHits = KeylessSearch.search(cleaned)
            if (cleanedHits.isNotEmpty()) return SmartResult(false, cleanedHits)
        }
        // A bare ingredient like "chicken" reads as noise to a search engine; as
        // "chicken recipes" it lands on real recipe pages. Try it before the
        // smaller token falls back.
        val recipeQuery = cleaned.ifEmpty { q.lowercase() }
        if (!recipeQuery.endsWith("recipe") && !recipeQuery.endsWith("recipes")) {
            val withRecipes = KeylessSearch.search("$recipeQuery recipes")
            if (withRecipes.isNotEmpty()) return SmartResult(false, withRecipes)
        }
        val seen = LinkedHashMap<String, KeylessSearch.Result>()
        for (i in 0 until tokens.size - 1) {
            KeylessSearch.search(tokens[i] + " " + tokens[i + 1]).forEach { seen.putIfAbsent(it.url, it) }
            if (seen.size >= 8) return SmartResult(false, seen.values.toList())
        }
        for (t in tokens) {
            KeylessSearch.search(t).forEach { seen.putIfAbsent(it.url, it) }
            if (seen.size >= 8) break
        }
        return SmartResult(false, seen.values.toList())
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
                val bytes = java.net.URL(url).openConnection().apply {
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
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun ResultCard(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) { content() }
}

// Kitchen appliances the hunt understands. The appliance words step aside for
// the title match and stay in the web query, where they steer the results.
private val APPLIANCES = listOf(
    "ninja foodi xl", "ninja foodi", "crock pot", "crockpot", "slow cooker",
    "instant pot", "instapot", "pressure cooker", "air fryer", "dutch oven", "rice cooker",
)

private fun applianceIn(query: String): String? = APPLIANCES.firstOrNull { query.lowercase().contains(it) }

@Composable
fun RecipeHuntRoute(db: AppDatabase, onBack: () -> Unit, onOpen: (Long) -> Unit, onOpenWeb: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var importingUrl by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var hits by remember { mutableStateOf<List<KeylessSearch.Result>>(emptyList()) }
    var localHits by remember { mutableStateOf<List<Recipe>>(emptyList()) }
    var exactMatch by remember { mutableStateOf(true) }
    var searched by remember { mutableStateOf(false) }
    var webLabel by remember { mutableStateOf("More from the web") }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Recipe hunt", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("What are you cooking?") },
            placeholder = { Text("braised pork chops, chicken in the ninja foodi...") },
            shape = RoundedCornerShape(14.dp),
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
                    hits = emptyList()
                    localHits = emptyList()
                    val applianceHit = applianceIn(q)
                    val webQuery = applianceHit?.let { a ->
                        val named = if (a == "instapot") "instant pot" else a
                        q.replace(a, named, ignoreCase = true).trim()
                    } ?: q
                    webLabel = applianceHit?.let { a -> a.replaceFirstChar { it.uppercase() } + " picks from the web" }
                        ?: "More from the web"
                    scope.launch {
                        val smart = withContext(Dispatchers.IO) { WebHunt.searchSmart(webQuery) }
                        hits = smart.hits
                        exactMatch = smart.exact
                        // The web engines can refuse an automated caller outright.
                        // When they all do, the closest saved recipes step in so
                        // the hunt never dead-ends on an empty screen.
                        localHits = if (smart.hits.isEmpty()) withContext(Dispatchers.IO) {
                            val words = webQuery.lowercase().split(Regex("\\s+"))
                                .filter { it.isNotBlank() && it.length > 2 }
                            db.recipeDao().getAllRecipesOnce().filter { r ->
                                val hay = (r.title + " " + r.tagsCsv + " " + (r.sourceNote ?: "")).lowercase()
                                words.any { hay.contains(it) }
                            }.take(6)
                        } else emptyList()
                        searched = true
                        loading = false
                    }
                },
                enabled = !loading,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text(if (loading) "Searching..." else "Search") }
            OutlinedButton(
                onClick = { onOpenWeb("") },
                enabled = !loading,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Paste link") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let {
            Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (searched && hits.isNotEmpty()) {
                item { SectionLabel(if (exactMatch) "Found these" else "Close enough") }
            }
            items(hits, key = { "web-" + it.url }) { r ->
                ResultCard {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = importingUrl == null) {
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
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp),
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
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(r.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(
                                hostOf(r.url) + "  \u00B7  tap to add",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            if (searched && !loading && localHits.isNotEmpty()) {
                item { SectionLabel("From your library") }
            }
            items(localHits, key = { "local-" + it.id }) { r ->
                ResultCard {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = importingUrl == null) { onOpen(r.id) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    r.title.trim().take(1).uppercase(),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(r.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(
                                "saved recipe  \u00B7  tap to open",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            if (searched && !loading && hits.isEmpty() && localHits.isEmpty()) {
                item {
                    Text(
                        "Nothing came back for that.\nTry fewer words, or paste a link to a recipe page.",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    )
                }
            }
        }
    }
}

private fun hostOf(url: String): String = try {
    URI(url).host?.removePrefix("www.") ?: url
} catch (t: Throwable) {
    url
}
