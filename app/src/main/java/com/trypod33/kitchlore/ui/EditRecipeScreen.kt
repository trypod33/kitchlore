package com.trypod33.kitchlore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trypod33.kitchlore.data.local.AppDatabase
import com.trypod33.kitchlore.data.local.entity.Ingredient
import com.trypod33.kitchlore.data.local.entity.Recipe
import com.trypod33.kitchlore.data.local.entity.Step
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private class DraftIng(var name: String, var amount: String, var unit: String)
private class DraftStep(var text: String)

@Composable
fun EditRecipeScreen(db: AppDatabase, recipeId: Long, isNew: Boolean, onDone: () -> Unit, onCancel: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var servings by remember { mutableStateOf("") }
    var prep by remember { mutableStateOf("") }
    var cook by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var ingDrafts by remember { mutableStateOf(listOf(DraftIng("", "", ""))) }
    var stepDrafts by remember { mutableStateOf(listOf(DraftStep(""))) }
    var photoPath by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(isNew) }

    LaunchedEffect(recipeId) {
        if (!isNew) {
            val r = db.recipeDao().getRecipeById(recipeId)
            if (r != null) {
                title = r.title
                photoPath = r.originalImagePath
                servings = r.baseServings.toString()
                prep = r.prepTimeMinutes?.toString() ?: ""
                cook = r.cookTimeMinutes?.toString() ?: ""
                notes = r.notes ?: ""
                ingDrafts = db.ingredientDao().getIngredientsForRecipe(recipeId).first().map {
                    DraftIng(it.name, it.amount?.toString() ?: "", it.unit ?: "")
                }
                stepDrafts = db.stepDao().getStepsForRecipe(recipeId).first().map { DraftStep(it.instructionText) }
            }
        }
        loaded = true
    }

    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (isNew) "New recipe" else "Edit recipe", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (!isNew) {
            PhotoEditSection(path = photoPath, onPathChange = { photoPath = it })
        }
        if (loaded) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = servings,
                    onValueChange = { servings = it },
                    label = { Text("Servings") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = prep,
                    onValueChange = { prep = it },
                    label = { Text("Prep (min)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = cook,
                    onValueChange = { cook = it },
                    label = { Text("Cook (min)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Ingredients", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            ingDrafts.forEachIndexed { i, d ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = d.name,
                        onValueChange = { d.name = it; ingDrafts = ingDrafts.toList() },
                        label = { Text("Name") },
                        modifier = Modifier.weight(1.6f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = d.amount,
                        onValueChange = { d.amount = it; ingDrafts = ingDrafts.toList() },
                        label = { Text("Amt") },
                        modifier = Modifier.weight(0.8f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = d.unit,
                        onValueChange = { d.unit = it; ingDrafts = ingDrafts.toList() },
                        label = { Text("Unit") },
                        modifier = Modifier.weight(0.8f),
                        singleLine = true,
                    )
                    TextButton(onClick = { ingDrafts = ingDrafts.filterIndexed { j, _ -> j != i } }) { Text("X") }
                }
            }
            TextButton(onClick = { ingDrafts = ingDrafts + DraftIng("", "", "") }) { Text("Add ingredient") }
            Text("Steps", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            stepDrafts.forEachIndexed { i, d ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${i + 1}.", fontSize = 16.sp)
                    OutlinedTextField(
                        value = d.text,
                        onValueChange = { d.text = it; stepDrafts = stepDrafts.toList() },
                        label = { Text("Step ${i + 1}") },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { stepDrafts = stepDrafts.filterIndexed { j, _ -> j != i } }) { Text("X") }
                }
            }
            TextButton(onClick = { stepDrafts = stepDrafts + DraftStep("") }) { Text("Add step") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Button(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            saveRecipe(db, recipeId, isNew, title, servings, prep, cook, notes, photoPath, ingDrafts, stepDrafts)
                        }
                    }
                    onDone()
                }) { Text("Save") }
            }
        }
    }
}

private suspend fun saveRecipe(
    db: AppDatabase,
    recipeId: Long,
    isNew: Boolean,
    title: String,
    servings: String,
    prep: String,
    cook: String,
    notes: String,
    photoPath: String?,
    ingDrafts: List<DraftIng>,
    stepDrafts: List<DraftStep>,
) {
    val base = (servings.toDoubleOrNull() ?: 1.0).roundToInt().coerceAtLeast(1)
    val existing = if (isNew) null else db.recipeDao().getRecipeById(recipeId)
    val recipe = Recipe(
        id = if (isNew) 0L else recipeId,
        title = title.ifBlank { "(untitled)" },
        baseServings = base,
        sourceType = existing?.sourceType ?: "manual",
        sourceNote = existing?.sourceNote,
        originalImagePath = photoPath,
        notes = notes.ifBlank { null },
        prepTimeMinutes = prep.toIntOrNull(),
        cookTimeMinutes = cook.toIntOrNull(),
        dateAdded = existing?.dateAdded ?: System.currentTimeMillis(),
        dateModified = System.currentTimeMillis(),
    )
    val rid: Long
    if (isNew) {
        rid = db.recipeDao().insert(recipe)
    } else {
        db.recipeDao().update(recipe)
        rid = recipeId
    }
    db.ingredientDao().deleteAllForRecipe(rid)
    db.stepDao().deleteAllForRecipe(rid)
    val ings = ingDrafts
        .filter { it.name.isNotBlank() || it.amount.isNotBlank() }
        .mapIndexed { i, d ->
            Ingredient(
                recipeId = rid,
                name = d.name.ifBlank { d.amount },
                rawText = listOf(d.amount, d.unit, d.name).filter { it.isNotBlank() }.joinToString(" "),
                amount = d.amount.toDoubleOrNull(),
                unit = d.unit.ifBlank { null },
                sortOrder = i,
            )
        }
    if (ings.isNotEmpty()) db.ingredientDao().insertAll(ings)
    val steps = stepDrafts
        .filter { it.text.isNotBlank() }
        .mapIndexed { i, d -> Step(recipeId = rid, stepNumber = i + 1, instructionText = d.text.trim()) }
    if (steps.isNotEmpty()) db.stepDao().insertAll(steps)
}
