package com.trypod33.kitchlore.ui

/**
 * The pantry lives inside Recipe hunt now (one screen, one box, no mode
 * switch). What survives here is the whole-word matcher the rest of the app
 * shares, so "pepper" does not ride on "bell pepper" alone standing in for
 * every pepper dish, and plurals still count.
 */
internal fun matchesPantryWord(name: String, pantryItem: String): Boolean {
    val pattern = Regex("\\b" + Regex.escape(pantryItem.lowercase().trim()) + "(es|s)?\\b")
    return pattern.containsMatchIn(name.trim())
}
