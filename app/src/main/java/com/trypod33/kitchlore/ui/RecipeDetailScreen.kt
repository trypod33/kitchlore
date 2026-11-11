package com.trypod33.kitchlore.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trypod33.kitchlore.data.local.AppDatabase
import com.trypod33.kitchlore.data.local.entity.Recipe

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

    LaunchedEffect(deleteRequested) {
        if (deleteRequested) {
            recipe?.let { db.recipeDao().delete(it) }
            deleted = true
            deleteRequested = false
        }
    }

    if (deleted) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("Recipe deleted", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("Back") }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Back") }
                Row {
                    TextButton(onClick = onEdit) { Text("Edit") }
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
                }
            }
            recipe?.let { r ->
                RecipeThumbnail(r.originalImagePath, Modifier.fillMaxWidth().height(190.dp))
                Spacer(Modifier.height(14.dp))
                Text(r.title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                val line = servingLine(r)
                if (line.isNotEmpty()) {
                    Text(line, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                }
                r.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            Button(onClick = onCook, enabled = steps.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text("Cook mode")
            }
            Spacer(Modifier.height(18.dp))
            Text("Ingredients", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (ingredients.isEmpty()) {
                Text("No ingredients recorded.", style = MaterialTheme.typography.bodyMedium)
            } else {
                ingredients.forEach { ing ->
                    Row(
                        Modifier.fillMaxWidth().clickable { checked[ing.id] = !(checked[ing.id] ?: false) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked[ing.id] ?: false, onCheckedChange = { on -> checked[ing.id] = on })
                        val amount = ing.amount
                        val quantity = if (amount != null) formatAmount(amount) + " " else ""
                        val unitPart = ing.unit ?: ""
                        val label = (quantity + unitPart + (if (unitPart.isNotEmpty() && quantity.isNotEmpty()) " " else "") + ing.name).trim()
                        Text(if (label.isEmpty()) ing.rawText else label, fontSize = 16.sp)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
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
    }
}
