package com.trypod33.kitchlore.ui

import android.accounts.AccountManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.trypod33.kitchlore.data.local.AppDatabase
import com.trypod33.kitchlore.data.local.entity.Recipe
import com.trypod33.kitchlore.sync.DriveSyncManager
import kotlinx.coroutines.launch

/** A small round tag, tappable when onTap is given. */
@Composable
fun TagPill(label: String, selected: Boolean = false, onTap: (() -> Unit)? = null) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        modifier = if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier,
    ) {
        Text(label, fontSize = 13.sp, color = fg, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

private data class IngHit(val recipe: Recipe, val matched: Int, val missing: Int)

private val CATEGORY_EMOJI = mapOf(
    "Chicken" to "\uD83C\uDF57", "Beef" to "\uD83E\uDD69", "Main" to "\uD83D\uDED2",
    "Side" to "\uD83E\uDD57", "Appetizer" to "\uD83E\uDDC0", "Dessert" to "\uD83C\uDF70",
    "Breakfast" to "\uD83C\uDF73", "Low salt" to "\uD83E\uDDC2", "Vegetarian" to "\uD83E\uDD66",
)

private val COMMON_INGREDIENTS = listOf(
    "garlic", "onion", "eggs", "milk", "butter", "chicken", "beef", "rice", "pasta", "tomato",
    "cheese", "potato", "carrot", "lemon", "mushroom", "bread", "fish", "beans", "bell pepper", "salt",
)

private val INGREDIENT_EMOJI = mapOf(
    "garlic" to "\uD83E\uDD84", "onion" to "\uD83E\uDDC5", "eggs" to "\uD83E\uDD5A",
    "milk" to "\uD83E\uDD5B", "butter" to "\uD83E\uDDC8", "chicken" to "\uD83C\uDF57",
    "beef" to "\uD83E\uDD69", "rice" to "\uD83C\uDF5A", "pasta" to "\uD83C\uDF5D",
    "tomato" to "\uD83C\uDF45", "cheese" to "\uD83E\uDDC0", "potato" to "\uD83E\uDD54",
    "carrot" to "\uD83E\uDD55", "lemon" to "\uD83C\uDF4B", "mushroom" to "\uD83C\uDF44",
    "bread" to "\uD83C\uDF5E", "fish" to "\uD83D\uDC1F", "beans" to "\uD83E\uDED8",
    "bell pepper" to "\uD83E\uDD51", "salt" to "\uD83E\uDDC2",
)

private fun pantryItems(context: Context): List<String> =
    (context.getSharedPreferences("kitchlore_pantry", Context.MODE_PRIVATE)
        .getString("items", "") ?: "")
        .split("|").map { it.trim() }.filter { it.isNotBlank() }

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun IngChip(label: String, selected: Boolean, onTap: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = RoundedCornerShape(50), color = bg, modifier = Modifier.clickable(onClick = onTap)) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            INGREDIENT_EMOJI[label.lowercase()]?.let {
                Text(it, fontSize = 14.sp)
                Spacer(Modifier.width(5.dp))
            }
            Text(label, fontSize = 13.sp, color = fg, maxLines = 1)
        }
    }
}

@Composable
private fun IngResultRow(hit: IngHit, total: Int, onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            RecipeThumbnail(
                hit.recipe.originalImagePath,
                Modifier
                    .fillMaxHeight()
                    .width(64.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)),
            )
            Column(Modifier.padding(horizontal = 12.dp)) {
                Text(
                    hit.recipe.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (hit.missing == 0) "uses all $total" else "missing ${hit.missing} of $total",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun RailCard(recipe: Recipe, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.width(150.dp).clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column {
            RecipeThumbnail(
                recipe.originalImagePath,
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            )
            Column(Modifier.padding(10.dp)) {
                Text(
                    recipe.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                recipe.cookTimeMinutes?.takeIf { it > 0 }?.let {
                    Text("Cook $it min", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    label: String,
    photoPath: String?,
    emoji: String?,
    selected: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onTap),
        shape = RoundedCornerShape(16.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(78.dp)) {
                if (photoPath != null) {
                    RecipeThumbnail(photoPath, Modifier.fillMaxSize())
                } else {
                    Box(
                        Modifier.fillMaxSize().background(Color(0xFFB96A45)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(emoji ?: label.take(1), fontSize = 30.sp)
                    }
                }
            }
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun RecipeListScreen(
    db: AppDatabase,
    onOpen: (Long) -> Unit,
    onScan: () -> Unit,
    onPhoto: () -> Unit,
    onDoc: () -> Unit,
    onHunt: () -> Unit,
    onPantry: () -> Unit,
    onSettings: () -> Unit,
    onCategory: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var syncing by remember { mutableStateOf(false) }
    var syncMsg by remember { mutableStateOf<String?>(null) }
    var selectedIng by remember { mutableStateOf<Set<String>>(emptySet()) }
    var ingIndex by remember { mutableStateOf<Map<Long, List<String>>>(emptyMap()) }
    var showAddIng by remember { mutableStateOf(false) }
    var newIng by remember { mutableStateOf("") }
    val recipes by db.recipeDao().getAllRecipes().collectAsState(initial = emptyList())

    val accountPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val accountName = res.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (accountName != null) {
            DriveSyncManager.saveAccount(context, accountName)
            syncMsg = "Account saved, tap Sync again"
        }
    }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        syncMsg = "Permission granted, tap Sync again"
    }

    fun doSync() {
        if (syncing) return
        val cred = DriveSyncManager.credential(context)
        if (cred.selectedAccountName == null) {
            accountPicker.launch(cred.newChooseAccountIntent())
            return
        }
        syncing = true
        syncMsg = "Syncing..."
        scope.launch {
            try {
                syncMsg = DriveSyncManager.sync(context, db)
            } catch (e: UserRecoverableAuthIOException) {
                consentLauncher.launch(e.intent)
                syncMsg = "Google asked for permission"
            } catch (e: java.io.IOException) {
                syncMsg = "No connection"
            } catch (e: Exception) {
                syncMsg = "Sync failed"
            }
            syncing = false
        }
    }

    val trimmed = query.trim()

    // Ingredient search: the pantry leads, common staples fill the rest.
    val pantry = remember { pantryItems(context) }
    val chips = (selectedIng + pantry + COMMON_INGREDIENTS)
        .distinctBy { it.lowercase() }
        .take(30)

    LaunchedEffect(selectedIng, recipes) {
        ingIndex = if (selectedIng.isEmpty()) emptyMap()
        else db.ingredientDao().getAllOnce().groupBy({ it.recipeId }, { it.name })
    }

    val ingHits = if (selectedIng.isEmpty()) emptyList() else recipes.mapNotNull { r ->
        val names = ingIndex[r.id] ?: return@mapNotNull null
        val matched = selectedIng.count { s -> names.any { matchesPantryWord(it, s) } }
        if (matched == 0) null else IngHit(r, matched, selectedIng.size - matched)
    }.sortedWith(compareBy<IngHit> { it.missing }.thenByDescending { it.matched }).take(12)

    // Saved rail: favorites lead, recent additions fill in until there are any.
    val favorites = recipes.filter { it.favorite }
    val rail = if (favorites.isNotEmpty()) favorites else recipes.sortedByDescending { it.dateAdded }.take(10)
    val railLabel = if (favorites.isNotEmpty()) "Saved" else "Recently added"

    // Category grid over the categories the library actually has.
    val canonical = listOf("Chicken", "Beef", "Main", "Side", "Appetizer", "Dessert", "Breakfast", "Low salt", "Vegetarian")
    val tagged = recipes.flatMap { r -> r.tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { it to r } }
    val present = tagged.map { it.first }.distinct()
    val categories = listOf("All") + canonical.filter { it in present } + present.filter { it !in canonical }
        .sortedByDescending { t -> tagged.count { it.first == t } }
    val catRows = categories.chunked(2)
    val catPhotos = categories.associateWith { t ->
        if (t == "All") recipes.firstOrNull { it.originalImagePath != null }?.originalImagePath
        else tagged.firstOrNull { it.first == t && it.second.originalImagePath != null }?.second?.originalImagePath
    }

    val filtered = recipes.filter { r ->
        trimmed.isEmpty() || r.title.contains(trimmed, ignoreCase = true)
    }

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
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = onScan, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text("Scan", maxLines = 1)
            }
            FilledTonalButton(onClick = onPhoto, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text("Photo", maxLines = 1)
            }
            FilledTonalButton(onClick = onDoc, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text("Doc", maxLines = 1)
            }
            OutlinedButton(
                onClick = { doSync() },
                enabled = !syncing,
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
                Text(if (syncing) "Syncing" else "Sync", maxLines = 1)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = onHunt, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text("Recipe hunt", maxLines = 1)
            }
            FilledTonalButton(onClick = onPantry, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text("Pantry", maxLines = 1)
            }
            OutlinedButton(
                onClick = onSettings,
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
                Text("Settings", maxLines = 1)
            }
        }
        syncMsg?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (recipes.isEmpty()) {
                item {
                    Text(
                        "No recipes yet. Scan a page, import a photo or document, sync the Drive library, or hunt for one online.",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else if (trimmed.isNotEmpty()) {
                // Search mode: matches lead at the top, and the decorative
                // rails, chips and category grid stand down until the query
                // clears.
                item {
                    SectionLabel(
                        if (filtered.isEmpty()) "No matches"
                        else if (filtered.size == 1) "1 match"
                        else "${filtered.size} matches"
                    )
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "Nothing in the library matches that. Recipe hunt can find one online.",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { recipe ->
                        RecipeResultRow(recipe = recipe, onOpen = onOpen)
                    }
                }
            } else {
                item { SectionLabel("Cook with what you have") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(chips, key = { it }) { ing ->
                            IngChip(
                                label = ing,
                                selected = selectedIng.any { it.equals(ing, ignoreCase = true) },
                                onTap = {
                                    selectedIng = if (selectedIng.any { it.equals(ing, ignoreCase = true) }) {
                                        selectedIng.filterNot { it.equals(ing, ignoreCase = true) }.toSet()
                                    } else {
                                        selectedIng + ing.lowercase()
                                    }
                                },
                            )
                        }
                        item {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { showAddIng = true },
                            ) {
                                Text(
                                    "+ Add",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
                if (selectedIng.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (ingHits.isEmpty()) "Nothing in the library yet" else "${ingHits.size} matches",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { selectedIng = emptySet() }) { Text("Clear") }
                        }
                    }
                    if (ingHits.isEmpty()) {
                        item {
                            Text(
                                "No recipes in your library use those yet. Hunt one online and import it.",
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        items(ingHits, key = { "ing${it.recipe.id}" }) { hit ->
                            IngResultRow(hit, selectedIng.size) { onOpen(hit.recipe.id) }
                        }
                    }
                }
                item { SectionLabel(railLabel) }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(rail, key = { "rail${it.id}" }) { r ->
                            RailCard(r) { onOpen(r.id) }
                        }
                    }
                }
                item { SectionLabel("Categories") }
                items(catRows.size) { i ->
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        catRows[i].forEach { t ->
                            CategoryCard(
                                label = t,
                                photoPath = catPhotos[t],
                                emoji = CATEGORY_EMOJI[t],
                                selected = false,
                                onTap = { onCategory(t) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (catRows[i].size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item { SectionLabel("All recipes") }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "No matches",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { recipe ->
                        RecipeResultRow(recipe = recipe, onOpen = onOpen)
                    }
                }
            }
        }
    }

    if (showAddIng) {
        AlertDialog(
            onDismissRequest = { showAddIng = false },
            title = { Text("Add ingredient") },
            text = {
                OutlinedTextField(
                    value = newIng,
                    onValueChange = { newIng = it },
                    label = { Text("Ingredient") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = newIng.trim().lowercase()
                    if (v.isNotEmpty()) selectedIng = selectedIng + v
                    newIng = ""
                    showAddIng = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddIng = false }) { Text("Cancel") } },
        )
    }
}

/** One library recipe row, shared by the search results and the All recipes list. */
@Composable
private fun RecipeResultRow(recipe: com.trypod33.kitchlore.data.local.entity.Recipe, onOpen: (Long) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "cardScale",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null) { onOpen(recipe.id) },
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.height(92.dp), verticalAlignment = Alignment.CenterVertically) {
            RecipeThumbnail(
                recipe.originalImagePath,
                Modifier
                    .fillMaxHeight()
                    .width(92.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)),
            )
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
                val tags = recipe.tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (tags.isNotEmpty()) {
                    Text(
                        tags.take(3).joinToString("  \u00B7  "),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
