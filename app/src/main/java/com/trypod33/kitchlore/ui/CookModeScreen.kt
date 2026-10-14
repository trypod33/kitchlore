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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.trypod33.kitchlore.data.local.entity.Step
import kotlinx.coroutines.delay

@Composable
fun CookModeRoute(db: AppDatabase, recipeId: Long, onBack: () -> Unit) {
    val steps by db.stepDao().getStepsForRecipe(recipeId).collectAsState(initial = emptyList())
    val ingredients by db.ingredientDao().getIngredientsForRecipe(recipeId).collectAsState(initial = emptyList())
    CookModeScreen(steps, ingredients, onBack)
}

@Composable
fun CookModeScreen(steps: List<Step>, ingredients: List<Ingredient>, onBack: () -> Unit) {
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
                        ingredients.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEachIndexed { i, ing ->
                                    Text(
                                        ingredientLabel(ing.name, ing.amount, ing.unit, ing.rawText),
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f).padding(top = 4.dp, end = if (i == 0) 10.dp else 0.dp),
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        Text(step?.instructionText ?: "No steps", fontSize = 24.sp, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.weight(1f))
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
