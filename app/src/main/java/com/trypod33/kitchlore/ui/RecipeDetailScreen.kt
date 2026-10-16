package com.trypod33.kitchlore.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trypod33.kitchlore.data.local.AppDatabase
import com.trypod33.kitchlore.data.local.entity.Recipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RecipeDetailRoute(db: AppDatabase, recipeId: Long, onBack: () -> Unit, onCook: () -> Unit, onEdit: () -> Unit) {
    var recipe by remember { mutableStateOf<Recipe?>(null) }
    var notFound by remember { mutableStateOf(false) }
    LaunchedEffect(recipeId) {
        recipe = db.recipeDao().getRecipeById(recipeId)
        if (recipe == null) notFound = true
    }
    if (notFound) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("Recipe not found", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("Back") }
        }
    } else {
        RecipeDetailScreen(recipe, db, recipeId, onBack, onCook, onEdit)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun RecipeDetailScreen(
    recipe: Recipe?,
    db: AppDatabase,
    recipeId: Long,
    onBack: () -> Unit,
    onCook: () -> Unit,
    onEdit: () -> Unit,
) {
    val ingredients by db.ingredientDao().getIngredientsForRecipe(recipeId).collectAsState(initial = emptyList())
    val steps by db.stepDao().getStepsForRecipe(recipeId).collectAsState(initial = emptyList())
    val checked = remember { mutableStateMapOf<Long, Boolean>() }
    ingredients.forEach { checked.putIfAbsent(it.id, false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteRequested by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(deleteRequested) {
        if (deleteRequested) {
            recipe?.let { db.recipeDao().delete(it) }
            deleted = true
            deleteRequested = false
        }
    }

    // Servings: every amount scales by servings / baseServings. The recipe's
    // own base count is never rewritten; the chosen serving count saves in its
    // own column (scaledServings), so the stored amounts always stay matched
    // to the stored base.
    val base = recipe?.baseServings ?: 1
    var servings by remember(recipe?.id, recipe?.baseServings, recipe?.scaledServings) {
        mutableStateOf(recipe?.scaledServings?.takeIf { it > 0 } ?: if (base > 0) base else 1)
    }
    val factor = if (base > 0) servings.toDouble() / base else 1.0

    // The 3x5 card: photo covers the whole face with a little "tap" in the
    var favorited by remember(recipe?.favorite) { mutableStateOf(recipe?.favorite == true) }
    // corner, and tapping flips it over, medium fast, into the recipe page.
    var editingTags by remember { mutableStateOf(false) }
    // Tags hold in their own state so an edit here shows instantly without
    // re-reading the whole recipe row.
    var tagsCsvState by remember(recipe?.id) { mutableStateOf(recipe?.tagsCsv ?: "") }
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // nothing to preload; rotation starts at 0
    }

    if (deleted) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("Recipe deleted", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("Back") }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                if (rotation.value >= 90f) {
                    Row {
                        TextButton(onClick = onEdit) { Text("Edit") }
                        TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
                    }
                }
            }
            if (rotation.value < 90f) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .aspectRatio(3f / 5f)
                            .graphicsLayer {
                                rotationY = rotation.value
                                cameraDistance = 12 * density
                            }
                            .clickable {
                                scope.launch {
                                    rotation.animateTo(180f, tween(420, easing = FastOutSlowInEasing))
                                }
                            },
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Box {
                            RecipeThumbnail(recipe?.originalImagePath, Modifier.fillMaxSize())
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.Black.copy(alpha = 0.45f),
                                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                            ) {
                                Text(
                                    "tap",
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .graphicsLayer { rotationY = rotation.value - 180f },
                ) {
                    recipe?.let { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RecipeThumbnail(
                                r.originalImagePath,
                                Modifier
                                    .width(96.dp)
                                    .height(96.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        scope.launch {
                                            rotation.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
                                        }
                                    },
                            )
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(r.title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                                val line = servingLine(r)
                                if (line.isNotEmpty()) {
                                    Text(line, fontSize = 14.sp)
                                }
                            }
                        }
                        // Tags and heart sit under the title so it gets every
                        // pixel between the picture and the screen edge.
                        val tags = tagsCsvState.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Pills wrap instead of scrolling so nothing
                            // hides behind the Tags button and heart.
                            FlowRow(
                                Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                tags.forEach { TagPill(label = it) }
                            }
                            TextButton(onClick = { editingTags = true }) { Text("Tags", fontSize = 14.sp) }
                            TextButton(
                                onClick = {
                                    val next = !(recipe?.favorite ?: false)
                                    favorited = next
                                    scope.launch { db.recipeDao().setFavorite(recipeId, next) }
                                },
                            ) { Text(if (favorited) "\u2665" else "\u2661", fontSize = 26.sp) }
                        }
                        r.notes?.takeIf { it.isNotBlank() }?.let {
                            Text(it, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
                        }

                        // Servings stepper, amounts follow along
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (servings > 1) {
                                        servings -= 1
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                db.recipeDao().update(r.copy(scaledServings = servings, dateModified = System.currentTimeMillis()))
                                            }
                                        }
                                    }
                                },
                                enabled = servings > 1,
                            ) { Text("-") }
                            Text(
                                "$servings servings" + if (factor != 1.0) "  (from $base)" else "",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            OutlinedButton(onClick = {
                                servings += 1
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        db.recipeDao().update(r.copy(scaledServings = servings, dateModified = System.currentTimeMillis()))
                                    }
                                }
                            }) { Text("+") }
                        }

                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onCook, enabled = steps.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text("Cook mode")
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Ingredients", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        if (ingredients.isEmpty()) {
                            Text("No ingredients recorded.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            // One column; two across wrapped badly with long lines.
                            ingredients.forEach { ing ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = checked[ing.id] ?: false,
                                        onCheckedChange = { on -> checked[ing.id] = on },
                                    )
                                    Text(
                                        ingredientLabel(ing.name, ing.amount, ing.unit, ing.rawText, factor),
                                        fontSize = 15.sp,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Steps", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        if (steps.isEmpty()) {
                            Text("No steps recorded.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            steps.forEach { step ->
                                Text(
                                    "${step.stepNumber}. ${step.instructionText}",
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete recipe?") },
                text = { Text("This removes it from this app's library.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        deleteRequested = true
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        }
        if (editingTags && recipe != null) {
            val target = recipe
            TagEditorDialog(
                initialTags = tagsCsvState.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                onDismiss = { editingTags = false },
                onSave = { list ->
                    editingTags = false
                    val joined = list.joinToString(", ")
                    tagsCsvState = joined
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            db.recipeDao().update(
                                target.copy(tagsCsv = joined, dateModified = System.currentTimeMillis())
                            )
                        }
                    }
                },
            )
        }
    }
}
