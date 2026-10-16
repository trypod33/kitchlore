package com.trypod33.kitchlore.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.delay

@Composable
fun CookModeRoute(db: AppDatabase, recipeId: Long, onBack: () -> Unit) {
    val steps by db.stepDao().getStepsForRecipe(recipeId).collectAsState(initial = emptyList())
    val ingredients by db.ingredientDao().getIngredientsForRecipe(recipeId).collectAsState(initial = emptyList())
    var recipe by remember { mutableStateOf<Recipe?>(null) }
    LaunchedEffect(recipeId) { recipe = db.recipeDao().getRecipeById(recipeId) }
    // Cook mode scales with the servings saved on the detail page, same
    // factor the ingredient list there shows.
    val base = recipe?.baseServings ?: 1
    val scaled = recipe?.scaledServings?.takeIf { it > 0 } ?: base
    val factor = if (base > 0) scaled.toDouble() / base else 1.0
    CookModeScreen(steps, ingredients, factor, onBack)
}

@Composable
fun CookModeScreen(steps: List<Step>, ingredients: List<Ingredient>, factor: Double, onBack: () -> Unit) {
    var idx by remember { mutableStateOf(0) }
    var secondsLeft by remember { mutableStateOf<Int?>(null) }
    var running by remember { mutableStateOf(false) }
    // Ingredients stay visible while you cook, the whole point of this screen.
    var showIngredients by remember { mutableStateOf(true) }

    LaunchedEffect(idx) {
        secondsLeft = steps.getOrNull(idx)?.timerSeconds
        running = false
    }
    LaunchedEffect(running) {
        if (running) {
            var s = secondsLeft ?: 0
            while (s > 0) {
                delay(1000)
                s -= 1
                secondsLeft = s
            }
            running = false
        }
    }

    val step = steps.getOrNull(idx)
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Step ${idx + 1} of ${steps.size}", fontSize = 16.sp)
        }
        if (ingredients.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { showIngredients = !showIngredients },
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Ingredients", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(if (showIngredients) "hide" else "show", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    if (showIngredients) {
                        // One column; two across wrapped badly with long lines.
                        ingredients.forEach { ing ->
                            Text(
                                ingredientLabel(ing.name, ing.amount, ing.unit, ing.rawText, factor),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        val paragraphs = remember(step?.instructionText) { splitLongStep(step?.instructionText ?: "") }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) {
                paragraphs.forEachIndexed { i, p ->
                    Text(
                        p,
                        fontSize = if (paragraphs.size > 1) 20.sp else 24.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (i < paragraphs.size - 1) 12.dp else 0.dp),
                    )
                }
            }
        }
        val total = step?.timerSeconds
        if (total != null && total > 0) {
            val s = secondsLeft ?: total
            Text("${s / 60}:${(s % 60).toString().padStart(2, '0')}", fontSize = 44.sp, fontWeight = FontWeight.Bold)
            if (running) {
                OutlinedButton(onClick = { running = false }) { Text("Pause") }
            } else {
                Button(onClick = { running = true }) { Text("Start timer") }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { if (idx > 0) idx -= 1 }, enabled = idx > 0) { Text("Prev") }
            Button(onClick = { if (idx < steps.size - 1) idx += 1 else onBack() }) {
                Text(if (idx < steps.size - 1) "Next" else "Done")
            }
        }
    }
}

// A step the recognizer handed over as one long blob still reads well: it
// breaks into a couple of paragraphs on sentence boundaries for display only,
// the saved step text is untouched.
private fun splitLongStep(text: String): List<String> {
    if (text.length <= 320) return listOf(text)
    val sentences = text.split(Regex("(?<=[.!?])\\s+"))
    if (sentences.size < 3) return listOf(text)
    val out = mutableListOf<String>()
    var cur = ""
    for (s in sentences) {
        if (cur.length + s.length > 260 && cur.isNotEmpty()) {
            out.add(cur.trim())
            cur = s
        } else {
            cur = if (cur.isEmpty()) s else "$cur $s"
        }
    }
    if (cur.isNotBlank()) out.add(cur.trim())
    return if (out.size > 1) out else listOf(text)
}
