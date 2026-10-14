package com.trypod33.kitchlore.ui

import android.accounts.AccountManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.trypod33.kitchlore.data.local.AppDatabase
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

@Composable
fun RecipeListScreen(
    db: AppDatabase,
    onOpen: (Long) -> Unit,
    onScan: () -> Unit,
    onPhoto: () -> Unit,
    onDoc: () -> Unit,
    onHunt: () -> Unit,
    onPantry: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var syncMsg by remember { mutableStateOf<String?>(null) }
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
    val allTags = recipes
        .flatMap { it.tagsCsv.split(",") }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
    val filtered = recipes.filter { r ->
        val tagOk = selectedTag == null || r.tagsCsv.split(",").map { it.trim() }.contains(selectedTag)
        val queryOk = trimmed.isEmpty() || r.title.contains(trimmed, ignoreCase = true)
        tagOk && queryOk
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
        if (allTags.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TagPill("All", selected = selectedTag == null) { selectedTag = null }
                allTags.take(8).forEach { tag ->
                    TagPill(tag, selected = selectedTag == tag) {
                        selectedTag = if (selectedTag == tag) null else tag
                    }
                }
            }
        }
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
        }
        syncMsg?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        when {
            filtered.isEmpty() && recipes.isEmpty() -> {
                Text(
                    "No recipes yet. Scan a page, import a photo or document, sync the Drive library, or hunt for one online.",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            filtered.isEmpty() -> {
                Text(
                    "No matches",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> {
                LazyColumn(Modifier.padding(horizontal = 20.dp)) {
                    items(filtered, key = { it.id }) { recipe ->
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (pressed) 0.97f else 1f,
                            label = "cardScale",
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
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
                }
            }
        }
    }
}
