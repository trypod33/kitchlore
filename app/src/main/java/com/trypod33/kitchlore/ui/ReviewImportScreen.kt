package com.trypod33.kitchlore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trypod33.kitchlore.data.OcrParser
import com.trypod33.kitchlore.data.ParsedRecipe
import com.trypod33.kitchlore.data.local.AppDatabase
import com.trypod33.kitchlore.data.local.entity.Ingredient
import com.trypod33.kitchlore.data.local.entity.Recipe
import com.trypod33.kitchlore.data.local.entity.Step
import kotlinx.coroutines.launch

// Review screen after a scan or document import: everything editable before
// it goes into the library. Select mode makes cleanup fast: tap the junk
// lines, one tap deletes them, two taps merge what the scan split apart, so
// a bad scan is fixed in seconds.

@Composable
fun ReviewImportScreen(
    db: AppDatabase,
    parsed: ParsedRecipe,
    photoPath: String?,
    sourceNote: String,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf(photoPath) }
    var title by remember { mutableStateOf(parsed.title.ifBlank { "New recipe" }) }
    var servings by remember { mutableStateOf(parsed.baseServings.coerceAtLeast(1).toString()) }
    var ingredients by remember {
        mutableStateOf(parsed.ingredients.map { displayLine(it) }.ifEmpty { listOf("") })
    }
    // Lines the scanner read with a unit but no number ("tablespoons creamy
    // peanut butter") get a quiet "amount missing" hint until they are fixed.
    var ingHints by remember {
        mutableStateOf(
            parsed.ingredients.mapIndexedNotNull { i, p -> if (p.needsAmount) i else null }.toSet()
        )
    }
    var steps by remember {
        mutableStateOf(parsed.steps.map { it.instructionText }.ifEmpty { listOf("") })
    }
    var selecting by remember { mutableStateOf(false) }
    var selectedIng by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var selectedSteps by remember { mutableStateOf<Set<Int>>(emptySet()) }

    fun toggleIng(i: Int) {
        selectedIng = if (i in selectedIng) selectedIng - i else selectedIng + i
    }
    fun toggleStep(i: Int) {
        selectedSteps = if (i in selectedSteps) selectedSteps - i else selectedSteps + i
    }
    fun deleteSelected() {
        ingredients = ingredients.filterIndexed { i, _ -> i !in selectedIng }
        steps = steps.filterIndexed { i, _ -> i !in selectedSteps }
        selectedIng = emptySet()
        selectedSteps = emptySet()
    }
    fun mergeSelected() {
        if (selectedIng.size >= 2) {
            val idxs = selectedIng.sorted()
            val merged = idxs.mapNotNull { ingredients.getOrNull(it)?.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
            val out = mutableListOf<String>()
            ingredients.forEachIndexed { i, v ->
                when {
                    i == idxs.first() -> out.add(merged)
                    i in idxs -> {}
                    else -> out.add(v)
                }
            }
            ingredients = out
            selectedIng = emptySet()
        } else if (selectedSteps.size >= 2) {
            val idxs = selectedSteps.sorted()
            val merged = idxs.mapNotNull { steps.getOrNull(it)?.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
            val out = mutableListOf<String>()
            steps.forEachIndexed { i, v ->
                when {
                    i == idxs.first() -> out.add(merged)
                    i in idxs -> {}
                    else -> out.add(v)
                }
            }
            steps = out
            selectedSteps = emptySet()
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Review recipe", fontSize = 26.sp)
        PhotoEditSection(
            path = photo,
            onPathChange = { photo = it },
            modifier = Modifier.padding(vertical = 12.dp),
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        OutlinedTextField(
            value = servings,
            onValueChange = { servings = it.filter { c -> c.isDigit() }.take(3) },
            label = { Text("Servings") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Ingredients", fontSize = 18.sp)
            TextButton(onClick = {
                selecting = !selecting
                selectedIng = emptySet()
                selectedSteps = emptySet()
            }) { Text(if (selecting) "Cancel select" else "Select lines") }
        }
        if (selecting) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = { deleteSelected() },
                    enabled = selectedIng.isNotEmpty() || selectedSteps.isNotEmpty(),
                ) { Text("Delete ${selectedIng.size + selectedSteps.size}") }
                FilledTonalButton(
                    onClick = { mergeSelected() },
                    enabled = selectedIng.size >= 2 || selectedSteps.size >= 2,
                ) { Text("Merge") }
            }
        }
        ingredients.forEachIndexed { i, line ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (selecting) Modifier.clickable { toggleIng(i) } else Modifier
                    )
                    .background(
                        if (selecting && i in selectedIng) MaterialTheme.colorScheme.secondaryContainer
                        else Color.Transparent
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selecting) {
                    Surface(
                        shape = CircleShape,
                        color = if (i in selectedIng) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(start = 4.dp).size(22.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (i in selectedIng) {
                                Text("\u2713", fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                    SpacerWidth(6.dp)
                }
                OutlinedTextField(
                    value = ingredients[i],
                    onValueChange = { v ->
                        ingredients = ingredients.toMutableList().also { it[i] = v }
                    },
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
                if (!selecting) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("\u2191", fontSize = 16.sp, modifier = Modifier
                            .clickable(enabled = i > 0) { if (i > 0) ingredients = ingredients.toMutableList().also { l -> val t = l[i - 1]; l[i - 1] = l[i]; l[i] = t } }
                            .padding(2.dp))
                        Text("x", fontSize = 14.sp, modifier = Modifier
                            .clickable { ingredients = ingredients.toMutableList().also { it.removeAt(i) } }
                            .padding(2.dp))
                        Text("\u2193", fontSize = 16.sp, modifier = Modifier
                            .clickable(enabled = i < ingredients.size - 1) { if (i < ingredients.size - 1) ingredients = ingredients.toMutableList().also { l -> val t = l[i + 1]; l[i + 1] = l[i]; l[i] = t } }
                            .padding(2.dp))
                    }
                }
            }
            if (i in ingHints && !selecting) {
                Text(
                    "amount missing from the scan, add it above",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
        FilledTonalButton(onClick = { ingredients = ingredients + "" }, modifier = Modifier.padding(vertical = 4.dp)) {
            Text("Add ingredient")
        }

        Text("Steps", fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        steps.forEachIndexed { i, line ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (selecting) Modifier.clickable { toggleStep(i) } else Modifier
                    )
                    .background(
                        if (selecting && i in selectedSteps) MaterialTheme.colorScheme.secondaryContainer
                        else Color.Transparent
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selecting) {
                    Surface(
                        shape = CircleShape,
                        color = if (i in selectedSteps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(start = 4.dp).size(22.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (i in selectedSteps) {
                                Text("\u2713", fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                    SpacerWidth(6.dp)
                }
                OutlinedTextField(
                    value = steps[i],
                    onValueChange = { v ->
                        steps = steps.toMutableList().also { it[i] = v }
                    },
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
                if (!selecting) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("\u2191", fontSize = 16.sp, modifier = Modifier
                            .clickable(enabled = i > 0) { if (i > 0) steps = steps.toMutableList().also { l -> val t = l[i - 1]; l[i - 1] = l[i]; l[i] = t } }
                            .padding(2.dp))
                        Text("x", fontSize = 14.sp, modifier = Modifier
                            .clickable { steps = steps.toMutableList().also { it.removeAt(i) } }
                            .padding(2.dp))
                        Text("\u2193", fontSize = 16.sp, modifier = Modifier
                            .clickable(enabled = i < steps.size - 1) { if (i < steps.size - 1) steps = steps.toMutableList().also { l -> val t = l[i + 1]; l[i + 1] = l[i]; l[i] = t } }
                            .padding(2.dp))
                    }
                }
            }
        }
        FilledTonalButton(onClick = { steps = steps + "" }, modifier = Modifier.padding(vertical = 4.dp)) {
            Text("Add step")
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = {
                scope.launch {
                    saveReview(db, title, servings, ingredients, steps, photo, sourceNote)
                    onSaved()
                }
            }) { Text("Save recipe") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun SpacerWidth(w: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.layout.Spacer(Modifier.width(w))
}

private suspend fun saveReview(
    db: AppDatabase,
    title: String,
    servings: String,
    ingredients: List<String>,
    steps: List<String>,
    photoPath: String?,
    sourceNote: String,
) {
    val now = System.currentTimeMillis()
    val rid = db.recipeDao().insert(
        Recipe(
            title = title.ifBlank { "(untitled)" },
            baseServings = servings.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            sourceType = "scan",
            sourceNote = sourceNote,
            originalImagePath = photoPath,
            dateAdded = now,
            dateModified = now,
        ),
    )
    val ingEntities = ingredients
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapIndexed { i, line ->
            val p = OcrParser.parseIngredientLine(line)
            Ingredient(
                recipeId = rid,
                name = p.name.ifBlank { p.rawText },
                rawText = p.rawText,
                amount = p.amount,
                unit = p.unit,
                sortOrder = i,
            )
        }
    if (ingEntities.isNotEmpty()) db.ingredientDao().insertAll(ingEntities)
    val stepEntities = steps
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapIndexed { i, line ->
            Step(recipeId = rid, stepNumber = i + 1, instructionText = line, timerSeconds = OcrParser.timerFor(line))
        }
    if (stepEntities.isNotEmpty()) db.stepDao().insertAll(stepEntities)
}

// Show the cleaned parse (like "1/2 cup butter or margarine") rather than the
// raw OCR line, so parser fixes reach what the reviewer sees; the raw line is
// the fallback when the parse came back empty.
private fun displayLine(p: com.trypod33.kitchlore.data.ParsedIngredient): String {
    val name = p.name.ifBlank { p.rawText }
    val head = listOfNotNull(
        p.amount?.let { formatFraction(it) },
        p.unit?.takeIf { it.isNotBlank() },
    )
    if (head.isEmpty()) return name
    val headText = head.joinToString(" ")
    if (name.startsWith(headText)) return name
    return "$headText $name"
}

private fun formatFraction(v: Double): String {
    val glyphs = listOf(0.25 to "\u00bc", (1.0 / 3) to "\u2153", 0.5 to "\u00bd", (2.0 / 3) to "\u2154", 0.75 to "\u00be")
    val whole = v.toInt()
    val frac = v - whole
    val g = glyphs.minByOrNull { kotlin.math.abs(it.first - frac) }
    val fracText = g?.takeIf { kotlin.math.abs(it.first - frac) < 0.08 }?.second
    return when {
        fracText != null -> (if (whole > 0) whole.toString() else "") + fracText
        v == v.toInt().toDouble() -> v.toInt().toString()
        else -> v.toString()
    }
}
