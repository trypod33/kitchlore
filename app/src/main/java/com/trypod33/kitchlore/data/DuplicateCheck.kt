package com.trypod33.kitchlore.data

import com.trypod33.kitchlore.data.local.AppDatabase
import com.trypod33.kitchlore.data.local.entity.Recipe

// Scan and document imports are about to get much more frequent, and
// duplicates come with them. Before saving, compare the new title against
// every existing recipe and flag a close match.
object DuplicateCheck {

    fun normalize(t: String): String =
        t.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun isCloseMatch(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isBlank() || nb.isBlank()) return false
        if (na == nb) return true
        // One title contains the other ("German Chocolate Cake" vs
        // "German Chocolate Cake - Grandma style").
        if (na.length >= 5 && nb.length >= 5 && (na.contains(nb) || nb.contains(na))) return true
        val ta = na.split(" ").filter { it.length > 2 }.toSet()
        val tb = nb.split(" ").filter { it.length > 2 }.toSet()
        if (ta.isEmpty() || tb.isEmpty()) return false
        val overlap = ta.intersect(tb).size.toDouble() / minOf(ta.size, tb.size)
        return overlap >= 0.75
    }

    /** The closest existing recipe for this title, or null when nothing matches. */
    suspend fun findDuplicate(db: AppDatabase, title: String): Recipe? {
        val candidates = db.recipeDao().getAllRecipesOnce()
        val matches = candidates.filter { isCloseMatch(it.title, title) }
        return matches.minByOrNull { normalize(it.title).length.coerceAtLeast(1) }
    }
}
