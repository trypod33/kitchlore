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
import com.trypod33.kitchlore.data.local.entity.Ingredient
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

/**
 * Scaling: multiplying every amount by (servings / baseServings), shown with
 * friendly kitchen fractions (1½ cups, ¾ tsp) when the number rounds cleanly
 * to halves, thirds, quarters or eighths.
 */
fun scaleAmount(amount: Double, factor: Double): Double = amount * factor

fun formatFractional(a: Double): String {
    val whole = a.toInt()
    val frac = a - whole
    if (frac < 0.03) return whole.toString()
    val options = mapOf(
        0.5 to "\u00BD",
        (1.0 / 3) to "\u2153",
        (2.0 / 3) to "\u2154",
        0.25 to "\u00BC",
        0.75 to "\u00BE",
        0.125 to "\u215B",
        0.375 to "\u215C",
        0.625 to "\u215D",
        0.875 to "\u215E",
    )
    val best = options.entries.minByOrNull { kotlin.math.abs(it.key - frac) }
    if (best != null && kotlin.math.abs(best.key - frac) < 0.07) {
        return if (whole > 0) "$whole${best.value}" else best.value
    }
    return String.format(java.util.Locale.US, "%.1f", a)
}

private val FRAC_CHARS = mapOf(
    '\u00BC' to 1.0 / 4,
    '\u00BD' to 1.0 / 2,
    '\u00BE' to 3.0 / 4,
    '\u2153' to 1.0 / 3,
    '\u2154' to 2.0 / 3,
    '\u2155' to 1.0 / 5,
    '\u2156' to 2.0 / 5,
    '\u2157' to 3.0 / 5,
    '\u2158' to 4.0 / 5,
    '\u2159' to 1.0 / 6,
    '\u215A' to 5.0 / 6,
    '\u215B' to 1.0 / 8,
    '\u215C' to 3.0 / 8,
    '\u215D' to 5.0 / 8,
    '\u215E' to 7.0 / 8,
    '\u2150' to 1.0 / 7,
    '\u2151' to 1.0 / 9,
    '\u2152' to 1.0 / 10,
)

/** One quantity token: "2", "0.5", "1/2", "1 1/2" pieces handled by the caller, "1\u00BD" mixed forms. */
internal fun quantityTokenValue(t: String): Double? {
    if (t.isEmpty()) return null
    if (t.length == 1) return FRAC_CHARS[t[0]]
    var i = 0
    var whole = 0.0
    var sawDigit = false
    while (i < t.length && (t[i].isDigit() || t[i] == '.')) {
        sawDigit = true
        i++
    }
    if (sawDigit) {
        whole = t.substring(0, i).toDoubleOrNull() ?: return null
    }
    if (i < t.length) {
        if (t[i] == '/') {
            val den = t.substring(i + 1).toDoubleOrNull() ?: return null
            if (den == 0.0) return null
            return whole / den
        }
        val frac = FRAC_CHARS[t[i]] ?: return null
        if (i == t.length - 1) return whole + frac
        return null
    }
    return if (sawDigit) whole else null
}

/**
 * Scales the quantity that opens a line, however it was written ("1 1/2 cups
 * rice" doubled becomes "3 cups rice", "\u00BC cup" tripled becomes "\u00BE cup"),
 * leaving the unit and name untouched. Lines that do not open with a quantity
 * come back unchanged.
 */
fun scaleQuantityText(text: String, factor: Double): String {
    if (factor == 1.0 || text.isBlank()) return text
    val tokens = text.trim().split(Regex("\\s+"))
    var value = 0.0
    var n = 0
    for (t in tokens) {
        val v = quantityTokenValue(t) ?: break
        value += v
        n++
    }
    if (n == 0) return text
    val scaled = formatFractional(value * factor)
    val rest = tokens.drop(n).joinToString(" ")
    return listOf(scaled, rest).filter { it.isNotBlank() }.joinToString(" ")
}

/** One tidy line for an ingredient row: quantity, unit, name (falls back to the raw OCR line). */
fun ingredientLabel(name: String, amount: Double?, unit: String?, rawText: String, factor: Double = 1.0): String {
    // When the recipe is scaled, rewrite the quantity in the raw line itself so
    // every recipe scales, parsed or not.
    if (factor != 1.0 && rawText.isNotBlank()) return scaleQuantityText(rawText, factor)
    val a = amount
    if (a == null) return rawText
    val qty = formatFractional(scaleAmount(a, factor))
    val unitPart = unit?.trim() ?: ""
    return listOf(qty, unitPart, name.trim()).filter { it.isNotBlank() }.joinToString(" ").ifBlank { rawText }
}
