package com.trypod33.kitchlore.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.trypod33.kitchlore.data.local.entity.Recipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun RecipeThumbnail(path: String?, modifier: Modifier = Modifier) {
    var bmp by remember(path) { mutableStateOf<Bitmap?>(null) }
    var missing by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path) {
        if (path.isNullOrBlank()) {
            missing = true
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 640) sample *= 2
                bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            } catch (t: Throwable) {
                bmp = null
            }
            if (bmp == null) missing = true
        }
    }
    val b = bmp
    if (b != null) {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    if (missing) "No photo" else "",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(6.dp),
                )
            }
        }
    }
}

fun servingLine(recipe: Recipe): String {
    val parts = mutableListOf<String>()
    if (recipe.baseServings > 0) parts.add("Serves ${recipe.baseServings}")
    recipe.prepTimeMinutes?.let { if (it > 0) parts.add("Prep ${it}m") }
    recipe.cookTimeMinutes?.let { if (it > 0) parts.add("Cook ${it}m") }
    return parts.joinToString(" · ")
}

fun formatAmount(a: Double): String =
    if (a == a.toLong().toDouble()) a.toLong().toString()
    else String.format(Locale.US, "%.1f", a)
