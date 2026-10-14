package com.trypod33.kitchlore.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
// it goes into the library, same role as the Termux review step.

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
    var steps by remember {
        mutableStateOf(parsed.steps.map { it.instructionText }.ifEmpty { listOf("") })
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

        Text("Ingredients", fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        ingredients.forEachIndexed { i, line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = ingredients[i],
                    onValueChange = { v ->
                        ingredients = ingredients.toMutableList().also { it[i] = v }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
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
        FilledTonalButton(onClick = { ingredients = ingredients + "" }, modifier = Modifier.padding(vertical = 4.dp)) {
            Text("Add ingredient")
        }

        Text("Steps", fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        steps.forEachIndexed { i, line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = steps[i],
                    onValueChange = { v ->
                        steps = steps.toMutableList().also { it[i] = v }
                    },
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
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
