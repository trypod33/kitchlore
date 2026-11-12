package com.trypod33.kitchlore.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.trypod33.kitchlore.data.HtmlRecipeParser
import com.trypod33.kitchlore.data.KeylessSearch
import com.trypod33.kitchlore.data.WebSearchEngine
import com.trypod33.kitchlore.data.fetchPage
import com.trypod33.kitchlore.data.ParsedWebRecipe
import com.trypod33.kitchlore.data.RoundupMiner
import com.trypod33.kitchlore.data.WebPageReader
import com.trypod33.kitchlore.data.insertWebRecipe
import com.trypod33.kitchlore.data.local.AppDatabase
import com.trypod33.kitchlore.data.local.entity.Recipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

/**
 * Recipe hunt and pantry in one screen. One box takes either a dish ("braised
 * pork chops"), an appliance ("ninja foodi xl"), drinks ("margarita"), or
 * what is on hand ("chicken, rice"), and no mode switch: the library matches
 * first, instantly and offline, then the web results arrive through the
 * hidden in-app browser search. A tap on a web card imports the recipe
 * through the same page parser the paste-a-link flow uses.
 */
object WebHunt {
    /** Words that match everything and nothing, stripped before falling back to smaller queries. */
    private val STOP_WORDS = setOf(
        "recipe", "recipes", "easy", "best", "simple", "quick", "healthy",
        "homemade", "authentic", "delicious", "tasty", "with", "and", "the",
        "a", "for", "of", "how", "to", "make",
    )

    data class SmartResult(val exact: Boolean, val hits: List<KeylessSearch.Result>)

    suspend fun searchSmart(context: Context, query: String): SmartResult {
        val q = query.trim()
        if (q.isEmpty()) return SmartResult(true, emptyList())

        // The hidden in-app browser search first: it reads like a person's
        // browser, so engines that refuse plain app requests answer it.
        val direct = WebSearchEngine.search(context, q)
        if (direct.isNotEmpty()) return SmartResult(true, direct)

        val tokens = q.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() && it !in STOP_WORDS }
        val cleaned = tokens.joinToString(" ")
        // A bare ingredient or appliance reads as noise to a search engine;
        // as "<query> recipes" it lands on real recipe pages.
        val shaped = cleaned.ifEmpty { q.lowercase() }
        if (!shaped.endsWith("recipe") && !shaped.endsWith("recipes")) {
            val viaRecipes = WebSearchEngine.search(context, "$shaped recipes", timeoutMs = 9_000L)
            if (viaRecipes.isNotEmpty()) return SmartResult(false, viaRecipes)
        }
        if (cleaned.isNotEmpty() && !cleaned.equals(q, ignoreCase = true)) {
            val cleanedHits = WebSearchEngine.search(context, cleaned, timeoutMs = 9_000L)
            if (cleanedHits.isNotEmpty()) return SmartResult(false, cleanedHits)
        }

        // Plain-HTTP fallbacks for the tail tiers, in case the hidden browser
        // is blocked everywhere too.
        val exact = KeylessSearch.search(q)
        if (exact.isNotEmpty()) return SmartResult(true, exact)
        val withRecipes = KeylessSearch.search("$shaped recipes")
        if (withRecipes.isNotEmpty()) return SmartResult(false, withRecipes)
        if (cleaned.isNotEmpty() && !cleaned.equals(q, ignoreCase = true)) {
            val cleanedHits = KeylessSearch.search(cleaned)
            if (cleanedHits.isNotEmpty()) return SmartResult(false, cleanedHits)
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

/**
 * What the hunt learned by reading a result's own page: the real recipe
 * (name, photo, times), or the dishes a roundup article links, or nothing
 * usable. The card upgrades itself as each read lands.
 */
private data class EnrichedHit(
    val parsed: ParsedWebRecipe?,
    val isRecipe: Boolean,
    val dishes: List<RoundupMiner.DishLink> = emptyList(),
)

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

// Pantry items live in the app's own shared preferences, one per line,
// nothing leaves the phone. Same store the old Pantry screen used.
private fun loadHuntPantry(context: Context): List<String> =
    (context.getSharedPreferences("kitchlore_pantry", Context.MODE_PRIVATE)
        .getString("items", "") ?: "")
        .split("|").map { it.trim() }.filter { it.isNotBlank() }

private fun saveHuntPantry(context: Context, items: List<String>) {
    context.getSharedPreferences("kitchlore_pantry", Context.MODE_PRIVATE)
        .edit().putString("items", items.joinToString("|")).apply()
}

/**
 * The library half of the hunt: a recipe scores a point per term it matches
 * in its title, tags or notes, plus a point for each ingredient the term
 * matches by whole word, and a bonus when the whole phrase is in the title.
 */
private suspend fun matchLibrary(db: AppDatabase, terms: List<String>, pantryMode: Boolean): List<Pair<Recipe, Int>> =
    withContext(Dispatchers.IO) {
        val lowered = terms.map { it.lowercase() }.filter { it.isNotEmpty() }
        if (lowered.isEmpty()) return@withContext emptyList()
        val phrases = if (pantryMode) lowered else lowered + lowered.joinToString(" ")
        val ings = db.ingredientDao().getAllOnce().groupBy { it.recipeId }
        db.recipeDao().getAllRecipesOnce().mapNotNull { r ->
            val names = (ings[r.id] ?: emptyList()).map { it.name.lowercase() }
            var score = 0
            lowered.forEach { term ->
                val hay = (r.title + " " + r.tagsCsv + " " + (r.sourceNote ?: "")).lowercase()
                if (hay.contains(term)) score++
                if (names.any { matchesPantryWord(it, term) }) score++
            }
            if (!pantryMode && r.title.lowercase().contains(phrases.last())) score += 2
            if (score > 0) r to score else null
        }.sortedByDescending { it.second }.take(12)
    }

@Composable
private fun LibraryCard(recipe: Recipe, subtitle: String, onOpen: () -> Unit) {
    ResultCard {
        Row(
            Modifier.fillMaxWidth()
                .clickable(onClick = onOpen)
                .height(72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (recipe.originalImagePath != null) {
                RecipeThumbnail(
                    recipe.originalImagePath,
                    Modifier.height(72.dp).fillMaxWidth(0.2f),
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(start = 12.dp).size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            recipe.title.trim().take(1).uppercase(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            Column(Modifier.padding(start = 12.dp, end = 12.dp)) {
                Text(recipe.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun RecipeHuntRoute(db: AppDatabase, onBack: () -> Unit, onOpen: (Long) -> Unit, onOpenWeb: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var importingUrl by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pantryItems by remember { mutableStateOf(loadHuntPantry(context)) }
    var libraryHits by remember { mutableStateOf<List<Pair<Recipe, Int>>>(emptyList()) }
    var webHits by remember { mutableStateOf<List<KeylessSearch.Result>>(emptyList()) }
    var pantryMode by remember { mutableStateOf(false) }
    var webLabel by remember { mutableStateOf("From the web") }
    var searched by remember { mutableStateOf(false) }
    // Peek-before-import: a web card opens a preview of the recipe, and the
    // import only happens from the preview's own button.
    var previewHit by remember { mutableStateOf<KeylessSearch.Result?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    var previewParsed by remember { mutableStateOf<ParsedWebRecipe?>(null) }
    var previewFailed by remember { mutableStateOf(false) }
    var enriched by remember { mutableStateOf<Map<String, EnrichedHit>>(emptyMap()) }
    var enrichJob by remember { mutableStateOf<Job?>(null) }
    var roundupUrl by remember { mutableStateOf<String?>(null) }

    /** Puts recipes first, dish-bearing roundups next, plain articles last. */
    fun resortHits() {
        val seenTitles = HashSet<String>()
        val kept = ArrayList<KeylessSearch.Result>()
        for (h in webHits) {
            val title = enriched[h.url]?.parsed?.title?.trim()?.lowercase()
            if (title != null && !seenTitles.add(title)) continue // same dish from another engine
            kept.add(h)
        }
        fun rank(h: KeylessSearch.Result): Int {
            val e = enriched[h.url] ?: return 2
            return when {
                e.isRecipe -> 0
                e.dishes.isNotEmpty() -> 1
                else -> 2
            }
        }
        webHits = kept.sortedBy { rank(it) }
    }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty() && pantryItems.isEmpty()) {
            message = "Type what you are cooking, or what you have."
            return
        }
        loading = true
        message = null
        searched = false
        libraryHits = emptyList()
        webHits = emptyList()
        enriched = emptyMap()
        enrichJob?.cancel()
        val terms = q.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val usePantry = terms.isEmpty()
        pantryMode = usePantry
        val libTerms = if (usePantry) pantryItems.take(6) else terms
        val applianceHit = terms.firstOrNull()?.let { applianceIn(it) }
        val webQuery = applianceHit?.let { a ->
            val named = if (a == "instapot") "instant pot" else a
            terms.joinToString(" ").replace(a, named, ignoreCase = true).trim()
        } ?: terms.joinToString(" ")
        webLabel = when {
            usePantry -> "From the web"
            applianceHit != null -> applianceHit.replaceFirstChar { it.uppercase() } + " picks from the web"
            else -> "From the web"
        }
        scope.launch {
            // Library first: instant, offline.
            libraryHits = matchLibrary(db, libTerms, usePantry)
            searched = true
            val web = withContext(Dispatchers.IO) {
                if (usePantry) {
                    val counts = HashMap<String, Int>()
                    val byUrl = LinkedHashMap<String, KeylessSearch.Result>()
                    pantryItems.take(3).forEach { p ->
                        WebHunt.searchSmart(context, "$p recipes").hits.forEach { h ->
                            counts[h.url] = (counts[h.url] ?: 0) + 1
                            byUrl.putIfAbsent(h.url, h)
                        }
                    }
                    counts.entries.sortedByDescending { it.value }.take(12).mapNotNull { byUrl[it.key] }
                } else {
                    WebHunt.searchSmart(context, webQuery).hits
                }
            }
            webHits = web
            if (libraryHits.isEmpty() && web.isEmpty()) {
                message = "Nothing came back for that. Try fewer words, or paste a link to a recipe page."
            }
            loading = false
            // Read each result's own page so the cards can name the dish.
            // One page at a time, top results first; cards update as reads land.
            enrichJob = scope.launch {
                for (hit in web.take(12)) {
                    if (enriched.containsKey(hit.url)) continue
                    val html = WebPageReader.read(context, hit.url)
                    val parsed = withContext(Dispatchers.IO) { html?.let { HtmlRecipeParser.parse(it) } }
                    val isRecipe = parsed != null &&
                        (parsed.ingredients.isNotEmpty() || parsed.steps.isNotEmpty())
                    val dishes = if (isRecipe || html == null) {
                        emptyList()
                    } else {
                        withContext(Dispatchers.IO) { RoundupMiner.mine(html, hit.url) }
                    }
                    if (enriched.containsKey(hit.url)) continue
                    enriched = enriched + (hit.url to EnrichedHit(parsed, isRecipe, dishes))
                    resortHits()
                }
            }
        }
    }

    fun openPreview(hit: KeylessSearch.Result) {
        val cached = enriched[hit.url]?.parsed
        if (cached != null && (cached.ingredients.isNotEmpty() || cached.steps.isNotEmpty())) {
            previewHit = hit
            previewLoading = false
            previewParsed = cached
            previewFailed = false
            return
        }
        previewHit = hit
        previewLoading = true
        previewParsed = null
        previewFailed = false
        scope.launch {
            val html = WebPageReader.read(context, hit.url)
            val parsed = withContext(Dispatchers.IO) { html?.let { HtmlRecipeParser.parse(it) } }
            if (previewHit?.url != hit.url) return@launch
            previewParsed = parsed
            previewFailed = parsed == null || (parsed.ingredients.isEmpty() && parsed.steps.isEmpty())
            previewLoading = false
            if (!previewFailed) {
                enriched = enriched + (hit.url to EnrichedHit(parsed, true))
                resortHits()
            }
        }
    }

    fun importFromPreview() {
        val hit = previewHit ?: return
        val parsed = previewParsed ?: return
        if (previewFailed || importingUrl != null) return
        importingUrl = hit.url
        message = "Bringing in ${hit.title}..."
        scope.launch {
            val newId = insertWebRecipe(db, context, parsed, hit.url)
            if (newId > 0) {
                previewHit = null
                onOpen(newId)
            } else {
                message = "Save failed, try again."
            }
            importingUrl = null
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Recipe hunt", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("What are you cooking, or what do you have?") },
            placeholder = { Text("braised pork chops, chicken in the ninja foodi...") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { runSearch() },
                enabled = !loading,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text(if (loading) "Searching..." else "Search") }
            if (query.trim().isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        val terms = query.trim().split(",").map { it.trim() }
                            .filter { it.isNotEmpty() && pantryItems.none { p -> p.equals(it, ignoreCase = true) } }
                        if (terms.isNotEmpty()) {
                            pantryItems = pantryItems + terms
                            saveHuntPantry(context, pantryItems)
                            message = "Saved to pantry"
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Save to pantry") }
            } else {
                OutlinedButton(
                    onClick = { onOpenWeb("") },
                    enabled = !loading,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Paste link") }
            }
        }
        if (pantryItems.isNotEmpty()) {
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                pantryItems.chunked(4).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowItems.forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
                                    Text(
                                        "  x",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clickable {
                                                pantryItems = pantryItems.filterNot { it.equals(item, ignoreCase = true) }
                                                saveHuntPantry(context, pantryItems)
                                            }
                                            .padding(end = 12.dp, top = 6.dp, bottom = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let {
            Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (searched && libraryHits.isNotEmpty()) {
                item { SectionLabel("From your library") }
            }
            items(libraryHits, key = { "local-" + it.first.id }) { (r, score) ->
                LibraryCard(
                    recipe = r,
                    subtitle = if (pantryMode) "matches $score of your items  \u00B7  tap to open" else "in your library  \u00B7  tap to open",
                    onOpen = { onOpen(r.id) },
                )
            }
            if (searched && webHits.isNotEmpty()) {
                item { SectionLabel(webLabel) }
            }
            items(webHits, key = { "web-" + it.url }) { r ->
                val e = enriched[r.url]
                val name = e?.parsed?.title?.trim()?.takeIf { it.isNotBlank() } ?: r.title
                val photo = e?.parsed?.imageUrl ?: r.thumbnailUrl
                val subtitle = when {
                    e?.isRecipe == true -> {
                        val meta = buildList {
                            e.parsed?.totalMinutes?.let { add(formatMinutes(it)) }
                            e.parsed?.yieldServings?.let { add("serves $it") }
                            e.parsed?.ingredients?.size?.let { if (it > 0) add("$it ingredients") }
                        }
                        (if (meta.isEmpty()) "recipe" else meta.joinToString("  \u00B7  ")) +
                            "  \u00B7  " + hostOf(r.url) + "  \u00B7  tap to look inside"
                    }
                    e != null && e.dishes.isNotEmpty() ->
                        "${e.dishes.size} dishes inside  \u00B7  " + hostOf(r.url) + "  \u00B7  tap to see them"
                    e != null ->
                        "article from " + hostOf(r.url) + "  \u00B7  tap to open it"
                    else ->
                        hostOf(r.url) + "  \u00B7  tap to look inside"
                }
                ResultCard {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = importingUrl == null) {
                                when {
                                    e == null || e.isRecipe -> openPreview(r)
                                    e.dishes.isNotEmpty() -> roundupUrl = r.url
                                    else -> onOpenWeb(r.url)
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WebThumb(
                            photo,
                            Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(
                                subtitle,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            if (e == null && r.description != null) {
                                Text(
                                    r.description,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (searched && !loading && libraryHits.isEmpty() && webHits.isEmpty()) {
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

    previewHit?.let { hit ->
        Dialog(
            onDismissRequest = { previewHit = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    if (previewLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 24.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("  Reading the page...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        val parsed = previewParsed
                        if (parsed != null && !previewFailed) {
                            parsed.imageUrl?.let { img ->
                                WebThumb(
                                    img,
                                    Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(14.dp)),
                                )
                            }
                            Text(
                                hit.title,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            Text(
                                hostOf(hit.url),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val meta = buildList {
                                parsed.totalMinutes?.let { add(formatMinutes(it)) }
                                parsed.yieldServings?.let { add("Serves $it") }
                                if (parsed.ingredients.isNotEmpty()) add("${parsed.ingredients.size} ingredients")
                                if (parsed.steps.isNotEmpty()) add("${parsed.steps.size} steps")
                            }
                            if (meta.isNotEmpty()) {
                                Text(
                                    meta.joinToString("  \u00B7  "),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            if (parsed.ingredients.isNotEmpty()) {
                                SectionLabel("Ingredients")
                                parsed.ingredients.forEach { line ->
                                    Text("\u2022  $line", fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                            if (parsed.steps.isNotEmpty()) {
                                SectionLabel("How to make it")
                                parsed.steps.take(2).forEachIndexed { i, step ->
                                    Text(
                                        "${i + 1}.  $step",
                                        fontSize = 14.sp,
                                        maxLines = 4,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                if (parsed.steps.size > 2) {
                                    Text(
                                        "Plus ${parsed.steps.size - 2} more steps once it is in your library.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        } else {
                            Text(
                                "That page hides its recipe, so there is nothing to peek at here.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                            Button(
                                onClick = { importFromPreview() },
                                enabled = importingUrl == null && previewParsed != null && !previewFailed,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text(if (importingUrl != null) "Adding..." else "Add to library")
                            }
                            OutlinedButton(
                                onClick = {
                                    previewHit = null
                                    onOpenWeb(hit.url)
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                            ) { Text("Open the page") }
                        }
                    }
                }
            }
        }
    }

    roundupUrl?.let { url ->
        val hit = webHits.firstOrNull { it.url == url }
        val dishes = enriched[url]?.dishes.orEmpty()
        Dialog(
            onDismissRequest = { roundupUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text(
                        hit?.title ?: "This roundup",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (dishes.isEmpty()) {
                            "Reading the article for its dishes..."
                        } else {
                            "${dishes.size} dishes inside  \u00B7  " + hostOf(url)
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    dishes.forEach { dish ->
                        Text(
                            dish.title,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    roundupUrl = null
                                    openPreview(KeylessSearch.Result(dish.title, dish.url, "hunt"))
                                }
                                .padding(top = 12.dp),
                        )
                    }
                    TextButton(
                        onClick = { roundupUrl = null },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Close") }
                }
            }
        }
    }
}

/** 45 -> "45 min", 135 -> "2 hr 15 min". */
private fun formatMinutes(m: Int): String =
    if (m >= 60) {
        val h = m / 60
        val r = m % 60
        if (r == 0) "$h hr" else "$h hr $r min"
    } else "$m min"

private fun hostOf(url: String): String = try {
    URI(url).host?.removePrefix("www.") ?: url
} catch (t: Throwable) {
    url
}
