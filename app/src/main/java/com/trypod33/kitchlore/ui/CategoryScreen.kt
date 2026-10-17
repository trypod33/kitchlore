package com.trypod33.kitchlore.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trypod33.kitchlore.data.local.AppDatabase
import com.trypod33.kitchlore.data.local.entity.Recipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CategoryRoute(db: AppDatabase, tag: String, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val recipes by db.recipeDao().getAllRecipes().collectAsState(initial = emptyList())
    val filtered = if (tag == "All") recipes
    else recipes.filter { r -> r.tagsCsv.split(",").map { it.trim() }.contains(tag) }
    CategoryScreen(filtered, tag, onBack, onOpen, db)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryScreen(
    recipes: List<Recipe>,
    tag: String,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    db: AppDatabase,
) {
    val title = if (tag == "All") "All recipes" else tag
    val scope = rememberCoroutineScope()
    var tagEditing by remember { mutableStateOf<Recipe?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(
                title,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        if (recipes.isEmpty()) {
            Text(
                "Nothing here yet. Recipes tagged $title show up on this page.",
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(recipes, key = { it.id }) { recipe ->
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (pressed) 0.97f else 1f,
                        label = "cardScale",
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .combinedClickable(
                                interactionSource = interaction,
                                indication = null,
                                onLongClick = { tagEditing = recipe },
                            ) { onOpen(recipe.id) },
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Row(Modifier.height(104.dp), verticalAlignment = Alignment.CenterVertically) {
                            RecipeThumbnail(
                                recipe.originalImagePath,
                                Modifier
                                    .fillMaxHeight()
                                    .width(92.dp)
                                    .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)),
                            )
                            Column(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
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
            }
        }
    }
    tagEditing?.let { target ->
        TagEditorDialog(
            initialTags = target.tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            onDismiss = { tagEditing = null },
            onSave = { list ->
                tagEditing = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        db.recipeDao().update(
                            target.copy(tagsCsv = list.joinToString(", "), dateModified = System.currentTimeMillis())
                        )
                    }
                }
            },
        )
    }
}
